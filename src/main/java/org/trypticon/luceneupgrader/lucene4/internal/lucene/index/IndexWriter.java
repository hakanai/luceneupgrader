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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xCodec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xSegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocValuesUpdate.BinaryDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocValuesUpdate.NumericDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos.FieldNumbers;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState.CheckAbort;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.*;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
public class IndexWriter implements Closeable, TwoPhaseCommit, Accountable {

  // We defensively subtract 128 to be well below the lowest
  // ArrayUtil.MAX_ARRAY_LENGTH on "typical" JVMs.  We don't just use
  // ArrayUtil.MAX_ARRAY_LENGTH here because this can vary across JVMs:
  public static final int MAX_DOCS = Integer.MAX_VALUE - 128;

  // Use package-private instance var to enforce the limit so testing
  // can use less electricity:
  private static int actualMaxDocs = MAX_DOCS;

  static void setMaxDocs(int maxDocs) {
    if (maxDocs > MAX_DOCS) {
      // Cannot go higher than the hard max:
      throw new IllegalArgumentException("maxDocs must be <= IndexWriter.MAX_DOCS=" + MAX_DOCS + "; got: " + maxDocs);
    }
    IndexWriter.actualMaxDocs = maxDocs;
  }

  static int getActualMaxDocs() {
    return IndexWriter.actualMaxDocs;
  }

  private static final int UNBOUNDED_MAX_MERGE_SEGMENTS = -1;
  
  public static final String WRITE_LOCK_NAME = "write.lock";

  public static final String SOURCE = "source";
  public static final String SOURCE_MERGE = "merge";
  public static final String SOURCE_FLUSH = "flush";
  public static final String SOURCE_ADDINDEXES_READERS = "addIndexes(IndexReader...)";

  public final static int MAX_TERM_LENGTH = DocumentsWriterPerThread.MAX_TERM_LENGTH_UTF8;
  // when unrecoverable disaster strikes, we populate this with the reason that we had to close IndexWriter
  volatile Throwable tragedy;

  private final Directory directory;  // where this index resides
  private final Analyzer analyzer;    // how to analyze text

  private volatile long changeCount; // increments every time a change is completed
  private volatile long lastCommitChangeCount; // last changeCount that was committed

  private List<SegmentCommitInfo> rollbackSegments;      // list of segmentInfo we will fallback to if the commit fails

  volatile SegmentInfos pendingCommit;            // set when a commit is pending (after prepareCommit() & before commit())
  volatile long pendingCommitChangeCount;

  private Collection<String> filesToCommit;

  final SegmentInfos segmentInfos;       // the segments
  final FieldNumbers globalFieldNumberMap;

  private final DocumentsWriter docWriter;
  private final Queue<Event> eventQueue;
  final IndexFileDeleter deleter;

  // used by forceMerge to note those needing merging
  private Map<SegmentCommitInfo,Boolean> segmentsToMerge = new HashMap<>();
  private int mergeMaxNumSegments;

  private Lock writeLock;

  private volatile boolean closed;
  private volatile boolean closing;

  // Holds all SegmentInfo instances currently involved in
  // merges
  private HashSet<SegmentCommitInfo> mergingSegments = new HashSet<>();

  private final MergeScheduler mergeScheduler;
  private LinkedList<MergePolicy.OneMerge> pendingMerges = new LinkedList<>();
  private Set<MergePolicy.OneMerge> runningMerges = new HashSet<>();
  private List<MergePolicy.OneMerge> mergeExceptions = new ArrayList<>();
  private long mergeGen;
  private boolean stopMerges;
  private boolean didMessageState;

  final AtomicInteger flushCount = new AtomicInteger();
  final AtomicInteger flushDeletesCount = new AtomicInteger();

  final ReaderPool readerPool = new ReaderPool();
  final BufferedUpdatesStream bufferedUpdatesStream;

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
  private final LiveIndexWriterConfig config;

  private long startCommitTime;


  final AtomicLong pendingNumDocs = new AtomicLong();

  DirectoryReader getReader() throws IOException {
    return getReader(true);
  }

