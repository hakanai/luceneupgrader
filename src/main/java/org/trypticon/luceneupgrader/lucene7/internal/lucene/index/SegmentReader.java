/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;

public final class SegmentReader extends CodecReader {
       
  private final SegmentCommitInfo si;
  // this is the original SI that IW uses internally but it's mutated behind the scenes
  // and we don't want this SI to be used for anything. Yet, IW needs this to do maintainance
  // and lookup pooled readers etc.
  private final SegmentCommitInfo originalSi;
  private final LeafMetaData metaData;
  private final Bits liveDocs;
  private final Bits hardLiveDocs;

  // Normally set to si.maxDoc - si.delDocCount, unless we
  // were created as an NRT reader from IW, in which case IW
  // tells us the number of live docs:
  private final int numDocs;

  final SegmentCoreReaders core;
  final SegmentDocValues segDocValues;

  final boolean isNRT;
  
  final DocValuesProducer docValuesProducer;
  final FieldInfos fieldInfos;

  SegmentReader(SegmentCommitInfo si, int createdVersionMajor, IOContext context) throws IOException {
    this.si = si.clone();
    this.originalSi = si;
    this.metaData = new LeafMetaData(createdVersionMajor, si.info.getMinVersion(), si.info.getIndexSort());

    // We pull liveDocs/DV updates from disk:
    this.isNRT = false;
    
    core = new SegmentCoreReaders(si.info.dir, si, context);
    segDocValues = new SegmentDocValues();
    
    boolean success = false;
    final Codec codec = si.info.getCodec();
    try {
      if (si.hasDeletions()) {
        // NOTE: the bitvector is stored using the regular directory, not cfs
        hardLiveDocs = liveDocs = codec.liveDocsFormat().readLiveDocs(directory(), si, IOContext.READONCE);
      } else {
        assert si.getDelCount() == 0;
        hardLiveDocs = liveDocs = null;
      }
      numDocs = si.info.maxDoc() - si.getDelCount();
      
      fieldInfos = initFieldInfos();
      docValuesProducer = initDocValuesProducer();
      assert assertLiveDocs(isNRT, hardLiveDocs, liveDocs);
      success = true;
    } finally {
      // With lock-less commits, it's entirely possible (and
      // fine) to hit a FileNotFound exception above.  In
      // this case, we want to explicitly close any subset
      // of things that were opened so that we don't have to
      // wait for a GC to do so.
      if (!success) {
        doClose();
      }
    }
  }

  SegmentReader(SegmentCommitInfo si, SegmentReader sr, Bits liveDocs, Bits hardLiveDocs, int numDocs, boolean isNRT) throws IOException {
    if (numDocs > si.info.maxDoc()) {
      throw new IllegalArgumentException("numDocs=" + numDocs + " but maxDoc=" + si.info.maxDoc());
    }
    if (liveDocs != null && liveDocs.length() != si.info.maxDoc()) {
      throw new IllegalArgumentException("maxDoc=" + si.info.maxDoc() + " but liveDocs.size()=" + liveDocs.length());
    }
    this.si = si.clone();
    this.originalSi = si;
    this.metaData = sr.getMetaData();
    this.liveDocs = liveDocs;
    this.hardLiveDocs = hardLiveDocs;
    assert assertLiveDocs(isNRT, hardLiveDocs, liveDocs);
    this.isNRT = isNRT;
    this.numDocs = numDocs;
    this.core = sr.core;
    core.incRef();
    this.segDocValues = sr.segDocValues;

    boolean success = false;
    try {
      fieldInfos = initFieldInfos();
      docValuesProducer = initDocValuesProducer();
      success = true;
    } finally {
      if (!success) {
        doClose();
      }
    }
  }

  private static boolean assertLiveDocs(boolean isNRT, Bits hardLiveDocs, Bits liveDocs) {
    if (isNRT) {
      assert hardLiveDocs == null || liveDocs != null : " liveDocs must be non null if hardLiveDocs are non null";
    } else {
      assert hardLiveDocs == liveDocs : "non-nrt case must have identical liveDocs";
    }
    return true;
  }

  private DocValuesProducer initDocValuesProducer() throws IOException {

    if (fieldInfos.hasDocValues() == false) {
      return null;
    } else {
      Directory dir;
      if (core.cfsReader != null) {
        dir = core.cfsReader;
      } else {
        dir = si.info.dir;
      }
      if (si.hasFieldUpdates()) {
        return new SegmentDocValuesProducer(si, dir, core.coreFieldInfos, fieldInfos, segDocValues);
      } else {
        // simple case, no DocValues updates
        return segDocValues.getDocValuesProducer(-1L, si, dir, fieldInfos);
      }
    }
  }
  
