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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.LimitTokenCountAnalyzer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Similarity;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Clarification: Check Points (and commits)
 * IndexWriter writes new index files to the directory without writing a new segments_N
 * file which references these new files. It also means that the state of 
 * the in memory SegmentInfos object is different than the most recent
 * segments_N file written to the directory.
 * 
 * Each time the SegmentInfos is changed, and matches the (possibly 
 * modified) directory files, we have a new "check point". 
 * If the modified/new SegmentInfos is written to disk - as a new 
 * (generation of) segments_N file - this check point is also an 
 * IndexCommit.
 * 
 * A new checkpoint always replaces the previous checkpoint and 
 * becomes the new "front" of the index. This allows the IndexFileDeleter 
 * to delete files that are referenced only by stale checkpoints.
 * (files that were created since the last commit, but are no longer
 * referenced by the "front" of the index). For this, IndexFileDeleter 
 * keeps track of the last non commit checkpoint.
 */
public class IndexWriter implements Closeable, TwoPhaseCommit {

  @Deprecated
  public static long WRITE_LOCK_TIMEOUT = IndexWriterConfig.WRITE_LOCK_TIMEOUT;

  private long writeLockTimeout;

  public static final String WRITE_LOCK_NAME = "write.lock";

  @Deprecated
  public final static int DISABLE_AUTO_FLUSH = IndexWriterConfig.DISABLE_AUTO_FLUSH;

  @Deprecated
  public final static int DEFAULT_MAX_BUFFERED_DOCS = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS;

  @Deprecated
  public final static double DEFAULT_RAM_BUFFER_SIZE_MB = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;

  @Deprecated
  public final static int DEFAULT_MAX_BUFFERED_DELETE_TERMS = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DELETE_TERMS;

  @Deprecated
  public final static int DEFAULT_MAX_FIELD_LENGTH = MaxFieldLength.UNLIMITED.getLimit();

  @Deprecated
  public final static int DEFAULT_TERM_INDEX_INTERVAL = IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL;

  public final static int MAX_TERM_LENGTH = DocumentsWriter.MAX_TERM_LENGTH;

  // The normal read buffer size defaults to 1024, but
  // increasing this during merging seems to yield
  // performance gains.  However we don't want to increase
  // it too much because there are quite a few
  // BufferedIndexInputs created during merging.  See
  // LUCENE-888 for details.
  private final static int MERGE_READ_BUFFER_SIZE = 4096;

  // Used for printing messages
  private static final AtomicInteger MESSAGE_ID = new AtomicInteger();
  private int messageID = MESSAGE_ID.getAndIncrement();
  volatile private boolean hitOOM;

  private final Directory directory;  // where this index resides
  private final Analyzer analyzer;    // how to analyze text

  // TODO 4.0: this should be made final once the setter is out
  private /*final*/Similarity similarity = Similarity.getDefault(); // how to normalize

  private volatile long changeCount; // increments every time a change is completed
  private long lastCommitChangeCount; // last changeCount that was committed

  private List<SegmentInfo> rollbackSegments;      // list of segmentInfo we will fallback to if the commit fails

  volatile SegmentInfos pendingCommit;            // set when a commit is pending (after prepareCommit() & before commit())
  volatile long pendingCommitChangeCount;

  final SegmentInfos segmentInfos = new SegmentInfos();       // the segments
  private Collection<String> filesToCommit;

  private DocumentsWriter docWriter;
  private IndexFileDeleter deleter;

  // used by forceMerge to note those needing merging
  private Map<SegmentInfo,Boolean> segmentsToMerge = new HashMap<SegmentInfo,Boolean>();
  private int mergeMaxNumSegments;

  private Lock writeLock;

  private volatile boolean closed;
  private volatile boolean closing;

  // Holds all SegmentInfo instances currently involved in
  // merges
  private HashSet<SegmentInfo> mergingSegments = new HashSet<SegmentInfo>();

  private MergePolicy mergePolicy;
  // TODO 4.0: this should be made final once the setter is removed
  private /*final*/MergeScheduler mergeScheduler;
  private LinkedList<MergePolicy.OneMerge> pendingMerges = new LinkedList<MergePolicy.OneMerge>();
  private Set<MergePolicy.OneMerge> runningMerges = new HashSet<MergePolicy.OneMerge>();
  private List<MergePolicy.OneMerge> mergeExceptions = new ArrayList<MergePolicy.OneMerge>();
  private long mergeGen;
  private boolean stopMerges;

  private final AtomicInteger flushCount = new AtomicInteger();
  private final AtomicInteger flushDeletesCount = new AtomicInteger();

  final ReaderPool readerPool = new ReaderPool();
  final BufferedDeletesStream bufferedDeletesStream;
  
  // This is a "write once" variable (like the organic dye
  // on a DVD-R that may or may not be heated by a laser and
  // then cooled to permanently record the event): it's
  // false, until getReader() is called for the first time,
  // at which point it's switched to true and never changes
  // back to false.  Once this is true, we hold open and
  // reuse SegmentReader instances internally for applying
  // deletes, doing merges, and reopening near real-time
  // readers.
  private volatile boolean poolReaders;

  // The instance that was passed to the constructor. It is saved only in order
  // to allow users to query an IndexWriter settings.
  private final IndexWriterConfig config;

  // The PayloadProcessorProvider to use when segments are merged
  private PayloadProcessorProvider payloadProcessorProvider;

  // for testing
  boolean anyNonBulkMerges;

  @Deprecated
  public IndexReader getReader() throws IOException {
    return getReader(config.getReaderTermsIndexDivisor(), true);
  }

  IndexReader getReader(boolean applyAllDeletes) throws IOException {
    return getReader(config.getReaderTermsIndexDivisor(), applyAllDeletes);
  }


  @Deprecated
  public IndexReader getReader(int termInfosIndexDivisor) throws IOException {
    return getReader(termInfosIndexDivisor, true);
  }

  IndexReader getReader(int termInfosIndexDivisor, boolean applyAllDeletes) throws IOException {
    ensureOpen();
    
    final long tStart = System.currentTimeMillis();

    if (infoStream != null) {
      message("flush at getReader");
    }

    // Do this up front before flushing so that the readers
    // obtained during this flush are pooled, the first time
    // this method is called:
    poolReaders = true;

    // Prevent segmentInfos from changing while opening the
    // reader; in theory we could do similar retry logic,
    // just like we do when loading segments_N
    IndexReader r;
    synchronized(this) {
      flush(false, applyAllDeletes);
      r = new ReadOnlyDirectoryReader(this, segmentInfos, termInfosIndexDivisor, applyAllDeletes);
      if (infoStream != null) {
        message("return reader version=" + r.getVersion() + " reader=" + r);
      }
    }

    maybeMerge();

    if (infoStream != null) {
      message("getReader took " + (System.currentTimeMillis() - tStart) + " msec");
    }
    return r;
  }



  class ReaderPool {

    private final Map<SegmentInfo,SegmentReader> readerMap = new HashMap<SegmentInfo,SegmentReader>();

    synchronized void clear(List<SegmentInfo> infos) throws IOException {
      if (infos == null) {
        for (Map.Entry<SegmentInfo,SegmentReader> ent: readerMap.entrySet()) {
          ent.getValue().hasChanges = false;
        }
      } else {
        for (final SegmentInfo info: infos) {
          final SegmentReader r = readerMap.get(info);
          if (r != null) {
            r.hasChanges = false;
          }
        }     
      }
    }
    
    // used only by asserts
    public synchronized boolean infoIsLive(SegmentInfo info) {
      int idx = segmentInfos.indexOf(info);
      assert idx != -1: "info=" + info + " isn't in pool";
      assert segmentInfos.info(idx) == info: "info=" + info + " doesn't match live info in segmentInfos";
      return true;
    }

    public synchronized SegmentInfo mapToLive(SegmentInfo info) {
      int idx = segmentInfos.indexOf(info);
      if (idx != -1) {
        info = segmentInfos.info(idx);
      }
      return info;
    }
    
    public synchronized boolean release(SegmentReader sr) throws IOException {
      return release(sr, false);
    }
    
    public synchronized boolean release(SegmentReader sr, boolean drop) throws IOException {

      final boolean pooled = readerMap.containsKey(sr.getSegmentInfo());

      assert !pooled || readerMap.get(sr.getSegmentInfo()) == sr;

      // Drop caller's ref; for an external reader (not
      // pooled), this decRef will close it
      sr.decRef();

      if (pooled && (drop || (!poolReaders && sr.getRefCount() == 1))) {

        // We invoke deleter.checkpoint below, so we must be
        // sync'd on IW if there are changes:
        assert !sr.hasChanges || Thread.holdsLock(IndexWriter.this);

        // Discard (don't save) changes when we are dropping
        // the reader; this is used only on the sub-readers
        // after a successful merge.
        sr.hasChanges &= !drop;

        final boolean hasChanges = sr.hasChanges;

        // Drop our ref -- this will commit any pending
        // changes to the dir
        sr.close();

        // We are the last ref to this reader; since we're
        // not pooling readers, we release it:
        readerMap.remove(sr.getSegmentInfo());

        return hasChanges;
      }

      return false;
    }

    public synchronized void drop(List<SegmentInfo> infos) throws IOException {
      for(SegmentInfo info : infos) {
        drop(info);
      }
    }

    public synchronized void drop(SegmentInfo info) throws IOException {
      final SegmentReader sr = readerMap.get(info);
      if (sr != null) {
        sr.hasChanges = false;
        readerMap.remove(info);
        sr.close();
      }
    }
    
    public synchronized void dropAll() throws IOException {
      for(SegmentReader reader : readerMap.values()) {
        reader.hasChanges = false;

        // NOTE: it is allowed that this decRef does not
        // actually close the SR; this can happen when a
        // near real-time reader using this SR is still open
        reader.decRef();
      }
      readerMap.clear();
    }

    synchronized void close() throws IOException {
      // We invoke deleter.checkpoint below, so we must be
      // sync'd on IW:
      assert Thread.holdsLock(IndexWriter.this);

      for(Map.Entry<SegmentInfo,SegmentReader> ent : readerMap.entrySet()) {
        
        SegmentReader sr = ent.getValue();
        if (sr.hasChanges) {
          assert infoIsLive(sr.getSegmentInfo());
          sr.doCommit(null);

          // Must checkpoint w/ deleter, because this
          // segment reader will have created new _X_N.del
          // file.
          deleter.checkpoint(segmentInfos, false);
        }

        // NOTE: it is allowed that this decRef does not
        // actually close the SR; this can happen when a
        // near real-time reader is kept open after the
        // IndexWriter instance is closed
        sr.decRef();
      }

      readerMap.clear();
    }
    
    synchronized void commit(SegmentInfos infos) throws IOException {

      // We invoke deleter.checkpoint below, so we must be
      // sync'd on IW:
      assert Thread.holdsLock(IndexWriter.this);

      for (SegmentInfo info : infos) {

        final SegmentReader sr = readerMap.get(info);
        if (sr != null && sr.hasChanges) {
          assert infoIsLive(info);
          sr.doCommit(null);
          // Must checkpoint w/ deleter, because this
          // segment reader will have created new _X_N.del
          // file.
          deleter.checkpoint(segmentInfos, false);
        }
      }
    }
    
    public synchronized SegmentReader getReadOnlyClone(SegmentInfo info, boolean doOpenStores, int termInfosIndexDivisor) throws IOException {
      SegmentReader sr = get(info, doOpenStores, BufferedIndexInput.BUFFER_SIZE, termInfosIndexDivisor);
      try {
        return (SegmentReader) sr.clone(true);
      } finally {
        sr.decRef();
      }
    }
   
    public synchronized SegmentReader get(SegmentInfo info, boolean doOpenStores) throws IOException {
      return get(info, doOpenStores, BufferedIndexInput.BUFFER_SIZE, config.getReaderTermsIndexDivisor());
    }

