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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.IOException;
import java.util.Collections;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;

public final class SegmentReader extends CodecReader {
       
  private final SegmentCommitInfo si;
  private final Bits liveDocs;

  // Normally set to si.maxDoc - si.delDocCount, unless we
  // were created as an NRT reader from IW, in which case IW
  // tells us the number of live docs:
  private final int numDocs;

  final SegmentCoreReaders core;
  final SegmentDocValues segDocValues;

  final boolean isNRT;
  
  final DocValuesProducer docValuesProducer;
  final FieldInfos fieldInfos;
  
  // TODO: why is this public?
  public SegmentReader(SegmentCommitInfo si, IOContext context) throws IOException {
    this.si = si;

    // We pull liveDocs/DV updates from disk:
    this.isNRT = false;
    
    core = new SegmentCoreReaders(si.info.dir, si, context);
    segDocValues = new SegmentDocValues();
    
    boolean success = false;
    final Codec codec = si.info.getCodec();
    try {
      if (si.hasDeletions()) {
        // NOTE: the bitvector is stored using the regular directory, not cfs
        liveDocs = codec.liveDocsFormat().readLiveDocs(directory(), si, IOContext.READONCE);
      } else {
        assert si.getDelCount() == 0;
        liveDocs = null;
      }
      numDocs = si.info.maxDoc() - si.getDelCount();
      
      fieldInfos = initFieldInfos();
      docValuesProducer = initDocValuesProducer();

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


  SegmentReader(SegmentCommitInfo si, SegmentReader sr) throws IOException {
    this(si, sr,
         si.hasDeletions() ? si.info.getCodec().liveDocsFormat().readLiveDocs(si.info.dir, si, IOContext.READONCE) : null,
         si.info.maxDoc() - si.getDelCount(), false);
  }


  SegmentReader(SegmentCommitInfo si, SegmentReader sr, Bits liveDocs, int numDocs) throws IOException {
    this(si, sr, liveDocs, numDocs, true);
  }
    

  SegmentReader(SegmentCommitInfo si, SegmentReader sr, Bits liveDocs, int numDocs, boolean isNRT) throws IOException {
    if (numDocs > si.info.maxDoc()) {
      throw new IllegalArgumentException("numDocs=" + numDocs + " but maxDoc=" + si.info.maxDoc());
    }
    if (liveDocs != null && liveDocs.length() != si.info.maxDoc()) {
      throw new IllegalArgumentException("maxDoc=" + si.info.maxDoc() + " but liveDocs.size()=" + liveDocs.length());
    }
    this.si = si;
    this.liveDocs = liveDocs;
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

  private DocValuesProducer initDocValuesProducer() throws IOException {
    final Directory dir = core.cfsReader != null ? core.cfsReader : si.info.dir;

    if (!fieldInfos.hasDocValues()) {
      return null;
    } else if (si.hasFieldUpdates()) {
      return new SegmentDocValuesProducer(si, dir, core.coreFieldInfos, fieldInfos, segDocValues);
    } else {
      // simple case, no DocValues updates
      return segDocValues.getDocValuesProducer(-1L, si, dir, fieldInfos);
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
      try {
        super.doClose();
      } finally {
        if (docValuesProducer instanceof SegmentDocValuesProducer) {
          segDocValues.decRef(((SegmentDocValuesProducer)docValuesProducer).dvGens);
        } else if (docValuesProducer != null) {
          segDocValues.decRef(Collections.singletonList(-1L));
        }
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
  public PointValues getPointValues() {
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
  public PointsReader getPointsReader() {
    ensureOpen();
    return core.pointsReader;
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

  // This is necessary so that cloned SegmentReaders (which
  // share the underlying postings data) will map to the
  // same entry for CachingWrapperFilter.  See LUCENE-1579.
  @Override
  public Object getCoreCacheKey() {
    // NOTE: if this ever changes, be sure to fix
    // SegmentCoreReader.notifyCoreClosedListeners to match!
    // Today it passes "this" as its coreCacheKey:
    return core;
  }

  @Override
  public Object getCombinedCoreAndDeletesKey() {
    return this;
  }

  @Override
  public void addCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.addCoreClosedListener(listener);
  }
  
  @Override
  public void removeCoreClosedListener(CoreClosedListener listener) {
    ensureOpen();
    core.removeCoreClosedListener(listener);
  }

  @Override
  public Sort getIndexSort() {
    return si.info.getIndexSort();
  }
}
