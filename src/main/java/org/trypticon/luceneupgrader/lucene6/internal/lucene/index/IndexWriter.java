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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValuesUpdate.BinaryDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValuesUpdate.NumericDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfos.FieldNumbers;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.MatchAllDocsQuery;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.FlushInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Lock;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.LockObtainFailedException;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.LockValidatingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.MMapDirectory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.MergeInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.TrackingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ThreadInterruptedException;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.UnicodeUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Version;

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

  public static final int MAX_POSITION = Integer.MAX_VALUE - 128;

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
  
  boolean enableTestPoints = false;

  private static final int UNBOUNDED_MAX_MERGE_SEGMENTS = -1;
  
  public static final String WRITE_LOCK_NAME = "write.lock";

  public static final String SOURCE = "source";
  public static final String SOURCE_MERGE = "merge";
  public static final String SOURCE_FLUSH = "flush";
  public static final String SOURCE_ADDINDEXES_READERS = "addIndexes(CodecReader...)";

  public final static int MAX_TERM_LENGTH = DocumentsWriterPerThread.MAX_TERM_LENGTH_UTF8;

  public final static int MAX_STORED_STRING_LENGTH = ArrayUtil.MAX_ARRAY_LENGTH / UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR;
    
  // when unrecoverable disaster strikes, we populate this with the reason that we had to close IndexWriter
  volatile Throwable tragedy;

  private final Directory directoryOrig;       // original user directory
  private final Directory directory;           // wrapped with additional checks
  private final Analyzer analyzer;    // how to analyze text

  private final AtomicLong changeCount = new AtomicLong(); // increments every time a change is completed
  private volatile long lastCommitChangeCount; // last changeCount that was committed

  private List<SegmentCommitInfo> rollbackSegments;      // list of segmentInfo we will fallback to if the commit fails

  volatile SegmentInfos pendingCommit;            // set when a commit is pending (after prepareCommit() & before commit())
  volatile long pendingSeqNo;
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

  private Iterable<Map.Entry<String,String>> commitUserData;

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
    return getReader(true, false);
  }

  DirectoryReader getReader(boolean applyAllDeletes, boolean writeAllDeletes) throws IOException {
    ensureOpen();

    if (writeAllDeletes && applyAllDeletes == false) {
      throw new IllegalArgumentException("applyAllDeletes must be true when writeAllDeletes=true");
    }

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
    boolean anyChanges = false;
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
          // TODO: should we somehow make this available in the returned NRT reader?
          long seqNo = docWriter.flushAllThreads();
          if (seqNo < 0) {
            anyChanges = true;
            seqNo = -seqNo;
          } else {
            anyChanges = false;
          }
          if (!anyChanges) {
            // prevent double increment since docWriter#doFlush increments the flushcount
            // if we flushed anything.
            flushCount.incrementAndGet();
          }
          // Prevent segmentInfos from changing while opening the
          // reader; in theory we could instead do similar retry logic,
          // just like we do when loading segments_N
          synchronized(this) {
            anyChanges |= maybeApplyDeletes(applyAllDeletes);
            if (writeAllDeletes) {
              // Must move the deletes to disk:
              readerPool.commit(segmentInfos);
            }

            r = StandardDirectoryReader.open(this, segmentInfos, applyAllDeletes, writeAllDeletes);
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
      if (anyChanges) {
        maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
      }
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "getReader took " + (System.currentTimeMillis() - tStart) + " msec");
      }
      success2 = true;
    } catch (AbortingException | VirtualMachineError tragedy) {
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
            throw IOUtils.rethrowAlways(t);
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
            throw IOUtils.rethrowAlways(t);
          } else if (priorE == null) {
            priorE = t;
          }
        }
      }
      assert readerMap.size() == 0;
      if (priorE != null) {
        throw IOUtils.rethrowAlways(priorE);
      }
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

      assert info.info.dir == directoryOrig: "info.dir=" + info.info.dir + " vs " + directoryOrig;

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
    if (d instanceof FSDirectory && ((FSDirectory) d).checkPendingDeletions()) {
      throw new IllegalArgumentException("Directory " + d + " still has pending deleted files; cannot initialize IndexWriter");
    }

    conf.setIndexWriter(this); // prevent reuse by other instances
    config = conf;
    infoStream = config.getInfoStream();

    // obtain the write.lock. If the user configured a timeout,
    // we wrap with a sleeper and this might take some time.
    writeLock = d.obtainLock(WRITE_LOCK_NAME);
    
    boolean success = false;
    try {
      directoryOrig = d;
      directory = new LockValidatingDirectoryWrapper(d, writeLock);

      analyzer = config.getAnalyzer();
      mergeScheduler = config.getMergeScheduler();
      mergeScheduler.setInfoStream(infoStream);
      codec = config.getCodec();

      bufferedUpdatesStream = new BufferedUpdatesStream(infoStream);
      poolReaders = config.getReaderPooling();

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

      boolean initialIndexExists = true;

      String[] files = directory.listAll();

      // Set up our initial SegmentInfos:
      IndexCommit commit = config.getIndexCommit();

      // Set up our initial SegmentInfos:
      StandardDirectoryReader reader;
      if (commit == null) {
        reader = null;
      } else {
        reader = commit.getReader();
      }

      if (create) {

        if (config.getIndexCommit() != null) {
          // We cannot both open from a commit point and create:
          if (mode == OpenMode.CREATE) {
            throw new IllegalArgumentException("cannot use IndexWriterConfig.setIndexCommit() with OpenMode.CREATE");
          } else {
            throw new IllegalArgumentException("cannot use IndexWriterConfig.setIndexCommit() when index has no commit");
          }
        }

        // Try to read first.  This is to allow create
        // against an index that's currently open for
        // searching.  In this case we write the next
        // segments_N file with no segments:
        SegmentInfos sis = null;
        try {
          sis = SegmentInfos.readLatestCommit(directory);
          sis.clear();
        } catch (IOException e) {
          // Likely this means it's a fresh directory
          initialIndexExists = false;
          sis = new SegmentInfos();
        }
        
        segmentInfos = sis;

        rollbackSegments = segmentInfos.createBackupSegmentInfos();

        // Record that we have a change (zero out all
        // segments) pending:
        changed();

      } else if (reader != null) {
        // Init from an existing already opened NRT or non-NRT reader:
      
        if (reader.directory() != commit.getDirectory()) {
          throw new IllegalArgumentException("IndexCommit's reader must have the same directory as the IndexCommit");
        }

        if (reader.directory() != directoryOrig) {
          throw new IllegalArgumentException("IndexCommit's reader must have the same directory passed to IndexWriter");
        }

        if (reader.segmentInfos.getLastGeneration() == 0) {  
          // TODO: maybe we could allow this?  It's tricky...
          throw new IllegalArgumentException("index must already have an initial commit to open from reader");
        }

        // Must clone because we don't want the incoming NRT reader to "see" any changes this writer now makes:
        segmentInfos = reader.segmentInfos.clone();

        SegmentInfos lastCommit;
        try {
          lastCommit = SegmentInfos.readCommit(directoryOrig, segmentInfos.getSegmentsFileName());
        } catch (IOException ioe) {
          throw new IllegalArgumentException("the provided reader is stale: its prior commit file \"" + segmentInfos.getSegmentsFileName() + "\" is missing from index");
        }

        if (reader.writer != null) {

          // The old writer better be closed (we have the write lock now!):
          assert reader.writer.closed;

          // In case the old writer wrote further segments (which we are now dropping),
          // update SIS metadata so we remain write-once:
          segmentInfos.updateGenerationVersionAndCounter(reader.writer.segmentInfos);
          lastCommit.updateGenerationVersionAndCounter(reader.writer.segmentInfos);
        }

        rollbackSegments = lastCommit.createBackupSegmentInfos();

        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "init from reader " + reader);
          messageState();
        }
      } else {
        // Init from either the latest commit point, or an explicit prior commit point:

        String lastSegmentsFile = SegmentInfos.getLastCommitSegmentsFileName(files);
        if (lastSegmentsFile == null) {
          throw new IndexNotFoundException("no segments* file found in " + directory + ": files: " + Arrays.toString(files));
        }

        // Do not use SegmentInfos.read(Directory) since the spooky
        // retrying it does is not necessary here (we hold the write lock):
        segmentInfos = SegmentInfos.readCommit(directoryOrig, lastSegmentsFile);

        if (commit != null) {
          // Swap out all segments, but, keep metadata in
          // SegmentInfos, like version & generation, to
          // preserve write-once.  This is important if
          // readers are open against the future commit
          // points.
          if (commit.getDirectory() != directoryOrig) {
            throw new IllegalArgumentException("IndexCommit's directory doesn't match my directory, expected=" + directoryOrig + ", got=" + commit.getDirectory());
          }
          
          SegmentInfos oldInfos = SegmentInfos.readCommit(directoryOrig, commit.getSegmentsFileName());
          segmentInfos.replace(oldInfos);
          changed();

          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "init: loaded commit \"" + commit.getSegmentsFileName() + "\"");
          }
        }

        rollbackSegments = segmentInfos.createBackupSegmentInfos();
      }

      commitUserData = new HashMap<String,String>(segmentInfos.getUserData()).entrySet();

      pendingNumDocs.set(segmentInfos.totalMaxDoc());

      // start with previous field numbers, but new FieldInfos
      // NOTE: this is correct even for an NRT reader because we'll pull FieldInfos even for the un-committed segments:
      globalFieldNumberMap = getFieldNumberMap();

      validateIndexSort();

      config.getFlushPolicy().init(config);
      docWriter = new DocumentsWriter(this, config, directoryOrig, directory);
      eventQueue = docWriter.eventQueue();

      // Default deleter (for backwards compatibility) is
      // KeepOnlyLastCommitDeleter:

      // Sync'd is silly here, but IFD asserts we sync'd on the IW instance:
      synchronized(this) {
        deleter = new IndexFileDeleter(files, directoryOrig, directory,
                                       config.getIndexDeletionPolicy(),
                                       segmentInfos, infoStream, this,
                                       initialIndexExists, reader != null);

        // We incRef all files when we return an NRT reader from IW, so all files must exist even in the NRT case:
        assert create || filesExist(segmentInfos);
      }

      if (deleter.startingCommitDeleted) {
        // Deletion policy deleted the "head" commit point.
        // We have to mark ourself as changed so that if we
        // are closed w/o any further changes we write a new
        // segments_N file.
        changed();
      }

      if (reader != null) {
        // Pre-enroll all segment readers into the reader pool; this is necessary so
        // any in-memory NRT live docs are correctly carried over, and so NRT readers
        // pulled from this IW share the same segment reader:
        List<LeafReaderContext> leaves = reader.leaves();
        assert segmentInfos.size() == leaves.size();

        for (int i=0;i<leaves.size();i++) {
          LeafReaderContext leaf = leaves.get(i);
          SegmentReader segReader = (SegmentReader) leaf.reader();
          SegmentReader newReader = new SegmentReader(segmentInfos.info(i), segReader, segReader.getLiveDocs(), segReader.numDocs());
          readerPool.readerMap.put(newReader.getSegmentInfo(), new ReadersAndUpdates(this, newReader));
        }

        // We always assume we are carrying over incoming changes when opening from reader:
        segmentInfos.changed();
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


  private void validateIndexSort() throws CorruptIndexException {
    Sort indexSort = config.getIndexSort();
    if (indexSort != null) {
      for(SegmentCommitInfo info : segmentInfos) {
        Sort segmentIndexSort = info.info.getIndexSort();
        if (segmentIndexSort != null && indexSort.equals(segmentIndexSort) == false) {
          throw new IllegalArgumentException("cannot change previous indexSort=" + segmentIndexSort + " (from segment=" + info + ") to new indexSort=" + indexSort);
        } else if (segmentIndexSort == null && info.info.getVersion().onOrAfter(Version.LUCENE_6_5_0)) {
          // Flushed segments are not sorted if they were built with a version prior to 6.5.0
          throw new CorruptIndexException("segment not sorted with indexSort=" + segmentIndexSort, info.info.toString());
        }
      }
    }
  }

  // reads latest field infos for the commit
  // this is used on IW init and addIndexes(Dir) to create/update the global field map.
  // TODO: fix tests abusing this method!
  static FieldInfos readFieldInfos(SegmentCommitInfo si) throws IOException {
    Codec codec = si.info.getCodec();
    FieldInfosFormat reader = codec.fieldInfosFormat();
    
    if (si.hasFieldUpdates()) {
      // there are updates, we read latest (always outside of CFS)
      final String segmentSuffix = Long.toString(si.getFieldInfosGen(), Character.MAX_RADIX);
      return reader.read(si.info.dir, si.info, segmentSuffix, IOContext.READONCE);
    } else if (si.info.getUseCompoundFile()) {
      // cfs
      try (Directory cfs = codec.compoundFormat().getCompoundReader(si.info.dir, si.info, IOContext.DEFAULT)) {
        return reader.read(cfs, si.info, "", IOContext.READONCE);
      }
    } else {
      // no cfs
      return reader.read(si.info.dir, si.info, "", IOContext.READONCE);
    }
  }

  private FieldNumbers getFieldNumberMap() throws IOException {
    final FieldNumbers map = new FieldNumbers();

    for(SegmentCommitInfo info : segmentInfos) {
      FieldInfos fis = readFieldInfos(info);
      for(FieldInfo fi : fis) {
        map.addOrGet(fi.name, fi.number, fi.getDocValuesType(), fi.getPointDimensionCount(), fi.getPointNumBytes());
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
      infoStream.message("IW", "\ndir=" + directoryOrig + "\n" +
            "index=" + segString() + "\n" +
            "version=" + Version.LATEST.toString() + "\n" +
            config.toString());
      final StringBuilder unmapInfo = new StringBuilder(Boolean.toString(MMapDirectory.UNMAP_SUPPORTED));
      if (!MMapDirectory.UNMAP_SUPPORTED) {
        unmapInfo.append(" (").append(MMapDirectory.UNMAP_NOT_SUPPORTED_REASON).append(")");
      }
      infoStream.message("IW", "MMapDirectory.UNMAP_SUPPORTED=" + unmapInfo);
    }
  }

  private void shutdown() throws IOException {
    if (pendingCommit != null) {
      throw new IllegalStateException("cannot close: prepareCommit was already called with no corresponding call to commit");
    }
    // Ensure that only one thread actually gets to do the
    // closing
    if (shouldClose(true)) {
      boolean success = false;
      try {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "now flush at close");
        }
        flush(true, true);
        waitForMerges();
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
    if (config.getCommitOnClose()) {
      shutdown();
    } else {
      rollback();
    }
  }

  // Returns true if this thread should attempt to close, or
  // false if IndexWriter is now closed; else,
  // waits until another thread finishes closing
  synchronized private boolean shouldClose(boolean waitForClose) {
    while (true) {
      if (closed == false) {
        if (closing == false) {
          // We get to close
          closing = true;
          return true;
        } else if (waitForClose == false) {
          return false;
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

  public Directory getDirectory() {
    // return the original directory the user supplied, unwrapped.
    return directoryOrig;
  }

  public Analyzer getAnalyzer() {
    ensureOpen();
    return analyzer;
  }


  public synchronized int maxDoc() {
    ensureOpen();
    return docWriter.getNumDocs() + segmentInfos.totalMaxDoc();
  }


  public synchronized void advanceSegmentInfosVersion(long newVersion) {
    ensureOpen();
    if (segmentInfos.getVersion() < newVersion) {
      segmentInfos.setVersion(newVersion);
    }
    changed();
  }


  public synchronized int numDocs() {
    ensureOpen();
    int count = docWriter.getNumDocs();
    for (final SegmentCommitInfo info : segmentInfos) {
      count += info.info.maxDoc() - numDeletedDocs(info);
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

  public long addDocument(Iterable<? extends IndexableField> doc) throws IOException {
    return updateDocument(null, doc);
  }

  public long addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    return updateDocuments(null, docs);
  }

  public long updateDocuments(Term delTerm, Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    ensureOpen();
    try {
      boolean success = false;
      try {
        long seqNo = docWriter.updateDocuments(docs, analyzer, delTerm);
        if (seqNo < 0) {
          seqNo = -seqNo;
          processEvents(true, false);
        }
        success = true;
        return seqNo;
      } finally {
        if (!success) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "hit exception updating document");
          }
        }
      }
    } catch (AbortingException | VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocuments");

      // dead code but javac disagrees
      return -1;
    }
  }


  public synchronized long tryDeleteDocument(IndexReader readerIn, int docID) throws IOException {

    final LeafReader reader;
    if (readerIn instanceof LeafReader) {
      // Reader is already atomic: use the incoming docID:
      reader = (LeafReader) readerIn;
    } else {
      // Composite reader: lookup sub-reader and re-base docID:
      List<LeafReaderContext> leaves = readerIn.leaves();
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
            if (fullDelCount == rld.info.info.maxDoc()) {
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
          return docWriter.deleteQueue.getNextSequenceNumber();
        }
      } else {
        //System.out.println("  no rld " + info.info.name + " " + docID);
      }
    } else {
      //System.out.println("  no seg " + info.info.name + " " + docID);
    }

    return -1;
  }

  public long deleteDocuments(Term... terms) throws IOException {
    ensureOpen();
    try {
      long seqNo = docWriter.deleteTerms(terms);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true, false);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteDocuments(Term..)");

      // dead code but javac disagrees:
      return -1;
    }
  }

  public long deleteDocuments(Query... queries) throws IOException {
    ensureOpen();

    // LUCENE-6379: Specialize MatchAllDocsQuery
    for(Query query : queries) {
      if (query.getClass() == MatchAllDocsQuery.class) {
        return deleteAll();
      }
    }

    try {
      long seqNo = docWriter.deleteQueries(queries);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true, false);
      }

      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteDocuments(Query..)");

      // dead code but javac disagrees:
      return -1;
    }
  }

  public long updateDocument(Term term, Iterable<? extends IndexableField> doc) throws IOException {
    ensureOpen();
    try {
      boolean success = false;
      try {
        long seqNo = docWriter.updateDocument(doc, analyzer, term);
        if (seqNo < 0) {
          seqNo = - seqNo;
          processEvents(true, false);
        }
        success = true;
        return seqNo;
      } finally {
        if (!success) {
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "hit exception updating document");
          }
        }
      }
    } catch (AbortingException | VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocument");

      // dead code but javac disagrees:
      return -1;
    }
  }

  public long updateNumericDocValue(Term term, String field, long value) throws IOException {
    ensureOpen();
    if (!globalFieldNumberMap.contains(field, DocValuesType.NUMERIC)) {
      throw new IllegalArgumentException("can only update existing numeric-docvalues fields!");
    }
    if (config.getIndexSortFields().contains(field)) {
      throw new IllegalArgumentException("cannot update docvalues field involved in the index sort, field=" + field + ", sort=" + config.getIndexSort());
    }
    try {
      long seqNo = docWriter.updateDocValues(new NumericDocValuesUpdate(term, field, value));
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true, false);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateNumericDocValue");

      // dead code but javac disagrees:
      return -1;
    }
  }

  public long updateBinaryDocValue(Term term, String field, BytesRef value) throws IOException {
    ensureOpen();
    if (value == null) {
      throw new IllegalArgumentException("cannot update a field to a null value: " + field);
    }
    if (!globalFieldNumberMap.contains(field, DocValuesType.BINARY)) {
      throw new IllegalArgumentException("can only update existing binary-docvalues fields!");
    }
    try {
      long seqNo = docWriter.updateDocValues(new BinaryDocValuesUpdate(term, field, value));
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true, false);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateBinaryDocValue");

      // dead code but javac disagrees:
      return -1;
    }
  }
  
  public long updateDocValues(Term term, Field... updates) throws IOException {
    ensureOpen();
    DocValuesUpdate[] dvUpdates = new DocValuesUpdate[updates.length];
    for (int i = 0; i < updates.length; i++) {
      final Field f = updates[i];
      final DocValuesType dvType = f.fieldType().docValuesType();
      if (dvType == null) {
        throw new NullPointerException("DocValuesType must not be null (field: \"" + f.name() + "\")");
      }
      if (dvType == DocValuesType.NONE) {
        throw new IllegalArgumentException("can only update NUMERIC or BINARY fields! field=" + f.name());
      }
      if (!globalFieldNumberMap.contains(f.name(), dvType)) {
        throw new IllegalArgumentException("can only update existing docvalues fields! field=" + f.name() + ", type=" + dvType);
      }
      if (config.getIndexSortFields().contains(f.name())) {
        throw new IllegalArgumentException("cannot update docvalues field involved in the index sort, field=" + f.name() + ", sort=" + config.getIndexSort());
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
      long seqNo = docWriter.updateDocValues(dvUpdates);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true, false);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocValues");

      // dead code but javac disagrees:
      return -1;
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
    return segmentInfos.files(true);
  }

  // for test purpose
  final synchronized int maxDoc(int i) {
    if (i >= 0 && i < segmentInfos.size()) {
      return segmentInfos.info(i).info.maxDoc();
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

  public Set<String> getFieldNames() {
    return globalFieldNumberMap.getFieldNames(); // FieldNumbers#getFieldNames() returns an unmodifiableSet
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
      changeCount.incrementAndGet();
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
              if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) {
                throw new IOException("background merge hit exception: " + merge.segString(), merge.getException());
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
      if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS)
        return true;
    }

    for (final MergePolicy.OneMerge merge : runningMerges) {
      if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS)
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
              throw new IOException("background merge hit exception: " + merge.segString(), t);
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

    assert maxNumSegments == UNBOUNDED_MAX_MERGE_SEGMENTS || maxNumSegments > 0;
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
    if (shouldClose(true)) {
      rollbackInternal();
    }
  }

  private void rollbackInternal() throws IOException {
    // Make sure no commit is running, else e.g. we can close while another thread is still fsync'ing:
    synchronized(commitLock) {
      rollbackInternalNoCommit();
    }
  }

  private void rollbackInternalNoCommit() throws IOException {
    boolean success = false;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "rollback");
    }
    
    try {
      abortMerges();

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "rollback: done finish merges");
      }

      // Must pre-close in case it increments changeCount so that we can then
      // set it to false before calling rollbackInternal
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
        // of its SegmentInfo instances so IFD below will remove
        // any segments we flushed since the last commit:
        segmentInfos.rollbackSegmentInfos(rollbackSegments);

        if (infoStream.isEnabled("IW") ) {
          infoStream.message("IW", "rollback: infos=" + segString(segmentInfos));
        }

        testPoint("rollback before checkpoint");

        // Ask deleter to locate unreferenced files & remove
        // them ... only when we are not experiencing a tragedy, else
        // these methods throw ACE:
        if (tragedy == null) {
          deleter.checkpoint(segmentInfos, false);
          deleter.refresh();
          deleter.close();
        }

        lastCommitChangeCount = changeCount.get();

        // Must set closed while inside same sync block where we call deleter.refresh, else concurrent threads may try to sneak a flush in,
        // after we leave this sync block and before we enter the sync block in the finally clause below that sets closed:
        closed = true;

        IOUtils.close(writeLock);                     // release write lock
        writeLock = null;
      }

      success = true;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "rollbackInternal");
    } finally {
      if (success == false) {
        // Must not hold IW's lock while closing
        // mergeScheduler: this can lead to deadlock,
        // e.g. TestIW.testThreadInterruptDeadlock
        IOUtils.closeWhileHandlingException(mergeScheduler);
      }
      synchronized(this) {
        if (success == false) {
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

        // So any "concurrently closing" threads wake up and see that the close has now completed:
        notifyAll();
      }
    }
  }

  public long deleteAll() throws IOException {
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
            // Let merges run again
            stopMerges = false;
            // Remove all segments
            pendingNumDocs.addAndGet(-segmentInfos.totalMaxDoc());
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
            changeCount.incrementAndGet();
            segmentInfos.changed();
            globalFieldNumberMap.clear();

            success = true;
            long seqNo = docWriter.deleteQueue.getNextSequenceNumber();
            docWriter.setLastSeqNo(seqNo);
            return seqNo;

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
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteAll");

      // dead code but javac disagrees
      return -1;
    }
  }


  private synchronized void abortMerges() {

    stopMerges = true;

    // Abort all pending & running merges:
    for (final MergePolicy.OneMerge merge : pendingMerges) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now abort pending merge " + segString(merge.segments));
      }
      merge.setAborted();
      mergeFinish(merge);
    }
    pendingMerges.clear();

    for (final MergePolicy.OneMerge merge : runningMerges) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now abort running merge " + segString(merge.segments));
      }
      merge.setAborted();
    }

    // We wait here to make all merges stop.  It should not
    // take very long because they periodically check if
    // they are aborted.
    while (runningMerges.size() != 0) {

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "now wait for " + runningMerges.size() + " running merge/s to abort");
      }

      doWait();
    }

    notifyAll();
    assert 0 == mergingSegments.size();

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "all running merges have aborted");
    }
  }

  void waitForMerges() throws IOException {

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
    changeCount.incrementAndGet();
    deleter.checkpoint(segmentInfos, false);
  }

  synchronized void changed() {
    changeCount.incrementAndGet();
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
      if (dirs[i] == directoryOrig)
        throw new IllegalArgumentException("Cannot add directory to itself");
      dups.add(dirs[i]);
    }
  }


  private List<Lock> acquireWriteLocks(Directory... dirs) throws IOException {
    List<Lock> locks = new ArrayList<>(dirs.length);
    for(int i=0;i<dirs.length;i++) {
      boolean success = false;
      try {
        Lock lock = dirs[i].obtainLock(WRITE_LOCK_NAME);
        locks.add(lock);
        success = true;
      } finally {
        if (success == false) {
          // Release all previously acquired locks:
          // TODO: addSuppressed? it could be many...
          IOUtils.closeWhileHandlingException(locks);
        }
      }
    }
    return locks;
  }

  public long addIndexes(Directory... dirs) throws IOException {
    ensureOpen();

    noDupDirs(dirs);

    List<Lock> locks = acquireWriteLocks(dirs);

    Sort indexSort = config.getIndexSort();

    boolean successTop = false;

    long seqNo;

    try {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "flush at addIndexes(Directory...)");
      }

      flush(false, true);

      List<SegmentCommitInfo> infos = new ArrayList<>();

      // long so we can detect int overflow:
      long totalMaxDoc = 0;
      List<SegmentInfos> commits = new ArrayList<>(dirs.length);
      for (Directory dir : dirs) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "addIndexes: process directory " + dir);
        }
        SegmentInfos sis = SegmentInfos.readLatestCommit(dir); // read infos from dir
        totalMaxDoc += sis.totalMaxDoc();
        commits.add(sis);
      }

      // Best-effort up front check:
      testReserveDocs(totalMaxDoc);
        
      boolean success = false;
      try {
        for (SegmentInfos sis : commits) {
          for (SegmentCommitInfo info : sis) {
            assert !infos.contains(info): "dup info dir=" + info.info.dir + " name=" + info.info.name;

            Sort segmentIndexSort = info.info.getIndexSort();

            if (indexSort != null && segmentIndexSort != null && indexSort.equals(segmentIndexSort) == false) {
              // TODO: we could make this smarter, e.g. if the incoming indexSort is congruent with our sort ("starts with") then it's OK
              throw new IllegalArgumentException("cannot change index sort from " + segmentIndexSort + " to " + indexSort);
            }

            String newSegName = newSegmentName();

            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "addIndexes: process segment origName=" + info.info.name + " newName=" + newSegName + " info=" + info);
            }

            IOContext context = new IOContext(new FlushInfo(info.info.maxDoc(), info.sizeInBytes()));

            FieldInfos fis = readFieldInfos(info);
            for(FieldInfo fi : fis) {
              // This will throw exceptions if any of the incoming fields have an illegal schema change:
              globalFieldNumberMap.addOrGet(fi.name, fi.number, fi.getDocValuesType(), fi.getPointDimensionCount(), fi.getPointNumBytes());
            }
            infos.add(copySegmentAsIs(info, newSegName, context));
          }
        }
        success = true;
      } finally {
        if (!success) {
          for(SegmentCommitInfo sipc : infos) {
            // Safe: these files must exist
            deleteNewFiles(sipc.files());
          }
        }
      }

      synchronized (this) {
        success = false;
        try {
          ensureOpen();

          // Now reserve the docs, just before we update SIS:
          reserveDocs(totalMaxDoc);

          seqNo = docWriter.deleteQueue.getNextSequenceNumber();

          success = true;
        } finally {
          if (!success) {
            for(SegmentCommitInfo sipc : infos) {
              // Safe: these files must exist
              deleteNewFiles(sipc.files());
            }
          }
        }
        segmentInfos.addAll(infos);
        checkpoint();
      }

      successTop = true;

    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "addIndexes(Directory...)");
      // dead code but javac disagrees:
      seqNo = -1;
    } finally {
      if (successTop) {
        IOUtils.close(locks);
      } else {
        IOUtils.closeWhileHandlingException(locks);
      }
    }
    maybeMerge();

    return seqNo;
  }
  
  public long addIndexes(CodecReader... readers) throws IOException {
    ensureOpen();

    // long so we can detect int overflow:
    long numDocs = 0;

    Sort indexSort = config.getIndexSort();

    long seqNo;

    try {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "flush at addIndexes(CodecReader...)");
      }
      flush(false, true);

      String mergedName = newSegmentName();
      for (CodecReader leaf : readers) {
        numDocs += leaf.numDocs();
        Sort leafIndexSort = leaf.getIndexSort();
        if (indexSort != null && leafIndexSort != null && indexSort.equals(leafIndexSort) == false) {
          throw new IllegalArgumentException("cannot change index sort from " + leafIndexSort + " to " + indexSort);
        }
      }
      
      // Best-effort up front check:
      testReserveDocs(numDocs);

      final IOContext context = new IOContext(new MergeInfo(Math.toIntExact(numDocs), -1, false, UNBOUNDED_MAX_MERGE_SEGMENTS));

      // TODO: somehow we should fix this merge so it's
      // abortable so that IW.close(false) is able to stop it
      TrackingDirectoryWrapper trackingDir = new TrackingDirectoryWrapper(directory);

      SegmentInfo info = new SegmentInfo(directoryOrig, Version.LATEST, mergedName, -1,
                                         false, codec, Collections.emptyMap(), StringHelper.randomId(), new HashMap<>(), config.getIndexSort());

      SegmentMerger merger = new SegmentMerger(Arrays.asList(readers), info, infoStream, trackingDir,
                                               globalFieldNumberMap, 
                                               context);

      if (!merger.shouldMerge()) {
        return docWriter.deleteQueue.getNextSequenceNumber();
      }

      merger.merge();                // merge 'em

      SegmentCommitInfo infoPerCommit = new SegmentCommitInfo(info, 0, -1L, -1L, -1L);

      info.setFiles(new HashSet<>(trackingDir.getCreatedFiles()));
      trackingDir.clearCreatedFiles();
                                         
      setDiagnostics(info, SOURCE_ADDINDEXES_READERS);

      final MergePolicy mergePolicy = config.getMergePolicy();
      boolean useCompoundFile;
      synchronized(this) { // Guard segmentInfos
        if (stopMerges) {
          // Safe: these files must exist
          deleteNewFiles(infoPerCommit.files());

          return docWriter.deleteQueue.getNextSequenceNumber();
        }
        ensureOpen();
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, infoPerCommit, this);
      }

      // Now create the compound file if needed
      if (useCompoundFile) {
        Collection<String> filesToDelete = infoPerCommit.files();
        TrackingDirectoryWrapper trackingCFSDir = new TrackingDirectoryWrapper(directory);
        // TODO: unlike merge, on exception we arent sniping any trash cfs files here?
        // createCompoundFile tries to cleanup, but it might not always be able to...
        try {
          createCompoundFile(infoStream, trackingCFSDir, info, context);
        } finally {
          // delete new non cfs files directly: they were never
          // registered with IFD
          deleteNewFiles(filesToDelete);
        }
        info.setUseCompoundFile(true);
      }

      // Have codec write SegmentInfo.  Must do this after
      // creating CFS so that 1) .si isn't slurped into CFS,
      // and 2) .si reflects useCompoundFile=true change
      // above:
      codec.segmentInfoFormat().write(trackingDir, info, context);

      info.addFiles(trackingDir.getCreatedFiles());

      // Register the new segment
      synchronized(this) {
        if (stopMerges) {
          // Safe: these files must exist
          deleteNewFiles(infoPerCommit.files());

          return docWriter.deleteQueue.getNextSequenceNumber();
        }
        ensureOpen();

        // Now reserve the docs, just before we update SIS:
        reserveDocs(numDocs);
      
        segmentInfos.add(infoPerCommit);
        seqNo = docWriter.deleteQueue.getNextSequenceNumber();
        checkpoint();
      }
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "addIndexes(CodecReader...)");
      // dead code but javac disagrees:
      seqNo = -1;
    }
    maybeMerge();

    return seqNo;
  }

  private SegmentCommitInfo copySegmentAsIs(SegmentCommitInfo info, String segName, IOContext context) throws IOException {
    
    //System.out.println("copy seg=" + info.info.name + " version=" + info.info.getVersion());
    // Same SI as before but we change directory and name
    SegmentInfo newInfo = new SegmentInfo(directoryOrig, info.info.getVersion(), segName, info.info.maxDoc(),
                                          info.info.getUseCompoundFile(), info.info.getCodec(), 
                                          info.info.getDiagnostics(), info.info.getId(), info.info.getAttributes(), info.info.getIndexSort());
    SegmentCommitInfo newInfoPerCommit = new SegmentCommitInfo(newInfo, info.getDelCount(), info.getDelGen(), 
                                                               info.getFieldInfosGen(), info.getDocValuesGen());
    
    newInfo.setFiles(info.files());

    boolean success = false;

    Set<String> copiedFiles = new HashSet<>();
    try {
      // Copy the segment's files
      for (String file: info.files()) {
        final String newFileName = newInfo.namedForThisSegment(file);
        directory.copyFrom(info.info.dir, file, newFileName, context);
        copiedFiles.add(newFileName);
      }
      success = true;
    } finally {
      if (!success) {
        // Safe: these files must exist
        deleteNewFiles(copiedFiles);
      }
    }

    assert copiedFiles.equals(newInfoPerCommit.files());
    
    return newInfoPerCommit;
  }
  
  protected void doAfterFlush() throws IOException {}

  protected void doBeforeFlush() throws IOException {}


  @Override
  public final long prepareCommit() throws IOException {
    ensureOpen();
    boolean[] doMaybeMerge = new boolean[1];
    pendingSeqNo = prepareCommitInternal(doMaybeMerge);
    // we must do this outside of the commitLock else we can deadlock:
    if (doMaybeMerge[0]) {
      maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);      
    }
    return pendingSeqNo;
  }

  private long prepareCommitInternal(boolean[] doMaybeMerge) throws IOException {
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
      testPoint("startDoFlush");
      SegmentInfos toCommit = null;
      boolean anySegmentsFlushed = false;
      long seqNo;

      // This is copied from doFlush, except it's modified to
      // clone & incRef the flushed SegmentInfos inside the
      // sync block:

      try {

        synchronized (fullFlushLock) {
          boolean flushSuccess = false;
          boolean success = false;
          try {
            seqNo = docWriter.flushAllThreads();
            if (seqNo < 0) {
              anySegmentsFlushed = true;
              seqNo = -seqNo;
            }
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

              if (changeCount.get() != lastCommitChangeCount) {
                // There are changes to commit, so we will write a new segments_N in startCommit.
                // The act of committing is itself an NRT-visible change (an NRT reader that was
                // just opened before this should see it on reopen) so we increment changeCount
                // and segments version so a future NRT reopen will see the change:
                changeCount.incrementAndGet();
                segmentInfos.changed();
              }

              if (commitUserData != null) {
                Map<String,String> userData = new HashMap<>();
                for(Map.Entry<String,String> ent : commitUserData) {
                  userData.put(ent.getKey(), ent.getValue());
                }
                segmentInfos.setUserData(userData, false);
              }

              // Must clone the segmentInfos while we still
              // hold fullFlushLock and while sync'd so that
              // no partial changes (eg a delete w/o
              // corresponding add from an updateDocument) can
              // sneak into the commit point:
              toCommit = segmentInfos.clone();

              pendingCommitChangeCount = changeCount.get();

              // This protects the segmentInfos we are now going
              // to commit.  This is important in case, eg, while
              // we are trying to sync all referenced files, a
              // merge completes which would otherwise have
              // removed the files we are now syncing.    
              filesToCommit = toCommit.files(false); 
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
      } catch (AbortingException | VirtualMachineError tragedy) {
        tragicEvent(tragedy, "prepareCommit");

        // dead code but javac disagrees:
        seqNo = -1;
      }
     
      boolean success = false;
      try {
        if (anySegmentsFlushed) {
          doMaybeMerge[0] = true;
        }
        startCommit(toCommit);
        success = true;
        if (pendingCommit == null) {
          return -1;
        } else {
          return seqNo;
        }
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
  
  public final synchronized void setLiveCommitData(Iterable<Map.Entry<String,String>> commitUserData) {
    setLiveCommitData(commitUserData, true);
  }


  @Deprecated
  public final synchronized void setCommitData(Map<String,String> commitUserData) {
    setLiveCommitData(new HashMap<>(commitUserData).entrySet());
  }


  public final synchronized void setLiveCommitData(Iterable<Map.Entry<String,String>> commitUserData, boolean doIncrementVersion) {
    this.commitUserData = commitUserData;
    if (doIncrementVersion) {
      segmentInfos.changed();
    }
    changeCount.incrementAndGet();
  }
  
  public final synchronized Iterable<Map.Entry<String,String>> getLiveCommitData() {
    return commitUserData;
  }
  
  @Deprecated
  public final synchronized Map<String,String> getCommitData() {
    Map<String,String> data = new HashMap<>();
    for(Map.Entry<String,String> ent : commitUserData) {
      data.put(ent.getKey(), ent.getValue());
    }
    return data;
  }

  // Used only by commit and prepareCommit, below; lock
  // order is commitLock -> IW
  private final Object commitLock = new Object();

  @Override
  public final long commit() throws IOException {
    ensureOpen();
    return commitInternal(config.getMergePolicy());
  }


  public final boolean hasUncommittedChanges() {
    return changeCount.get() != lastCommitChangeCount || docWriter.anyChanges() || bufferedUpdatesStream.any();
  }

  private final long commitInternal(MergePolicy mergePolicy) throws IOException {

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commit: start");
    }

    boolean[] doMaybeMerge = new boolean[1];

    long seqNo;

    synchronized(commitLock) {
      ensureOpen(false);

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "commit: enter lock");
      }

      if (pendingCommit == null) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "commit: now prepare");
        }
        seqNo = prepareCommitInternal(doMaybeMerge);
      } else {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "commit: already prepared");
        }
        seqNo = pendingSeqNo;
      }

      finishCommit();
    }

    // we must do this outside of the commitLock else we can deadlock:
    if (doMaybeMerge[0]) {
      maybeMerge(mergePolicy, MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);      
    }
    
    return seqNo;
  }

  private final void finishCommit() throws IOException {

    boolean commitCompleted = false;
    boolean finished = false;
    String committedSegmentsFileName = null;

    try {
      synchronized(this) {
        ensureOpen(false);

        if (tragedy != null) {
          throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete commit", tragedy);
        }

        if (pendingCommit != null) {
          try {

            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "commit: pendingCommit != null");
            }

            committedSegmentsFileName = pendingCommit.finishCommit(directory);

            // we committed, if anything goes wrong after this, we are screwed and it's a tragedy:
            commitCompleted = true;

            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "commit: done writing segments file \"" + committedSegmentsFileName + "\"");
            }

            // NOTE: don't use this.checkpoint() here, because
            // we do not want to increment changeCount:
            deleter.checkpoint(pendingCommit, true);

            // Carry over generation to our master SegmentInfos:
            segmentInfos.updateGeneration(pendingCommit);

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
        throw IOUtils.rethrowAlways(t);
      }
    }

    if (infoStream.isEnabled("IW")) {
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

  public final void flush() throws IOException {
    flush(true, true);
  }

  final void flush(boolean triggerMerge, boolean applyAllDeletes) throws IOException {

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
    testPoint("startDoFlush");
    boolean success = false;
    try {

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "  start flush: applyAllDeletes=" + applyAllDeletes);
        infoStream.message("IW", "  index before flush " + segString());
      }
      boolean anyChanges = false;
      
      synchronized (fullFlushLock) {
        boolean flushSuccess = false;
        try {
          long seqNo = docWriter.flushAllThreads();
          if (seqNo < 0) {
            seqNo = -seqNo;
            anyChanges = true;
          } else {
            anyChanges = false;
          }
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
        anyChanges |= maybeApplyDeletes(applyAllDeletes);
        doAfterFlush();
        success = true;
        return anyChanges;
      }
    } catch (AbortingException | VirtualMachineError tragedy) {
      tragicEvent(tragedy, "doFlush");
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
  
  final synchronized boolean maybeApplyDeletes(boolean applyAllDeletes) throws IOException {
    if (applyAllDeletes) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "apply all deletes during flush");
      }
      return applyAllDeletesAndUpdates();
    } else if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "don't apply deletes now delTermCount=" + bufferedUpdatesStream.numTerms() + " bytesUsed=" + bufferedUpdatesStream.ramBytesUsed());
    }

    return false;
  }
  
  final synchronized boolean applyAllDeletesAndUpdates() throws IOException {
    flushDeletesCount.incrementAndGet();
    final BufferedUpdatesStream.ApplyDeletesResult result;
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "now apply all deletes for all segments maxDoc=" + (docWriter.getNumDocs() + segmentInfos.totalMaxDoc()));
    }
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
          pendingNumDocs.addAndGet(-info.info.maxDoc());
          readerPool.drop(info);
        }
      }
      checkpoint();
    }
    bufferedUpdatesStream.prune(segmentInfos);
    return result.anyDeletes;
  }

  // for testing only
  DocumentsWriter getDocsWriter() {
    return docWriter;
  }

  public final synchronized int numRamDocs() {
    ensureOpen();
    return docWriter.getNumDocs();
  }

  private synchronized void ensureValidMerge(MergePolicy.OneMerge merge) {
    for(SegmentCommitInfo info : merge.segments) {
      if (!segmentInfos.contains(info)) {
        throw new MergePolicy.MergeException("MergePolicy selected a segment (" + info.info.name + ") that is not in the current index " + segString(), directoryOrig);
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
    boolean initializedWritableLiveDocs = false;
    
    MergedDeletesAndUpdates() {}
    
    final void init(ReaderPool readerPool, MergePolicy.OneMerge merge, boolean initWritableLiveDocs) throws IOException {
      if (mergedDeletesAndUpdates == null) {
        mergedDeletesAndUpdates = readerPool.get(merge.info, true);
      }
      if (initWritableLiveDocs && !initializedWritableLiveDocs) {
        mergedDeletesAndUpdates.initWritableLiveDocs();
        this.initializedWritableLiveDocs = true;
      }
    }
    
  }
  
  private void maybeApplyMergedDVUpdates(MergePolicy.OneMerge merge, MergeState mergeState,
      MergedDeletesAndUpdates holder, String[] mergingFields, DocValuesFieldUpdates[] dvFieldUpdates,
      DocValuesFieldUpdates.Iterator[] updatesIters, int segment, int curDoc) throws IOException {
    int newDoc = -1;
    for (int idx = 0; idx < mergingFields.length; idx++) {
      DocValuesFieldUpdates.Iterator updatesIter = updatesIters[idx];
      if (updatesIter.doc() == curDoc) { // document has an update
        if (holder.mergedDeletesAndUpdates == null) {
          holder.init(readerPool, merge, false);
        }
        if (newDoc == -1) { // map once per all field updates, but only if there are any updates
          newDoc = mergeState.docMaps[segment].get(curDoc);
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

    testPoint("startCommitMergeDeletes");

    final List<SegmentCommitInfo> sourceSegments = merge.segments;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commitMergeDeletes " + segString(merge.segments));
    }

    // Carefully merge deletes that occurred after we
    // started merging:
    long minGen = Long.MAX_VALUE;

    // Lazy init (only when we find a delete to carry over):
    final MergedDeletesAndUpdates holder = new MergedDeletesAndUpdates();
    final DocValuesFieldUpdates.Container mergedDVUpdates = new DocValuesFieldUpdates.Container();

    assert sourceSegments.size() == mergeState.docMaps.length;
    for (int i = 0; i < sourceSegments.size(); i++) {
      SegmentCommitInfo info = sourceSegments.get(i);
      minGen = Math.min(info.getBufferedDeletesGen(), minGen);
      final int maxDoc = info.info.maxDoc();
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
            dvFieldUpdates[idx] = mergedDVUpdates.newUpdates(field, updates.type, mergeState.segmentInfo.maxDoc());
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
        assert prevLiveDocs.length() == maxDoc;
        assert currentLiveDocs.length() == maxDoc;

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
          for (int j = 0; j < maxDoc; j++) {
            if (prevLiveDocs.get(j) == false) {
              // if the document was deleted before, it better still be deleted!
              assert currentLiveDocs.get(j) == false;
            } else if (currentLiveDocs.get(j) == false) {
              // the document was deleted while we were merging:
              if (holder.mergedDeletesAndUpdates == null || holder.initializedWritableLiveDocs == false) {
                holder.init(readerPool, merge, true);
              }
              holder.mergedDeletesAndUpdates.delete(mergeState.docMaps[i].get(mergeState.leafDocMaps[i].get(j)));
              if (mergingFields != null) { // advance all iters beyond the deleted document
                skipDeletedDoc(updatesIters, j);
              }
            } else if (mergingFields != null) {
              maybeApplyMergedDVUpdates(merge, mergeState, holder, mergingFields, dvFieldUpdates, updatesIters, i, j);
            }
          }
        } else if (mergingFields != null) {
          // need to check each non-deleted document if it has any updates
          for (int j = 0; j < maxDoc; j++) {
            if (prevLiveDocs.get(j)) {
              // document isn't deleted, check if any of the fields have an update to it
              maybeApplyMergedDVUpdates(merge, mergeState, holder, mergingFields, dvFieldUpdates, updatesIters, i, j);
            } else {
              // advance all iters beyond the deleted document
              skipDeletedDoc(updatesIters, j);
            }
          }
        }
      } else if (currentLiveDocs != null) {
        assert currentLiveDocs.length() == maxDoc;
        // This segment had no deletes before but now it
        // does:
        for (int j = 0; j < maxDoc; j++) {
          if (currentLiveDocs.get(j) == false) {
            if (holder.mergedDeletesAndUpdates == null || !holder.initializedWritableLiveDocs) {
              holder.init(readerPool, merge, true);
            }
            holder.mergedDeletesAndUpdates.delete(mergeState.docMaps[i].get(mergeState.leafDocMaps[i].get(j)));
            if (mergingFields != null) { // advance all iters beyond the deleted document
              skipDeletedDoc(updatesIters, j);
            }
          } else if (mergingFields != null) {
            maybeApplyMergedDVUpdates(merge, mergeState, holder, mergingFields, dvFieldUpdates, updatesIters, i, j);
          }
        }
      } else if (mergingFields != null) {
        // no deletions before or after, but there were updates
        for (int j = 0; j < maxDoc; j++) {
          maybeApplyMergedDVUpdates(merge, mergeState, holder, mergingFields, dvFieldUpdates, updatesIters, i, j);
        }
      }
    }

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

    testPoint("startCommitMerge");

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

      // Safe: these files must exist:
      deleteNewFiles(merge.info.files());
      return false;
    }

    final ReadersAndUpdates mergedUpdates = merge.info.info.maxDoc() == 0 ? null : commitMergedDeletesAndUpdates(merge, mergeState);
//    System.out.println("[" + Thread.currentThread().getName() + "] IW.commitMerge: mergedDeletes=" + mergedDeletes);

    // If the doc store we are using has been closed and
    // is in now compound format (but wasn't when we
    // started), then we will switch to the compound
    // format as well:

    assert !segmentInfos.contains(merge.info);

    final boolean allDeleted = merge.segments.size() == 0 ||
      merge.info.info.maxDoc() == 0 ||
      (mergedUpdates != null &&
       mergedUpdates.getPendingDeleteCount() == merge.info.info.maxDoc());

    if (infoStream.isEnabled("IW")) {
      if (allDeleted) {
        infoStream.message("IW", "merged segment " + merge.info + " is 100% deleted" +  (keepFullyDeletedSegments ? "" : "; skipping insert"));
      }
    }

    final boolean dropSegment = allDeleted && !keepFullyDeletedSegments;

    // If we merged no segments then we better be dropping
    // the new segment:
    assert merge.segments.size() > 0 || dropSegment;

    assert merge.info.info.maxDoc() != 0 || keepFullyDeletedSegments || dropSegment;

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
    int delDocCount = merge.totalMaxDoc - merge.info.info.maxDoc();
    assert delDocCount >= 0;
    pendingNumDocs.addAndGet(-delDocCount);

    if (dropSegment) {
      assert !segmentInfos.contains(merge.info);
      readerPool.drop(merge.info);
      // Safe: these files must exist
      deleteNewFiles(merge.info.files());
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

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "after commitMerge: " + segString());
    }

    if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS && !dropSegment) {
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
      // deleteAll or rollback is called), unless the
      // merge involves segments from external directories,
      // in which case we must throw it so, for example, the
      // rollbackTransaction code in addIndexes* is
      // executed.
      if (merge.isExternal) {
        throw (MergePolicy.MergeAbortedException) t;
      }
    } else {
      assert t != null;
      throw IOUtils.rethrowAlways(t);
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

          if (success == false) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception during merge");
            }
          } else if (!merge.isAborted() && (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS || (!closed && !closing))) {
            // This merge (and, generally, any change to the
            // segments) may now enable new merges, so we call
            // merge policy & update pending merges.
            updatePendingMerges(mergePolicy, MergeTrigger.MERGE_FINISHED, merge.maxNumSegments);
          }
        }
      }
    } catch (Throwable t) {
      // Important that tragicEvent is called after mergeFinish, else we hang
      // waiting for our merge thread to be removed from runningMerges:
      tragicEvent(t, "merge");
    }

    if (merge.info != null && merge.isAborted() == false) {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "merge time " + (System.currentTimeMillis()-t0) + " msec for " + merge.info.info.maxDoc() + " docs");
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
      merge.setAborted();
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
      if (info.info.dir != directoryOrig) {
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
      if (info.info.maxDoc() > 0) {
        final int delCount = numDeletedDocs(info);
        assert delCount <= info.info.maxDoc();
        final double delRatio = ((double) delCount)/info.info.maxDoc();
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

    testPoint("startMergeInit");

    assert merge.registerDone;
    assert merge.maxNumSegments == UNBOUNDED_MAX_MERGE_SEGMENTS || merge.maxNumSegments > 0;

    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot merge", tragedy);
    }

    if (merge.info != null) {
      // mergeInit already done
      return;
    }

    merge.mergeInit();

    if (merge.isAborted()) {
      return;
    }

    // TODO: in the non-pool'd case this is somewhat
    // wasteful, because we open these readers, close them,
    // and then open them again for merging.  Maybe  we
    // could pre-pool them somehow in that case...

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "now apply deletes for " + merge.segments.size() + " merging segments");
    }

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
        pendingNumDocs.addAndGet(-info.info.maxDoc());
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
    SegmentInfo si = new SegmentInfo(directoryOrig, Version.LATEST, mergeSegmentName, -1, false, codec, Collections.emptyMap(), StringHelper.randomId(), new HashMap<>(), config.getIndexSort());
    Map<String,String> details = new HashMap<>();
    details.put("mergeMaxNumSegments", "" + merge.maxNumSegments);
    details.put("mergeFactor", Integer.toString(merge.segments.size()));
    setDiagnostics(si, SOURCE_MERGE, details);
    merge.setMergeInfo(new SegmentCommitInfo(si, 0, -1L, -1L, -1L));

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
    // On IBM J9 JVM this is better than java.version which is just 1.7.0 (no update level):
    diagnostics.put("java.runtime.version", System.getProperty("java.runtime.version", "undefined"));
    // Hotspot version, e.g. 2.8 for J9:
    diagnostics.put("java.vm.version", System.getProperty("java.vm.version", "undefined"));
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

    try {
      merge.mergeFinished();
    } catch (Throwable t) {
      if (th == null) {
        th = t;
      }
    }
    
    // If any error occurred, throw it.
    if (!suppressExceptions && th != null) {
      throw IOUtils.rethrowAlways(th);
    }
  }


  private int mergeMiddle(MergePolicy.OneMerge merge, MergePolicy mergePolicy) throws IOException {
    merge.checkAborted();

    Directory mergeDirectory = config.getMergeScheduler().wrapForMerge(merge, directory);
    List<SegmentCommitInfo> sourceSegments = merge.segments;
    
    IOContext context = new IOContext(merge.getStoreMergeInfo());

    final TrackingDirectoryWrapper dirWrapper = new TrackingDirectoryWrapper(mergeDirectory);

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "merging " + segString(merge.segments));
    }

    merge.readers = new ArrayList<>(sourceSegments.size());

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
            newReader = new SegmentReader(info, reader, liveDocs, info.info.maxDoc() - delCount);
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
        assert delCount <= info.info.maxDoc(): "delCount=" + delCount + " info.maxDoc=" + info.info.maxDoc() + " rld.pendingDeleteCount=" + rld.getPendingDeleteCount() + " info.getDelCount()=" + info.getDelCount();
        segUpto++;
      }