    public synchronized SegmentReader get(SegmentInfo info, boolean doOpenStores, int readBufferSize, int termsIndexDivisor) throws IOException {

      if (poolReaders) {
        readBufferSize = BufferedIndexInput.BUFFER_SIZE;
      }

      SegmentReader sr = readerMap.get(info);
      if (sr == null) {
        // TODO: we may want to avoid doing this while
        // synchronized
        // Returns a ref, which we xfer to readerMap:
        sr = SegmentReader.get(false, info.dir, info, readBufferSize, doOpenStores, termsIndexDivisor);

        if (info.dir == directory) {
          // Only pool if reader is not external
          readerMap.put(info, sr);
        }
      } else {
        if (doOpenStores) {
          sr.openDocStores();
        }
        if (termsIndexDivisor != -1 && !sr.termsIndexLoaded()) {
          // If this reader was originally opened because we
          // needed to merge it, we didn't load the terms
          // index.  But now, if the caller wants the terms
          // index (eg because it's doing deletes, or an NRT
          // reader is being opened) we ask the reader to
          // load its terms index.
          sr.loadTermsIndex(termsIndexDivisor);
        }
      }

      // Return a ref to our caller
      if (info.dir == directory) {
        // Only incRef if we pooled (reader is not external)
        sr.incRef();
      }
      return sr;
    }

    // Returns a ref
    public synchronized SegmentReader getIfExists(SegmentInfo info) throws IOException {
      SegmentReader sr = readerMap.get(info);
      if (sr != null) {
        sr.incRef();
      }
      return sr;
    }
  }
  
  
  
  public int numDeletedDocs(SegmentInfo info) throws IOException {
    ensureOpen(false);
    SegmentReader reader = readerPool.getIfExists(info);
    try {
      if (reader != null) {
        return reader.numDeletedDocs();
      } else {
        return info.getDelCount();
      }
    } finally {
      if (reader != null) {
        readerPool.release(reader);
      }
    }
  }
  
  protected final void ensureOpen(boolean includePendingClose) throws AlreadyClosedException {
    if (closed || (includePendingClose && closing)) {
      throw new AlreadyClosedException("this IndexWriter is closed");
    }
  }

  protected final void ensureOpen() throws AlreadyClosedException {
    ensureOpen(true);
  }

  public void message(String message) {
    if (infoStream != null)
      infoStream.println("IW " + messageID + " [" + new Date() + "; " + Thread.currentThread().getName() + "]: " + message);
  }

  private LogMergePolicy getLogMergePolicy() {
    if (mergePolicy instanceof LogMergePolicy)
      return (LogMergePolicy) mergePolicy;
    else
      throw new IllegalArgumentException("this method can only be called when the merge policy is the default LogMergePolicy");
  }


  @Deprecated
  public boolean getUseCompoundFile() {
    return getLogMergePolicy().getUseCompoundFile();
  }

  @Deprecated
  public void setUseCompoundFile(boolean value) {
    getLogMergePolicy().setUseCompoundFile(value);
  }


  @Deprecated
  public void setSimilarity(Similarity similarity) {
    ensureOpen();
    this.similarity = similarity;
    docWriter.setSimilarity(similarity);
    // Required so config.getSimilarity returns the right value. But this will
    // go away together with the method in 4.0.
    config.setSimilarity(similarity);
  }


  @Deprecated
  public Similarity getSimilarity() {
    ensureOpen();
    return similarity;
  }


  @Deprecated
  public void setTermIndexInterval(int interval) {
    ensureOpen();
    config.setTermIndexInterval(interval);
  }


  @Deprecated
  public int getTermIndexInterval() {
    // We pass false because this method is called by SegmentMerger while we are in the process of closing
    ensureOpen(false);
    return config.getTermIndexInterval();
  }

  @Deprecated
  public IndexWriter(Directory d, Analyzer a, boolean create, MaxFieldLength mfl)
       throws CorruptIndexException, LockObtainFailedException, IOException {
    this(d, new IndexWriterConfig(Version.LUCENE_31, a).setOpenMode(
        create ? OpenMode.CREATE : OpenMode.APPEND));
    setMaxFieldLength(mfl.getLimit());
  }

  @Deprecated
  public IndexWriter(Directory d, Analyzer a, MaxFieldLength mfl)
    throws CorruptIndexException, LockObtainFailedException, IOException {
    this(d, new IndexWriterConfig(Version.LUCENE_31, a));
    setMaxFieldLength(mfl.getLimit());
  }

  @Deprecated
  public IndexWriter(Directory d, Analyzer a, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl)
    throws CorruptIndexException, LockObtainFailedException, IOException {
    this(d, new IndexWriterConfig(Version.LUCENE_31, a).setIndexDeletionPolicy(deletionPolicy));
    setMaxFieldLength(mfl.getLimit());
  }

  @Deprecated
  public IndexWriter(Directory d, Analyzer a, boolean create, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl)
       throws CorruptIndexException, LockObtainFailedException, IOException {
    this(d, new IndexWriterConfig(Version.LUCENE_31, a).setOpenMode(
        create ? OpenMode.CREATE : OpenMode.APPEND).setIndexDeletionPolicy(deletionPolicy));
    setMaxFieldLength(mfl.getLimit());
  }
  
  @Deprecated
  public IndexWriter(Directory d, Analyzer a, IndexDeletionPolicy deletionPolicy, MaxFieldLength mfl, IndexCommit commit)
       throws CorruptIndexException, LockObtainFailedException, IOException {
    this(d, new IndexWriterConfig(Version.LUCENE_31, a)
        .setOpenMode(OpenMode.APPEND).setIndexDeletionPolicy(deletionPolicy).setIndexCommit(commit));
    setMaxFieldLength(mfl.getLimit());
  }

  public IndexWriter(Directory d, IndexWriterConfig conf)
      throws CorruptIndexException, LockObtainFailedException, IOException {
    config = (IndexWriterConfig) conf.clone();
    directory = d;
    analyzer = conf.getAnalyzer();
    infoStream = defaultInfoStream;
    writeLockTimeout = conf.getWriteLockTimeout();
    similarity = conf.getSimilarity();
    mergePolicy = conf.getMergePolicy();
    mergePolicy.setIndexWriter(this);
    mergeScheduler = conf.getMergeScheduler();
    bufferedDeletesStream = new BufferedDeletesStream(messageID);
    bufferedDeletesStream.setInfoStream(infoStream);
    poolReaders = conf.getReaderPooling();

    writeLock = directory.makeLock(WRITE_LOCK_NAME);

    if (!writeLock.obtain(writeLockTimeout)) // obtain write lock
      throw new LockObtainFailedException("Index locked for write: " + writeLock);

    
    boolean success = false;
    try {
      OpenMode mode = conf.getOpenMode();
      boolean create;
      if (mode == OpenMode.CREATE) {
        create = true;
      } else if (mode == OpenMode.APPEND) {
        create = false;
      } else {
        // CREATE_OR_APPEND - create only if an index does not exist
        create = !IndexReader.indexExists(directory);
      }

      // TODO: we should check whether this index is too old,
      // and throw an IndexFormatTooOldExc up front, here,
      // instead of later when merge, applyDeletes, getReader
      // is attempted.  I think to do this we should store the
      // oldest segment's version in segments_N.

      if (create) {
        // Try to read first.  This is to allow create
        // against an index that's currently open for
        // searching.  In this case we write the next
        // segments_N file with no segments:
        try {
          segmentInfos.read(directory);
          segmentInfos.clear();
        } catch (IOException e) {
          // Likely this means it's a fresh directory
        }

        // Record that we have a change (zero out all
        // segments) pending:
        changeCount++;
        segmentInfos.changed();
      } else {
        segmentInfos.read(directory);

        IndexCommit commit = conf.getIndexCommit();
        if (commit != null) {
          // Swap out all segments, but, keep metadata in
          // SegmentInfos, like version & generation, to
          // preserve write-once.  This is important if
          // readers are open against the future commit
          // points.
          if (commit.getDirectory() != directory)
            throw new IllegalArgumentException("IndexCommit's directory doesn't match my directory");
          SegmentInfos oldInfos = new SegmentInfos();
          oldInfos.read(directory, commit.getSegmentsFileName());
          segmentInfos.replace(oldInfos);
          changeCount++;
          segmentInfos.changed();
          if (infoStream != null)
            message("init: loaded commit \"" + commit.getSegmentsFileName() + "\"");
        }
      }

      rollbackSegments = segmentInfos.createBackupSegmentInfos(true);

      docWriter = new DocumentsWriter(config, directory, this, getCurrentFieldInfos(), bufferedDeletesStream);
      docWriter.setInfoStream(infoStream);
      docWriter.setMaxFieldLength(maxFieldLength);

      // Default deleter (for backwards compatibility) is
      // KeepOnlyLastCommitDeleter:
      synchronized(this) {
        deleter = new IndexFileDeleter(directory,
                                       conf.getIndexDeletionPolicy(),
                                       segmentInfos, infoStream,
                                       this);
      }

      if (deleter.startingCommitDeleted) {
        // Deletion policy deleted the "head" commit point.
        // We have to mark ourself as changed so that if we
        // are closed w/o any further changes we write a new
        // segments_N file.
        changeCount++;
        segmentInfos.changed();
      }

      if (infoStream != null) {
        messageState();
      }

      success = true;

    } finally {
      if (!success) {
        if (infoStream != null) {
          message("init: hit exception on init; releasing write lock");
        }
        try {
          writeLock.release();
        } catch (Throwable t) {
          // don't mask the original exception
        }
        writeLock = null;
      }
    }
  }

  private FieldInfos getFieldInfos(SegmentInfo info) throws IOException {
    Directory cfsDir = null;
    try {
      if (info.getUseCompoundFile()) {
        cfsDir = new CompoundFileReader(directory, IndexFileNames.segmentFileName(info.name, IndexFileNames.COMPOUND_FILE_EXTENSION));
      } else {
        cfsDir = directory;
      }
      return new FieldInfos(cfsDir, IndexFileNames.segmentFileName(info.name, IndexFileNames.FIELD_INFOS_EXTENSION));
    } finally {
      if (info.getUseCompoundFile() && cfsDir != null) {
        cfsDir.close();
      }
    }
  }

  private FieldInfos getCurrentFieldInfos() throws IOException {
    final FieldInfos fieldInfos;
    if (segmentInfos.size() > 0) {
      if (segmentInfos.getFormat() > SegmentInfos.FORMAT_DIAGNOSTICS) {
        // Pre-3.1 index.  In this case we sweep all
        // segments, merging their FieldInfos:
        fieldInfos = new FieldInfos();
        for(SegmentInfo info : segmentInfos) {
          final FieldInfos segFieldInfos = getFieldInfos(info);
          final int fieldCount = segFieldInfos.size();
          for(int fieldNumber=0;fieldNumber<fieldCount;fieldNumber++) {
            fieldInfos.add(segFieldInfos.fieldInfo(fieldNumber));
          }
        }
      } else {
        // Already a 3.1 index; just seed the FieldInfos
        // from the last segment
        fieldInfos = getFieldInfos(segmentInfos.info(segmentInfos.size()-1));
      }
    } else {
      fieldInfos = new FieldInfos();
    }
    return fieldInfos;
  }

  public IndexWriterConfig getConfig() {
    ensureOpen(false);
    return config;
  }
  
  @Deprecated
  public void setMergePolicy(MergePolicy mp) {
    ensureOpen();
    if (mp == null)
      throw new NullPointerException("MergePolicy must be non-null");

    if (mergePolicy != mp)
      mergePolicy.close();
    mergePolicy = mp;
    mergePolicy.setIndexWriter(this);
    pushMaxBufferedDocs();
    if (infoStream != null)
      message("setMergePolicy " + mp);
    // Required so config.getMergePolicy returns the right value. But this will
    // go away together with the method in 4.0.
    config.setMergePolicy(mp);
  }