  DirectoryReader getReader(boolean applyAllDeletes) throws IOException {
    ensureOpen();

    final long tStart = System.currentTimeMillis();

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "flush at getReader");
    }
    // Do this up front before flushing so that the readers
    // obtained during this flush are pooled, the first time
    // this method is called:
    poolReaders = true;
    DirectoryReader r = null;
    doBeforeFlush();
    boolean anySegmentFlushed = false;
    /*
     * for releasing a NRT reader we must ensure that 
     * DW doesn't add any segments or deletes until we are
     * done with creating the NRT DirectoryReader. 
     * We release the two stage full flush after we are done opening the
     * directory reader!
     */
    boolean success2 = false;
    try {
      boolean success = false;
      synchronized (fullFlushLock) {
        try {
          anySegmentFlushed = docWriter.flushAllThreads(this);
          if (!anySegmentFlushed) {
            // prevent double increment since docWriter#doFlush increments the flushcount
            // if we flushed anything.
            flushCount.incrementAndGet();
          }
          // Prevent segmentInfos from changing while opening the
          // reader; in theory we could instead do similar retry logic,
          // just like we do when loading segments_N
          synchronized(this) {
            maybeApplyDeletes(applyAllDeletes);
            r = StandardDirectoryReader.open(this, segmentInfos, applyAllDeletes);
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "return reader version=" + r.getVersion() + " reader=" + r);
            }
          }
          success = true;
        } finally {
          // Done: finish the full flush!
          docWriter.finishFullFlush(this, success);
          if (success) {
            processEvents(false, true);
            doAfterFlush();
          } else {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception during NRT reader");
            }
          }
        }
      }
      if (anySegmentFlushed) {
        maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
      }
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "getReader took " + (System.currentTimeMillis() - tStart) + " msec");
      }
      success2 = true;
    } catch (OutOfMemoryError tragedy) {
      tragicEvent(tragedy, "getReader");
      // never reached but javac disagrees:
      return null;
    } finally {
      if (!success2) {
        IOUtils.closeWhileHandlingException(r);
      }
    }
    return r;
  }

  @Override
  public final long ramBytesUsed() {
    ensureOpen();
    return docWriter.ramBytesUsed();
  }



  class ReaderPool implements Closeable {
    
    private final Map<SegmentCommitInfo,ReadersAndUpdates> readerMap = new HashMap<>();

    // used only by asserts
    public synchronized boolean infoIsLive(SegmentCommitInfo info) {
      int idx = segmentInfos.indexOf(info);
      assert idx != -1: "info=" + info + " isn't live";
      assert segmentInfos.info(idx) == info: "info=" + info + " doesn't match live info in segmentInfos";
      return true;
    }

    public synchronized void drop(SegmentCommitInfo info) throws IOException {
      final ReadersAndUpdates rld = readerMap.get(info);
      if (rld != null) {
        assert info == rld.info;
//        System.out.println("[" + Thread.currentThread().getName() + "] ReaderPool.drop: " + info);
        readerMap.remove(info);
        rld.dropReaders();
      }
    }

    public synchronized boolean anyPendingDeletes() {
      for(ReadersAndUpdates rld : readerMap.values()) {
        if (rld.getPendingDeleteCount() != 0) {
          return true;
        }
      }

      return false;
    }

    public synchronized void release(ReadersAndUpdates rld) throws IOException {
      release(rld, true);
    }

    public synchronized void release(ReadersAndUpdates rld, boolean assertInfoLive) throws IOException {

      // Matches incRef in get:
      rld.decRef();

      // Pool still holds a ref:
      assert rld.refCount() >= 1;

      if (!poolReaders && rld.refCount() == 1) {
        // This is the last ref to this RLD, and we're not
        // pooling, so remove it:
//        System.out.println("[" + Thread.currentThread().getName() + "] ReaderPool.release: " + rld.info);
        if (rld.writeLiveDocs(directory)) {
          // Make sure we only write del docs for a live segment:
          assert assertInfoLive == false || infoIsLive(rld.info);
          // Must checkpoint because we just
          // created new _X_N.del and field updates files;
          // don't call IW.checkpoint because that also
          // increments SIS.version, which we do not want to
          // do here: it was done previously (after we
          // invoked BDS.applyDeletes), whereas here all we
          // did was move the state to disk:
          checkpointNoSIS();
        }
        //System.out.println("IW: done writeLiveDocs for info=" + rld.info);

//        System.out.println("[" + Thread.currentThread().getName() + "] ReaderPool.release: drop readers " + rld.info);
        rld.dropReaders();
        readerMap.remove(rld.info);
      }
    }
    
    @Override
    public void close() throws IOException {
      dropAll(false);
    }

    synchronized void dropAll(boolean doSave) throws IOException {
      Throwable priorE = null;
      final Iterator<Map.Entry<SegmentCommitInfo,ReadersAndUpdates>> it = readerMap.entrySet().iterator();
      while(it.hasNext()) {
        final ReadersAndUpdates rld = it.next().getValue();

        try {
          if (doSave && rld.writeLiveDocs(directory)) {
            // Make sure we only write del docs and field updates for a live segment:
            assert infoIsLive(rld.info);
            // Must checkpoint because we just
            // created new _X_N.del and field updates files;
            // don't call IW.checkpoint because that also
            // increments SIS.version, which we do not want to
            // do here: it was done previously (after we
            // invoked BDS.applyDeletes), whereas here all we
            // did was move the state to disk:
            checkpointNoSIS();
          }
        } catch (Throwable t) {
          if (doSave) {
            IOUtils.reThrow(t);
          } else if (priorE == null) {
            priorE = t;
          }
        }

        // Important to remove as-we-go, not with .clear()
        // in the end, in case we hit an exception;
        // otherwise we could over-decref if close() is
        // called again:
        it.remove();

        // NOTE: it is allowed that these decRefs do not
        // actually close the SRs; this happens when a
        // near real-time reader is kept open after the
        // IndexWriter instance is closed:
        try {
          rld.dropReaders();
        } catch (Throwable t) {
          if (doSave) {
            IOUtils.reThrow(t);
          } else if (priorE == null) {
            priorE = t;
          }
        }
      }
      assert readerMap.size() == 0;
      IOUtils.reThrow(priorE);
    }

    public synchronized void commit(SegmentInfos infos) throws IOException {
      for (SegmentCommitInfo info : infos) {
        final ReadersAndUpdates rld = readerMap.get(info);
        if (rld != null) {
          assert rld.info == info;
          if (rld.writeLiveDocs(directory)) {
            // Make sure we only write del docs for a live segment:
            assert infoIsLive(info);
            // Must checkpoint because we just
            // created new _X_N.del and field updates files;
            // don't call IW.checkpoint because that also
            // increments SIS.version, which we do not want to
            // do here: it was done previously (after we
            // invoked BDS.applyDeletes), whereas here all we
            // did was move the state to disk:
            checkpointNoSIS();
          }
        }
      }
    }

    public synchronized ReadersAndUpdates get(SegmentCommitInfo info, boolean create) {

      // Make sure no new readers can be opened if another thread just closed us:
      ensureOpen(false);

      assert info.info.dir == directory: "info.dir=" + info.info.dir + " vs " + directory;

      ReadersAndUpdates rld = readerMap.get(info);
      if (rld == null) {
        if (!create) {
          return null;
        }
        rld = new ReadersAndUpdates(IndexWriter.this, info);
        // Steal initial reference:
        readerMap.put(info, rld);
      } else {
        assert rld.info == info: "rld.info=" + rld.info + " info=" + info + " isLive?=" + infoIsLive(rld.info) + " vs " + infoIsLive(info);
      }

      if (create) {
        // Return ref to caller:
        rld.incRef();
      }

      assert noDups();

      return rld;
    }

    // Make sure that every segment appears only once in the
    // pool:
    private boolean noDups() {
      Set<String> seen = new HashSet<>();
      for(SegmentCommitInfo info : readerMap.keySet()) {
        assert !seen.contains(info.info.name);
        seen.add(info.info.name);
      }
      return true;
    }
  }

  public int numDeletedDocs(SegmentCommitInfo info) {
    ensureOpen(false);
    int delCount = info.getDelCount();

    final ReadersAndUpdates rld = readerPool.get(info, false);
    if (rld != null) {
      delCount += rld.getPendingDeleteCount();
    }
    return delCount;
  }

  protected final void ensureOpen(boolean failIfClosing) throws AlreadyClosedException {
    if (closed || (failIfClosing && closing)) {
      throw new AlreadyClosedException("this IndexWriter is closed", tragedy);
    }
  }

  protected final void ensureOpen() throws AlreadyClosedException {
    ensureOpen(true);
  }

  final Codec codec; // for writing new segments

  public IndexWriter(Directory d, IndexWriterConfig conf) throws IOException {
    conf.setIndexWriter(this); // prevent reuse by other instances
    config = conf;
    directory = d;
    analyzer = config.getAnalyzer();
    infoStream = config.getInfoStream();
    mergeScheduler = config.getMergeScheduler();
    codec = config.getCodec();

    bufferedUpdatesStream = new BufferedUpdatesStream(infoStream);
    poolReaders = config.getReaderPooling();

    writeLock = directory.makeLock(WRITE_LOCK_NAME);

    if (!writeLock.obtain(config.getWriteLockTimeout())) // obtain write lock
      throw new LockObtainFailedException("Index locked for write: " + writeLock);

    boolean success = false;
    try {
      OpenMode mode = config.getOpenMode();
      boolean create;
      if (mode == OpenMode.CREATE) {
        create = true;
      } else if (mode == OpenMode.APPEND) {
        create = false;
      } else {
        // CREATE_OR_APPEND - create only if an index does not exist
        create = !DirectoryReader.indexExists(directory);
      }

      // If index is too old, reading the segments will throw
      // IndexFormatTooOldException.
      segmentInfos = new SegmentInfos();

      boolean initialIndexExists = true;

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
          initialIndexExists = false;
        }

        // Record that we have a change (zero out all
        // segments) pending:
        changed();
      } else {
        segmentInfos.read(directory);

        IndexCommit commit = config.getIndexCommit();
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
          changed();
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "init: loaded commit \"" + commit.getSegmentsFileName() + "\"");
          }
        }
      }

      rollbackSegments = segmentInfos.createBackupSegmentInfos();
      pendingNumDocs.set(segmentInfos.totalDocCount());

      // start with previous field numbers, but new FieldInfos
      globalFieldNumberMap = getFieldNumberMap();
      config.getFlushPolicy().init(config);
      docWriter = new DocumentsWriter(this, config, directory);
      eventQueue = docWriter.eventQueue();

      // Default deleter (for backwards compatibility) is
      // KeepOnlyLastCommitDeleter:
      synchronized(this) {
        deleter = new IndexFileDeleter(directory,
                                       config.getIndexDeletionPolicy(),
                                       segmentInfos, infoStream, this,
                                       initialIndexExists);
      }

      if (deleter.startingCommitDeleted) {
        // Deletion policy deleted the "head" commit point.
        // We have to mark ourself as changed so that if we
        // are closed w/o any further changes we write a new
        // segments_N file.
        changed();
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "init: create=" + create);
        messageState();
      }

      success = true;

    } finally {
      if (!success) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "init: hit exception on init; releasing write lock");
        }
        IOUtils.closeWhileHandlingException(writeLock);
        writeLock = null;
      }
    }
  }

  private FieldNumbers getFieldNumberMap() throws IOException {
    final FieldNumbers map = new FieldNumbers();

    for(SegmentCommitInfo info : segmentInfos) {
      for(FieldInfo fi : SegmentReader.readFieldInfos(info)) {
        map.addOrGet(fi.name, fi.number, fi.getDocValuesType());
      }
    }

    return map;
  }
  
  public LiveIndexWriterConfig getConfig() {
    ensureOpen(false);
    return config;
  }

  private void messageState() {
    if (infoStream.isEnabled("IW") && didMessageState == false) {
      didMessageState = true;
      infoStream.message("IW", "\ndir=" + directory + "\n" +
            "index=" + segString() + "\n" +
            "version=" + Version.LATEST.toString() + "\n" +
            config.toString());
    }
  }

  private void shutdown(boolean waitForMerges) throws IOException {
    if (pendingCommit != null) {
      throw new IllegalStateException("cannot close: prepareCommit was already called with no corresponding call to commit");
    }
    // Ensure that only one thread actually gets to do the
    // closing
    if (shouldClose()) {
      boolean success = false;
      try {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "now flush at close");
        }
        flush(true, true);
        if (waitForMerges) {
          waitForMerges();
        } else {
          abortMerges();
        }
        commitInternal(config.getMergePolicy());
        rollbackInternal(); // ie close, since we just committed
        success = true;
      } finally {
        if (success == false) {
          // Be certain to close the index on any exception
          try {
            rollbackInternal();
          } catch (Throwable t) {
            // Suppress so we keep throwing original exception
          }
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    close(true);
  }

  @Deprecated
  public void close(boolean waitForMerges) throws IOException {
    shutdown(waitForMerges);
  }

  private boolean assertEventQueueAfterClose() {
    if (eventQueue.isEmpty()) {
      return true;
    }
    for (Event e : eventQueue) {
      assert e instanceof DocumentsWriter.MergePendingEvent : e;
    }
    return true;
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
      } else {
        return false;
      }
    }
  }

  private void closeInternal(boolean waitForMerges, boolean doFlush) throws IOException {
    boolean interrupted = false;
    try {

      if (pendingCommit != null) {
        throw new IllegalStateException("cannot close: prepareCommit was already called with no corresponding call to commit");
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now flush at close waitForMerges=" + waitForMerges);
      }

      docWriter.close();

      try {
        // Only allow a new merge to be triggered if we are
        // going to wait for merges:
        if (doFlush) {
          flush(waitForMerges, true);
        } else {
          docWriter.abort(this); // already closed -- never sync on IW 
        }
        
      } finally {
        try {
          // clean up merge scheduler in all cases, although flushing may have failed:
          interrupted = Thread.interrupted();
        
          if (waitForMerges) {
            try {
              // Give merge scheduler last chance to run, in case
              // any pending merges are waiting:
              mergeScheduler.merge(this, MergeTrigger.CLOSING, false);
            } catch (ThreadInterruptedException tie) {
              // ignore any interruption, does not matter
              interrupted = true;
              if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "interrupted while waiting for final merges");
              }
            }
          }
          
          synchronized(this) {
            for (;;) {
              try {
                if (waitForMerges && !interrupted) {
                  waitForMerges();
                } else {
                  abortMerges();
                }
                break;
              } catch (ThreadInterruptedException tie) {
                // by setting the interrupted status, the next iteration will abort merges
                interrupted = true;
                if (infoStream.isEnabled("IW")) {
                  infoStream.message("IW", "interrupted while waiting for merges to finish");
                }
              }
            }
            stopMerges = true;
          }
          
        } finally {
          // shutdown policy, scheduler and all threads (this call is not interruptible):
          IOUtils.closeWhileHandlingException(mergeScheduler);
        }
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now call final commit()");
      }

      if (doFlush) {
        commitInternal(config.getMergePolicy());
      }
      processEvents(false, true);
      synchronized(this) {
        // commitInternal calls ReaderPool.commit, which
        // writes any pending liveDocs from ReaderPool, so
        // it's safe to drop all readers now:
        readerPool.dropAll(true);
        deleter.close();
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "at close: " + segString());
      }

      if (writeLock != null) {
        writeLock.close();                          // release write lock
        writeLock = null;
      }
      synchronized(this) {
        closed = true;
      }
      assert docWriter.perThreadPool.numDeactivatedThreadStates() == docWriter.perThreadPool.getMaxThreadStates() : "" +  docWriter.perThreadPool.numDeactivatedThreadStates() + " " +  docWriter.perThreadPool.getMaxThreadStates();
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "closeInternal");
    } finally {
      synchronized(this) {
        closing = false;
        notifyAll();
        if (!closed) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "hit exception while closing");
          }
        }
      }
      // finally, restore interrupt status:
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  public Directory getDirectory() {
    return directory;
  }

  public Analyzer getAnalyzer() {
    ensureOpen();
    return analyzer;
  }


  public synchronized int maxDoc() {
    ensureOpen();
    return docWriter.getNumDocs() + segmentInfos.totalDocCount();
  }


  public synchronized int numDocs() {
    ensureOpen();
    int count = docWriter.getNumDocs();
    for (final SegmentCommitInfo info : segmentInfos) {
      count += info.info.getDocCount() - numDeletedDocs(info);
    }
    return count;
  }

  public synchronized boolean hasDeletions() {
    ensureOpen();
    if (bufferedUpdatesStream.any()) {
      return true;
    }
    if (docWriter.anyDeletions()) {
      return true;
    }
    if (readerPool.anyPendingDeletes()) {
      return true;
    }
    for (final SegmentCommitInfo info : segmentInfos) {
      if (info.hasDeletions()) {
        return true;
      }
    }
    return false;
  }

  public void addDocument(Iterable<? extends IndexableField> doc) throws IOException {
    addDocument(doc, analyzer);
  }

  @Deprecated
  public void addDocument(Iterable<? extends IndexableField> doc, Analyzer analyzer) throws IOException {
    updateDocument(null, doc, analyzer);
  }

  public void addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    addDocuments(docs, analyzer);
  }

  @Deprecated
  public void addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs, Analyzer analyzer) throws IOException {
    updateDocuments(null, docs, analyzer);
  }

  public void updateDocuments(Term delTerm, Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    updateDocuments(delTerm, docs, analyzer);
  }

  @Deprecated
  public void updateDocuments(Term delTerm, Iterable<? extends Iterable<? extends IndexableField>> docs, Analyzer analyzer) throws IOException {
    ensureOpen();
    try {
      boolean success = false;
      try {
        if (docWriter.updateDocuments(docs, analyzer, delTerm)) {
          processEvents(true, false);
        }
        success = true;
      } finally {
        if (!success) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "hit exception updating document");
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "updateDocuments");
    }
  }


  public synchronized boolean tryDeleteDocument(IndexReader readerIn, int docID) throws IOException {

    final AtomicReader reader;
    if (readerIn instanceof AtomicReader) {
      // Reader is already atomic: use the incoming docID:
      reader = (AtomicReader) readerIn;
    } else {
      // Composite reader: lookup sub-reader and re-base docID:
      List<AtomicReaderContext> leaves = readerIn.leaves();
      int subIndex = ReaderUtil.subIndex(docID, leaves);
      reader = leaves.get(subIndex).reader();
      docID -= leaves.get(subIndex).docBase;
      assert docID >= 0;
      assert docID < reader.maxDoc();
    }

    if (!(reader instanceof SegmentReader)) {
      throw new IllegalArgumentException("the reader must be a SegmentReader or composite reader containing only SegmentReaders");
    }
      
    final SegmentCommitInfo info = ((SegmentReader) reader).getSegmentInfo();

    // TODO: this is a slow linear search, but, number of
    // segments should be contained unless something is
    // seriously wrong w/ the index, so it should be a minor
    // cost:

    if (segmentInfos.indexOf(info) != -1) {
      ReadersAndUpdates rld = readerPool.get(info, false);
      if (rld != null) {
        synchronized(bufferedUpdatesStream) {
          rld.initWritableLiveDocs();
          if (rld.delete(docID)) {
            final int fullDelCount = rld.info.getDelCount() + rld.getPendingDeleteCount();
            if (fullDelCount == rld.info.info.getDocCount()) {
              // If a merge has already registered for this
              // segment, we leave it in the readerPool; the
              // merge will skip merging it and will then drop
              // it once it's done:
              if (!mergingSegments.contains(rld.info)) {
                segmentInfos.remove(rld.info);
                readerPool.drop(rld.info);
                checkpoint();
              }
            }

            // Must bump changeCount so if no other changes
            // happened, we still commit this change:
            changed();
          }
          //System.out.println("  yes " + info.info.name + " " + docID);
          return true;
        }
      } else {
        //System.out.println("  no rld " + info.info.name + " " + docID);
      }
    } else {
      //System.out.println("  no seg " + info.info.name + " " + docID);
    }
    return false;
  }

  public void deleteDocuments(Term... terms) throws IOException {
    ensureOpen();
    try {
      if (docWriter.deleteTerms(terms)) {
        processEvents(true, false);
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "deleteDocuments(Term..)");
    }
  }

  public void deleteDocuments(Query... queries) throws IOException {
    ensureOpen();
    try {
      if (docWriter.deleteQueries(queries)) {
        processEvents(true, false);
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "deleteDocuments(Query..)");
    }
  }

  public void updateDocument(Term term, Iterable<? extends IndexableField> doc) throws IOException {
    ensureOpen();
    updateDocument(term, doc, analyzer);
  }

  @Deprecated
  public void updateDocument(Term term, Iterable<? extends IndexableField> doc, Analyzer analyzer)
      throws IOException {
    ensureOpen();
    try {
      boolean success = false;
      try {
        if (docWriter.updateDocument(doc, analyzer, term)) {
          processEvents(true, false);
        }
        success = true;
      } finally {
        if (!success) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "hit exception updating document");
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "updateDocument");
    }
  }

  public void updateNumericDocValue(Term term, String field, long value) throws IOException {
    ensureOpen();
    if (!globalFieldNumberMap.contains(field, DocValuesType.NUMERIC)) {
      throw new IllegalArgumentException("can only update existing numeric-docvalues fields!");
    }
    try {
      if (docWriter.updateDocValues(new NumericDocValuesUpdate(term, field, value))) {
        processEvents(true, false);
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "updateNumericDocValue");
    }
  }

  public void updateBinaryDocValue(Term term, String field, BytesRef value) throws IOException {
    ensureOpen();
    if (value == null) {
      throw new IllegalArgumentException("cannot update a field to a null value: " + field);
    }
    if (!globalFieldNumberMap.contains(field, DocValuesType.BINARY)) {
      throw new IllegalArgumentException("can only update existing binary-docvalues fields!");
    }
    try {
      if (docWriter.updateDocValues(new BinaryDocValuesUpdate(term, field, value))) {
        processEvents(true, false);
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "updateBinaryDocValue");
    }
  }
  
  public void updateDocValues(Term term, Field... updates) throws IOException {
    ensureOpen();
    DocValuesUpdate[] dvUpdates = new DocValuesUpdate[updates.length];
    for (int i = 0; i < updates.length; i++) {
      final Field f = updates[i];
      final DocValuesType dvType = f.fieldType().docValueType();
      if (dvType == null) {
        throw new IllegalArgumentException("can only update NUMERIC or BINARY fields! field=" + f.name());
      }
      if (!globalFieldNumberMap.contains(f.name(), dvType)) {
        throw new IllegalArgumentException("can only update existing docvalues fields! field=" + f.name() + ", type=" + dvType);
      }
      switch (dvType) {
        case NUMERIC:
          dvUpdates[i] = new NumericDocValuesUpdate(term, f.name(), (Long) f.numericValue());
          break;
        case BINARY:
          dvUpdates[i] = new BinaryDocValuesUpdate(term, f.name(), f.binaryValue());
          break;
        default:
          throw new IllegalArgumentException("can only update NUMERIC or BINARY fields: field=" + f.name() + ", type=" + dvType);
      }
    }
    try {
      if (docWriter.updateDocValues(dvUpdates)) {
        processEvents(true, false);
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "updateDocValues");
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
  final synchronized Collection<String> getIndexFileNames() throws IOException {
    return segmentInfos.files(directory, true);
  }

  // for test purpose
  final synchronized int getDocCount(int i) {
    if (i >= 0 && i < segmentInfos.size()) {
      return segmentInfos.info(i).info.getDocCount();
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

  final InfoStream infoStream;

  public void forceMerge(int maxNumSegments) throws IOException {
    forceMerge(maxNumSegments, true);
  }


  public void forceMerge(int maxNumSegments, boolean doWait) throws IOException {
    ensureOpen();

    if (maxNumSegments < 1)
      throw new IllegalArgumentException("maxNumSegments must be >= 1; got " + maxNumSegments);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "forceMerge: index now " + segString());
      infoStream.message("IW", "now flush at forceMerge");
    }

    flush(true, true);

    synchronized(this) {
      resetMergeExceptions();
      segmentsToMerge.clear();
      for(SegmentCommitInfo info : segmentInfos) {
        segmentsToMerge.put(info, Boolean.TRUE);
      }
      mergeMaxNumSegments = maxNumSegments;

      // Now mark all pending & running merges for forced
      // merge:
      for(final MergePolicy.OneMerge merge  : pendingMerges) {
        merge.maxNumSegments = maxNumSegments;
        segmentsToMerge.put(merge.info, Boolean.TRUE);
      }

      for (final MergePolicy.OneMerge merge: runningMerges) {
        merge.maxNumSegments = maxNumSegments;
        segmentsToMerge.put(merge.info, Boolean.TRUE);
      }
    }

    maybeMerge(config.getMergePolicy(), MergeTrigger.EXPLICIT, maxNumSegments);

    if (doWait) {
      synchronized(this) {
        while(true) {

          if (tragedy != null) {
            throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete forceMerge", tragedy);
          }

          if (mergeExceptions.size() > 0) {
            // Forward any exceptions in background merge
            // threads to the current thread:
            final int size = mergeExceptions.size();
            for(int i=0;i<size;i++) {
              final MergePolicy.OneMerge merge = mergeExceptions.get(i);
              if (merge.maxNumSegments != -1) {
                throw new IOException("background merge hit exception: " + merge.segString(directory), merge.getException());
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


  public void forceMergeDeletes(boolean doWait)
    throws IOException {
    ensureOpen();

    flush(true, true);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "forceMergeDeletes: index now " + segString());
    }

    final MergePolicy mergePolicy = config.getMergePolicy();
    MergePolicy.MergeSpecification spec;
    boolean newMergesFound = false;
    synchronized(this) {
      spec = mergePolicy.findForcedDeletesMerges(segmentInfos, this);
      newMergesFound = spec != null;
      if (newMergesFound) {
        final int numMerges = spec.merges.size();
        for(int i=0;i<numMerges;i++)
          registerMerge(spec.merges.get(i));
      }
    }

    mergeScheduler.merge(this, MergeTrigger.EXPLICIT, newMergesFound);

    if (spec != null && doWait) {
      final int numMerges = spec.merges.size();
      synchronized(this) {
        boolean running = true;
        while(running) {

          if (tragedy != null) {
            throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete forceMergeDeletes", tragedy);
          }

          // Check each merge that MergePolicy asked us to
          // do, to see if any of them are still running and
          // if any of them have hit an exception.
          running = false;
          for(int i=0;i<numMerges;i++) {
            final MergePolicy.OneMerge merge = spec.merges.get(i);
            if (pendingMerges.contains(merge) || runningMerges.contains(merge)) {
              running = true;
            }
            Throwable t = merge.getException();
            if (t != null) {
              throw new IOException("background merge hit exception: " + merge.segString(directory), t);
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


  public void forceMergeDeletes() throws IOException {
    forceMergeDeletes(true);
  }

  public final void maybeMerge() throws IOException {
    maybeMerge(config.getMergePolicy(), MergeTrigger.EXPLICIT, UNBOUNDED_MAX_MERGE_SEGMENTS);
  }

  private final void maybeMerge(MergePolicy mergePolicy, MergeTrigger trigger, int maxNumSegments) throws IOException {
    ensureOpen(false);
    boolean newMergesFound = updatePendingMerges(mergePolicy, trigger, maxNumSegments);
    mergeScheduler.merge(this, trigger, newMergesFound);
  }

  private synchronized boolean updatePendingMerges(MergePolicy mergePolicy, MergeTrigger trigger, int maxNumSegments)
    throws IOException {

    // In case infoStream was disabled on init, but then enabled at some
    // point, try again to log the config here:
    messageState();

    assert maxNumSegments == -1 || maxNumSegments > 0;
    assert trigger != null;
    if (stopMerges) {
      return false;
    }

    // Do not start new merges if disaster struck
    if (tragedy != null) {
      return false;
    }
    boolean newMergesFound = false;
    final MergePolicy.MergeSpecification spec;
    if (maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) {
      assert trigger == MergeTrigger.EXPLICIT || trigger == MergeTrigger.MERGE_FINISHED :
        "Expected EXPLICT or MERGE_FINISHED as trigger even with maxNumSegments set but was: " + trigger.name();
      spec = mergePolicy.findForcedMerges(segmentInfos, maxNumSegments, Collections.unmodifiableMap(segmentsToMerge), this);
      newMergesFound = spec != null;
      if (newMergesFound) {
        final int numMerges = spec.merges.size();
        for(int i=0;i<numMerges;i++) {
          final MergePolicy.OneMerge merge = spec.merges.get(i);
          merge.maxNumSegments = maxNumSegments;
        }
      }
    } else {
      spec = mergePolicy.findMerges(trigger, segmentInfos, this);
    }
    newMergesFound = spec != null;
    if (newMergesFound) {
      final int numMerges = spec.merges.size();
      for(int i=0;i<numMerges;i++) {
        registerMerge(spec.merges.get(i));
      }
    }
    return newMergesFound;
  }


  public synchronized Collection<SegmentCommitInfo> getMergingSegments() {
    return mergingSegments;
  }

  public synchronized MergePolicy.OneMerge getNextMerge() {
    if (pendingMerges.size() == 0) {
      return null;
    } else {
      // Advance the merge from pending to running
      MergePolicy.OneMerge merge = pendingMerges.removeFirst();
      runningMerges.add(merge);
      return merge;
    }
  }

  public synchronized boolean hasPendingMerges() {
    return pendingMerges.size() != 0;
  }

  @Override
  public void rollback() throws IOException {
    // don't call ensureOpen here: this acts like "close()" in closeable.
    
    // Ensure that only one thread actually gets to do the
    // closing, and make sure no commit is also in progress:
    synchronized(commitLock) {
      if (shouldClose()) {
        rollbackInternal();
      }
    }
  }

  private void rollbackInternal() throws IOException {

    boolean success = false;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "rollback");
    }
    
    try {
      synchronized(this) {
        abortMerges();
        stopMerges = true;
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "rollback: done finish merges");
      }

      // Must pre-close in case it increments changeCount so that we can then
      // set it to false before calling closeInternal
      mergeScheduler.close();

      bufferedUpdatesStream.clear();
      docWriter.close(); // mark it as closed first to prevent subsequent indexing actions/flushes 
      docWriter.abort(this); // don't sync on IW here
      synchronized(this) {

        if (pendingCommit != null) {
          pendingCommit.rollbackCommit(directory);
          try {
            deleter.decRef(pendingCommit);
          } finally {
            pendingCommit = null;
            notifyAll();
          }
        }

        // Don't bother saving any changes in our segmentInfos
        readerPool.dropAll(false);

        // Keep the same segmentInfos instance but replace all
        // of its SegmentInfo instances.  This is so the next
        // attempt to commit using this instance of IndexWriter
        // will always write to a new generation ("write
        // once").
        segmentInfos.rollbackSegmentInfos(rollbackSegments);
        if (infoStream.isEnabled("IW") ) {
          infoStream.message("IW", "rollback: infos=" + segString(segmentInfos));
        }

        assert testPoint("rollback before checkpoint");

        // Ask deleter to locate unreferenced files & remove
        // them:
        deleter.checkpoint(segmentInfos, false);
        deleter.refresh();

        lastCommitChangeCount = changeCount;
        
        deleter.refresh();
        deleter.close();

        // Must set closed while inside same sync block where we call deleter.refresh, else concurrent threads may try to sneak a flush in,
        // after we leave this sync block and before we enter the sync block in the finally clause below that sets closed:
        closed = true;

        IOUtils.close(writeLock);                     // release write lock
        writeLock = null;
        
        assert docWriter.perThreadPool.numDeactivatedThreadStates() == docWriter.perThreadPool.getMaxThreadStates() : "" +  docWriter.perThreadPool.numDeactivatedThreadStates() + " " +  docWriter.perThreadPool.getMaxThreadStates();
      }

      success = true;
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "rollbackInternal");
    } finally {
      if (!success) {
        // Must not hold IW's lock while closing
        // mergeScheduler: this can lead to deadlock,
        // e.g. TestIW.testThreadInterruptDeadlock
        IOUtils.closeWhileHandlingException(mergeScheduler);
      }
      synchronized(this) {
        if (!success) {
          // we tried to be nice about it: do the minimum
          
          // don't leak a segments_N file if there is a pending commit
          if (pendingCommit != null) {
            try {
              pendingCommit.rollbackCommit(directory);
              deleter.decRef(pendingCommit);
            } catch (Throwable t) {
            }
            pendingCommit = null;
          }
          
          // close all the closeables we can (but important is readerPool and writeLock to prevent leaks)
          IOUtils.closeWhileHandlingException(readerPool, deleter, writeLock);
          writeLock = null;
        }
        closed = true;
        closing = false;
      }
    }
  }

  public void deleteAll() throws IOException {
    ensureOpen();
    // Remove any buffered docs
    boolean success = false;
    /* hold the full flush lock to prevent concurrency commits / NRT reopens to
     * get in our way and do unnecessary work. -- if we don't lock this here we might
     * get in trouble if */
    /*
     * We first abort and trash everything we have in-memory
     * and keep the thread-states locked, the lockAndAbortAll operation
     * also guarantees "point in time semantics" ie. the checkpoint that we need in terms
     * of logical happens-before relationship in the DW. So we do
     * abort all in memory structures 
     * We also drop global field numbering before during abort to make
     * sure it's just like a fresh index.
     */
    try {
      synchronized (fullFlushLock) { 
        long abortedDocCount = docWriter.lockAndAbortAll(this);
        pendingNumDocs.addAndGet(-abortedDocCount);
        
        processEvents(false, true);
        synchronized (this) {
          try {
            // Abort any running merges
            abortMerges();
            // Remove all segments
            pendingNumDocs.addAndGet(-segmentInfos.totalDocCount());
            segmentInfos.clear();
            // Ask deleter to locate unreferenced files & remove them:
            deleter.checkpoint(segmentInfos, false);
            /* don't refresh the deleter here since there might
             * be concurrent indexing requests coming in opening
             * files on the directory after we called DW#abort()
             * if we do so these indexing requests might hit FNF exceptions.
             * We will remove the files incrementally as we go...
             */
            // Don't bother saving any changes in our segmentInfos
            readerPool.dropAll(false);
            // Mark that the index has changed
            ++changeCount;
            segmentInfos.changed();
            globalFieldNumberMap.clear();

            success = true;
          } finally {
            docWriter.unlockAllAfterAbortAll(this);
            if (!success) {
              if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "hit exception during deleteAll");
              }
            }
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "deleteAll");
    }
  }


  @Deprecated
  public synchronized void abortMerges() {
    stopMerges = true;

    // Abort all pending & running merges:
    for (final MergePolicy.OneMerge merge : pendingMerges) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now abort pending merge " + segString(merge.segments));
      }
      merge.abort();
      mergeFinish(merge);
    }
    pendingMerges.clear();

    for (final MergePolicy.OneMerge merge : runningMerges) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now abort running merge " + segString(merge.segments));
      }
      merge.abort();
    }

    // These merges periodically check whether they have
    // been aborted, and stop if so.  We wait here to make
    // sure they all stop.  It should not take very long
    // because the merge threads periodically check if
    // they are aborted.
    while(runningMerges.size() > 0) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now wait for " + runningMerges.size() + " running merge/s to abort");
      }
      doWait();
    }

    stopMerges = false;
    notifyAll();

    assert 0 == mergingSegments.size();

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "all running merges have aborted");
    }
  }

  @Deprecated
  public void waitForMerges() throws IOException {

    // Give merge scheduler last chance to run, in case
    // any pending merges are waiting. We can't hold IW's lock
    // when going into merge because it can lead to deadlock.
    mergeScheduler.merge(this, MergeTrigger.CLOSING, false);

    synchronized (this) {
      ensureOpen(false);
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "waitForMerges");
      }


      while (pendingMerges.size() > 0 || runningMerges.size() > 0) {
        doWait();
      }

      // sanity check
      assert 0 == mergingSegments.size();

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "waitForMerges done");
      }
    }
  }

  synchronized void checkpoint() throws IOException {
    changed();
    deleter.checkpoint(segmentInfos, false);
  }


  synchronized void checkpointNoSIS() throws IOException {
    changeCount++;
    deleter.checkpoint(segmentInfos, false);
  }

  synchronized void changed() {
    changeCount++;
    segmentInfos.changed();
  }

  synchronized void publishFrozenUpdates(FrozenBufferedUpdates packet) {
    assert packet != null && packet.any();
    synchronized (bufferedUpdatesStream) {
      bufferedUpdatesStream.push(packet);
    }
  }
  
  void publishFlushedSegment(SegmentCommitInfo newSegment,
      FrozenBufferedUpdates packet, FrozenBufferedUpdates globalPacket) throws IOException {
    try {
      synchronized (this) {
        // Lock order IW -> BDS
        ensureOpen(false);
        synchronized (bufferedUpdatesStream) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "publishFlushedSegment");
          }
          
          if (globalPacket != null && globalPacket.any()) {
            bufferedUpdatesStream.push(globalPacket);
          } 
          // Publishing the segment must be synched on IW -> BDS to make the sure
          // that no merge prunes away the seg. private delete packet
          final long nextGen;
          if (packet != null && packet.any()) {
            nextGen = bufferedUpdatesStream.push(packet);
          } else {
            // Since we don't have a delete packet to apply we can get a new
            // generation right away
            nextGen = bufferedUpdatesStream.getNextGen();
          }
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "publish sets newSegment delGen=" + nextGen + " seg=" + segString(newSegment));
          }
          newSegment.setBufferedDeletesGen(nextGen);
          segmentInfos.add(newSegment);
          checkpoint();
        }
      }
    } finally {
      flushCount.incrementAndGet();
      doAfterFlush();
    }
  }

  private synchronized void resetMergeExceptions() {
    mergeExceptions = new ArrayList<>();
    mergeGen++;
  }

  private void noDupDirs(Directory... dirs) {
    HashSet<Directory> dups = new HashSet<>();
    for(int i=0;i<dirs.length;i++) {
      if (dups.contains(dirs[i]))
        throw new IllegalArgumentException("Directory " + dirs[i] + " appears more than once");
      if (dirs[i] == directory)
        throw new IllegalArgumentException("Cannot add directory to itself");
      dups.add(dirs[i]);
    }
  }


  private List<Lock> acquireWriteLocks(Directory... dirs) throws IOException {
    List<Lock> locks = new ArrayList<>();
    for(int i=0;i<dirs.length;i++) {
      boolean success = false;
      try {
        Lock lock = dirs[i].makeLock(WRITE_LOCK_NAME);
        locks.add(lock);
        lock.obtain(config.getWriteLockTimeout());
        success = true;
      } finally {
        if (success == false) {
          // Release all previously acquired locks:
          IOUtils.closeWhileHandlingException(locks);
        }
      }
    }
    return locks;
  }

  public void addIndexes(Directory... dirs) throws IOException {
    ensureOpen();

    noDupDirs(dirs);

    List<Lock> locks = acquireWriteLocks(dirs);

    boolean successTop = false;

    try {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "flush at addIndexes(Directory...)");
      }

      flush(false, true);

      List<SegmentCommitInfo> infos = new ArrayList<>();

      // long so we can detect int overflow:
      long totalDocCount = 0;
      List<SegmentInfos> commits = new ArrayList<>(dirs.length);
      for (Directory dir : dirs) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "addIndexes: process directory " + dir);
        }
        SegmentInfos sis = new SegmentInfos(); // read infos from dir
        sis.read(dir);
        totalDocCount += sis.totalDocCount();
        commits.add(sis);
      }

      // Best-effort up front check:
      testReserveDocs(totalDocCount);

      boolean success = false;
      try {
        for (SegmentInfos sis : commits) {

          final Set<String> dsFilesCopied = new HashSet<>();
          final Map<String, String> dsNames = new HashMap<>();
          final Set<String> copiedFiles = new HashSet<>();

          for (SegmentCommitInfo info : sis) {
            assert !infos.contains(info): "dup info dir=" + info.info.dir + " name=" + info.info.name;

            String newSegName = newSegmentName();

            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "addIndexes: process segment origName=" + info.info.name + " newName=" + newSegName + " info=" + info);
            }

            IOContext context = new IOContext(new MergeInfo(info.info.getDocCount(), info.sizeInBytes(), true, -1));

            for(FieldInfo fi : SegmentReader.readFieldInfos(info)) {
              globalFieldNumberMap.addOrGet(fi.name, fi.number, fi.getDocValuesType());
            }
            infos.add(copySegmentAsIs(info, newSegName, dsNames, dsFilesCopied, context, copiedFiles));
          }
        }
        success = true;
      } finally {
        if (!success) {
          for(SegmentCommitInfo sipc : infos) {
            IOUtils.deleteFilesIgnoringExceptions(directory, sipc.files().toArray(new String[0]));
          }
        }
      }

      synchronized (this) {
        success = false;
        try {
          ensureOpen();

          // Now reserve the docs, just before we update SIS:
          reserveDocs(totalDocCount);

          success = true;
        } finally {
          if (!success) {
            for(SegmentCommitInfo sipc : infos) {
              for(String file : sipc.files()) {
                try {
                  directory.deleteFile(file);
                } catch (Throwable t) {
                }
              }
            }
          }
        }
        segmentInfos.addAll(infos);
        checkpoint();
      }

      successTop = true;

    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "addIndexes(Directory...)");
    } finally {
      if (successTop) {
        IOUtils.close(locks);
      } else {
        IOUtils.closeWhileHandlingException(locks);
      }
    }
    maybeMerge();
  }
  
  public void addIndexes(IndexReader... readers) throws IOException {
    ensureOpen();

    try {
      // long so we can detect int overflow:
      long numDocs = 0;

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "flush at addIndexes(IndexReader...)");
      }
      flush(false, true);

      String mergedName = newSegmentName();
      final List<AtomicReader> mergeReaders = new ArrayList<>();
      for (IndexReader indexReader : readers) {
        numDocs += indexReader.numDocs();
        for (AtomicReaderContext ctx : indexReader.leaves()) {
          mergeReaders.add(ctx.reader());
        }
      }

      // Best-effort up front check:
      testReserveDocs(numDocs);

      final IOContext context = new IOContext(new MergeInfo((int) numDocs, -1, true, -1));

      // TODO: somehow we should fix this merge so it's
      // abortable so that IW.close(false) is able to stop it
      TrackingDirectoryWrapper trackingDir = new TrackingDirectoryWrapper(directory);

      SegmentInfo info = new SegmentInfo(directory, Version.LATEST, mergedName, -1,
                                         false, codec, null);

      SegmentMerger merger = new SegmentMerger(mergeReaders, info, infoStream, trackingDir, config.getTermIndexInterval(),
                                               MergeState.CheckAbort.NONE, globalFieldNumberMap, 
                                               context, config.getCheckIntegrityAtMerge());
      
      if (!merger.shouldMerge()) {
        return;
      }

      MergeState mergeState;
      boolean success = false;
      try {
        mergeState = merger.merge();                // merge 'em
        success = true;
      } finally {
        if (!success) { 
          synchronized(this) {
            deleter.refresh(info.name);
          }
        }
      }

      SegmentCommitInfo infoPerCommit = new SegmentCommitInfo(info, 0, -1L, -1L, -1L);

      info.setFiles(new HashSet<>(trackingDir.getCreatedFiles()));
      trackingDir.getCreatedFiles().clear();
                                         
      setDiagnostics(info, SOURCE_ADDINDEXES_READERS);

      final MergePolicy mergePolicy = config.getMergePolicy();
      boolean useCompoundFile;
      synchronized(this) { // Guard segmentInfos
        if (stopMerges) {
          deleter.deleteNewFiles(infoPerCommit.files());
          return;
        }
        ensureOpen();
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, infoPerCommit, this);
      }

      // Now create the compound file if needed
      if (useCompoundFile) {
        Collection<String> filesToDelete = infoPerCommit.files();
        try {
          createCompoundFile(infoStream, directory, MergeState.CheckAbort.NONE, info, context);
        } finally {
          // delete new non cfs files directly: they were never
          // registered with IFD
          synchronized(this) {
            deleter.deleteNewFiles(filesToDelete);
          }
        }
        info.setUseCompoundFile(true);
      }

      // Have codec write SegmentInfo.  Must do this after
      // creating CFS so that 1) .si isn't slurped into CFS,
      // and 2) .si reflects useCompoundFile=true change
      // above:
      success = false;
      try {
        codec.segmentInfoFormat().getSegmentInfoWriter().write(trackingDir, info, mergeState.fieldInfos, context);
        success = true;
      } finally {
        if (!success) {
          synchronized(this) {
            deleter.refresh(info.name);
          }
        }
      }

      info.addFiles(trackingDir.getCreatedFiles());

      // Register the new segment
      synchronized(this) {
        if (stopMerges) {
          deleter.deleteNewFiles(info.files());
          return;
        }
        ensureOpen();

        // Now reserve the docs, just before we update SIS:
        reserveDocs(numDocs);
      
        segmentInfos.add(infoPerCommit);
        checkpoint();
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "addIndexes(IndexReader...)");
    }
    maybeMerge();
  }

  private SegmentCommitInfo copySegmentAsIs(SegmentCommitInfo info, String segName,
                                               Map<String, String> dsNames, Set<String> dsFilesCopied, IOContext context,
                                               Set<String> copiedFiles)
      throws IOException {
    // Determine if the doc store of this segment needs to be copied. It's
    // only relevant for segments that share doc store with others,
    // because the DS might have been copied already, in which case we
    // just want to update the DS name of this SegmentInfo.
    final String dsName = Lucene3xSegmentInfoFormat.getDocStoreSegment(info.info);
    assert dsName != null;
    final String newDsName;
    if (dsNames.containsKey(dsName)) {
      newDsName = dsNames.get(dsName);
    } else {
      dsNames.put(dsName, segName);
      newDsName = segName;
    }

    // note: we don't really need this fis (its copied), but we load it up
    // so we don't pass a null value to the si writer
    FieldInfos fis = SegmentReader.readFieldInfos(info);
    
    Set<String> docStoreFiles3xOnly = Lucene3xCodec.getDocStoreFiles(info.info);

    final Map<String,String> attributes;
    // copy the attributes map, we might modify it below.
    // also we need to ensure its read-write, since we will invoke the SIwriter (which might want to set something).
    if (info.info.attributes() == null) {
      attributes = new HashMap<>();
    } else {
      attributes = new HashMap<>(info.info.attributes());
    }
    if (docStoreFiles3xOnly != null) {
      // only violate the codec this way if it's preflex &
      // shares doc stores
      // change docStoreSegment to newDsName
      attributes.put(Lucene3xSegmentInfoFormat.DS_NAME_KEY, newDsName);
    }

    //System.out.println("copy seg=" + info.info.name + " version=" + info.info.getVersion());
    // Same SI as before but we change directory, name and docStoreSegment:
    SegmentInfo newInfo = new SegmentInfo(directory, info.info.getVersion(), segName, info.info.getDocCount(),
                                          info.info.getUseCompoundFile(), info.info.getCodec(), 
                                          info.info.getDiagnostics(), attributes);
    SegmentCommitInfo newInfoPerCommit = new SegmentCommitInfo(newInfo,
        info.getDelCount(), info.getDelGen(), info.getFieldInfosGen(),
        info.getDocValuesGen());

    Set<String> segFiles = new HashSet<>();

    // Build up new segment's file names.  Must do this
    // before writing SegmentInfo:
    for (String file: info.files()) {
      final String newFileName;
      if (docStoreFiles3xOnly != null && docStoreFiles3xOnly.contains(file)) {
        newFileName = newDsName + IndexFileNames.stripSegmentName(file);
      } else {
        newFileName = segName + IndexFileNames.stripSegmentName(file);
      }
      segFiles.add(newFileName);
    }
    newInfo.setFiles(segFiles);

    // We must rewrite the SI file because it references
    // segment name (its own name, if its 3.x, and doc
    // store segment name):
    TrackingDirectoryWrapper trackingDir = new TrackingDirectoryWrapper(directory);
    final Codec currentCodec = newInfo.getCodec();
    try {
      currentCodec.segmentInfoFormat().getSegmentInfoWriter().write(trackingDir, newInfo, fis, context);
    } catch (UnsupportedOperationException uoe) {
      if (currentCodec instanceof Lucene3xCodec) {
        // OK: 3x codec cannot write a new SI file;
        // SegmentInfos will write this on commit
      } else {
        throw uoe;
      }
    }

    final Collection<String> siFiles = trackingDir.getCreatedFiles();

    boolean success = false;
    try {

      // Copy the segment's files
      for (String file: info.files()) {

        final String newFileName;
        if (docStoreFiles3xOnly != null && docStoreFiles3xOnly.contains(file)) {
          newFileName = newDsName + IndexFileNames.stripSegmentName(file);
          if (dsFilesCopied.contains(newFileName)) {
            continue;
          }
          dsFilesCopied.add(newFileName);
        } else {
          newFileName = segName + IndexFileNames.stripSegmentName(file);
        }

        if (siFiles.contains(newFileName)) {
          // We already rewrote this above
          continue;
        }

        assert !slowFileExists(directory, newFileName): "file \"" + newFileName + "\" already exists; siFiles=" + siFiles;
        assert !copiedFiles.contains(file): "file \"" + file + "\" is being copied more than once";
        copiedFiles.add(file);
        info.info.dir.copy(directory, file, newFileName, context);
      }
      success = true;
    } finally {
      if (!success) {
        for(String file : newInfo.files()) {
          try {
            directory.deleteFile(file);
          } catch (Throwable t) {
          }
        }
      }
    }
    
    return newInfoPerCommit;
  }
  
  protected void doAfterFlush() throws IOException {}

  protected void doBeforeFlush() throws IOException {}


  @Override
  public final void prepareCommit() throws IOException {
    ensureOpen();
    prepareCommitInternal(config.getMergePolicy());
  }

  private void prepareCommitInternal(MergePolicy mergePolicy) throws IOException {
    startCommitTime = System.nanoTime();
    synchronized(commitLock) {
      ensureOpen(false);
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "prepareCommit: flush");
        infoStream.message("IW", "  index before flush " + segString());
      }

      if (tragedy != null) {
        throw new IllegalStateException("this writer hit an unrecoverable error; cannot commit", tragedy);
      }

      if (pendingCommit != null) {
        throw new IllegalStateException("prepareCommit was already called with no corresponding call to commit");
      }

      doBeforeFlush();
      assert testPoint("startDoFlush");
      SegmentInfos toCommit = null;
      boolean anySegmentsFlushed = false;

      // This is copied from doFlush, except it's modified to
      // clone & incRef the flushed SegmentInfos inside the
      // sync block:

      try {

        synchronized (fullFlushLock) {
          boolean flushSuccess = false;
          boolean success = false;
          try {
            anySegmentsFlushed = docWriter.flushAllThreads(this);
            if (!anySegmentsFlushed) {
              // prevent double increment since docWriter#doFlush increments the flushcount
              // if we flushed anything.
              flushCount.incrementAndGet();
            }
            processEvents(false, true);
            flushSuccess = true;

            synchronized(this) {
              maybeApplyDeletes(true);

              readerPool.commit(segmentInfos);

              // Must clone the segmentInfos while we still
              // hold fullFlushLock and while sync'd so that
              // no partial changes (eg a delete w/o
              // corresponding add from an updateDocument) can
              // sneak into the commit point:
              toCommit = segmentInfos.clone();

              pendingCommitChangeCount = changeCount;

              // This protects the segmentInfos we are now going
              // to commit.  This is important in case, eg, while
              // we are trying to sync all referenced files, a
              // merge completes which would otherwise have
              // removed the files we are now syncing.    
              filesToCommit = toCommit.files(directory, false);
              deleter.incRef(filesToCommit);
            }
            success = true;
          } finally {
            if (!success) {
              if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "hit exception during prepareCommit");
              }
            }
            // Done: finish the full flush!
            docWriter.finishFullFlush(this, flushSuccess);
            doAfterFlush();
          }
        }
      } catch (OutOfMemoryError oom) {
        tragicEvent(oom, "prepareCommit");
      }
     
      boolean success = false;
      try {
        if (anySegmentsFlushed) {
          maybeMerge(mergePolicy, MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
        }
        startCommit(toCommit);
        success = true;
      } finally {
        if (!success) {
          synchronized (this) {
            if (filesToCommit != null) {
              deleter.decRefWhileHandlingException(filesToCommit);
              filesToCommit = null;
            }
          }
        }
      }
    }
  }
  
  public final synchronized void setCommitData(Map<String,String> commitUserData) {
    segmentInfos.setUserData(new HashMap<>(commitUserData));
    ++changeCount;
  }
  
  public final synchronized Map<String,String> getCommitData() {
    return segmentInfos.getUserData();
  }
  
  // Used only by commit and prepareCommit, below; lock
  // order is commitLock -> IW
  private final Object commitLock = new Object();

  @Override
  public final void commit() throws IOException {
    ensureOpen();
    commitInternal(config.getMergePolicy());
  }


  public final boolean hasUncommittedChanges() {
    return changeCount != lastCommitChangeCount || docWriter.anyChanges() || bufferedUpdatesStream.any();
  }

  private final void commitInternal(MergePolicy mergePolicy) throws IOException {

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commit: start");
    }

    synchronized(commitLock) {
      ensureOpen(false);

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "commit: enter lock");
      }

      if (pendingCommit == null) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "commit: now prepare");
        }
        prepareCommitInternal(mergePolicy);
      } else {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "commit: already prepared");
        }
      }

      finishCommit();
    }
  }

  private final void finishCommit() throws IOException {

    boolean commitCompleted = false;
    boolean finished = false;
    String committedSegmentsFileName = null;

    try {
      synchronized(this) {
        if (pendingCommit != null) {
          try {

            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "commit: pendingCommit != null");
            }

            committedSegmentsFileName = pendingCommit.finishCommit(directory);

            // we committed, if anything goes wrong after this, we are screwed and it's a tragedy:
            commitCompleted = true;

            // NOTE: don't use this.checkpoint() here, because
            // we do not want to increment changeCount:
            deleter.checkpoint(pendingCommit, true);

            lastCommitChangeCount = pendingCommitChangeCount;
            rollbackSegments = pendingCommit.createBackupSegmentInfos();

            finished = true;
          } finally {
            notifyAll();
            try {
              if (finished) {
                // all is good
                deleter.decRef(filesToCommit);
              } else if (commitCompleted == false) {
                // exc happened in finishCommit: not a tragedy
                deleter.decRefWhileHandlingException(filesToCommit);
              }
            } finally {
              pendingCommit = null;
              filesToCommit = null;
            }
          }
        } else {
          assert filesToCommit == null;
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "commit: pendingCommit == null; skip");
          }
        }
      }
    } catch (Throwable t) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "hit exception during finishCommit: " + t.getMessage());
      }
      if (commitCompleted) {
        tragicEvent(t, "finishCommit");
      } else {
        IOUtils.reThrow(t);
      }
    }

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commit: wrote segments file \"" + committedSegmentsFileName + "\"");
      infoStream.message("IW", String.format(Locale.ROOT, "commit: took %.1f msec", (System.nanoTime()-startCommitTime)/1000000.0));
      infoStream.message("IW", "commit: done");
    }
  }

  // Ensures only one flush() is actually flushing segments
  // at a time:
  private final Object fullFlushLock = new Object();
  
  // for assert
  boolean holdsFullFlushLock() {
    return Thread.holdsLock(fullFlushLock);
  }

  protected final void flush(boolean triggerMerge, boolean applyAllDeletes) throws IOException {

    // NOTE: this method cannot be sync'd because
    // maybeMerge() in turn calls mergeScheduler.merge which
    // in turn can take a long time to run and we don't want
    // to hold the lock for that.  In the case of
    // ConcurrentMergeScheduler this can lead to deadlock
    // when it stalls due to too many running merges.

    // We can be called during close, when closing==true, so we must pass false to ensureOpen:
    ensureOpen(false);
    if (doFlush(applyAllDeletes) && triggerMerge) {
      maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
    }
  }

  private boolean doFlush(boolean applyAllDeletes) throws IOException {
    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot flush", tragedy);
    }

    doBeforeFlush();
    assert testPoint("startDoFlush");
    boolean success = false;
    try {

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "  start flush: applyAllDeletes=" + applyAllDeletes);
        infoStream.message("IW", "  index before flush " + segString());
      }
      final boolean anyChanges;
      
      synchronized (fullFlushLock) {
        boolean flushSuccess = false;
        try {
          anyChanges = docWriter.flushAllThreads(this);
          if (!anyChanges) {
            // flushCount is incremented in flushAllThreads
            flushCount.incrementAndGet();
          }
          flushSuccess = true;
        } finally {
          docWriter.finishFullFlush(this, flushSuccess);
          processEvents(false, true);
        }
      }
      synchronized(this) {
        maybeApplyDeletes(applyAllDeletes);
        doAfterFlush();
        if (!anyChanges) {
          // flushCount is incremented in flushAllThreads
          flushCount.incrementAndGet();
        }
        success = true;
        return anyChanges;
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "doFlush");
      // never hit
      return false;
    } finally {
      if (!success) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "hit exception during flush");
        }
      }
    }
  }
  
  final synchronized void maybeApplyDeletes(boolean applyAllDeletes) throws IOException {
    if (applyAllDeletes) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "apply all deletes during flush");
      }
      applyAllDeletesAndUpdates();
    } else if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "don't apply deletes now delTermCount=" + bufferedUpdatesStream.numTerms() + " bytesUsed=" + bufferedUpdatesStream.ramBytesUsed());
    }
  }
  
  final synchronized void applyAllDeletesAndUpdates() throws IOException {
    flushDeletesCount.incrementAndGet();
    final BufferedUpdatesStream.ApplyDeletesResult result;
    result = bufferedUpdatesStream.applyDeletesAndUpdates(readerPool, segmentInfos.asList());
    if (result.anyDeletes) {
      checkpoint();
    }
    if (!keepFullyDeletedSegments && result.allDeleted != null) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "drop 100% deleted segments: " + segString(result.allDeleted));
      }
      for (SegmentCommitInfo info : result.allDeleted) {
        // If a merge has already registered for this
        // segment, we leave it in the readerPool; the
        // merge will skip merging it and will then drop
        // it once it's done:
        if (!mergingSegments.contains(info)) {
          segmentInfos.remove(info);
          pendingNumDocs.addAndGet(-info.info.getDocCount());
          readerPool.drop(info);
        }
      }
      checkpoint();
    }
    bufferedUpdatesStream.prune(segmentInfos);
  }
  
   @Deprecated
   public final long ramSizeInBytes() {
     return ramBytesUsed();
   }

  // for testing only
  DocumentsWriter getDocsWriter() {
    boolean test = false;
    assert test = true;
    return test ? docWriter : null;
  }

  public final synchronized int numRamDocs() {
    ensureOpen();
    return docWriter.getNumDocs();
  }

  private synchronized void ensureValidMerge(MergePolicy.OneMerge merge) {
    for(SegmentCommitInfo info : merge.segments) {
      if (!segmentInfos.contains(info)) {
        throw new MergePolicy.MergeException("MergePolicy selected a segment (" + info.info.name + ") that is not in the current index " + segString(), directory);
      }
    }
  }

  private void skipDeletedDoc(DocValuesFieldUpdates.Iterator[] updatesIters, int deletedDoc) {
    for (DocValuesFieldUpdates.Iterator iter : updatesIters) {
      if (iter.doc() == deletedDoc) {
        iter.nextDoc();
      }
      // when entering the method, all iterators must already be beyond the
      // deleted document, or right on it, in which case we advance them over
      // and they must be beyond it now.
      assert iter.doc() > deletedDoc : "updateDoc=" + iter.doc() + " deletedDoc=" + deletedDoc;
    }
  }
  
  private static class MergedDeletesAndUpdates {
    ReadersAndUpdates mergedDeletesAndUpdates = null;
    MergePolicy.DocMap docMap = null;
    boolean initializedWritableLiveDocs = false;
    
    MergedDeletesAndUpdates() {}
    
    final void init(ReaderPool readerPool, MergePolicy.OneMerge merge, MergeState mergeState, boolean initWritableLiveDocs) throws IOException {
      if (mergedDeletesAndUpdates == null) {
        mergedDeletesAndUpdates = readerPool.get(merge.info, true);
        docMap = merge.getDocMap(mergeState);
        assert docMap.isConsistent(merge.info.info.getDocCount());
      }
      if (initWritableLiveDocs && !initializedWritableLiveDocs) {
        mergedDeletesAndUpdates.initWritableLiveDocs();
        this.initializedWritableLiveDocs = true;
      }
    }
    
  }
  
  private void maybeApplyMergedDVUpdates(MergePolicy.OneMerge merge, MergeState mergeState, int docUpto, 
      MergedDeletesAndUpdates holder, String[] mergingFields, DocValuesFieldUpdates[] dvFieldUpdates,
      DocValuesFieldUpdates.Iterator[] updatesIters, int curDoc) throws IOException {
    int newDoc = -1;
    for (int idx = 0; idx < mergingFields.length; idx++) {
      DocValuesFieldUpdates.Iterator updatesIter = updatesIters[idx];
      if (updatesIter.doc() == curDoc) { // document has an update
        if (holder.mergedDeletesAndUpdates == null) {
          holder.init(readerPool, merge, mergeState, false);
        }
        if (newDoc == -1) { // map once per all field updates, but only if there are any updates
          newDoc = holder.docMap.map(docUpto);
        }
        DocValuesFieldUpdates dvUpdates = dvFieldUpdates[idx];
        dvUpdates.add(newDoc, updatesIter.value());
        updatesIter.nextDoc(); // advance to next document
      } else {
        assert updatesIter.doc() > curDoc : "field=" + mergingFields[idx] + " updateDoc=" + updatesIter.doc() + " curDoc=" + curDoc;
      }
    }
  }

  synchronized private ReadersAndUpdates commitMergedDeletesAndUpdates(MergePolicy.OneMerge merge, MergeState mergeState) throws IOException {

    assert testPoint("startCommitMergeDeletes");

    final List<SegmentCommitInfo> sourceSegments = merge.segments;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commitMergeDeletes " + segString(merge.segments));
    }

    // Carefully merge deletes that occurred after we
    // started merging:
    int docUpto = 0;
    long minGen = Long.MAX_VALUE;

    // Lazy init (only when we find a delete to carry over):
    final MergedDeletesAndUpdates holder = new MergedDeletesAndUpdates();
    final DocValuesFieldUpdates.Container mergedDVUpdates = new DocValuesFieldUpdates.Container();
    
    for (int i = 0; i < sourceSegments.size(); i++) {
      SegmentCommitInfo info = sourceSegments.get(i);
      minGen = Math.min(info.getBufferedDeletesGen(), minGen);
      final int docCount = info.info.getDocCount();
      final Bits prevLiveDocs = merge.readers.get(i).getLiveDocs();
      final ReadersAndUpdates rld = readerPool.get(info, false);
      // We hold a ref so it should still be in the pool:
      assert rld != null: "seg=" + info.info.name;
      final Bits currentLiveDocs = rld.getLiveDocs();
      final Map<String,DocValuesFieldUpdates> mergingFieldUpdates = rld.getMergingFieldUpdates();
      final String[] mergingFields;
      final DocValuesFieldUpdates[] dvFieldUpdates;
      final DocValuesFieldUpdates.Iterator[] updatesIters;
      if (mergingFieldUpdates.isEmpty()) {
        mergingFields = null;
        updatesIters = null;
        dvFieldUpdates = null;
      } else {
        mergingFields = new String[mergingFieldUpdates.size()];
        dvFieldUpdates = new DocValuesFieldUpdates[mergingFieldUpdates.size()];
        updatesIters = new DocValuesFieldUpdates.Iterator[mergingFieldUpdates.size()];
        int idx = 0;
        for (Entry<String,DocValuesFieldUpdates> e : mergingFieldUpdates.entrySet()) {
          String field = e.getKey();
          DocValuesFieldUpdates updates = e.getValue();
          mergingFields[idx] = field;
          dvFieldUpdates[idx] = mergedDVUpdates.getUpdates(field, updates.type);
          if (dvFieldUpdates[idx] == null) {
            dvFieldUpdates[idx] = mergedDVUpdates.newUpdates(field, updates.type, mergeState.segmentInfo.getDocCount());
          }
          updatesIters[idx] = updates.iterator();
          updatesIters[idx].nextDoc(); // advance to first update doc
          ++idx;
        }
      }
//      System.out.println("[" + Thread.currentThread().getName() + "] IW.commitMergedDeletes: info=" + info + ", mergingUpdates=" + mergingUpdates);

      if (prevLiveDocs != null) {

        // If we had deletions on starting the merge we must
        // still have deletions now:
        assert currentLiveDocs != null;
        assert prevLiveDocs.length() == docCount;
        assert currentLiveDocs.length() == docCount;

        // There were deletes on this segment when the merge
        // started.  The merge has collapsed away those
        // deletes, but, if new deletes were flushed since
        // the merge started, we must now carefully keep any
        // newly flushed deletes but mapping them to the new
        // docIDs.

        // Since we copy-on-write, if any new deletes were
        // applied after merging has started, we can just
        // check if the before/after liveDocs have changed.
        // If so, we must carefully merge the liveDocs one
        // doc at a time:
        if (currentLiveDocs != prevLiveDocs) {
          // This means this segment received new deletes
          // since we started the merge, so we
          // must merge them:
          for (int j = 0; j < docCount; j++) {
            if (!prevLiveDocs.get(j)) {
              assert !currentLiveDocs.get(j);
            } else {
              if (!currentLiveDocs.get(j)) {
                if (holder.mergedDeletesAndUpdates == null || !holder.initializedWritableLiveDocs) {
                  holder.init(readerPool, merge, mergeState, true);
                }
                holder.mergedDeletesAndUpdates.delete(holder.docMap.map(docUpto));
                if (mergingFields != null) { // advance all iters beyond the deleted document
                  skipDeletedDoc(updatesIters, j);
                }
              } else if (mergingFields != null) {
                maybeApplyMergedDVUpdates(merge, mergeState, docUpto, holder, mergingFields, dvFieldUpdates, updatesIters, j);
              }
              docUpto++;
            }
          }
        } else if (mergingFields != null) {
          // need to check each non-deleted document if it has any updates
          for (int j = 0; j < docCount; j++) {
            if (prevLiveDocs.get(j)) {
              // document isn't deleted, check if any of the fields have an update to it
              maybeApplyMergedDVUpdates(merge, mergeState, docUpto, holder, mergingFields, dvFieldUpdates, updatesIters, j);
              // advance docUpto for every non-deleted document
              docUpto++;
            } else {
              // advance all iters beyond the deleted document
              skipDeletedDoc(updatesIters, j);
            }
          }
        } else {
          docUpto += info.info.getDocCount() - info.getDelCount() - rld.getPendingDeleteCount();
        }
      } else if (currentLiveDocs != null) {
        assert currentLiveDocs.length() == docCount;
        // This segment had no deletes before but now it
        // does:
        for (int j = 0; j < docCount; j++) {
          if (!currentLiveDocs.get(j)) {
            if (holder.mergedDeletesAndUpdates == null || !holder.initializedWritableLiveDocs) {
              holder.init(readerPool, merge, mergeState, true);
            }
            holder.mergedDeletesAndUpdates.delete(holder.docMap.map(docUpto));
            if (mergingFields != null) { // advance all iters beyond the deleted document
              skipDeletedDoc(updatesIters, j);
            }
          } else if (mergingFields != null) {
            maybeApplyMergedDVUpdates(merge, mergeState, docUpto, holder, mergingFields, dvFieldUpdates, updatesIters, j);
          }
          docUpto++;
        }
      } else if (mergingFields != null) {
        // no deletions before or after, but there were updates
        for (int j = 0; j < docCount; j++) {
          maybeApplyMergedDVUpdates(merge, mergeState, docUpto, holder, mergingFields, dvFieldUpdates, updatesIters, j);
          // advance docUpto for every non-deleted document
          docUpto++;
        }
      } else {
        // No deletes or updates before or after
        docUpto += info.info.getDocCount();
      }
    }

    assert docUpto == merge.info.info.getDocCount();

    if (mergedDVUpdates.any()) {
//      System.out.println("[" + Thread.currentThread().getName() + "] IW.commitMergedDeletes: mergedDeletes.info=" + mergedDeletes.info + ", mergedFieldUpdates=" + mergedFieldUpdates);
      boolean success = false;
      try {
        // if any error occurs while writing the field updates we should release
        // the info, otherwise it stays in the pool but is considered not "live"
        // which later causes false exceptions in pool.dropAll().
        // NOTE: currently this is the only place which throws a true
        // IOException. If this ever changes, we need to extend that try/finally
        // block to the rest of the method too.
        holder.mergedDeletesAndUpdates.writeFieldUpdates(directory, mergedDVUpdates);
        success = true;
      } finally {
        if (!success) {
          holder.mergedDeletesAndUpdates.dropChanges();
          readerPool.drop(merge.info);
        }
      }
    }
    
    if (infoStream.isEnabled("IW")) {
      if (holder.mergedDeletesAndUpdates == null) {
        infoStream.message("IW", "no new deletes or field updates since merge started");
      } else {
        String msg = holder.mergedDeletesAndUpdates.getPendingDeleteCount() + " new deletes";
        if (mergedDVUpdates.any()) {
          msg += " and " + mergedDVUpdates.size() + " new field updates";
        }
        msg += " since merge started";
        infoStream.message("IW", msg);
      }
    }

    merge.info.setBufferedDeletesGen(minGen);

    return holder.mergedDeletesAndUpdates;
  }

  synchronized private boolean commitMerge(MergePolicy.OneMerge merge, MergeState mergeState) throws IOException {

    assert testPoint("startCommitMerge");

    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete merge", tragedy);
    }

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commitMerge: " + segString(merge.segments) + " index=" + segString());
    }

    assert merge.registerDone;

    // If merge was explicitly aborted, or, if rollback() or
    // rollbackTransaction() had been called since our merge
    // started (which results in an unqualified
    // deleter.refresh() call that will remove any index
    // file that current segments does not reference), we
    // abort this merge
    if (merge.isAborted()) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "commitMerge: skip: it was aborted");
      }
      // In case we opened and pooled a reader for this
      // segment, drop it now.  This ensures that we close
      // the reader before trying to delete any of its
      // files.  This is not a very big deal, since this
      // reader will never be used by any NRT reader, and
      // another thread is currently running close(false)
      // so it will be dropped shortly anyway, but not
      // doing this  makes  MockDirWrapper angry in
      // TestNRTThreads (LUCENE-5434):
      readerPool.drop(merge.info);
      deleter.deleteNewFiles(merge.info.files());
      return false;
    }

    final ReadersAndUpdates mergedUpdates = merge.info.info.getDocCount() == 0 ? null : commitMergedDeletesAndUpdates(merge, mergeState);