//      System.out.println("[" + Thread.currentThread().getName() + "] IW.mergeMiddle: merging " + merge.getMergeReaders());

      // Let the merge wrap readers
      List<CodecReader> mergeReaders = new ArrayList<>();
      for (SegmentReader reader : merge.readers) {
        mergeReaders.add(merge.wrapForMerge(reader));
      }
      final SegmentMerger merger = new SegmentMerger(mergeReaders,
                                                     merge.info.info, infoStream, dirWrapper,
                                                     globalFieldNumberMap, 
                                                     context);

      merge.checkAborted();

      merge.mergeStartNS = System.nanoTime();

      // This is where all the work happens:
      if (merger.shouldMerge()) {
        merger.merge();
      }

      MergeState mergeState = merger.mergeState;
      assert mergeState.segmentInfo == merge.info.info;
      merge.info.info.setFiles(new HashSet<>(dirWrapper.getCreatedFiles()));

      if (infoStream.isEnabled("IW")) {
        if (merger.shouldMerge()) {
          String pauseInfo = merge.getMergeProgress().getPauseTimes().entrySet()
            .stream()
            .filter((e) -> e.getValue() > 0)
            .map((e) -> String.format(Locale.ROOT, "%.1f sec %s", 
                e.getValue() / 1000000000., 
                e.getKey().name().toLowerCase(Locale.ROOT)))
            .collect(Collectors.joining(", "));
          if (!pauseInfo.isEmpty()) {
            pauseInfo = " (" + pauseInfo + ")";
          }

          long t1 = System.nanoTime();
          double sec = (t1-merge.mergeStartNS)/1000000000.;
          double segmentMB = (merge.info.sizeInBytes()/1024./1024.);
          infoStream.message("IW", "merge codec=" + codec + " maxDoc=" + merge.info.info.maxDoc() + "; merged segment has " +
                             (mergeState.mergeFieldInfos.hasVectors() ? "vectors" : "no vectors") + "; " +
                             (mergeState.mergeFieldInfos.hasNorms() ? "norms" : "no norms") + "; " + 
                             (mergeState.mergeFieldInfos.hasDocValues() ? "docValues" : "no docValues") + "; " + 
                             (mergeState.mergeFieldInfos.hasProx() ? "prox" : "no prox") + "; " + 
                             (mergeState.mergeFieldInfos.hasProx() ? "freqs" : "no freqs") + "; " +
                             (mergeState.mergeFieldInfos.hasPointValues() ? "points" : "no points") + "; " +
                             String.format(Locale.ROOT,
                                           "%.1f sec%s to merge segment [%.2f MB, %.2f MB/sec]",
                                           sec,
                                           pauseInfo,
                                           segmentMB,
                                           segmentMB / sec));
        } else {
          infoStream.message("IW", "skip merging fully deleted segments");
        }
      }

      if (merger.shouldMerge() == false) {
        // Merge would produce a 0-doc segment, so we do nothing except commit the merge to remove all the 0-doc segments that we "merged":
        assert merge.info.info.maxDoc() == 0;
        commitMerge(merge, mergeState);
        return 0;
      }

      assert merge.info.info.maxDoc() > 0;

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
        TrackingDirectoryWrapper trackingCFSDir = new TrackingDirectoryWrapper(mergeDirectory);
        try {
          createCompoundFile(infoStream, trackingCFSDir, merge.info.info, context);
          success = true;
        } catch (Throwable t) {
          synchronized(this) {
            if (merge.isAborted()) {
              // This can happen if rollback is called while we were building
              // our CFS -- fall through to logic below to remove the non-CFS
              // merged files:
              if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "hit merge abort exception creating compound file during merge");
              }
              return 0;
            } else {
              handleMergeException(t, merge);
            }
          }
        } finally {
          if (success == false) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception creating compound file during merge");
            }
            // Safe: these files must exist
            deleteNewFiles(merge.info.files());
          }
        }

        // So that, if we hit exc in deleteNewFiles (next)
        // or in commitMerge (later), we close the
        // per-segment readers in the finally clause below:
        success = false;

        synchronized(this) {

          // delete new non cfs files directly: they were never
          // registered with IFD
          deleteNewFiles(filesToRemove);

          if (merge.isAborted()) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "abort merge after building CFS");
            }
            // Safe: these files must exist
            deleteNewFiles(merge.info.files());
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
        codec.segmentInfoFormat().write(directory, merge.info.info, context);
        success2 = true;
      } finally {
        if (!success2) {
          // Safe: these files must exist
          deleteNewFiles(merge.info.files());
        }
      }

      // TODO: ideally we would freeze merge.info here!!
      // because any changes after writing the .si will be
      // lost... 

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", String.format(Locale.ROOT, "merged segment size=%.3f MB vs estimate=%.3f MB", merge.info.sizeInBytes()/1024./1024., merge.estimatedMergeBytes/1024/1024.));
      }

      final IndexReaderWarmer mergedSegmentWarmer = config.getMergedSegmentWarmer();
      if (poolReaders && mergedSegmentWarmer != null) {
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

      if (!commitMerge(merge, mergeState)) {
        // commitMerge will return false if this merge was
        // aborted
        return 0;
      }

      success = true;

    } finally {
      // Readers are already closed in commitMerge if we didn't hit
      // an exc:
      if (success == false) {
        closeMergeReaders(merge, true);
      }
    }

    return merge.info.info.maxDoc();
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


  synchronized String segString() {
    return segString(segmentInfos);
  }


  synchronized String segString(Iterable<SegmentCommitInfo> infos) {
    final StringBuilder buffer = new StringBuilder();
    for(final SegmentCommitInfo info : infos) {
      if (buffer.length() > 0) {
        buffer.append(' ');
      }
      buffer.append(segString(info));
    }
    return buffer.toString();
  }


  synchronized String segString(SegmentCommitInfo info) {
    return info.toString(numDeletedDocs(info) - info.getDelCount());
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
    
    Collection<String> files = toSync.files(false);
    for(final String fileName: files) {
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

    testPoint("startStartCommit");
    assert pendingCommit == null;

    if (tragedy != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot commit", tragedy);
    }

    try {

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "startCommit(): start");
      }

      synchronized(this) {

        if (lastCommitChangeCount > changeCount.get()) {
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

      testPoint("midStartCommit");

      boolean pendingCommitSet = false;

      try {

        testPoint("midStartCommit2");

        synchronized(this) {

          assert pendingCommit == null;

          assert segmentInfos.getGeneration() == toSync.getGeneration();

          // Exception here means nothing is prepared
          // (this method unwinds everything it did on
          // an exception)
          toSync.prepareCommit(directory);
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "startCommit: wrote pending segments file \"" + IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS, "", toSync.getGeneration()) + "\"");
          }

          //System.out.println("DONE prepareCommit");

          pendingCommitSet = true;
          pendingCommit = toSync;
        }

        // This call can take a long time -- 10s of seconds
        // or more.  We do it without syncing on this:
        boolean success = false;
        final Collection<String> filesToSync;
        try {
          filesToSync = toSync.files(false);
          directory.sync(filesToSync);
          success = true;
        } finally {
          if (!success) {
            pendingCommitSet = false;
            pendingCommit = null;
            toSync.rollbackCommit(directory);
          }
        }

        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "done all syncs: " + filesToSync);
        }

        testPoint("midStartCommitSuccess");

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
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "startCommit");
    }
    testPoint("finishStartCommit");
  }

  @Deprecated
  public static boolean isLocked(Directory directory) throws IOException {
    try {
      directory.obtainLock(WRITE_LOCK_NAME).close();
      return false;
    } catch (LockObtainFailedException failed) {
      return true;
    }
  }


  public static abstract class IndexReaderWarmer {

    protected IndexReaderWarmer() {
    }


    public abstract void warm(LeafReader reader) throws IOException;
  }

  void tragicEvent(Throwable tragedy, String location) throws IOException {

    // unbox our internal AbortingException
    if (tragedy instanceof AbortingException) {
      tragedy = tragedy.getCause();
    }

    // This is not supposed to be tragic: IW is supposed to catch this and
    // ignore, because it means we asked the merge to abort:
    assert tragedy instanceof MergePolicy.MergeAbortedException == false;

    // We cannot hold IW's lock here else it can lead to deadlock:
    assert Thread.holdsLock(this) == false;

    // How can it be a tragedy when nothing happened?
    assert tragedy != null;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "hit tragic " + tragedy.getClass().getSimpleName() + " inside " + location);
    }

    synchronized (this) {
      // It's possible you could have a really bad day
      if (this.tragedy != null) {
        // Another thread is already dealing / has dealt with the tragedy:
        throw IOUtils.rethrowAlways(tragedy);
      }

      this.tragedy = tragedy;
    }

    // if we are already closed (e.g. called by rollback), this will be a no-op.
    if (shouldClose(false)) {
      rollbackInternal();
    }

    throw IOUtils.rethrowAlways(tragedy);
  }


  public Throwable getTragicException() {
    return tragedy;
  }

  public boolean isOpen() {
    return closing == false && closed == false;
  }

  // Used for testing.  Current points:
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
  private final void testPoint(String message) {
    if (enableTestPoints) {
      assert infoStream.isEnabled("TP"); // don't enable unless you need them.
      infoStream.message("TP", message);
    }
  }

  synchronized boolean nrtIsCurrent(SegmentInfos infos) {
    //System.out.println("IW.nrtIsCurrent " + (infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedDeletesStream.any()));
    ensureOpen();
    boolean isCurrent = infos.getVersion() == segmentInfos.getVersion() && !docWriter.anyChanges() && !bufferedUpdatesStream.any();
    if (infoStream.isEnabled("IW")) {
      if (isCurrent == false) {
        infoStream.message("IW", "nrtIsCurrent: infoVersion matches: " + (infos.getVersion() == segmentInfos.getVersion()) + "; DW changes: " + docWriter.anyChanges() + "; BD changes: "+ bufferedUpdatesStream.any());
      }
    }
    return isCurrent;
  }

  synchronized boolean isClosed() {
    return closed;
  }


  public synchronized void deleteUnusedFiles() throws IOException {
    // TODO: should we remove this method now that it's the Directory's job to retry deletions?  Except, for the super expert IDP use case
    // it's still needed?
    ensureOpen(false);
    deleter.revisitPolicy();
  }

  final void createCompoundFile(InfoStream infoStream, TrackingDirectoryWrapper directory, final SegmentInfo info, IOContext context) throws IOException {

    // maybe this check is not needed, but why take the risk?
    if (!directory.getCreatedFiles().isEmpty()) {
      throw new IllegalStateException("pass a clean trackingdir for CFS creation");
    }
    
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "create compound file");
    }
    // Now merge all added files    
    boolean success = false;
    try {
      info.getCodec().compoundFormat().write(directory, info, context);
      success = true;
    } finally {
      if (!success) {
        // Safe: these files must exist
        deleteNewFiles(directory.getCreatedFiles());
      }
    }

    // Replace all previous files with the CFS/CFE files:
    info.setFiles(new HashSet<>(directory.getCreatedFiles()));
  }
  
  synchronized final void deleteNewFiles(Collection<String> files) throws IOException {
    deleter.deleteNewFiles(files);
  }
  
  synchronized final void flushFailed(SegmentInfo info) throws IOException {
    // TODO: this really should be a tragic
    Collection<String> files;
    try {
      files = info.files();
    } catch (IllegalStateException ise) {
      // OK
      files = null;
    }
    if (files != null) {
      deleter.deleteNewFiles(files);
    }
  }
  
  final int purge(boolean forced) throws IOException {
    return docWriter.purgeBuffer(this, forced);
  }

  final void applyDeletesAndPurge(boolean forcePurge) throws IOException {
    try {
      purge(forcePurge);
    } finally {
      if (applyAllDeletesAndUpdates()) {
        maybeMerge(config.getMergePolicy(), MergeTrigger.SEGMENT_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
      }
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
  

  public synchronized void incRefDeleter(SegmentInfos segmentInfos) throws IOException {
    ensureOpen();
    deleter.incRef(segmentInfos, false);
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "incRefDeleter for NRT reader version=" + segmentInfos.getVersion() + " segments=" + segString(segmentInfos));
    }
  }


  public synchronized void decRefDeleter(SegmentInfos segmentInfos) throws IOException {
    ensureOpen();
    deleter.decRef(segmentInfos);
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "decRefDeleter for NRT reader version=" + segmentInfos.getVersion() + " segments=" + segString(segmentInfos));
    }
  }
  
  private boolean processEvents(boolean triggerMerge, boolean forcePurge) throws IOException {
    return processEvents(eventQueue, triggerMerge, forcePurge);
  }
  
  private boolean processEvents(Queue<Event> queue, boolean triggerMerge, boolean forcePurge) throws IOException {
    boolean processed = false;
    if (tragedy == null) {
      Event event;
      while((event = queue.poll()) != null)  {
        processed = true;
        event.process(this, triggerMerge, forcePurge);
      }
    }
    return processed;
  }
  
  static interface Event {
    
    void process(IndexWriter writer, boolean triggerMerge, boolean clearBuffers) throws IOException;
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


  public long getMaxCompletedSequenceNumber() {
    ensureOpen();
    return docWriter.getMaxCompletedSequenceNumber();
  }
}