  @Deprecated
  public MergePolicy getMergePolicy() {
    ensureOpen();
    return mergePolicy;
  }

  @Deprecated
  synchronized public void setMergeScheduler(MergeScheduler mergeScheduler) throws CorruptIndexException, IOException {
    ensureOpen();
    if (mergeScheduler == null)
      throw new NullPointerException("MergeScheduler must be non-null");

    if (this.mergeScheduler != mergeScheduler) {
      finishMerges(true);
      this.mergeScheduler.close();
    }
    this.mergeScheduler = mergeScheduler;
    if (infoStream != null)
      message("setMergeScheduler " + mergeScheduler);
    // Required so config.getMergeScheduler returns the right value. But this will
    // go away together with the method in 4.0.
    config.setMergeScheduler(mergeScheduler);
  }

  @Deprecated
  public MergeScheduler getMergeScheduler() {
    ensureOpen();
    return mergeScheduler;
  }


  @Deprecated
  public void setMaxMergeDocs(int maxMergeDocs) {
    getLogMergePolicy().setMaxMergeDocs(maxMergeDocs);
  }

  @Deprecated
  public int getMaxMergeDocs() {
    return getLogMergePolicy().getMaxMergeDocs();
  }

  @Deprecated
  public void setMaxFieldLength(int maxFieldLength) {
    ensureOpen();
    this.maxFieldLength = maxFieldLength;
    docWriter.setMaxFieldLength(maxFieldLength);
    if (infoStream != null)
      message("setMaxFieldLength " + maxFieldLength);
  }

  @Deprecated
  public int getMaxFieldLength() {
    ensureOpen();
    return maxFieldLength;
  }

  @Deprecated
  public void setReaderTermsIndexDivisor(int divisor) {
    ensureOpen();
    config.setReaderTermsIndexDivisor(divisor);
    if (infoStream != null) {
      message("setReaderTermsIndexDivisor " + divisor);
    }
  }

  @Deprecated
  public int getReaderTermsIndexDivisor() {
    ensureOpen();
    return config.getReaderTermsIndexDivisor();
  }


  @Deprecated
  public void setMaxBufferedDocs(int maxBufferedDocs) {
    ensureOpen();
    pushMaxBufferedDocs();
    if (infoStream != null) {
      message("setMaxBufferedDocs " + maxBufferedDocs);
    }
    // Required so config.getMaxBufferedDocs returns the right value. But this
    // will go away together with the method in 4.0.
    config.setMaxBufferedDocs(maxBufferedDocs);
  }

  private void pushMaxBufferedDocs() {
    if (config.getMaxBufferedDocs() != DISABLE_AUTO_FLUSH) {
      final MergePolicy mp = mergePolicy;
      if (mp instanceof LogDocMergePolicy) {
        LogDocMergePolicy lmp = (LogDocMergePolicy) mp;
        final int maxBufferedDocs = config.getMaxBufferedDocs();
        if (lmp.getMinMergeDocs() != maxBufferedDocs) {
          if (infoStream != null)
            message("now push maxBufferedDocs " + maxBufferedDocs + " to LogDocMergePolicy");
          lmp.setMinMergeDocs(maxBufferedDocs);
        }
      }
    }
  }

  @Deprecated
  public int getMaxBufferedDocs() {
    ensureOpen();
    return config.getMaxBufferedDocs();
  }


  @Deprecated
  public void setRAMBufferSizeMB(double mb) {
    if (infoStream != null) {
      message("setRAMBufferSizeMB " + mb);
    }
    // Required so config.getRAMBufferSizeMB returns the right value. But this
    // will go away together with the method in 4.0.
    config.setRAMBufferSizeMB(mb);
  }

  @Deprecated
  public double getRAMBufferSizeMB() {
    return config.getRAMBufferSizeMB();
  }

  @Deprecated
  public void setMaxBufferedDeleteTerms(int maxBufferedDeleteTerms) {
    ensureOpen();
    if (infoStream != null)
      message("setMaxBufferedDeleteTerms " + maxBufferedDeleteTerms);
    // Required so config.getMaxBufferedDeleteTerms returns the right value. But
    // this will go away together with the method in 4.0.
    config.setMaxBufferedDeleteTerms(maxBufferedDeleteTerms);
  }

  @Deprecated
  public int getMaxBufferedDeleteTerms() {
    ensureOpen();
    return config.getMaxBufferedDeleteTerms();
  }


  @Deprecated
  public void setMergeFactor(int mergeFactor) {
    getLogMergePolicy().setMergeFactor(mergeFactor);
  }

  @Deprecated
  public int getMergeFactor() {
    return getLogMergePolicy().getMergeFactor();
  }


  public static void setDefaultInfoStream(PrintStream infoStream) {
    IndexWriter.defaultInfoStream = infoStream;
  }

  public static PrintStream getDefaultInfoStream() {
    return IndexWriter.defaultInfoStream;
  }


  public void setInfoStream(PrintStream infoStream) throws IOException {
    ensureOpen();
    this.infoStream = infoStream;
    docWriter.setInfoStream(infoStream);
    deleter.setInfoStream(infoStream);
    bufferedDeletesStream.setInfoStream(infoStream);
    if (infoStream != null)
      messageState();
  }

  private void messageState() throws IOException {
    message("\ndir=" + directory + "\n" +
            "index=" + segString() + "\n" +
            "version=" + Constants.LUCENE_VERSION + "\n" +
            config.toString());
  }

  public PrintStream getInfoStream() {
    ensureOpen();
    return infoStream;
  }

  public boolean verbose() {
    return infoStream != null;
  }
  
  @Deprecated
  public void setWriteLockTimeout(long writeLockTimeout) {
    ensureOpen();
    this.writeLockTimeout = writeLockTimeout;
    // Required so config.getWriteLockTimeout returns the right value. But this
    // will go away together with the method in 4.0.
    config.setWriteLockTimeout(writeLockTimeout);
  }

  @Deprecated
  public long getWriteLockTimeout() {
    ensureOpen();
    return writeLockTimeout;
  }

  @Deprecated
  public static void setDefaultWriteLockTimeout(long writeLockTimeout) {
    IndexWriterConfig.setDefaultWriteLockTimeout(writeLockTimeout);
  }

  @Deprecated
  public static long getDefaultWriteLockTimeout() {
    return IndexWriterConfig.getDefaultWriteLockTimeout();
  }

  public void close() throws CorruptIndexException, IOException {
    close(true);
  }

  public void close(boolean waitForMerges) throws CorruptIndexException, IOException {

    // Ensure that only one thread actually gets to do the closing:
    if (shouldClose()) {
      // If any methods have hit OutOfMemoryError, then abort
      // on close, in case the internal state of IndexWriter
      // or DocumentsWriter is corrupt
      if (hitOOM)
        rollbackInternal();
      else
        closeInternal(waitForMerges);
    }
  }

  // Returns true if this thread should attempt to close, or
  // false if IndexWriter is now closed; else, waits until
  // another thread finishes closing
  synchronized private boolean shouldClose() {
    while(true) {
      if (!closed) {
        if (!closing) {
          closing = true;
          return true;
        } else {
          // Another thread is presently trying to close;
          // wait until it finishes one way (closes
          // successfully) or another (fails to close)
          doWait();
        }
      } else
        return false;
    }
  }

  private void closeInternal(boolean waitForMerges) throws CorruptIndexException, IOException {

    try {
      if (pendingCommit != null) {
        throw new IllegalStateException("cannot close: prepareCommit was already called with no corresponding call to commit");
      }

      if (infoStream != null) {
        message("now flush at close waitForMerges=" + waitForMerges);
      }

      docWriter.close();

      // Only allow a new merge to be triggered if we are
      // going to wait for merges:
      if (!hitOOM) {
        flush(waitForMerges, true);
      }

      if (waitForMerges)
        // Give merge scheduler last chance to run, in case
        // any pending merges are waiting:
        mergeScheduler.merge(this);

      mergePolicy.close();

      synchronized(this) {
        finishMerges(waitForMerges);
        stopMerges = true;
      }

      mergeScheduler.close();

      if (infoStream != null)
        message("now call final commit()");
      
      if (!hitOOM) {
        commitInternal(null);
      }

      if (infoStream != null)
        message("at close: " + segString());

      synchronized(this) {
        readerPool.close();
        docWriter = null;
        deleter.close();
      }
      
      if (writeLock != null) {
        writeLock.release();                          // release write lock
        writeLock = null;
      }
      synchronized(this) {
        closed = true;
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "closeInternal");
    } finally {
      synchronized(this) {
        closing = false;
        notifyAll();
        if (!closed) {
          if (infoStream != null)
            message("hit exception while closing");
        }
      }
    }
  }

  public Directory getDirectory() {
    // Pass false because the flush during closing calls getDirectory
    ensureOpen(false);
    return directory;
  }

  public Analyzer getAnalyzer() {
    ensureOpen();
    return analyzer;
  }


  public synchronized int maxDoc() {
    ensureOpen();
    int count;
    if (docWriter != null)
      count = docWriter.getNumDocs();
    else
      count = 0;

    count += segmentInfos.totalDocCount();
    return count;
  }


  public synchronized int numDocs() throws IOException {
    ensureOpen();
    int count;
    if (docWriter != null)
      count = docWriter.getNumDocs();
    else
      count = 0;

    for (final SegmentInfo info : segmentInfos) {
      count += info.docCount - numDeletedDocs(info);
    }
    return count;
  }

  public synchronized boolean hasDeletions() throws IOException {
    ensureOpen();
    if (bufferedDeletesStream.any()) {
      return true;
    }
    if (docWriter.anyDeletions()) {
      return true;
    }
    for (final SegmentInfo info : segmentInfos) {
      if (info.hasDeletions()) {
        return true;
      }
    }
    return false;
  }

  @Deprecated
  private int maxFieldLength = DEFAULT_MAX_FIELD_LENGTH;

  public void addDocument(Document doc) throws CorruptIndexException, IOException {
    addDocument(doc, analyzer);
  }