  private FieldInfos initFieldInfos() throws IOException {
    if (!si.hasFieldUpdates()) {
      return core.coreFieldInfos;
    } else {
      // updates always outside of CFS
      FieldInfosFormat fisFormat = si.info.getCodec().fieldInfosFormat();
      final String segmentSuffix = Long.toString(si.getFieldInfosGen(), Character.MAX_RADIX);
      return fisFormat.read(si.info.dir, si.info, segmentSuffix, IOContext.READONCE);
    }
  }
  
  @Override
  public Bits getLiveDocs() {
    ensureOpen();
    return liveDocs;
  }

  @Override
  protected void doClose() throws IOException {
    //System.out.println("SR.close seg=" + si);
    try {
      core.decRef();
    } finally {
      if (docValuesProducer instanceof SegmentDocValuesProducer) {
        segDocValues.decRef(((SegmentDocValuesProducer)docValuesProducer).dvGens);
      } else if (docValuesProducer != null) {
        segDocValues.decRef(Collections.singletonList(-1L));
      }
    }
  }

  @Override
  public FieldInfos getFieldInfos() {
    ensureOpen();
    return fieldInfos;
  }

  @Override
  public int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return numDocs;
  }

  @Override
  public int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return si.info.maxDoc();
  }

  @Override
  public TermVectorsReader getTermVectorsReader() {
    ensureOpen();
    return core.termVectorsLocal.get();
  }

  @Override
  public StoredFieldsReader getFieldsReader() {
    ensureOpen();
    return core.fieldsReaderLocal.get();
  }
  
  @Override
  public PointsReader getPointsReader() {
    ensureOpen();
    return core.pointsReader;
  }

  @Override
  public NormsProducer getNormsReader() {
    ensureOpen();
    return core.normsProducer;
  }
  
  @Override
  public DocValuesProducer getDocValuesReader() {
    ensureOpen();
    return docValuesProducer;
  }

  @Override
  public FieldsProducer getPostingsReader() {
    ensureOpen();
    return core.fields;
  }

  @Override
  public String toString() {
    // SegmentInfo.toString takes dir and number of
    // *pending* deletions; so we reverse compute that here:
    return si.toString(si.info.maxDoc() - numDocs - si.getDelCount());
  }
  
  public String getSegmentName() {
    return si.info.name;
  }
  
  public SegmentCommitInfo getSegmentInfo() {
    return si;
  }

  public Directory directory() {
    // Don't ensureOpen here -- in certain cases, when a
    // cloned/reopened reader needs to commit, it may call
    // this method on the closed original reader
    return si.info.dir;
  }

  private final Set<ClosedListener> readerClosedListeners = new CopyOnWriteArraySet<>();

  @Override
  void notifyReaderClosedListeners() throws IOException {
    synchronized(readerClosedListeners) {
      IOUtils.applyToAll(readerClosedListeners, l -> l.onClose(readerCacheHelper.getKey()));
    }
  }

  private final IndexReader.CacheHelper readerCacheHelper = new IndexReader.CacheHelper() {
    private final IndexReader.CacheKey cacheKey = new IndexReader.CacheKey();

    @Override
    public CacheKey getKey() {
      return cacheKey;
    }

    @Override
    public void addClosedListener(ClosedListener listener) {
      ensureOpen();
      readerClosedListeners.add(listener);
    }
  };

  @Override
  public CacheHelper getReaderCacheHelper() {
    return readerCacheHelper;
  }

  private final IndexReader.CacheHelper coreCacheHelper = new IndexReader.CacheHelper() {

    @Override
    public CacheKey getKey() {
      return core.getCacheHelper().getKey();
    }

    @Override
    public void addClosedListener(ClosedListener listener) {
      ensureOpen();
      core.getCacheHelper().addClosedListener(listener);
    }
  };

  @Override
  public CacheHelper getCoreCacheHelper() {
    return coreCacheHelper;
  }

  @Override
  public LeafMetaData getMetaData() {
    return metaData;
  }

  SegmentCommitInfo getOriginalSegmentInfo() {
    return originalSi;
  }

  public Bits getHardLiveDocs() {
    return hardLiveDocs;
  }
}