//    System.out.println("[" + Thread.currentThread().getName() + "] IW.commitMerge: mergedDeletes=" + mergedDeletes);

    // If the doc store we are using has been closed and
    // is in now compound format (but wasn't when we
    // started), then we will switch to the compound
    // format as well:

    assert !segmentInfos.contains(merge.info);

    final boolean allDeleted = merge.segments.size() == 0 ||
      merge.info.info.getDocCount() == 0 ||
      (mergedUpdates != null &&
       mergedUpdates.getPendingDeleteCount() == merge.info.info.getDocCount());

    if (infoStream.isEnabled("IW")) {
      if (allDeleted) {
        infoStream.message("IW", "merged segment " + merge.info + " is 100% deleted" +  (keepFullyDeletedSegments ? "" : "; skipping insert"));
      }
    }

    final boolean dropSegment = allDeleted && !keepFullyDeletedSegments;

    // If we merged no segments then we better be dropping
    // the new segment:
    assert merge.segments.size() > 0 || dropSegment;

    assert merge.info.info.getDocCount() != 0 || keepFullyDeletedSegments || dropSegment;

    if (mergedUpdates != null) {
      boolean success = false;
      try {
        if (dropSegment) {
          mergedUpdates.dropChanges();
        }
        // Pass false for assertInfoLive because the merged
        // segment is not yet live (only below do we commit it
        // to the segmentInfos):
        readerPool.release(mergedUpdates, false);
        success = true;
      } finally {
        if (!success) {
          mergedUpdates.dropChanges();
          readerPool.drop(merge.info);
        }
      }
    }

    // Must do this after readerPool.release, in case an
    // exception is hit e.g. writing the live docs for the
    // merge segment, in which case we need to abort the
    // merge:
    segmentInfos.applyMergeChanges(merge, dropSegment);

    // Now deduct the deleted docs that we just reclaimed from this
    // merge:
    int delDocCount = merge.totalDocCount - merge.info.info.getDocCount();
    assert delDocCount >= 0;
    pendingNumDocs.addAndGet(-delDocCount);

    if (dropSegment) {
      assert !segmentInfos.contains(merge.info);
      readerPool.drop(merge.info);
      deleter.deleteNewFiles(merge.info.files());
    }

    boolean success = false;
    try {
      // Must close before checkpoint, otherwise IFD won't be
      // able to delete the held-open files from the merge
      // readers:
      closeMergeReaders(merge, false);
      success = true;
    } finally {
      // Must note the change to segmentInfos so any commits
      // in-flight don't lose it (IFD will incRef/protect the
      // new files we created):
      if (success) {
        checkpoint();
      } else {
        try {
          checkpoint();
        } catch (Throwable t) {
          // Ignore so we keep throwing original exception.
        }
      }
    }

    deleter.deletePendingFiles();

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "after commitMerge: " + segString());
    }

    if (merge.maxNumSegments != -1 && !dropSegment) {
      // cascade the forceMerge:
      if (!segmentsToMerge.containsKey(merge.info)) {
        segmentsToMerge.put(merge.info, Boolean.FALSE);
      }
    }

    return true;
  }

  final private void handleMergeException(Throwable t, MergePolicy.OneMerge merge) throws IOException {

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "handleMergeException: merge=" + segString(merge.segments) + " exc=" + t);
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
      if (merge.isExternal) {
        throw (MergePolicy.MergeAbortedException) t;
      }
    } else {
      IOUtils.reThrow(t);
    }
  }

  public void merge(MergePolicy.OneMerge merge) throws IOException {

    boolean success = false;

    final long t0 = System.currentTimeMillis();

    final MergePolicy mergePolicy = config.getMergePolicy();
    try {
      try {
        try {
          mergeInit(merge);
          //if (merge.info != null) {
          //System.out.println("MERGE: " + merge.info.info.name);
          //}

          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "now merge\n  merge=" + segString(merge.segments) + "\n  index=" + segString());
          }

          mergeMiddle(merge, mergePolicy);
          mergeSuccess(merge);
          success = true;
        } catch (Throwable t) {
          handleMergeException(t, merge);
        }
      } finally {
        synchronized(this) {
          mergeFinish(merge);

          if (!success) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception during merge");
            }
            if (merge.info != null && !segmentInfos.contains(merge.info)) {
              deleter.refresh(merge.info.info.name);
            }
          }

          // This merge (and, generally, any change to the
          // segments) may now enable new merges, so we call
          // merge policy & update pending merges.
          if (success && !merge.isAborted() && (merge.maxNumSegments != -1 || (!closed && !closing))) {
            updatePendingMerges(mergePolicy, MergeTrigger.MERGE_FINISHED, merge.maxNumSegments);
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "merge");
    }
    if (merge.info != null && !merge.isAborted()) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "merge time " + (System.currentTimeMillis()-t0) + " msec for " + merge.info.info.getDocCount() + " docs");
      }
    }
  }

  void mergeSuccess(MergePolicy.OneMerge merge) {
  }


  final synchronized boolean registerMerge(MergePolicy.OneMerge merge) throws IOException {

    if (merge.registerDone) {
      return true;
    }
    assert merge.segments.size() > 0;

    if (stopMerges) {
      merge.abort();
      throw new MergePolicy.MergeAbortedException("merge is aborted: " + segString(merge.segments));
    }

    boolean isExternal = false;
    for(SegmentCommitInfo info : merge.segments) {
      if (mergingSegments.contains(info)) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "reject merge " + segString(merge.segments) + ": segment " + segString(info) + " is already marked for merge");
        }
        return false;
      }
      if (!segmentInfos.contains(info)) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "reject merge " + segString(merge.segments) + ": segment " + segString(info) + " does not exist in live infos");
        }
        return false;
      }
      if (info.info.dir != directory) {
        isExternal = true;
      }
      if (segmentsToMerge.containsKey(info)) {
        merge.maxNumSegments = mergeMaxNumSegments;
      }
    }

    ensureValidMerge(merge);

    pendingMerges.add(merge);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "add merge to pendingMerges: " + segString(merge.segments) + " [total " + pendingMerges.size() + " pending]");
    }

    merge.mergeGen = mergeGen;
    merge.isExternal = isExternal;

    // OK it does not conflict; now record that this merge
    // is running (while synchronized) to avoid race
    // condition where two conflicting merges from different
    // threads, start
    if (infoStream.isEnabled("IW")) {
      StringBuilder builder = new StringBuilder("registerMerge merging= [");
      for (SegmentCommitInfo info : mergingSegments) {
        builder.append(info.info.name).append(", ");  
      }
      builder.append("]");
      // don't call mergingSegments.toString() could lead to ConcurrentModException
      // since merge updates the segments FieldInfos
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", builder.toString());  
      }
    }
    for(SegmentCommitInfo info : merge.segments) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "registerMerge info=" + segString(info));
      }
      mergingSegments.add(info);
    }

    assert merge.estimatedMergeBytes == 0;
    assert merge.totalMergeBytes == 0;
    for(SegmentCommitInfo info : merge.segments) {
      if (info.info.getDocCount() > 0) {
        final int delCount = numDeletedDocs(info);
        assert delCount <= info.info.getDocCount();
        final double delRatio = ((double) delCount)/info.info.getDocCount();
        merge.estimatedMergeBytes += info.sizeInBytes() * (1.0 - delRatio);
        merge.totalMergeBytes += info.sizeInBytes();
      }
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
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "hit exception in mergeInit");
        }
        mergeFinish(merge);
      }
    }
  }

  synchronized private void _mergeInit(MergePolicy.OneMerge merge) throws IOException {

    assert testPoint("startMergeInit");

    assert merge.registerDone;
    assert merge.maxNumSegments == -1 || merge.maxNumSegments > 0;

    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot merge", tragedy);
    }

    if (merge.info != null) {
      // mergeInit already done
      return;
    }

    if (merge.isAborted()) {
      return;
    }

    // TODO: in the non-pool'd case this is somewhat
    // wasteful, because we open these readers, close them,
    // and then open them again for merging.  Maybe  we
    // could pre-pool them somehow in that case...

    // Lock order: IW -> BD
    final BufferedUpdatesStream.ApplyDeletesResult result = bufferedUpdatesStream.applyDeletesAndUpdates(readerPool, merge.segments);
    
    if (result.anyDeletes) {
      checkpoint();
    }

    if (!keepFullyDeletedSegments && result.allDeleted != null) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "drop 100% deleted segments: " + result.allDeleted);
      }
      for(SegmentCommitInfo info : result.allDeleted) {
        segmentInfos.remove(info);
        pendingNumDocs.addAndGet(-info.info.getDocCount());
        if (merge.segments.contains(info)) {
          mergingSegments.remove(info);
          merge.segments.remove(info);
        }
        readerPool.drop(info);
      }
      checkpoint();
    }

    // Bind a new segment name here so even with
    // ConcurrentMergePolicy we keep deterministic segment
    // names.
    final String mergeSegmentName = newSegmentName();
    SegmentInfo si = new SegmentInfo(directory, Version.LATEST, mergeSegmentName, -1, false, codec, null);
    Map<String,String> details = new HashMap<>();
    details.put("mergeMaxNumSegments", "" + merge.maxNumSegments);
    details.put("mergeFactor", Integer.toString(merge.segments.size()));
    setDiagnostics(si, SOURCE_MERGE, details);
    merge.setInfo(new SegmentCommitInfo(si, 0, -1L, -1L, -1L));