  public void addDocument(Document doc, Analyzer analyzer) throws CorruptIndexException, IOException {
    ensureOpen();
    boolean doFlush = false;
    boolean success = false;
    try {
      try {
        doFlush = docWriter.updateDocument(doc, analyzer, null);
        success = true;
      } finally {
        if (!success && infoStream != null)
          message("hit exception adding document");
      }
      if (doFlush)
        flush(true, false);
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "addDocument");
    }
  }

  public void addDocuments(Collection<Document> docs) throws CorruptIndexException, IOException {
    // TODO: if we backport DWPT we should change arg to Iterable<Document>
    addDocuments(docs, analyzer);
  }

  public void addDocuments(Collection<Document> docs, Analyzer analyzer) throws CorruptIndexException, IOException {
    // TODO: if we backport DWPT we should change arg to Iterable<Document>
    updateDocuments(null, docs, analyzer);
  }

  public void updateDocuments(Term delTerm, Collection<Document> docs) throws CorruptIndexException, IOException {
    // TODO: if we backport DWPT we should change arg to Iterable<Document>
    updateDocuments(delTerm, docs, analyzer);
  }

  public void updateDocuments(Term delTerm, Collection<Document> docs, Analyzer analyzer) throws CorruptIndexException, IOException {
    // TODO: if we backport DWPT we should change arg to Iterable<Document>
    ensureOpen();
    try {
      boolean success = false;
      boolean doFlush = false;
      try {
        doFlush = docWriter.updateDocuments(docs, analyzer, delTerm);
        success = true;
      } finally {
        if (!success && infoStream != null) {
          message("hit exception updating document");
        }
      }
      if (doFlush) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "updateDocuments");
    }
  }

  public void deleteDocuments(Term term) throws CorruptIndexException, IOException {
    ensureOpen();
    try {
      if (docWriter.deleteTerm(term, false)) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "deleteDocuments(Term)");
    }
  }

  public void deleteDocuments(Term... terms) throws CorruptIndexException, IOException {
    ensureOpen();
    try {
      if (docWriter.deleteTerms(terms)) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "deleteDocuments(Term..)");
    }
  }

  public void deleteDocuments(Query query) throws CorruptIndexException, IOException {
    ensureOpen();
    try {
      if (docWriter.deleteQuery(query)) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "deleteDocuments(Query)");
    }
  }

  public void deleteDocuments(Query... queries) throws CorruptIndexException, IOException {
    ensureOpen();
    try {
      if (docWriter.deleteQueries(queries)) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "deleteDocuments(Query..)");
    }
  }

  public void updateDocument(Term term, Document doc) throws CorruptIndexException, IOException {
    ensureOpen();
    updateDocument(term, doc, getAnalyzer());
  }

  public void updateDocument(Term term, Document doc, Analyzer analyzer)
      throws CorruptIndexException, IOException {
    ensureOpen();
    try {
      boolean doFlush = false;
      boolean success = false;
      try {
        doFlush = docWriter.updateDocument(doc, analyzer, term);
        success = true;
      } finally {
        if (!success && infoStream != null)
          message("hit exception updating document");
      }
      if (doFlush) {
        flush(true, false);
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "updateDocument");
    }
  }

  // for test purpose
  final synchronized int getSegmentCount(){
    return segmentInfos.size();
  }

  // for test purpose
  final synchronized int getNumBufferedDocuments(){
    return docWriter.getNumDocs();
  }

  // for test purpose
  final synchronized int getDocCount(int i) {
    if (i >= 0 && i < segmentInfos.size()) {
      return segmentInfos.info(i).docCount;
    } else {
      return -1;
    }
  }

  // for test purpose
  final int getFlushCount() {
    return flushCount.get();
  }

  // for test purpose
  final int getFlushDeletesCount() {
    return flushDeletesCount.get();
  }

  final String newSegmentName() {
    // Cannot synchronize on IndexWriter because that causes
    // deadlock
    synchronized(segmentInfos) {
      // Important to increment changeCount so that the
      // segmentInfos is written on close.  Otherwise we
      // could close, re-open and re-return the same segment
      // name that was previously returned which can cause
      // problems at least with ConcurrentMergeScheduler.
      changeCount++;
      segmentInfos.changed();
      return "_" + Integer.toString(segmentInfos.counter++, Character.MAX_RADIX);
    }
  }

  private PrintStream infoStream;
  private static PrintStream defaultInfoStream;


  @Deprecated
  public void optimize() throws CorruptIndexException, IOException {
    forceMerge(1, true);
  }


  @Deprecated
  public void optimize(int maxNumSegments) throws CorruptIndexException, IOException {
    forceMerge(maxNumSegments, true);
  }


  @Deprecated
  public void optimize(boolean doWait) throws CorruptIndexException, IOException {
    forceMerge(1, doWait);
  }

  public void forceMerge(int maxNumSegments) throws CorruptIndexException, IOException {
    forceMerge(maxNumSegments, true);
  }


  public void forceMerge(int maxNumSegments, boolean doWait) throws CorruptIndexException, IOException {
    ensureOpen();
    
    if (maxNumSegments < 1)
      throw new IllegalArgumentException("maxNumSegments must be >= 1; got " + maxNumSegments);

    if (infoStream != null) {
      message("forceMerge: index now " + segString());
      message("now flush at forceMerge");
    }

    flush(true, true);

    synchronized(this) {
      resetMergeExceptions();
      segmentsToMerge.clear();
      for(SegmentInfo info : segmentInfos) {
        segmentsToMerge.put(info, Boolean.TRUE);
      }
      mergeMaxNumSegments = maxNumSegments;
      
      // Now mark all pending & running merges as isMaxNumSegments:
      for(final MergePolicy.OneMerge merge  : pendingMerges) {
        merge.maxNumSegments = maxNumSegments;
        segmentsToMerge.put(merge.info, Boolean.TRUE);
      }

      for ( final MergePolicy.OneMerge merge: runningMerges ) {
        merge.maxNumSegments = maxNumSegments;
        segmentsToMerge.put(merge.info, Boolean.TRUE);
      }
    }

    maybeMerge(maxNumSegments);

    if (doWait) {
      synchronized(this) {
        while(true) {

          if (hitOOM) {
            throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot complete forceMerge");
          }

          if (mergeExceptions.size() > 0) {
            // Forward any exceptions in background merge
            // threads to the current thread:
            final int size = mergeExceptions.size();
            for(int i=0;i<size;i++) {
              final MergePolicy.OneMerge merge = mergeExceptions.get(i);
              if (merge.maxNumSegments != -1) {
                IOException err = new IOException("background merge hit exception: " + merge.segString(directory));
                final Throwable t = merge.getException();
                if (t != null)
                  err.initCause(t);
                throw err;
              }
            }
          }

          if (maxNumSegmentsMergesPending())
            doWait();
          else
            break;
        }
      }

      // If close is called while we are still
      // running, throw an exception so the calling
      // thread will know merging did not
      // complete
      ensureOpen();
    }

    // NOTE: in the ConcurrentMergeScheduler case, when
    // doWait is false, we can return immediately while
    // background threads accomplish the merging
  }

  private synchronized boolean maxNumSegmentsMergesPending() {
    for (final MergePolicy.OneMerge merge : pendingMerges) {
      if (merge.maxNumSegments != -1)
        return true;
    }
    
    for (final MergePolicy.OneMerge merge : runningMerges) {
      if (merge.maxNumSegments != -1)
        return true;
    }
    
    return false;
  }


  @Deprecated
  public void expungeDeletes(boolean doWait) throws CorruptIndexException, IOException {
    forceMergeDeletes(doWait);
  }


  public void forceMergeDeletes(boolean doWait)
    throws CorruptIndexException, IOException {
    ensureOpen();

    flush(true, true);

    if (infoStream != null)
      message("forceMergeDeletes: index now " + segString());

    MergePolicy.MergeSpecification spec;

    synchronized(this) {
      spec = mergePolicy.findForcedDeletesMerges(segmentInfos);
      if (spec != null) {
        final int numMerges = spec.merges.size();
        for(int i=0;i<numMerges;i++)
          registerMerge(spec.merges.get(i));
      }
    }

    mergeScheduler.merge(this);

    if (spec != null && doWait) {
      final int numMerges = spec.merges.size();
      synchronized(this) {
        boolean running = true;
        while(running) {

          if (hitOOM) {
            throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot complete forceMergeDeletes");
          }

          // Check each merge that MergePolicy asked us to
          // do, to see if any of them are still running and
          // if any of them have hit an exception.
          running = false;
          for(int i=0;i<numMerges;i++) {
            final MergePolicy.OneMerge merge = spec.merges.get(i);
            if (pendingMerges.contains(merge) || runningMerges.contains(merge))
              running = true;
            Throwable t = merge.getException();
            if (t != null) {
              IOException ioe = new IOException("background merge hit exception: " + merge.segString(directory));
              ioe.initCause(t);
              throw ioe;
            }
          }

          // If any of our merges are still running, wait:
          if (running)
            doWait();
        }
      }
    }

    // NOTE: in the ConcurrentMergeScheduler case, when
    // doWait is false, we can return immediately while
    // background threads accomplish the merging
  }



  @Deprecated
  public void expungeDeletes() throws CorruptIndexException, IOException {
    forceMergeDeletes();
  }

  public void forceMergeDeletes() throws CorruptIndexException, IOException {
    forceMergeDeletes(true);
  }

  public final void maybeMerge() throws CorruptIndexException, IOException {
    maybeMerge(-1);
  }

  private final void maybeMerge(int maxNumSegments) throws CorruptIndexException, IOException {
    ensureOpen(false);
    updatePendingMerges(maxNumSegments);
    mergeScheduler.merge(this);
  }

  private synchronized void updatePendingMerges(int maxNumSegments)
    throws CorruptIndexException, IOException {
    assert maxNumSegments == -1 || maxNumSegments > 0;

    if (stopMerges) {
      return;
    }

    // Do not start new merges if we've hit OOME
    if (hitOOM) {
      return;
    }

    final MergePolicy.MergeSpecification spec;
    if (maxNumSegments != -1) {
      spec = mergePolicy.findForcedMerges(segmentInfos, maxNumSegments, Collections.unmodifiableMap(segmentsToMerge));
      if (spec != null) {
        final int numMerges = spec.merges.size();
        for(int i=0;i<numMerges;i++) {
          final MergePolicy.OneMerge merge = spec.merges.get(i);
          merge.maxNumSegments = maxNumSegments;
        }
      }

    } else {
      spec = mergePolicy.findMerges(segmentInfos);
    }

    if (spec != null) {
      final int numMerges = spec.merges.size();
      for(int i=0;i<numMerges;i++) {
        registerMerge(spec.merges.get(i));
      }
    }
  }


  public synchronized Collection<SegmentInfo> getMergingSegments() {
    return mergingSegments;
  }


  public synchronized MergePolicy.OneMerge getNextMerge() {
    if (pendingMerges.size() == 0)
      return null;
    else {
      // Advance the merge from pending to running
      MergePolicy.OneMerge merge = pendingMerges.removeFirst();
      runningMerges.add(merge);
      return merge;
    }
  }

  public void rollback() throws IOException {
    ensureOpen();

    // Ensure that only one thread actually gets to do the closing:
    if (shouldClose())
      rollbackInternal();
  }

  private void rollbackInternal() throws IOException {

    boolean success = false;

    if (infoStream != null ) {
      message("rollback");
    }

    try {
      synchronized(this) {
        finishMerges(false);
        stopMerges = true;
      }

      if (infoStream != null ) {
        message("rollback: done finish merges");
      }

      // Must pre-close these two, in case they increment
      // changeCount so that we can then set it to false
      // before calling closeInternal
      mergePolicy.close();
      mergeScheduler.close();

      bufferedDeletesStream.clear();

      synchronized(this) {

        if (pendingCommit != null) {
          pendingCommit.rollbackCommit(directory);
          deleter.decRef(pendingCommit);
          pendingCommit = null;
          notifyAll();
        }

        // Keep the same segmentInfos instance but replace all
        // of its SegmentInfo instances.  This is so the next
        // attempt to commit using this instance of IndexWriter
        // will always write to a new generation ("write
        // once").
        segmentInfos.rollbackSegmentInfos(rollbackSegments);
        if (infoStream != null ) {
          message("rollback: infos=" + segString(segmentInfos));
        }

        docWriter.abort();

        assert testPoint("rollback before checkpoint");

        // Ask deleter to locate unreferenced files & remove
        // them:
        deleter.checkpoint(segmentInfos, false);
        deleter.refresh();
      }

      // Don't bother saving any changes in our segmentInfos
      readerPool.clear(null);

      lastCommitChangeCount = changeCount;

      success = true;
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "rollbackInternal");
    } finally {
      synchronized(this) {
        if (!success) {
          closing = false;
          notifyAll();
          if (infoStream != null)
            message("hit exception during rollback");
        }
      }
    }

    closeInternal(false);
  }

  public synchronized void deleteAll() throws IOException {
    ensureOpen();
    try {

      // Abort any running merges
      finishMerges(false);

      // Remove any buffered docs
      docWriter.abort();

      // Remove all segments
      segmentInfos.clear();

      // Ask deleter to locate unreferenced files & remove them:
      deleter.checkpoint(segmentInfos, false);
      deleter.refresh();

      // Don't bother saving any changes in our segmentInfos
      readerPool.dropAll();

      // Mark that the index has changed
      ++changeCount;
      segmentInfos.changed();
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "deleteAll");
    } finally {
      if (infoStream != null) {
        message("hit exception during deleteAll");
      }
    }
  }

  private synchronized void finishMerges(boolean waitForMerges) throws IOException {
    if (!waitForMerges) {

      stopMerges = true;

      // Abort all pending & running merges:
      for (final MergePolicy.OneMerge merge : pendingMerges) {
        if (infoStream != null)
          message("now abort pending merge " + merge.segString(directory));
        merge.abort();
        mergeFinish(merge);
      }
      pendingMerges.clear();
      
      for (final MergePolicy.OneMerge merge : runningMerges) {
        if (infoStream != null)
          message("now abort running merge " + merge.segString(directory));
        merge.abort();
      }

      // These merges periodically check whether they have
      // been aborted, and stop if so.  We wait here to make
      // sure they all stop.  It should not take very long
      // because the merge threads periodically check if
      // they are aborted.
      while(runningMerges.size() > 0) {
        if (infoStream != null)
          message("now wait for " + runningMerges.size() + " running merge to abort");
        doWait();
      }

      stopMerges = false;
      notifyAll();

      assert 0 == mergingSegments.size();

      if (infoStream != null)
        message("all running merges have aborted");

    } else {
      // waitForMerges() will ensure any running addIndexes finishes.  
      // It's fine if a new one attempts to start because from our
      // caller above the call will see that we are in the
      // process of closing, and will throw an
      // AlreadyClosedException.
      waitForMerges();
    }
  }

  public synchronized void waitForMerges() {
    ensureOpen(false);
    if (infoStream != null) {
      message("waitForMerges");
    }
    while(pendingMerges.size() > 0 || runningMerges.size() > 0) {
      doWait();
    }

    // sanity check
    assert 0 == mergingSegments.size();

    if (infoStream != null) {
      message("waitForMerges done");
    }
  }

  synchronized void checkpoint() throws IOException {
    changeCount++;
    segmentInfos.changed();
    deleter.checkpoint(segmentInfos, false);
  }

  private synchronized void resetMergeExceptions() {
    mergeExceptions = new ArrayList<MergePolicy.OneMerge>();
    mergeGen++;
  }

  private void noDupDirs(Directory... dirs) {
    HashSet<Directory> dups = new HashSet<Directory>();
    for (Directory dir : dirs) {
      if (dups.contains(dir))
        throw new IllegalArgumentException("Directory " + dir + " appears more than once");
      if (dir == directory)
        throw new IllegalArgumentException("Cannot add directory to itself");
      dups.add(dir);
    }
  }

  @Deprecated
  public void addIndexesNoOptimize(Directory... dirs)
      throws CorruptIndexException, IOException {
    addIndexes(dirs);
  }

  public void addIndexes(Directory... dirs) throws CorruptIndexException, IOException {
    ensureOpen();
    
    noDupDirs(dirs);

    try {
      if (infoStream != null)
        message("flush at addIndexes(Directory...)");
      flush(false, true);
      
      List<SegmentInfo> infos = new ArrayList<SegmentInfo>();
      Comparator<String> versionComparator = StringHelper.getVersionComparator();
      for (Directory dir : dirs) {
        if (infoStream != null) {
          message("addIndexes: process directory " + dir);
        }
        SegmentInfos sis = new SegmentInfos(); // read infos from dir
        sis.read(dir);
        final Set<String> dsFilesCopied = new HashSet<String>();
        final Map<String, String> dsNames = new HashMap<String, String>();
        for (SegmentInfo info : sis) {
          assert !infos.contains(info): "dup info dir=" + info.dir + " name=" + info.name;
          
          String newSegName = newSegmentName();
          String dsName = info.getDocStoreSegment();
          
          if (infoStream != null) {
            message("addIndexes: process segment origName=" + info.name + " newName=" + newSegName + " dsName=" + dsName + " info=" + info);
          }
          
          copySegmentAsIs(info, newSegName, dsNames, dsFilesCopied);

          infos.add(info);
        }
      }      

      synchronized (this) {
        ensureOpen();
        segmentInfos.addAll(infos);
        checkpoint();
      }
      
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "addIndexes(Directory...)");
    }
  }


  public void addIndexes(IndexReader... readers) throws CorruptIndexException, IOException {

    ensureOpen();

    try {
      if (infoStream != null)
        message("flush at addIndexes(IndexReader...)");
      flush(false, true);

      String mergedName = newSegmentName();
      // TODO: somehow we should fix this merge so it's
      // abortable so that IW.close(false) is able to stop it
      SegmentMerger merger = new SegmentMerger(directory, config.getTermIndexInterval(),
                                               mergedName, null, payloadProcessorProvider,
                                               ((FieldInfos) docWriter.getFieldInfos().clone()));
      
      for (IndexReader reader : readers)      // add new indexes
        merger.add(reader);
      
      int docCount = merger.merge();                // merge 'em
      
      SegmentInfo info = new SegmentInfo(mergedName, docCount, directory,
                                         false, true,
                                         merger.fieldInfos().hasProx(),
                                         merger.fieldInfos().hasVectors());
      setDiagnostics(info, "addIndexes(IndexReader...)");

      boolean useCompoundFile;
      synchronized(this) { // Guard segmentInfos
        if (stopMerges) {
          deleter.deleteNewFiles(info.files());
          return;
        }
        ensureOpen();
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, info);
      }

      // Now create the compound file if needed
      if (useCompoundFile) {
        merger.createCompoundFile(mergedName + ".cfs", info);

        // delete new non cfs files directly: they were never
        // registered with IFD
        synchronized(this) {
          deleter.deleteNewFiles(info.files());
        }
        info.setUseCompoundFile(true);
      }

      // Register the new segment
      synchronized(this) {
        if (stopMerges) {
          deleter.deleteNewFiles(info.files());
          return;
        }
        ensureOpen();
        segmentInfos.add(info);
        checkpoint();
      }
      
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "addIndexes(IndexReader...)");
    }
  }
 
  private void copySegmentAsIs(SegmentInfo info, String segName,
      Map<String, String> dsNames, Set<String> dsFilesCopied)
      throws IOException {
    // Determine if the doc store of this segment needs to be copied. It's
    // only relevant for segments that share doc store with others,
    // because the DS might have been copied already, in which case we
    // just want to update the DS name of this SegmentInfo.
    // NOTE: pre-3x segments include a null DSName if they don't share doc
    // store. The following code ensures we don't accidentally insert
    // 'null' to the map.
    String dsName = info.getDocStoreSegment();
    final String newDsName;
    if (dsName != null) {
      if (dsNames.containsKey(dsName)) {
        newDsName = dsNames.get(dsName);
      } else {
        dsNames.put(dsName, segName);
        newDsName = segName;
      }
    } else {
      newDsName = segName;
    }
    
    // Copy the segment files
    for (String file: info.files()) {
      final String newFileName;
      if (IndexFileNames.isDocStoreFile(file)) {
        newFileName = newDsName + IndexFileNames.stripSegmentName(file);
        if (dsFilesCopied.contains(newFileName)) {
          continue;
        }
        dsFilesCopied.add(newFileName);
      } else {
        newFileName = segName + IndexFileNames.stripSegmentName(file);
      }
      
      assert !directory.fileExists(newFileName): "file \"" + newFileName + "\" already exists";
      info.dir.copy(directory, file, newFileName);
    }
    
    info.setDocStore(info.getDocStoreOffset(), newDsName, info.getDocStoreIsCompoundFile());
    info.dir = directory;
    info.name = segName;
  }
  
  protected void doAfterFlush() throws IOException {}

  protected void doBeforeFlush() throws IOException {}


  public final void prepareCommit() throws CorruptIndexException, IOException {
    ensureOpen();
    prepareCommit(null);
  }


  public final void prepareCommit(Map<String, String> commitUserData)
      throws CorruptIndexException, IOException {
    ensureOpen(false);

    if (hitOOM) {
      throw new IllegalStateException(
          "this writer hit an OutOfMemoryError; cannot commit");
    }

    if (pendingCommit != null)
      throw new IllegalStateException(
          "prepareCommit was already called with no corresponding call to commit");

    if (infoStream != null)
      message("prepareCommit: flush");

    ensureOpen(false);
    boolean anySegmentsFlushed = false;
    SegmentInfos toCommit = null;
    boolean success = false;
    try {
      try {
        synchronized (this) {
          anySegmentsFlushed = doFlush(true);
          readerPool.commit(segmentInfos);
          toCommit = (SegmentInfos) segmentInfos.clone();
          pendingCommitChangeCount = changeCount;
          // This protects the segmentInfos we are now going
          // to commit. This is important in case, eg, while
          // we are trying to sync all referenced files, a
          // merge completes which would otherwise have
          // removed the files we are now syncing.
          filesToCommit = toCommit.files(directory, false);
          deleter.incRef(filesToCommit);
        }
        success = true;
      } finally {
        if (!success && infoStream != null) {
          message("hit exception during prepareCommit");
        }
        doAfterFlush();
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "prepareCommit");
    }

    success = false;
    try {
      if (anySegmentsFlushed) {
        maybeMerge();
      } 
      success = true;
    } finally {
      if (!success) {
        synchronized (this) {
          deleter.decRef(filesToCommit);
          filesToCommit = null;
        }
      }
    }

    startCommit(toCommit, commitUserData);
  }

  // Used only by commit, below; lock order is commitLock -> IW
  private final Object commitLock = new Object();

  public final void forceCommit() throws CorruptIndexException, IOException {
    changeCount++;
    commit();
  }

  public final void commit() throws CorruptIndexException, IOException {
    commit(null);
  }


  public final void commit(Map<String,String> commitUserData) throws CorruptIndexException, IOException {

    ensureOpen();

    commitInternal(commitUserData);
  }

  private final void commitInternal(Map<String,String> commitUserData) throws CorruptIndexException, IOException {

    if (infoStream != null) {
      message("commit: start");
    }

    synchronized(commitLock) {
      if (infoStream != null) {
        message("commit: enter lock");
      }

      if (pendingCommit == null) {
        if (infoStream != null) {
          message("commit: now prepare");
        }
        prepareCommit(commitUserData);
      } else if (infoStream != null) {
        message("commit: already prepared");
      }

      finishCommit();
    }
  }

  private synchronized final void finishCommit() throws CorruptIndexException, IOException {

    if (pendingCommit != null) {
      try {
        if (infoStream != null) {
    	  message("commit: pendingCommit != null");
        }
        pendingCommit.finishCommit(directory);
        if (infoStream != null) {
          message("commit: wrote segments file \"" + pendingCommit.getSegmentsFileName() + "\"");
        }
        lastCommitChangeCount = pendingCommitChangeCount;
        segmentInfos.updateGeneration(pendingCommit);
        segmentInfos.setUserData(pendingCommit.getUserData());
        rollbackSegments = pendingCommit.createBackupSegmentInfos(true);
        deleter.checkpoint(pendingCommit, true);
      } finally {
        // Matches the incRef done in prepareCommit:
        deleter.decRef(filesToCommit);
        filesToCommit = null;
        pendingCommit = null;
        notifyAll();
      }

    } else if (infoStream != null) {
      message("commit: pendingCommit == null; skip");
    }

    if (infoStream != null) {
      message("commit: done");
    }
  }


  protected final void flush(boolean triggerMerge, boolean flushDocStores, boolean flushDeletes) throws CorruptIndexException, IOException {
    flush(triggerMerge, flushDeletes);
  }

  protected final void flush(boolean triggerMerge, boolean applyAllDeletes) throws CorruptIndexException, IOException {

    // NOTE: this method cannot be sync'd because
    // maybeMerge() in turn calls mergeScheduler.merge which
    // in turn can take a long time to run and we don't want
    // to hold the lock for that.  In the case of
    // ConcurrentMergeScheduler this can lead to deadlock
    // when it stalls due to too many running merges.

    // We can be called during close, when closing==true, so we must pass false to ensureOpen:
    ensureOpen(false);
    if (doFlush(applyAllDeletes) && triggerMerge) {
      maybeMerge();
    }
  }

  // TODO: this method should not have to be entirely
  // synchronized, ie, merges should be allowed to commit
  // even while a flush is happening
  private synchronized boolean doFlush(boolean applyAllDeletes) throws CorruptIndexException, IOException {

    if (hitOOM) {
      throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot flush");
    }

    doBeforeFlush();

    assert testPoint("startDoFlush");

    // We may be flushing because it was triggered by doc
    // count, del count, ram usage (in which case flush
    // pending is already set), or we may be flushing
    // due to external event eg getReader or commit is
    // called (in which case we now set it, and this will
    // pause all threads):
    flushControl.setFlushPendingNoWait("explicit flush");

    boolean success = false;

    try {

      if (infoStream != null) {
        message("  start flush: applyAllDeletes=" + applyAllDeletes);
        message("  index before flush " + segString());
      }
    
      final SegmentInfo newSegment = docWriter.flush(this, deleter, mergePolicy, segmentInfos);
      if (newSegment != null) {
        setDiagnostics(newSegment, "flush");
        segmentInfos.add(newSegment);
        checkpoint();
      }

      if (!applyAllDeletes) {
        // If deletes alone are consuming > 1/2 our RAM
        // buffer, force them all to apply now. This is to
        // prevent too-frequent flushing of a long tail of
        // tiny segments:
        if (flushControl.getFlushDeletes() ||
            (config.getRAMBufferSizeMB() != IndexWriterConfig.DISABLE_AUTO_FLUSH &&
             bufferedDeletesStream.bytesUsed() > (1024*1024*config.getRAMBufferSizeMB()/2))) {
          applyAllDeletes = true;
          if (infoStream != null) {
            message("force apply deletes bytesUsed=" + bufferedDeletesStream.bytesUsed() + " vs ramBuffer=" + (1024*1024*config.getRAMBufferSizeMB()));
          }
        }
      }

      if (applyAllDeletes) {
        if (infoStream != null) {
          message("apply all deletes during flush");
        }
        
        flushDeletesCount.incrementAndGet();
        final BufferedDeletesStream.ApplyDeletesResult result = bufferedDeletesStream
          .applyDeletes(readerPool, segmentInfos.asList());
        if (result.anyDeletes) {
          checkpoint();
        }
        if (!keepFullyDeletedSegments && result.allDeleted != null) {
          if (infoStream != null) {
            message("drop 100% deleted segments: " + result.allDeleted);
          }
          for (SegmentInfo info : result.allDeleted) {
            // If a merge has already registered for this
            // segment, we leave it in the readerPool; the
            // merge will skip merging it and will then drop
            // it once it's done:
            if (!mergingSegments.contains(info)) {
              segmentInfos.remove(info);
              if (readerPool != null) {
                readerPool.drop(info);
              }
            }
          }
          checkpoint();
        }
        bufferedDeletesStream.prune(segmentInfos);

        assert !bufferedDeletesStream.any();
        flushControl.clearDeletes();
      } else if (infoStream != null) {
        message("don't apply deletes now delTermCount=" + bufferedDeletesStream.numTerms() + " bytesUsed=" + bufferedDeletesStream.bytesUsed());
      }
      

      doAfterFlush();
      flushCount.incrementAndGet();

      success = true;

      return newSegment != null;

    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "doFlush");
      // never hit
      return false;
    } finally {
      flushControl.clearFlushPending();
      if (!success && infoStream != null)
        message("hit exception during flush");
    }
  }


  public final long ramSizeInBytes() {
    ensureOpen();
    return docWriter.bytesUsed() + bufferedDeletesStream.bytesUsed();
  }

  public final synchronized int numRamDocs() {
    ensureOpen();
    return docWriter.getNumDocs();
  }

  private void ensureValidMerge(MergePolicy.OneMerge merge) throws IOException {
    for(SegmentInfo info : merge.segments) {
      if (!segmentInfos.contains(info)) {
        throw new MergePolicy.MergeException("MergePolicy selected a segment (" + info.name + ") that is not in the current index " + segString(), directory);
      }
    }
  }


  synchronized private void commitMergedDeletes(MergePolicy.OneMerge merge, SegmentReader mergedReader) throws IOException {

    assert testPoint("startCommitMergeDeletes");

    final List<SegmentInfo> sourceSegments = merge.segments;

    if (infoStream != null)
      message("commitMergeDeletes " + merge.segString(directory));

    // Carefully merge deletes that occurred after we
    // started merging:
    int docUpto = 0;
    int delCount = 0;
    long minGen = Long.MAX_VALUE;

    for(int i=0; i < sourceSegments.size(); i++) {
      SegmentInfo info = sourceSegments.get(i);
      minGen = Math.min(info.getBufferedDeletesGen(), minGen);
      int docCount = info.docCount;
      final SegmentReader previousReader = merge.readerClones.get(i);
      if (previousReader == null) {
        // Reader was skipped because it was 100% deletions
        continue;
      }
      final SegmentReader currentReader = merge.readers.get(i);
      if (previousReader.hasDeletions()) {

        // There were deletes on this segment when the merge
        // started.  The merge has collapsed away those
        // deletes, but, if new deletes were flushed since
        // the merge started, we must now carefully keep any
        // newly flushed deletes but mapping them to the new
        // docIDs.

        if (currentReader.numDeletedDocs() > previousReader.numDeletedDocs()) {
          // This means this segment has had new deletes
          // committed since we started the merge, so we
          // must merge them:
          for(int j=0;j<docCount;j++) {
            if (previousReader.isDeleted(j))
              assert currentReader.isDeleted(j);
            else {
              if (currentReader.isDeleted(j)) {
                mergedReader.doDelete(docUpto);
                delCount++;
              }
              docUpto++;
            }
          }
        } else {
          docUpto += docCount - previousReader.numDeletedDocs();
        }
      } else if (currentReader.hasDeletions()) {
        // This segment had no deletes before but now it
        // does:
        for(int j=0; j<docCount; j++) {
          if (currentReader.isDeleted(j)) {
            mergedReader.doDelete(docUpto);
            delCount++;
          }
          docUpto++;
        }
      } else
        // No deletes before or after
        docUpto += info.docCount;
    }

    assert mergedReader.numDeletedDocs() == delCount;

    mergedReader.hasChanges = delCount > 0;

    // If new deletes were applied while we were merging
    // (which happens if eg commit() or getReader() is
    // called during our merge), then it better be the case
    // that the delGen has increased for all our merged
    // segments:
    assert !mergedReader.hasChanges || minGen > mergedReader.getSegmentInfo().getBufferedDeletesGen();

    mergedReader.getSegmentInfo().setBufferedDeletesGen(minGen);
  }

  synchronized private boolean commitMerge(MergePolicy.OneMerge merge, SegmentReader mergedReader) throws IOException {

    assert testPoint("startCommitMerge");

    if (hitOOM) {
      throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot complete merge");
    }

    if (infoStream != null)
      message("commitMerge: " + merge.segString(directory) + " index=" + segString());

    assert merge.registerDone;

    // If merge was explicitly aborted, or, if rollback() or
    // rollbackTransaction() had been called since our merge
    // started (which results in an unqualified
    // deleter.refresh() call that will remove any index
    // file that current segments does not reference), we
    // abort this merge
    if (merge.isAborted()) {
      if (infoStream != null)
        message("commitMerge: skipping merge " + merge.segString(directory) + ": it was aborted");
      return false;
    }

    if (merge.info.docCount > 0) {
      commitMergedDeletes(merge, mergedReader);
    }

    // If the doc store we are using has been closed and
    // is in now compound format (but wasn't when we
    // started), then we will switch to the compound
    // format as well:

    assert !segmentInfos.contains(merge.info);

    final boolean allDeleted = mergedReader.numDocs() == 0;

    if (infoStream != null && allDeleted) {
      message("merged segment " + merge.info + " is 100% deleted" +  (keepFullyDeletedSegments ? "" : "; skipping insert"));
    }

    final boolean dropSegment = allDeleted && !keepFullyDeletedSegments;
    segmentInfos.applyMergeChanges(merge, dropSegment);
    
    if (dropSegment) {
      readerPool.drop(merge.info);
      deleter.deleteNewFiles(merge.info.files());
      assert !segmentInfos.contains(merge.info);
    }
    
    if (infoStream != null) {
      message("after commit: " + segString());
    }

    closeMergeReaders(merge, false);

    // Must note the change to segmentInfos so any commits
    // in-flight don't lose it:
    checkpoint();

    // If the merged segments had pending changes, clear
    // them so that they don't bother writing them to
    // disk, updating SegmentInfo, etc.:
    readerPool.clear(merge.segments);
    
    if (merge.maxNumSegments != -1 && !dropSegment) {
      // cascade the forceMerge:
      if (!segmentsToMerge.containsKey(merge.info)) {
        segmentsToMerge.put(merge.info, Boolean.FALSE);
      }
    }
    
    return true;
  }
  
  final private void handleMergeException(Throwable t, MergePolicy.OneMerge merge) throws IOException {

    if (infoStream != null) {
      message("handleMergeException: merge=" + merge.segString(directory) + " exc=" + t);
    }

    // Set the exception on the merge, so if
    // forceMerge is waiting on us it sees the root
    // cause exception:
    merge.setException(t);
    addMergeException(merge);

    if (t instanceof MergePolicy.MergeAbortedException) {
      // We can ignore this exception (it happens when
      // close(false) or rollback is called), unless the
      // merge involves segments from external directories,
      // in which case we must throw it so, for example, the
      // rollbackTransaction code in addIndexes* is
      // executed.
      if (merge.isExternal)
        throw (MergePolicy.MergeAbortedException) t;
    } else if (t instanceof IOException)
      throw (IOException) t;
    else if (t instanceof RuntimeException)
      throw (RuntimeException) t;
    else if (t instanceof Error)
      throw (Error) t;
    else
      // Should not get here
      throw new RuntimeException(t);
  }

  public void merge(MergePolicy.OneMerge merge)
    throws CorruptIndexException, IOException {

    boolean success = false;

    final long t0 = System.currentTimeMillis();
    //System.out.println(Thread.currentThread().getName() + ": merge start: size=" + (merge.estimatedMergeBytes/1024./1024.) + " MB\n  merge=" + merge.segString(directory) + "\n  idx=" + segString());

    try {
      try {
        try {
          mergeInit(merge);

          if (infoStream != null)
            message("now merge\n  merge=" + merge.segString(directory) + "\n  index=" + segString());

          mergeMiddle(merge);
          mergeSuccess(merge);
          success = true;
        } catch (Throwable t) {
          handleMergeException(t, merge);
        }
      } finally {
        synchronized(this) {
          mergeFinish(merge);

          if (!success) {
            if (infoStream != null)
              message("hit exception during merge");
            if (merge.info != null && !segmentInfos.contains(merge.info))
              deleter.refresh(merge.info.name);
          }

          // This merge (and, generally, any change to the
          // segments) may now enable new merges, so we call
          // merge policy & update pending merges.
          if (success && !merge.isAborted() && (merge.maxNumSegments != -1 || (!closed && !closing))) {
            updatePendingMerges(merge.maxNumSegments);
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "merge");
    }
    if (infoStream != null && merge.info != null) {
      message("merge time " + (System.currentTimeMillis()-t0) + " msec for " + merge.info.docCount + " docs");
    }
    //System.out.println(Thread.currentThread().getName() + ": merge end");
  }

  void mergeSuccess(MergePolicy.OneMerge merge) {
  }
  

  final synchronized boolean registerMerge(MergePolicy.OneMerge merge) throws MergePolicy.MergeAbortedException, IOException {

    if (merge.registerDone)
      return true;

    if (stopMerges) {
      merge.abort();
      throw new MergePolicy.MergeAbortedException("merge is aborted: " + merge.segString(directory));
    }

    boolean isExternal = false;
    for(SegmentInfo info : merge.segments) {
      if (mergingSegments.contains(info)) {
        return false;
      }
      if (!segmentInfos.contains(info)) {
        return false;
      }
      if (info.dir != directory) {
        isExternal = true;
      }
      if (segmentsToMerge.containsKey(info)) {
        merge.maxNumSegments = mergeMaxNumSegments;
      }
    }

    ensureValidMerge(merge);

    pendingMerges.add(merge);

    if (infoStream != null)
      message("add merge to pendingMerges: " + merge.segString(directory) + " [total " + pendingMerges.size() + " pending]");

    merge.mergeGen = mergeGen;
    merge.isExternal = isExternal;

    // OK it does not conflict; now record that this merge
    // is running (while synchronized) to avoid race
    // condition where two conflicting merges from different
    // threads, start
    message("registerMerge merging=" + mergingSegments);
    for(SegmentInfo info : merge.segments) {
      message("registerMerge info=" + info);
      mergingSegments.add(info);
    }

    // Merge is now registered
    merge.registerDone = true;
    return true;
  }

  final synchronized void mergeInit(MergePolicy.OneMerge merge) throws IOException {
    boolean success = false;
    try {
      _mergeInit(merge);
      success = true;
    } finally {
      if (!success) {
        if (infoStream != null) {
          message("hit exception in mergeInit");
        }
        mergeFinish(merge);
      }
    }
  }

  synchronized private void _mergeInit(MergePolicy.OneMerge merge) throws IOException {

    assert testPoint("startMergeInit");

    assert merge.registerDone;
    assert merge.maxNumSegments == -1 || merge.maxNumSegments > 0;

    if (hitOOM) {
      throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot merge");
    }

    // TODO: is there any perf benefit to sorting
    // merged segments?  eg biggest to smallest?

    if (merge.info != null)
      // mergeInit already done
      return;

    if (merge.isAborted())
      return;

    boolean hasVectors = false;
    for (SegmentInfo sourceSegment : merge.segments) {
      if (sourceSegment.getHasVectors()) {
        hasVectors = true;
      }
    }

    // Bind a new segment name here so even with
    // ConcurrentMergePolicy we keep deterministic segment
    // names.
    merge.info = new SegmentInfo(newSegmentName(), 0, directory, false, true, false, hasVectors);

    // Lock order: IW -> BD
    final BufferedDeletesStream.ApplyDeletesResult result = bufferedDeletesStream.applyDeletes(readerPool, merge.segments);

    if (result.anyDeletes) {
      checkpoint();
    }

    if (!keepFullyDeletedSegments && result.allDeleted != null) {
      if (infoStream != null) {
        message("drop 100% deleted segments: " + result.allDeleted);
      }
      for(SegmentInfo info : result.allDeleted) {
        segmentInfos.remove(info);
        if (merge.segments.contains(info)) {
          mergingSegments.remove(info);
          merge.segments.remove(info);
        }
      }
      if (readerPool != null) {
        readerPool.drop(result.allDeleted);
      }
      checkpoint();
    }

    merge.info.setBufferedDeletesGen(result.gen);

    // Lock order: IW -> BD
    bufferedDeletesStream.prune(segmentInfos);

    Map<String,String> details = new HashMap<String,String>();
    details.put("mergeMaxNumSegments", ""+merge.maxNumSegments);
    details.put("mergeFactor", Integer.toString(merge.segments.size()));
    setDiagnostics(merge.info, "merge", details);

    if (infoStream != null) {
      message("merge seg=" + merge.info.name);
    }

    assert merge.estimatedMergeBytes == 0;
    for(SegmentInfo info : merge.segments) {
      if (info.docCount > 0) {
        final int delCount = numDeletedDocs(info);
        assert delCount <= info.docCount;
        final double delRatio = ((double) delCount)/info.docCount;
        merge.estimatedMergeBytes += info.sizeInBytes(true) * (1.0 - delRatio);
      }
    }

    // TODO: I think this should no longer be needed (we
    // now build CFS before adding segment to the infos);
    // however, on removing it, tests fail for some reason!

    // Also enroll the merged segment into mergingSegments;
    // this prevents it from getting selected for a merge
    // after our merge is done but while we are building the
    // CFS:
    mergingSegments.add(merge.info);
  }

  private void setDiagnostics(SegmentInfo info, String source) {
    setDiagnostics(info, source, null);
  }

  private void setDiagnostics(SegmentInfo info, String source, Map<String,String> details) {
    Map<String,String> diagnostics = new HashMap<String,String>();
    diagnostics.put("source", source);
    diagnostics.put("lucene.version", Constants.LUCENE_VERSION);
    diagnostics.put("os", Constants.OS_NAME);
    diagnostics.put("os.arch", Constants.OS_ARCH);
    diagnostics.put("os.version", Constants.OS_VERSION);
    diagnostics.put("java.version", Constants.JAVA_VERSION);
    diagnostics.put("java.vendor", Constants.JAVA_VENDOR);
    if (details != null) {
      diagnostics.putAll(details);
    }
    info.setDiagnostics(diagnostics);
  }

  final synchronized void mergeFinish(MergePolicy.OneMerge merge) throws IOException {
    
    // forceMerge, addIndexes or finishMerges may be waiting
    // on merges to finish.
    notifyAll();

    // It's possible we are called twice, eg if there was an
    // exception inside mergeInit
    if (merge.registerDone) {
      final List<SegmentInfo> sourceSegments = merge.segments;
      for(SegmentInfo info : sourceSegments) {
        mergingSegments.remove(info);
      }
      // TODO: if we remove the add in _mergeInit, we should
      // also remove this:
      mergingSegments.remove(merge.info);
      merge.registerDone = false;
    }

    runningMerges.remove(merge);
  }

  private final synchronized void closeMergeReaders(MergePolicy.OneMerge merge, boolean suppressExceptions) throws IOException {
    final int numSegments = merge.readers.size();
    Throwable th = null;
    
    boolean anyChanges = false;
    boolean drop = !suppressExceptions;
    for (int i = 0; i < numSegments; i++) {
      if (merge.readers.get(i) != null) {
        try {
          anyChanges |= readerPool.release(merge.readers.get(i), drop);
        } catch (Throwable t) {
          if (th == null) {
            th = t;
          }
        }
        merge.readers.set(i, null);
      }
      
      if (i < merge.readerClones.size() && merge.readerClones.get(i) != null) {
        try {
          merge.readerClones.get(i).close();
        } catch (Throwable t) {
          if (th == null) {
            th = t;
          }
        }
        // This was a private clone and we had the
        // only reference
        assert merge.readerClones.get(i).getRefCount() == 0: "refCount should be 0 but is " + merge.readerClones.get(i).getRefCount();
        merge.readerClones.set(i, null);
      }
    }
    
    if (suppressExceptions && anyChanges) {
      checkpoint();
    }
    
    // If any error occured, throw it.
    if (!suppressExceptions && th != null) {
      if (th instanceof IOException) throw (IOException) th;
      if (th instanceof RuntimeException) throw (RuntimeException) th;
      if (th instanceof Error) throw (Error) th;
      throw new RuntimeException(th);
    }
  }


  final private int mergeMiddle(MergePolicy.OneMerge merge) 
    throws CorruptIndexException, IOException {
    
    merge.checkAborted(directory);

    final String mergedName = merge.info.name;
    
    int mergedDocCount = 0;

    List<SegmentInfo> sourceSegments = merge.segments;

    SegmentMerger merger = new SegmentMerger(directory, config.getTermIndexInterval(), mergedName, merge,
                                             payloadProcessorProvider,
                                             ((FieldInfos) docWriter.getFieldInfos().clone()));

    if (infoStream != null) {
      message("merging " + merge.segString(directory) + " mergeVectors=" + merge.info.getHasVectors());
    }

    merge.readers = new ArrayList<SegmentReader>();
    merge.readerClones = new ArrayList<SegmentReader>();

    // This is try/finally to make sure merger's readers are
    // closed:
    boolean success = false;
    try {
      int totDocCount = 0;
      int segUpto = 0;
      while(segUpto < sourceSegments.size()) {

        final SegmentInfo info = sourceSegments.get(segUpto);

        // Hold onto the "live" reader; we will use this to
        // commit merged deletes
        final SegmentReader reader = readerPool.get(info, true,
                                                    MERGE_READ_BUFFER_SIZE,
                                                    -1);
        merge.readers.add(reader);

        // We clone the segment readers because other
        // deletes may come in while we're merging so we
        // need readers that will not change
        final SegmentReader clone = (SegmentReader) reader.clone(true);
        merge.readerClones.add(clone);

        if (clone.numDocs() > 0) {
          merger.add(clone);
          totDocCount += clone.numDocs();
        }
        segUpto++;
      }

      if (infoStream != null) {
        message("merge: total " + totDocCount + " docs");
      }

      merge.checkAborted(directory);

      // This is where all the work happens:
      mergedDocCount = merge.info.docCount = merger.merge();

      // LUCENE-3403: set hasVectors after merge(), so that it is properly set.
      merge.info.setHasVectors(merger.fieldInfos().hasVectors());

      assert mergedDocCount == totDocCount;

      if (infoStream != null) {
        message("merge store matchedCount=" + merger.getMatchedSubReaderCount() + " vs " + merge.readers.size());
      }

      anyNonBulkMerges |= merger.getAnyNonBulkMerges();
      
      assert mergedDocCount == totDocCount: "mergedDocCount=" + mergedDocCount + " vs " + totDocCount;

      // Very important to do this before opening the reader
      // because SegmentReader must know if prox was written for
      // this segment:
      merge.info.setHasProx(merger.fieldInfos().hasProx());

      boolean useCompoundFile;
      synchronized (this) { // Guard segmentInfos
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, merge.info);
      }

      if (useCompoundFile) {

        success = false;
        final String compoundFileName = IndexFileNames.segmentFileName(mergedName, IndexFileNames.COMPOUND_FILE_EXTENSION);

        try {
          if (infoStream != null) {
            message("create compound file " + compoundFileName);
          }
          merger.createCompoundFile(compoundFileName, merge.info);
          success = true;
        } catch (IOException ioe) {
          synchronized(this) {
            if (merge.isAborted()) {
              // This can happen if rollback or close(false)
              // is called -- fall through to logic below to
              // remove the partially created CFS:
            } else {
              handleMergeException(ioe, merge);
            }
          }
        } catch (Throwable t) {
          handleMergeException(t, merge);
        } finally {
          if (!success) {
            if (infoStream != null) {
              message("hit exception creating compound file during merge");
            }

            synchronized(this) {
              deleter.deleteFile(compoundFileName);
              deleter.deleteNewFiles(merge.info.files());
            }
          }
        }

        success = false;

        synchronized(this) {

          // delete new non cfs files directly: they were never
          // registered with IFD
          deleter.deleteNewFiles(merge.info.files());

          if (merge.isAborted()) {
            if (infoStream != null) {
              message("abort merge after building CFS");
            }
            deleter.deleteFile(compoundFileName);
            return 0;
          }
        }

        merge.info.setUseCompoundFile(true);
      }

      if (infoStream != null) {
        message(String.format("merged segment size=%.3f MB vs estimate=%.3f MB", merge.info.sizeInBytes(true)/1024./1024., merge.estimatedMergeBytes/1024/1024.));
      }

      final IndexReaderWarmer mergedSegmentWarmer = config.getMergedSegmentWarmer();

      final int termsIndexDivisor;
      final boolean loadDocStores;

      if (mergedSegmentWarmer != null) {
        // Load terms index & doc stores so the segment
        // warmer can run searches, load documents/term
        // vectors
        termsIndexDivisor = config.getReaderTermsIndexDivisor();
        loadDocStores = true;
      } else {
        termsIndexDivisor = -1;
        loadDocStores = false;
      }

      // TODO: in the non-realtime case, we may want to only
      // keep deletes (it's costly to open entire reader
      // when we just need deletes)

      final SegmentReader mergedReader = readerPool.get(merge.info, loadDocStores, BufferedIndexInput.BUFFER_SIZE, termsIndexDivisor);
      try {
        if (poolReaders && mergedSegmentWarmer != null) {
          mergedSegmentWarmer.warm(mergedReader);
        }

        if (!commitMerge(merge, mergedReader)) {
          // commitMerge will return false if this merge was aborted
          return 0;
        }
      } finally {
        synchronized(this) {
          if (readerPool.release(mergedReader)) {
            // Must checkpoint after releasing the
            // mergedReader since it may have written a new
            // deletes file:
            checkpoint();
          }
        }
      }

      success = true;

    } finally {
      // Readers are already closed in commitMerge if we didn't hit
      // an exc:
      if (!success) {
        closeMergeReaders(merge, true);
      }
    }

    return mergedDocCount;
  }

  synchronized void addMergeException(MergePolicy.OneMerge merge) {
    assert merge.getException() != null;
    if (!mergeExceptions.contains(merge) && mergeGen == merge.mergeGen)
      mergeExceptions.add(merge);
  }

  // For test purposes.
  final int getBufferedDeleteTermsSize() {
    return docWriter.getPendingDeletes().terms.size();
  }

  // For test purposes.
  final int getNumBufferedDeleteTerms() {
    return docWriter.getPendingDeletes().numTermDeletes.get();
  }

  // utility routines for tests
  synchronized SegmentInfo newestSegment() {
    return segmentInfos.size() > 0 ? segmentInfos.info(segmentInfos.size()-1) : null;
  }

  public synchronized String segString() throws IOException {
    return segString(segmentInfos);
  }

  public synchronized String segString(Iterable<SegmentInfo> infos) throws IOException {
    final StringBuilder buffer = new StringBuilder();
    for(final SegmentInfo s : infos) {
      if (buffer.length() > 0) {
        buffer.append(' ');
      }
      buffer.append(segString(s));
    }
    return buffer.toString();
  }

  public synchronized String segString(SegmentInfo info) throws IOException {
    StringBuilder buffer = new StringBuilder();
    SegmentReader reader = readerPool.getIfExists(info);
    try {
      if (reader != null) {
        buffer.append(reader.toString());
      } else {
        buffer.append(info.toString(directory, 0));
        if (info.dir != directory) {
          buffer.append("**");
        }
      }
    } finally {
      if (reader != null) {
        readerPool.release(reader);
      }
    }
    return buffer.toString();
  }

  private synchronized void doWait() {
    // NOTE: the callers of this method should in theory
    // be able to do simply wait(), but, as a defense
    // against thread timing hazards where notifyAll()
    // fails to be called, we wait for at most 1 second
    // and then return so caller can check if wait
    // conditions are satisfied:
    try {
      wait(1000);
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
  }

  private boolean keepFullyDeletedSegments;


  void keepFullyDeletedSegments() {
    keepFullyDeletedSegments = true;
  }

  boolean getKeepFullyDeletedSegments() {
    return keepFullyDeletedSegments;
  }

  // called only from assert
  private boolean filesExist(SegmentInfos toSync) throws IOException {
    Collection<String> files = toSync.files(directory, false);
    for(final String fileName: files) {
      assert directory.fileExists(fileName): "file " + fileName + " does not exist";
      // If this trips it means we are missing a call to
      // .checkpoint somewhere, because by the time we
      // are called, deleter should know about every
      // file referenced by the current head
      // segmentInfos:
      assert deleter.exists(fileName): "IndexFileDeleter doesn't know about file " + fileName;
    }
    return true;
  }


  private void startCommit(SegmentInfos toSync, Map<String,String> commitUserData) throws IOException {

    assert testPoint("startStartCommit");
    assert pendingCommit == null;

    if (hitOOM) {
      throw new IllegalStateException("this writer hit an OutOfMemoryError; cannot commit");
    }

    try {

      if (infoStream != null)
        message("startCommit(): start");


      synchronized(this) {

        assert lastCommitChangeCount <= changeCount;
        
        if (pendingCommitChangeCount == lastCommitChangeCount) {
          if (infoStream != null) {
            message("  skip startCommit(): no changes pending");
          }
          deleter.decRef(filesToCommit);
          filesToCommit = null;
          return;
        }
        
        // First, we clone & incref the segmentInfos we intend
        // to sync, then, without locking, we sync() all files
        // referenced by toSync, in the background.
        
        if (infoStream != null)
          message("startCommit index=" + segString(toSync) + " changeCount=" + changeCount);

        assert filesExist(toSync);
        
        if (commitUserData != null) {
          toSync.setUserData(commitUserData);
        }
      }

      assert testPoint("midStartCommit");

      boolean pendingCommitSet = false;

      try {
        // This call can take a long time -- 10s of seconds
        // or more.  We do it without sync:
        directory.sync(toSync.files(directory, false));

        assert testPoint("midStartCommit2");

        synchronized(this) {

          assert pendingCommit == null;

          assert segmentInfos.getGeneration() == toSync.getGeneration();

          // Exception here means nothing is prepared
          // (this method unwinds everything it did on
          // an exception)
          toSync.prepareCommit(directory);
          pendingCommitSet = true;
          pendingCommit = toSync;
        }

        if (infoStream != null) {
          message("done all syncs");
        }

        assert testPoint("midStartCommitSuccess");

      } finally {
        synchronized(this) {

          // Have our master segmentInfos record the
          // generations we just prepared.  We do this
          // on error or success so we don't
          // double-write a segments_N file.
          segmentInfos.updateGeneration(toSync);

          if (!pendingCommitSet) {
            if (infoStream != null) {
              message("hit exception committing segments file");
            }

            deleter.decRef(filesToCommit);
            filesToCommit = null;
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      handleOOM(oom, "startCommit");
    }
    assert testPoint("finishStartCommit");
  }

  public static boolean isLocked(Directory directory) throws IOException {
    return directory.makeLock(WRITE_LOCK_NAME).isLocked();
  }

  public static void unlock(Directory directory) throws IOException {
    directory.makeLock(IndexWriter.WRITE_LOCK_NAME).release();
  }

  @Deprecated
  public static final class MaxFieldLength {

    private int limit;
    private String name;

    private MaxFieldLength(String name, int limit) {
      this.name = name;
      this.limit = limit;
    }

    public MaxFieldLength(int limit) {
      this("User-specified", limit);
    }
    
    public int getLimit() {
      return limit;
    }
    
    @Override
    public String toString()
    {
      return name + ":" + limit;
    }

    public static final MaxFieldLength UNLIMITED
        = new MaxFieldLength("UNLIMITED", Integer.MAX_VALUE);


    public static final MaxFieldLength LIMITED
        = new MaxFieldLength("LIMITED", 10000);
  }


  public static abstract class IndexReaderWarmer {
    public abstract void warm(IndexReader reader) throws IOException;
  }

  @Deprecated
  public void setMergedSegmentWarmer(IndexReaderWarmer warmer) {
    config.setMergedSegmentWarmer(warmer);
  }

  @Deprecated
  public IndexReaderWarmer getMergedSegmentWarmer() {
    return config.getMergedSegmentWarmer();
  }

  private void handleOOM(OutOfMemoryError oom, String location) {
    if (infoStream != null) {
      message("hit OutOfMemoryError inside " + location);
    }
    hitOOM = true;
    throw oom;
  }

  // Used only by assert for testing.  Current points:
  //   startDoFlush
  //   startCommitMerge
  //   startStartCommit
  //   midStartCommit
  //   midStartCommit2
  //   midStartCommitSuccess
  //   finishStartCommit
  //   startCommitMergeDeletes
  //   startMergeInit
  //   DocumentsWriter.ThreadState.init start
  boolean testPoint(String name) {
    return true;
  }

  synchronized boolean nrtIsCurrent(SegmentInfos infos) {
    //System.out.println("IW.nrtIsCurrent " + (infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedDeletesStream.any()));
    ensureOpen();
    return infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedDeletesStream.any();
  }

  synchronized boolean isClosed() {
    return closed;
  }


  public synchronized void deleteUnusedFiles() throws IOException {
    ensureOpen(false);
    deleter.deletePendingFiles();
    deleter.revisitPolicy();
  }

  // Called by DirectoryReader.doClose
  synchronized void deletePendingFiles() throws IOException {
    deleter.deletePendingFiles();
  }

  public void setPayloadProcessorProvider(PayloadProcessorProvider pcp) {
    ensureOpen();
    payloadProcessorProvider = pcp;
  }
  
  public PayloadProcessorProvider getPayloadProcessorProvider() {
    ensureOpen();
    return payloadProcessorProvider;
  }

  // decides when flushes happen
  final class FlushControl {

    private boolean flushPending;
    private boolean flushDeletes;
    private int delCount;
    private int docCount;
    private boolean flushing;

    private synchronized boolean setFlushPending(String reason, boolean doWait) {
      if (flushPending || flushing) {
        if (doWait) {
          while(flushPending || flushing) {
            try {
              wait();
            } catch (InterruptedException ie) {
              throw new ThreadInterruptedException(ie);
            }
          }
        }
        return false;
      } else {
        if (infoStream != null) {
          message("now trigger flush reason=" + reason);
        }
        flushPending = true;
        return flushPending;
      }
    }

    public synchronized void setFlushPendingNoWait(String reason) {
      setFlushPending(reason, false);
    }

    public synchronized boolean getFlushPending() {
      return flushPending;
    }

    public synchronized boolean getFlushDeletes() {
      return flushDeletes;
    }

    public synchronized void clearFlushPending() {
      if (infoStream != null) {
        message("clearFlushPending");
      }
      flushPending = false;
      flushDeletes = false;
      docCount = 0;
      notifyAll();
    }

    public synchronized void clearDeletes() {
      delCount = 0;
    }

    public synchronized boolean waitUpdate(int docInc, int delInc) {
      return waitUpdate(docInc, delInc, false);
    }

    public synchronized boolean waitUpdate(int docInc, int delInc, boolean skipWait) {
      while(flushPending) {
        try {
          wait();
        } catch (InterruptedException ie) {
          throw new ThreadInterruptedException(ie);
        }
      }

      docCount += docInc;
      delCount += delInc;

      // skipWait is only used when a thread is BOTH adding
      // a doc and buffering a del term, and, the adding of
      // the doc already triggered a flush
      if (skipWait) {
        return false;
      }

      final int maxBufferedDocs = config.getMaxBufferedDocs();
      if (maxBufferedDocs != IndexWriterConfig.DISABLE_AUTO_FLUSH &&
          docCount >= maxBufferedDocs) {
        return setFlushPending("maxBufferedDocs", true);
      }

      final int maxBufferedDeleteTerms = config.getMaxBufferedDeleteTerms();
      if (maxBufferedDeleteTerms != IndexWriterConfig.DISABLE_AUTO_FLUSH &&
          delCount >= maxBufferedDeleteTerms) {
        flushDeletes = true;
        return setFlushPending("maxBufferedDeleteTerms", true);
      }

      return flushByRAMUsage("add delete/doc");
    }

    public synchronized boolean flushByRAMUsage(String reason) {
      final double ramBufferSizeMB = config.getRAMBufferSizeMB();
      if (ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH) {
        final long limit = (long) (ramBufferSizeMB*1024*1024);
        long used = bufferedDeletesStream.bytesUsed() + docWriter.bytesUsed();
        if (used >= limit) {
          
          // DocumentsWriter may be able to free up some
          // RAM:
          // Lock order: FC -> DW
          docWriter.balanceRAM();

          used = bufferedDeletesStream.bytesUsed() + docWriter.bytesUsed();
          if (used >= limit) {
            return setFlushPending("ram full: " + reason, false);
          }
        }
      }
      return false;
    }
  }

  final FlushControl flushControl = new FlushControl();
}