//    System.out.println("[" + Thread.currentThread().getName() + "] IW._mergeInit: " + segString(merge.segments) + " into " + si);

    // Lock order: IW -> BD
    bufferedUpdatesStream.prune(segmentInfos);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "merge seg=" + merge.info.info.name + " " + segString(merge.segments));
    }
  }

  static void setDiagnostics(SegmentInfo info, String source) {
    setDiagnostics(info, source, null);
  }

  private static void setDiagnostics(SegmentInfo info, String source, Map<String,String> details) {
    Map<String,String> diagnostics = new HashMap<>();
    diagnostics.put("source", source);
    diagnostics.put("lucene.version", Version.LATEST.toString());
    diagnostics.put("os", Constants.OS_NAME);
    diagnostics.put("os.arch", Constants.OS_ARCH);
    diagnostics.put("os.version", Constants.OS_VERSION);
    diagnostics.put("java.version", Constants.JAVA_VERSION);
    diagnostics.put("java.vendor", Constants.JAVA_VENDOR);
    diagnostics.put("timestamp", Long.toString(new Date().getTime()));
    if (details != null) {
      diagnostics.putAll(details);
    }
    info.setDiagnostics(diagnostics);
  }

  final synchronized void mergeFinish(MergePolicy.OneMerge merge) {

    // forceMerge, addIndexes or waitForMerges may be waiting
    // on merges to finish.
    notifyAll();

    // It's possible we are called twice, eg if there was an
    // exception inside mergeInit
    if (merge.registerDone) {
      final List<SegmentCommitInfo> sourceSegments = merge.segments;
      for (SegmentCommitInfo info : sourceSegments) {
        mergingSegments.remove(info);
      }
      merge.registerDone = false;
    }

    runningMerges.remove(merge);
  }

  private final synchronized void closeMergeReaders(MergePolicy.OneMerge merge, boolean suppressExceptions) throws IOException {
    final int numSegments = merge.readers.size();
    Throwable th = null;

    boolean drop = !suppressExceptions;
    
    for (int i = 0; i < numSegments; i++) {
      final SegmentReader sr = merge.readers.get(i);
      if (sr != null) {
        try {
          final ReadersAndUpdates rld = readerPool.get(sr.getSegmentInfo(), false);
          // We still hold a ref so it should not have been removed:
          assert rld != null;
          if (drop) {
            rld.dropChanges();
          } else {
            rld.dropMergingUpdates();
          }
          rld.release(sr);
          readerPool.release(rld);
          if (drop) {
            readerPool.drop(rld.info);
          }
        } catch (Throwable t) {
          if (th == null) {
            th = t;
          }
        }
        merge.readers.set(i, null);
      }
    }
    
    // If any error occured, throw it.
    if (!suppressExceptions) {
      IOUtils.reThrow(th);
    }
  }


  private int mergeMiddle(MergePolicy.OneMerge merge, MergePolicy mergePolicy) throws IOException {

    merge.checkAborted(directory);

    final String mergedName = merge.info.info.name;

    List<SegmentCommitInfo> sourceSegments = merge.segments;
    
    IOContext context = new IOContext(merge.getMergeInfo());

    final MergeState.CheckAbort checkAbort = new MergeState.CheckAbort(merge, directory);
    final TrackingDirectoryWrapper dirWrapper = new TrackingDirectoryWrapper(directory);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "merging " + segString(merge.segments));
    }

    merge.readers = new ArrayList<>();

    // This is try/finally to make sure merger's readers are
    // closed:
    boolean success = false;
    try {
      int segUpto = 0;
      while(segUpto < sourceSegments.size()) {

        final SegmentCommitInfo info = sourceSegments.get(segUpto);

        // Hold onto the "live" reader; we will use this to
        // commit merged deletes
        final ReadersAndUpdates rld = readerPool.get(info, true);

        // Carefully pull the most recent live docs and reader
        SegmentReader reader;
        final Bits liveDocs;
        final int delCount;

        synchronized (this) {
          // Must sync to ensure BufferedDeletesStream cannot change liveDocs,
          // pendingDeleteCount and field updates while we pull a copy:
          reader = rld.getReaderForMerge(context);
          liveDocs = rld.getReadOnlyLiveDocs();
          delCount = rld.getPendingDeleteCount() + info.getDelCount();

          assert reader != null;
          assert rld.verifyDocCounts();

          if (infoStream.isEnabled("IW")) {
            if (rld.getPendingDeleteCount() != 0) {
              infoStream.message("IW", "seg=" + segString(info) + " delCount=" + info.getDelCount() + " pendingDelCount=" + rld.getPendingDeleteCount());
            } else if (info.getDelCount() != 0) {
              infoStream.message("IW", "seg=" + segString(info) + " delCount=" + info.getDelCount());
            } else {
              infoStream.message("IW", "seg=" + segString(info) + " no deletes");
            }
          }
        }

        // Deletes might have happened after we pulled the merge reader and
        // before we got a read-only copy of the segment's actual live docs
        // (taking pending deletes into account). In that case we need to
        // make a new reader with updated live docs and del count.
        if (reader.numDeletedDocs() != delCount) {
          // fix the reader's live docs and del count
          assert delCount > reader.numDeletedDocs(); // beware of zombies

          SegmentReader newReader;

          synchronized (this) {
            // We must also sync on IW here, because another thread could be writing
            // new DV updates / remove old gen field infos files causing FNFE:
            newReader = new SegmentReader(info, reader, liveDocs, info.info.getDocCount() - delCount);
          }

          boolean released = false;
          try {
            rld.release(reader);
            released = true;
          } finally {
            if (!released) {
              newReader.decRef();
            }
          }

          reader = newReader;
        }

        merge.readers.add(reader);
        assert delCount <= info.info.getDocCount(): "delCount=" + delCount + " info.docCount=" + info.info.getDocCount() + " rld.pendingDeleteCount=" + rld.getPendingDeleteCount() + " info.getDelCount()=" + info.getDelCount();
        segUpto++;
      }

//      System.out.println("[" + Thread.currentThread().getName() + "] IW.mergeMiddle: merging " + merge.getMergeReaders());
      
      // we pass merge.getMergeReaders() instead of merge.readers to allow the
      // OneMerge to return a view over the actual segments to merge
      final SegmentMerger merger = new SegmentMerger(merge.getMergeReaders(),
          merge.info.info, infoStream, dirWrapper, config.getTermIndexInterval(),
          checkAbort, globalFieldNumberMap, 
          context, config.getCheckIntegrityAtMerge());

      merge.checkAborted(directory);

      // This is where all the work happens:
      MergeState mergeState;
      boolean success3 = false;
      try {
        if (!merger.shouldMerge()) {
          // would result in a 0 document segment: nothing to merge!
          mergeState = new MergeState(new ArrayList<AtomicReader>(), merge.info.info, infoStream, checkAbort);
        } else {
          mergeState = merger.merge();
        }
        success3 = true;
      } finally {
        if (!success3) {
          synchronized(this) {  
            deleter.refresh(merge.info.info.name);
          }
        }
      }
      assert mergeState.segmentInfo == merge.info.info;
      merge.info.info.setFiles(new HashSet<>(dirWrapper.getCreatedFiles()));

      // Record which codec was used to write the segment

      if (infoStream.isEnabled("IW")) {
        if (merge.info.info.getDocCount() == 0) {
          infoStream.message("IW", "merge away fully deleted segments");
        } else {
          infoStream.message("IW", "merge codec=" + codec + " docCount=" + merge.info.info.getDocCount() + "; merged segment has " +
                           (mergeState.fieldInfos.hasVectors() ? "vectors" : "no vectors") + "; " +
                           (mergeState.fieldInfos.hasNorms() ? "norms" : "no norms") + "; " + 
                           (mergeState.fieldInfos.hasDocValues() ? "docValues" : "no docValues") + "; " + 
                           (mergeState.fieldInfos.hasProx() ? "prox" : "no prox") + "; " + 
                           (mergeState.fieldInfos.hasProx() ? "freqs" : "no freqs"));
        }
      }

      // Very important to do this before opening the reader
      // because codec must know if prox was written for
      // this segment:
      //System.out.println("merger set hasProx=" + merger.hasProx() + " seg=" + merge.info.name);
      boolean useCompoundFile;
      synchronized (this) { // Guard segmentInfos
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, merge.info, this);
      }

      if (useCompoundFile) {
        success = false;

        Collection<String> filesToRemove = merge.info.files();

        try {
          filesToRemove = createCompoundFile(infoStream, directory, checkAbort, merge.info.info, context);
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
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception creating compound file during merge");
            }

            synchronized(this) {
              deleter.deleteFile(IndexFileNames.segmentFileName(mergedName, "", IndexFileNames.COMPOUND_FILE_EXTENSION));
              deleter.deleteFile(IndexFileNames.segmentFileName(mergedName, "", IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION));
              deleter.deleteNewFiles(merge.info.files());
            }
          }
        }

        // So that, if we hit exc in deleteNewFiles (next)
        // or in commitMerge (later), we close the
        // per-segment readers in the finally clause below:
        success = false;

        synchronized(this) {

          // delete new non cfs files directly: they were never
          // registered with IFD
          deleter.deleteNewFiles(filesToRemove);

          if (merge.isAborted()) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "abort merge after building CFS");
            }
            deleter.deleteFile(IndexFileNames.segmentFileName(mergedName, "", IndexFileNames.COMPOUND_FILE_EXTENSION));
            deleter.deleteFile(IndexFileNames.segmentFileName(mergedName, "", IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION));
            return 0;
          }
        }

        merge.info.info.setUseCompoundFile(true);
      } else {
        // So that, if we hit exc in commitMerge (later),
        // we close the per-segment readers in the finally
        // clause below:
        success = false;
      }

      // Have codec write SegmentInfo.  Must do this after
      // creating CFS so that 1) .si isn't slurped into CFS,
      // and 2) .si reflects useCompoundFile=true change
      // above:
      boolean success2 = false;
      try {
        codec.segmentInfoFormat().getSegmentInfoWriter().write(directory, merge.info.info, mergeState.fieldInfos, context);
        success2 = true;
      } finally {
        if (!success2) {
          synchronized(this) {
            deleter.deleteNewFiles(merge.info.files());
          }
        }
      }

      // TODO: ideally we would freeze merge.info here!!
      // because any changes after writing the .si will be
      // lost... 

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", String.format(Locale.ROOT, "merged segment size=%.3f MB vs estimate=%.3f MB", merge.info.sizeInBytes()/1024./1024., merge.estimatedMergeBytes/1024/1024.));
      }

      final IndexReaderWarmer mergedSegmentWarmer = config.getMergedSegmentWarmer();
      if (poolReaders && mergedSegmentWarmer != null && merge.info.info.getDocCount() != 0) {
        final ReadersAndUpdates rld = readerPool.get(merge.info, true);
        final SegmentReader sr = rld.getReader(IOContext.READ);
        try {
          mergedSegmentWarmer.warm(sr);
        } finally {
          synchronized(this) {
            rld.release(sr);
            readerPool.release(rld);
          }
        }
      }

      // Force READ context because we merge deletes onto
      // this reader:
      if (!commitMerge(merge, mergeState)) {
        // commitMerge will return false if this merge was
        // aborted
        return 0;
      }

      success = true;

    } finally {
      // Readers are already closed in commitMerge if we didn't hit
      // an exc:
      if (!success) {
        closeMergeReaders(merge, true);
      }
    }

    return merge.info.info.getDocCount();
  }

  synchronized void addMergeException(MergePolicy.OneMerge merge) {
    assert merge.getException() != null;
    if (!mergeExceptions.contains(merge) && mergeGen == merge.mergeGen) {
      mergeExceptions.add(merge);
    }
  }

  // For test purposes.
  final int getBufferedDeleteTermsSize() {
    return docWriter.getBufferedDeleteTermsSize();
  }

  // For test purposes.
  final int getNumBufferedDeleteTerms() {
    return docWriter.getNumBufferedDeleteTerms();
  }

  // utility routines for tests
  synchronized SegmentCommitInfo newestSegment() {
    return segmentInfos.size() > 0 ? segmentInfos.info(segmentInfos.size()-1) : null;
  }


  public synchronized String segString() {
    return segString(segmentInfos);
  }


  public synchronized String segString(Iterable<SegmentCommitInfo> infos) {
    final StringBuilder buffer = new StringBuilder();
    for(final SegmentCommitInfo info : infos) {
      if (buffer.length() > 0) {
        buffer.append(' ');
      }
      buffer.append(segString(info));
    }
    return buffer.toString();
  }


  public synchronized String segString(SegmentCommitInfo info) {
    return info.toString(info.info.dir, numDeletedDocs(info) - info.getDelCount());
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


  void setKeepFullyDeletedSegments(boolean v) {
    keepFullyDeletedSegments = v;
  }

  boolean getKeepFullyDeletedSegments() {
    return keepFullyDeletedSegments;
  }

  // called only from assert
  private boolean filesExist(SegmentInfos toSync) throws IOException {
    
    Collection<String> files = toSync.files(directory, false);
    for(final String fileName: files) {
      assert slowFileExists(directory, fileName): "file " + fileName + " does not exist; files=" + Arrays.toString(directory.listAll());
      // If this trips it means we are missing a call to
      // .checkpoint somewhere, because by the time we
      // are called, deleter should know about every
      // file referenced by the current head
      // segmentInfos:
      assert deleter.exists(fileName): "IndexFileDeleter doesn't know about file " + fileName;
    }
    return true;
  }

  // For infoStream output
  synchronized SegmentInfos toLiveInfos(SegmentInfos sis) {
    final SegmentInfos newSIS = new SegmentInfos();
    final Map<SegmentCommitInfo,SegmentCommitInfo> liveSIS = new HashMap<>();
    for(SegmentCommitInfo info : segmentInfos) {
      liveSIS.put(info, info);
    }
    for(SegmentCommitInfo info : sis) {
      SegmentCommitInfo liveInfo = liveSIS.get(info);
      if (liveInfo != null) {
        info = liveInfo;
      }
      newSIS.add(info);
    }

    return newSIS;
  }


  private void startCommit(final SegmentInfos toSync) throws IOException {

    assert testPoint("startStartCommit");
    assert pendingCommit == null;

    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot commit", tragedy);
    }

    try {

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "startCommit(): start");
      }

      synchronized(this) {

        if (lastCommitChangeCount > changeCount) {
          throw new IllegalStateException("lastCommitChangeCount=" + lastCommitChangeCount + ",changeCount=" + changeCount);
        }

        if (pendingCommitChangeCount == lastCommitChangeCount) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "  skip startCommit(): no changes pending");
          }
          try {
            deleter.decRef(filesToCommit);
          } finally {
            filesToCommit = null;
          }
          return;
        }

        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "startCommit index=" + segString(toLiveInfos(toSync)) + " changeCount=" + changeCount);
        }

        assert filesExist(toSync);
      }

      assert testPoint("midStartCommit");

      boolean pendingCommitSet = false;

      try {

        assert testPoint("midStartCommit2");

        synchronized(this) {

          assert pendingCommit == null;

          assert segmentInfos.getGeneration() == toSync.getGeneration();

          // Exception here means nothing is prepared
          // (this method unwinds everything it did on
          // an exception)
          toSync.prepareCommit(directory);
          //System.out.println("DONE prepareCommit");

          // prepareCommit may have created new _N.si and _N_upgraded.si
          // (marker) files if the segments file was 3.x, so we must now
          // (while still under IW's monitor lock) incRef these files in
          // IFD to protect them until the commit is done:
          final Collection<String> newFiles = toSync.files(directory, false);
          deleter.incRef(newFiles);
          deleter.decRef(filesToCommit);
          filesToCommit = newFiles;

          pendingCommitSet = true;
          pendingCommit = toSync;
        }

        // This call can take a long time -- 10s of seconds
        // or more.  We do it without syncing on this:
        boolean success = false;
        try {
          directory.sync(filesToCommit);
          success = true;
        } finally {
          if (!success) {
            pendingCommitSet = false;
            pendingCommit = null;
            toSync.rollbackCommit(directory);
          }
        }

        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "done all syncs: " + filesToCommit);
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
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception committing segments file");
            }

            // Hit exception
            deleter.decRefWhileHandlingException(filesToCommit);
            filesToCommit = null;
          }
        }
      }
    } catch (OutOfMemoryError oom) {
      tragicEvent(oom, "startCommit");
    }
    assert testPoint("finishStartCommit");
  }

  public static boolean isLocked(Directory directory) throws IOException {
    return directory.makeLock(WRITE_LOCK_NAME).isLocked();
  }

  @Deprecated
  public static void unlock(Directory directory) throws IOException {
    directory.makeLock(IndexWriter.WRITE_LOCK_NAME).close();
  }


  public static abstract class IndexReaderWarmer {

    protected IndexReaderWarmer() {
    }


    public abstract void warm(AtomicReader reader) throws IOException;
  }

  private void tragicEvent(Throwable tragedy, String location) {
    // We cannot hold IW's lock here else it can lead to deadlock:
    assert Thread.holdsLock(this) == false;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "hit " + tragedy.getClass().getSimpleName() + " inside " + location);
    }
    synchronized (this) {
      // its possible you could have a really bad day
      if (this.tragedy == null) {
        this.tragedy = tragedy;
      }
    }
    // if we are already closed (e.g. called by rollback), this will be a no-op.
    synchronized(commitLock) {
      if (closing == false) {
        try {
          rollback();
        } catch (Throwable ignored) {
          // it would be confusing to addSuppressed here, its unrelated to the disaster,
          // and its possible our internal state is amiss anyway.
        }
      }
    }
    IOUtils.reThrowUnchecked(tragedy);
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
  private final boolean testPoint(String message) {
    if (infoStream.isEnabled("TP")) {
      infoStream.message("TP", message);
    }
    return true;
  }

  synchronized boolean nrtIsCurrent(SegmentInfos infos) {
    //System.out.println("IW.nrtIsCurrent " + (infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedDeletesStream.any()));
    ensureOpen();
    boolean isCurrent = infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedUpdatesStream.any();
    if (infoStream.isEnabled("IW")) {
      if (isCurrent == false) {
        infoStream.message("IW", "nrtIsCurrent: infoVersion matches: " + (infos.version == segmentInfos.version) + "; DW changes: " + docWriter.anyChanges() + "; BD changes: "+ bufferedUpdatesStream.any());
      }
    }
    return isCurrent;
  }

  synchronized boolean isClosed() {
    return closed;
  }


  public synchronized void deleteUnusedFiles() throws IOException {
    ensureOpen(false);
    deleter.deletePendingFiles();
    deleter.revisitPolicy();
  }

  private synchronized void deletePendingFiles() throws IOException {
    deleter.deletePendingFiles();
  }
  
  static final Collection<String> createCompoundFile(InfoStream infoStream, Directory directory, CheckAbort checkAbort, final SegmentInfo info, IOContext context)
          throws IOException {

    final String fileName = IndexFileNames.segmentFileName(info.name, "", IndexFileNames.COMPOUND_FILE_EXTENSION);
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "create compound file " + fileName);
    }
    assert Lucene3xSegmentInfoFormat.getDocStoreOffset(info) == -1;
    // Now merge all added files
    Collection<String> files = info.files();
    CompoundFileDirectory cfsDir = new CompoundFileDirectory(directory, fileName, context, true);
    boolean success = false;
    try {
      for (String file : files) {
        directory.copy(cfsDir, file, file, context);
        checkAbort.work(directory.fileLength(file));
      }
      success = true;
    } finally {
      if (success) {
        IOUtils.close(cfsDir);
      } else {
        IOUtils.closeWhileHandlingException(cfsDir);
        try {
          directory.deleteFile(fileName);
        } catch (Throwable t) {
        }
        try {
          directory.deleteFile(IndexFileNames.segmentFileName(info.name, "", IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION));
        } catch (Throwable t) {
        }
      }
    }

    // Replace all previous files with the CFS/CFE files:
    Set<String> siFiles = new HashSet<>();
    siFiles.add(fileName);
    siFiles.add(IndexFileNames.segmentFileName(info.name, "", IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION));
    info.setFiles(siFiles);

    return files;
  }
  
  synchronized final void deleteNewFiles(Collection<String> files) throws IOException {
    deleter.deleteNewFiles(files);
  }
  
  synchronized final void flushFailed(SegmentInfo info) throws IOException {
    deleter.refresh(info.name);
  }
  
  final int purge(boolean forced) throws IOException {
    return docWriter.purgeBuffer(this, forced);
  }

  final void applyDeletesAndPurge(boolean forcePurge) throws IOException {
    try {
      purge(forcePurge);
    } finally {
      applyAllDeletesAndUpdates();
      flushCount.incrementAndGet();
    }
  }
  
  final void doAfterSegmentFlushed(boolean triggerMerge, boolean forcePurge) throws IOException {
    try {
      purge(forcePurge);
    } finally {
      if (triggerMerge) {
        maybeMerge(config.getMergePolicy(), MergeTrigger.SEGMENT_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
      }
    }
    
  }
  
  synchronized void incRefDeleter(SegmentInfos segmentInfos) throws IOException {
    ensureOpen();
    deleter.incRef(segmentInfos, false);
  }
  
  synchronized void decRefDeleter(SegmentInfos segmentInfos) throws IOException {
    ensureOpen();
    deleter.decRef(segmentInfos);
  }
  
  private boolean processEvents(boolean triggerMerge, boolean forcePurge) throws IOException {
    return processEvents(eventQueue, triggerMerge, forcePurge);
  }
  
  private boolean processEvents(Queue<Event> queue, boolean triggerMerge, boolean forcePurge) throws IOException {
    Event event;
    boolean processed = false;
    while((event = queue.poll()) != null)  {
      processed = true;
      event.process(this, triggerMerge, forcePurge);
    }
    return processed;
  }
  
  static interface Event {
    
    void process(IndexWriter writer, boolean triggerMerge, boolean clearBuffers) throws IOException;
  }


  private static boolean slowFileExists(Directory dir, String fileName) throws IOException {
    try {
      dir.openInput(fileName, IOContext.DEFAULT).close();
      return true;
    } catch (NoSuchFileException | FileNotFoundException e) {
      return false;
    }
  }


  private void reserveDocs(long addedNumDocs) {
    assert addedNumDocs >= 0;
    if (pendingNumDocs.addAndGet(addedNumDocs) > actualMaxDocs) {
      // Reserve failed: put the docs back and throw exc:
      pendingNumDocs.addAndGet(-addedNumDocs);
      tooManyDocs(addedNumDocs);
    }
  }


  private void testReserveDocs(long addedNumDocs) {
    assert addedNumDocs >= 0;
    if (pendingNumDocs.get() + addedNumDocs > actualMaxDocs) {
      tooManyDocs(addedNumDocs);
    }
  }

  private void tooManyDocs(long addedNumDocs) {
    assert addedNumDocs >= 0;
    throw new IllegalArgumentException("number of documents in the index cannot exceed " + actualMaxDocs + " (current document count is " + pendingNumDocs.get() + "; added numDocs is " + addedNumDocs + ")");
  }
}
