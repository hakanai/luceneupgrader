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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesUpdate.BinaryDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesUpdate.NumericDocValuesUpdate;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfos.FieldNumbers;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocValuesFieldExistsQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.MatchAllDocsQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.FlushInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Lock;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.LockObtainFailedException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.LockValidatingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.MMapDirectory;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.MergeInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.TrackingDirectoryWrapper;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ThreadInterruptedException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.UnicodeUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Version;

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;


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
public class IndexWriter implements Closeable, TwoPhaseCommit, Accountable,
    MergePolicy.MergeContext {

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
  
  private final boolean enableTestPoints;

  static final int UNBOUNDED_MAX_MERGE_SEGMENTS = -1;
  
  public static final String WRITE_LOCK_NAME = "write.lock";

  public static final String SOURCE = "source";
  public static final String SOURCE_MERGE = "merge";
  public static final String SOURCE_FLUSH = "flush";
  public static final String SOURCE_ADDINDEXES_READERS = "addIndexes(CodecReader...)";

  public final static int MAX_TERM_LENGTH = DocumentsWriterPerThread.MAX_TERM_LENGTH_UTF8;

  public final static int MAX_STORED_STRING_LENGTH = ArrayUtil.MAX_ARRAY_LENGTH / UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR;
    
  // when unrecoverable disaster strikes, we populate this with the reason that we had to close IndexWriter
  final AtomicReference<Throwable> tragedy = new AtomicReference<>(null);

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

  private final SegmentInfos segmentInfos;
  final FieldNumbers globalFieldNumberMap;

  final DocumentsWriter docWriter;
  private final Queue<Event> eventQueue = new ConcurrentLinkedQueue<>();
  final IndexFileDeleter deleter;

  // used by forceMerge to note those needing merging
  private Map<SegmentCommitInfo,Boolean> segmentsToMerge = new HashMap<>();
  private int mergeMaxNumSegments;

  private Lock writeLock;

  private volatile boolean closed;
  private volatile boolean closing;

  final AtomicBoolean maybeMerge = new AtomicBoolean();

  private Iterable<Map.Entry<String,String>> commitUserData;

  // Holds all SegmentInfo instances currently involved in
  // merges
  HashSet<SegmentCommitInfo> mergingSegments = new HashSet<>();

  private final MergeScheduler mergeScheduler;
  private LinkedList<MergePolicy.OneMerge> pendingMerges = new LinkedList<>();
  private Set<MergePolicy.OneMerge> runningMerges = new HashSet<>();
  private List<MergePolicy.OneMerge> mergeExceptions = new ArrayList<>();
  private long mergeGen;
  private boolean stopMerges;
  private boolean didMessageState;

  final AtomicInteger flushCount = new AtomicInteger();

  final AtomicInteger flushDeletesCount = new AtomicInteger();

  private final ReaderPool readerPool;
  final BufferedUpdatesStream bufferedUpdatesStream;

  final AtomicLong mergeFinishedGen = new AtomicLong();

  // The instance that was passed to the constructor. It is saved only in order
  // to allow users to query an IndexWriter settings.
  private final LiveIndexWriterConfig config;

  private long startCommitTime;

  final AtomicLong pendingNumDocs = new AtomicLong();
  final boolean softDeletesEnabled;

  private final DocumentsWriter.FlushNotifications flushNotifications = new DocumentsWriter.FlushNotifications() {
    @Override
    public void deleteUnusedFiles(Collection<String> files) {
      eventQueue.add(w -> w.deleteNewFiles(files));
    }

    @Override
    public void flushFailed(SegmentInfo info) {
      eventQueue.add(w -> w.flushFailed(info));
    }

    @Override
    public void afterSegmentsFlushed() throws IOException {
      try {
        publishFlushedSegments(false);
      } finally {
        if (false) {
          maybeMerge(config.getMergePolicy(), MergeTrigger.SEGMENT_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
        }
      }
    }

    @Override
    public void onTragicEvent(Throwable event, String message) {
      IndexWriter.this.onTragicEvent(event, message);
    }

    @Override
    public void onDeletesApplied() {
      eventQueue.add(w -> {
          try {
            w.publishFlushedSegments(true);
          } finally {
            flushCount.incrementAndGet();
          }
        }
      );
    }

    @Override
    public void onTicketBacklog() {
      eventQueue.add(w -> w.publishFlushedSegments(true));
    }
  };

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
    readerPool.enableReaderPooling();
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
          if (anyChanges == false) {
            // prevent double increment since docWriter#doFlush increments the flushcount
            // if we flushed anything.
            flushCount.incrementAndGet();
          }
          publishFlushedSegments(true);
          processEvents(false);

          if (applyAllDeletes) {
            applyAllDeletesAndUpdates();
          }

          synchronized(this) {

            // NOTE: we cannot carry doc values updates in memory yet, so we always must write them through to disk and re-open each
            // SegmentReader:

            // TODO: we could instead just clone SIS and pull/incref readers in sync'd block, and then do this w/o IW's lock?
            // Must do this sync'd on IW to prevent a merge from completing at the last second and failing to write its DV updates:
           writeReaderPool(writeAllDeletes);

            // Prevent segmentInfos from changing while opening the
            // reader; in theory we could instead do similar retry logic,
            // just like we do when loading segments_N
            
            r = StandardDirectoryReader.open(this, segmentInfos, applyAllDeletes, writeAllDeletes);
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "return reader version=" + r.getVersion() + " reader=" + r);
            }
          }
          success = true;
        } finally {
          // Done: finish the full flush!
          assert holdsFullFlushLock();
          docWriter.finishFullFlush(success);
          if (success) {
            processEvents(false);
            doAfterFlush();
          } else {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception during NRT reader");
            }
          }
        }
      }
      anyChanges |= maybeMerge.getAndSet(false);
      if (anyChanges) {
        maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
      }
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "getReader took " + (System.currentTimeMillis() - tStart) + " msec");
      }
      success2 = true;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "getReader");
      throw tragedy;
    } finally {
      if (!success2) {
        try {
          IOUtils.closeWhileHandlingException(r);
        } finally {
          maybeCloseOnTragicEvent();
        }
      }
    }
    return r;
  }

  @Override
  public final long ramBytesUsed() {
    ensureOpen();
    return docWriter.ramBytesUsed();
  }

  public final long getFlushingBytes() {
    ensureOpen();
    return docWriter.getFlushingBytes();
  }

  final long getReaderPoolRamBytesUsed() {
    return readerPool.ramBytesUsed();
  }

  private final AtomicBoolean writeDocValuesLock = new AtomicBoolean();

  void writeSomeDocValuesUpdates() throws IOException {
    if (writeDocValuesLock.compareAndSet(false, true)) {
      try {
        final double ramBufferSizeMB = config.getRAMBufferSizeMB();
        // If the reader pool is > 50% of our IW buffer, then write the updates:
        if (ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH) {
          long startNS = System.nanoTime();

          long ramBytesUsed = getReaderPoolRamBytesUsed();
          if (ramBytesUsed > 0.5 * ramBufferSizeMB * 1024 * 1024) {
            if (infoStream.isEnabled("BD")) {
              infoStream.message("BD", String.format(Locale.ROOT, "now write some pending DV updates: %.2f MB used vs IWC Buffer %.2f MB",
                  ramBytesUsed/1024./1024., ramBufferSizeMB));
            }

            // Sort by largest ramBytesUsed:
            final List<ReadersAndUpdates> list = readerPool.getReadersByRam();
            int count = 0;
            for (ReadersAndUpdates rld : list) {

              if (ramBytesUsed <= 0.5 * ramBufferSizeMB * 1024 * 1024) {
                break;
              }
              // We need to do before/after because not all RAM in this RAU is used by DV updates, and
              // not all of those bytes can be written here:
              long bytesUsedBefore = rld.ramBytesUsed.get();
              if (bytesUsedBefore == 0) {
                continue; // nothing to do here - lets not acquire the lock
              }
              // Only acquire IW lock on each write, since this is a time consuming operation.  This way
              // other threads get a chance to run in between our writes.
              synchronized (this) {
                // It's possible that the segment of a reader returned by readerPool#getReadersByRam
                // is dropped before being processed here. If it happens, we need to skip that reader.
                // this is also best effort to free ram, there might be some other thread writing this rld concurrently
                // which wins and then if readerPooling is off this rld will be dropped.
                if (readerPool.get(rld.info, false) == null) {
                  continue;
                }
                if (rld.writeFieldUpdates(directory, globalFieldNumberMap, bufferedUpdatesStream.getCompletedDelGen(), infoStream)) {
                  checkpointNoSIS();
                }
              }
              long bytesUsedAfter = rld.ramBytesUsed.get();
              ramBytesUsed -= bytesUsedBefore - bytesUsedAfter;
              count++;
            }

            if (infoStream.isEnabled("BD")) {
              infoStream.message("BD", String.format(Locale.ROOT, "done write some DV updates for %d segments: now %.2f MB used vs IWC Buffer %.2f MB; took %.2f sec",
                  count, getReaderPoolRamBytesUsed()/1024./1024., ramBufferSizeMB, ((System.nanoTime() - startNS)/1000000000.)));
            }
          }
        }
      } finally {
        writeDocValuesLock.set(false);
      }
    }
  }

  @Override
  public int numDeletedDocs(SegmentCommitInfo info) {
    ensureOpen(false);
    validate(info);
    final ReadersAndUpdates rld = getPooledInstance(info, false);
    if (rld != null) {
      return rld.getDelCount(); // get the full count from here since SCI might change concurrently
    } else {
      final int delCount = info.getDelCount(softDeletesEnabled);
      assert delCount <= info.info.maxDoc(): "delCount: " + delCount + " maxDoc: " + info.info.maxDoc();
      return delCount;
    }
  }

  protected final void ensureOpen(boolean failIfClosing) throws AlreadyClosedException {
    if (closed || (failIfClosing && closing)) {
      throw new AlreadyClosedException("this IndexWriter is closed", tragedy.get());
    }
  }

  protected final void ensureOpen() throws AlreadyClosedException {
    ensureOpen(true);
  }

  final Codec codec; // for writing new segments

  public IndexWriter(Directory d, IndexWriterConfig conf) throws IOException {
    enableTestPoints = isEnableTestPoints();
    conf.setIndexWriter(this); // prevent reuse by other instances
    config = conf;
    infoStream = config.getInfoStream();
    softDeletesEnabled = config.getSoftDeletesField() != null;
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
      OpenMode mode = config.getOpenMode();
      final boolean indexExists;
      final boolean create;
      if (mode == OpenMode.CREATE) {
        indexExists = DirectoryReader.indexExists(directory);
        create = true;
      } else if (mode == OpenMode.APPEND) {
        indexExists = true;
        create = false;
      } else {
        // CREATE_OR_APPEND - create only if an index does not exist
        indexExists = DirectoryReader.indexExists(directory);
        create = !indexExists;
      }

      // If index is too old, reading the segments will throw
      // IndexFormatTooOldException.

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
        final SegmentInfos sis = new SegmentInfos(config.getIndexCreatedVersionMajor());
        if (indexExists) {
          final SegmentInfos previous = SegmentInfos.readLatestCommit(directory);
          sis.updateGenerationVersionAndCounter(previous);
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



      commitUserData = new HashMap<>(segmentInfos.getUserData()).entrySet();

      pendingNumDocs.set(segmentInfos.totalMaxDoc());

      // start with previous field numbers, but new FieldInfos
      // NOTE: this is correct even for an NRT reader because we'll pull FieldInfos even for the un-committed segments:
      globalFieldNumberMap = getFieldNumberMap();

      validateIndexSort();

      config.getFlushPolicy().init(config);
      bufferedUpdatesStream = new BufferedUpdatesStream(infoStream);
      docWriter = new DocumentsWriter(flushNotifications, segmentInfos.getIndexCreatedVersionMajor(), pendingNumDocs,
          enableTestPoints, this::newSegmentName,
          config, directoryOrig, directory, globalFieldNumberMap);
      readerPool = new ReaderPool(directory, directoryOrig, segmentInfos, globalFieldNumberMap,
          bufferedUpdatesStream::getCompletedDelGen, infoStream, conf.getSoftDeletesField(), reader);
      if (config.getReaderPooling()) {
        readerPool.enableReaderPooling();
      }
      // Default deleter (for backwards compatibility) is
      // KeepOnlyLastCommitDeleter:

      // Sync'd is silly here, but IFD asserts we sync'd on the IW instance:
      synchronized(this) {
        deleter = new IndexFileDeleter(files, directoryOrig, directory,
                                       config.getIndexDeletionPolicy(),
                                       segmentInfos, infoStream, this,
                                       indexExists, reader != null);

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
        // We always assume we are carrying over incoming changes when opening from reader:
        segmentInfos.changed();
        changed();
      }

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "init: create=" + create + " reader=" + reader);
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
    final FieldNumbers map = new FieldNumbers(config.softDeletesField);

    for(SegmentCommitInfo info : segmentInfos) {
      FieldInfos fis = readFieldInfos(info);
      for(FieldInfo fi : fis) {
        map.addOrGet(fi.name, fi.number, fi.getDocValuesType(), fi.getPointDataDimensionCount(), fi.getPointIndexDimensionCount(), fi.getPointNumBytes(), fi.isSoftDeletesField());
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
      try {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "now flush at close");
        }

        flush(true, true);
        waitForMerges();
        commitInternal(config.getMergePolicy());
        rollbackInternal(); // ie close, since we just committed
      } catch (Throwable t) {
        // Be certain to close the index on any exception
        try {
          rollbackInternal();
        } catch (Throwable t1) {
          t.addSuppressed(t1);
        }
        throw t;
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

  @Override
  public InfoStream getInfoStream() {
    return infoStream;
  }

  public Analyzer getAnalyzer() {
    ensureOpen();
    return analyzer;
  }

  @Deprecated
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

  @Deprecated
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
    if (readerPool.anyDeletions()) {
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
    return updateDocument((DocumentsWriterDeleteQueue.Node<?>) null, doc);
  }

  public long addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    return updateDocuments((DocumentsWriterDeleteQueue.Node<?>) null, docs);
  }

  public long updateDocuments(Term delTerm, Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    return updateDocuments(delTerm == null ? null : DocumentsWriterDeleteQueue.newNode(delTerm), docs);
  }

  private long updateDocuments(final DocumentsWriterDeleteQueue.Node<?> delNode, Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    ensureOpen();
    boolean success = false;
    try {
      long seqNo = docWriter.updateDocuments(docs, analyzer, delNode);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true);
      }
      success = true;
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocuments");
      throw tragedy;
    } finally {
      if (success == false) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "hit exception updating document");
        }
        maybeCloseOnTragicEvent();
      }
    }

  }

  public long softUpdateDocuments(Term term, Iterable<? extends Iterable<? extends IndexableField>> docs, Field... softDeletes) throws IOException {
    if (term == null) {
      throw new IllegalArgumentException("term must not be null");
    }
    if (softDeletes == null || softDeletes.length == 0) {
      throw new IllegalArgumentException("at least one soft delete must be present");
    }
    return updateDocuments(DocumentsWriterDeleteQueue.newNode(buildDocValuesUpdate(term, softDeletes)), docs);
  }

  public synchronized long tryDeleteDocument(IndexReader readerIn, int docID) throws IOException {
    // NOTE: DON'T use docID inside the closure
    return tryModifyDocument(readerIn, docID, (leafDocId, rld) -> {
      if (rld.delete(leafDocId)) {
        if (isFullyDeleted(rld)) {
          dropDeletedSegment(rld.info);
          checkpoint();
        }

        // Must bump changeCount so if no other changes
        // happened, we still commit this change:
        changed();
      }
    });
  }

  public synchronized long tryUpdateDocValue(IndexReader readerIn, int docID, Field... fields) throws IOException {
    // NOTE: DON'T use docID inside the closure
    final DocValuesUpdate[] dvUpdates = buildDocValuesUpdate(null, fields);
    return tryModifyDocument(readerIn, docID, (leafDocId, rld) -> {
      long nextGen = bufferedUpdatesStream.getNextGen();
      try {
        Map<String, DocValuesFieldUpdates> fieldUpdatesMap = new HashMap<>();
        for (DocValuesUpdate update : dvUpdates) {
          DocValuesFieldUpdates docValuesFieldUpdates = fieldUpdatesMap.computeIfAbsent(update.field, k -> {
            switch (update.type) {
              case NUMERIC:
                return new NumericDocValuesFieldUpdates(nextGen, k, rld.info.info.maxDoc());
              case BINARY:
                return new BinaryDocValuesFieldUpdates(nextGen, k, rld.info.info.maxDoc());
              default:
                throw new AssertionError("type: " + update.type + " is not supported");
            }
          });
          if (update.hasValue()) {
            switch (update.type) {
              case NUMERIC:
                docValuesFieldUpdates.add(leafDocId, ((NumericDocValuesUpdate) update).getValue());
                break;
              case BINARY:
                docValuesFieldUpdates.add(leafDocId, ((BinaryDocValuesUpdate) update).getValue());
                break;
              default:
                throw new AssertionError("type: " + update.type + " is not supported");
            }
          } else {
            docValuesFieldUpdates.reset(leafDocId);
          }
        }
        for (DocValuesFieldUpdates updates : fieldUpdatesMap.values()) {
          updates.finish();
          rld.addDVUpdate(updates);
        }
      } finally {
        bufferedUpdatesStream.finishedSegment(nextGen);
      }
      // Must bump changeCount so if no other changes
      // happened, we still commit this change:
      changed();
    });
  }

  @FunctionalInterface
  private interface DocModifier {
    void run(int docId, ReadersAndUpdates readersAndUpdates) throws IOException;
  }

  private synchronized long tryModifyDocument(IndexReader readerIn, int docID, DocModifier toApply) throws IOException {
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

    final SegmentCommitInfo info = ((SegmentReader) reader).getOriginalSegmentInfo();

    // TODO: this is a slow linear search, but, number of
    // segments should be contained unless something is
    // seriously wrong w/ the index, so it should be a minor
    // cost:

    if (segmentInfos.indexOf(info) != -1) {
      ReadersAndUpdates rld = getPooledInstance(info, false);
      if (rld != null) {
        synchronized(bufferedUpdatesStream) {
          toApply.run(docID, rld);
          return docWriter.deleteQueue.getNextSequenceNumber();
        }
      }
    }
    return -1;
  }

  synchronized void dropDeletedSegment(SegmentCommitInfo info) throws IOException {
    // If a merge has already registered for this
    // segment, we leave it in the readerPool; the
    // merge will skip merging it and will then drop
    // it once it's done:
    if (mergingSegments.contains(info) == false) {
      // it's possible that we invoke this method more than once for the same SCI
      // we must only remove the docs once!
      boolean dropPendingDocs = segmentInfos.remove(info);
      try {
        // this is sneaky - we might hit an exception while dropping a reader but then we have already
        // removed the segment for the segmentInfo and we lost the pendingDocs update due to that.
        // therefore we execute the adjustPendingNumDocs in a finally block to account for that.
        dropPendingDocs |= readerPool.drop(info);
      } finally {
        if (dropPendingDocs) {
          adjustPendingNumDocs(-info.info.maxDoc());
        }
      }
    }
  }

  public long deleteDocuments(Term... terms) throws IOException {
    ensureOpen();
    try {
      long seqNo = docWriter.deleteTerms(terms);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteDocuments(Term..)");
      throw tragedy;
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
        processEvents(true);
      }

      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteDocuments(Query..)");
      throw tragedy;
    }
  }

  public long updateDocument(Term term, Iterable<? extends IndexableField> doc) throws IOException {
    return updateDocument(term == null ? null : DocumentsWriterDeleteQueue.newNode(term), doc);
  }

  private long updateDocument(final DocumentsWriterDeleteQueue.Node<?> delNode,
                              Iterable<? extends IndexableField> doc) throws IOException {
    ensureOpen();
    boolean success = false;
    try {
      long seqNo = docWriter.updateDocument(doc, analyzer, delNode);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true);
      }
      success = true;
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocument");
      throw tragedy;
    } finally {
      if (success == false) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "hit exception updating document");
        }
      }
      maybeCloseOnTragicEvent();
    }
  }


  public long softUpdateDocument(Term term, Iterable<? extends IndexableField> doc, Field... softDeletes) throws IOException {
    if (term == null) {
      throw new IllegalArgumentException("term must not be null");
    }
    if (softDeletes == null || softDeletes.length == 0) {
      throw new IllegalArgumentException("at least one soft delete must be present");
    }
    return updateDocument(DocumentsWriterDeleteQueue.newNode(buildDocValuesUpdate(term, softDeletes)), doc);
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
        processEvents(true);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateNumericDocValue");
      throw tragedy;
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
        processEvents(true);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateBinaryDocValue");
      throw tragedy;
    }
  }
  
  public long updateDocValues(Term term, Field... updates) throws IOException {
    ensureOpen();
    DocValuesUpdate[] dvUpdates = buildDocValuesUpdate(term, updates);
    try {
      long seqNo = docWriter.updateDocValues(dvUpdates);
      if (seqNo < 0) {
        seqNo = -seqNo;
        processEvents(true);
      }
      return seqNo;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "updateDocValues");
      throw tragedy;
    }
  }

  private DocValuesUpdate[] buildDocValuesUpdate(Term term, Field[] updates) {
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
      if (globalFieldNumberMap.contains(f.name(), dvType) == false) {
        // if this field doesn't exists we try to add it. if it exists and the DV type doesn't match we
        // get a consistent error message as if you try to do that during an indexing operation.
        globalFieldNumberMap.addOrGet(f.name(), -1, dvType, 0, 0, 0, f.name().equals(config.softDeletesField));
        assert globalFieldNumberMap.contains(f.name(), dvType);
      }
      if (config.getIndexSortFields().contains(f.name())) {
        throw new IllegalArgumentException("cannot update docvalues field involved in the index sort, field=" + f.name() + ", sort=" + config.getIndexSort());
      }

      switch (dvType) {
        case NUMERIC:
          Long value = (Long)f.numericValue();
          dvUpdates[i] = new NumericDocValuesUpdate(term, f.name(), value);
          break;
        case BINARY:
          dvUpdates[i] = new BinaryDocValuesUpdate(term, f.name(), f.binaryValue());
          break;
        default:
          throw new IllegalArgumentException("can only update NUMERIC or BINARY fields: field=" + f.name() + ", type=" + dvType);
      }
    }
    return dvUpdates;
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
      return "_" + Long.toString(segmentInfos.counter++, Character.MAX_RADIX);
    }
  }

  final InfoStream infoStream;

  public void forceMerge(int maxNumSegments) throws IOException {
    forceMerge(maxNumSegments, true);
  }

  public void forceMerge(int maxNumSegments, boolean doWait) throws IOException {
    ensureOpen();

    if (maxNumSegments < 1) {
      throw new IllegalArgumentException("maxNumSegments must be >= 1; got " + maxNumSegments);
    }

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "forceMerge: index now " + segString());
      infoStream.message("IW", "now flush at forceMerge");
    }
    flush(true, true);
    synchronized(this) {
      resetMergeExceptions();
      segmentsToMerge.clear();
      for(SegmentCommitInfo info : segmentInfos) {
        assert info != null;
        segmentsToMerge.put(info, Boolean.TRUE);
      }
      mergeMaxNumSegments = maxNumSegments;

      // Now mark all pending & running merges for forced
      // merge:
      for(final MergePolicy.OneMerge merge  : pendingMerges) {
        merge.maxNumSegments = maxNumSegments;
        if (merge.info != null) {
          // TODO: explain why this is sometimes still null
          segmentsToMerge.put(merge.info, Boolean.TRUE);
        }
      }

      for (final MergePolicy.OneMerge merge: runningMerges) {
        merge.maxNumSegments = maxNumSegments;
        if (merge.info != null) {
          // TODO: explain why this is sometimes still null
          segmentsToMerge.put(merge.info, Boolean.TRUE);
        }
      }
    }

    maybeMerge(config.getMergePolicy(), MergeTrigger.EXPLICIT, maxNumSegments);

    if (doWait) {
      synchronized(this) {
        while(true) {

          if (tragedy.get() != null) {
            throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete forceMerge", tragedy.get());
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

          if (tragedy.get() != null) {
            throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete forceMergeDeletes", tragedy.get());
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

  final void maybeMerge(MergePolicy mergePolicy, MergeTrigger trigger, int maxNumSegments) throws IOException {
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
    if (tragedy.get() != null) {
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

  public synchronized Set<SegmentCommitInfo> getMergingSegments() {
    return Collections.unmodifiableSet(mergingSegments);
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

      assert pendingNumDocs.get() == segmentInfos.totalMaxDoc()
          : "pendingNumDocs " + pendingNumDocs.get() + " != " + segmentInfos.totalMaxDoc() + " totalMaxDoc";
    }
  }

  private void rollbackInternalNoCommit() throws IOException {
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

      docWriter.close(); // mark it as closed first to prevent subsequent indexing actions/flushes
      assert !Thread.holdsLock(this) : "IndexWriter lock should never be hold when aborting";
      docWriter.abort(); // don't sync on IW here
      docWriter.flushControl.waitForFlush(); // wait for all concurrently running flushes
      publishFlushedSegments(true); // empty the flush ticket queue otherwise we might not have cleaned up all resources
      synchronized (this) {

        if (pendingCommit != null) {
          pendingCommit.rollbackCommit(directory);
          try {
            deleter.decRef(pendingCommit);
          } finally {
            pendingCommit = null;
            notifyAll();
          }
        }
        final int totalMaxDoc = segmentInfos.totalMaxDoc();
        // Keep the same segmentInfos instance but replace all
        // of its SegmentInfo instances so IFD below will remove
        // any segments we flushed since the last commit:
        segmentInfos.rollbackSegmentInfos(rollbackSegments);
        int rollbackMaxDoc = segmentInfos.totalMaxDoc();
        // now we need to adjust this back to the rolled back SI but don't set it to the absolute value
        // otherwise we might hide internal bugsf
        adjustPendingNumDocs(-(totalMaxDoc - rollbackMaxDoc));
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "rollback: infos=" + segString(segmentInfos));
        }

        testPoint("rollback before checkpoint");

        // Ask deleter to locate unreferenced files & remove
        // them ... only when we are not experiencing a tragedy, else
        // these methods throw ACE:
        if (tragedy.get() == null) {
          deleter.checkpoint(segmentInfos, false);
          deleter.refresh();
          deleter.close();
        }

        lastCommitChangeCount = changeCount.get();
        // Don't bother saving any changes in our segmentInfos
        readerPool.close();
        // Must set closed while inside same sync block where we call deleter.refresh, else concurrent threads may try to sneak a flush in,
        // after we leave this sync block and before we enter the sync block in the finally clause below that sets closed:
        closed = true;

        IOUtils.close(writeLock); // release write lock
        writeLock = null;
        closed = true;
        closing = false;
        // So any "concurrently closing" threads wake up and see that the close has now completed:
        notifyAll();
      }
    } catch (Throwable throwable) {
      try {
        // Must not hold IW's lock while closing
        // mergeScheduler: this can lead to deadlock,
        // e.g. TestIW.testThreadInterruptDeadlock
        IOUtils.closeWhileHandlingException(mergeScheduler);
        synchronized (this) {
          // we tried to be nice about it: do the minimum
          // don't leak a segments_N file if there is a pending commit
          if (pendingCommit != null) {
            try {
              pendingCommit.rollbackCommit(directory);
              deleter.decRef(pendingCommit);
            } catch (Throwable t) {
              throwable.addSuppressed(t);
            }
            pendingCommit = null;
          }

          // close all the closeables we can (but important is readerPool and writeLock to prevent leaks)
          IOUtils.closeWhileHandlingException(readerPool, deleter, writeLock);
          writeLock = null;
          closed = true;
          closing = false;

          // So any "concurrently closing" threads wake up and see that the close has now completed:
          notifyAll();
        }
      } catch (Throwable t) {
        throwable.addSuppressed(t);
      } finally {
        if (throwable instanceof VirtualMachineError) {
          try {
            tragicEvent(throwable, "rollbackInternal");
          } catch (Throwable t1){
            throwable.addSuppressed(t1);
          }
        }
      }
      throw throwable;
    }
  }

  @SuppressWarnings("try")
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
        try (Closeable finalizer = docWriter.lockAndAbortAll()) {
          processEvents(false);
          synchronized (this) {
            try {
              // Abort any running merges
              abortMerges();
              // Let merges run again
              stopMerges = false;
              adjustPendingNumDocs(-segmentInfos.totalMaxDoc());
              // Remove all segments
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
              readerPool.dropAll();
              // Mark that the index has changed
              changeCount.incrementAndGet();
              segmentInfos.changed();
              globalFieldNumberMap.clear();
              success = true;
              long seqNo = docWriter.deleteQueue.getNextSequenceNumber();
              docWriter.setLastSeqNo(seqNo);
              return seqNo;
            } finally {
              if (success == false) {
                if (infoStream.isEnabled("IW")) {
                  infoStream.message("IW", "hit exception during deleteAll");
                }
              }
            }
          }
        }
      }
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "deleteAll");
      throw tragedy;
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

  synchronized long publishFrozenUpdates(FrozenBufferedUpdates packet) {
    assert packet != null && packet.any();
    long nextGen = bufferedUpdatesStream.push(packet);
    // Do this as an event so it applies higher in the stack when we are not holding DocumentsWriterFlushQueue.purgeLock:
    eventQueue.add(w -> {
      try {
        // we call tryApply here since we don't want to block if a refresh or a flush is already applying the
        // packet. The flush will retry this packet anyway to ensure all of them are applied
        packet.tryApply(w);
      } catch (Throwable t) {
        try {
          w.onTragicEvent(t, "applyUpdatesPacket");
        } catch (Throwable t1) {
          t.addSuppressed(t1);
        }
        throw t;
      }
      w.flushDeletesCount.incrementAndGet();
    });
    return nextGen;
  }

  private synchronized void publishFlushedSegment(SegmentCommitInfo newSegment, FieldInfos fieldInfos,
                                                  FrozenBufferedUpdates packet, FrozenBufferedUpdates globalPacket,
                                                  Sorter.DocMap sortMap) throws IOException {
    boolean published = false;
    try {
      // Lock order IW -> BDS
      ensureOpen(false);

      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "publishFlushedSegment " + newSegment);
      }

      if (globalPacket != null && globalPacket.any()) {
        publishFrozenUpdates(globalPacket);
      }

      // Publishing the segment must be sync'd on IW -> BDS to make the sure
      // that no merge prunes away the seg. private delete packet
      final long nextGen;
      if (packet != null && packet.any()) {
        nextGen = publishFrozenUpdates(packet);
      } else {
        // Since we don't have a delete packet to apply we can get a new
        // generation right away
        nextGen = bufferedUpdatesStream.getNextGen();
        // No deletes/updates here, so marked finished immediately:
        bufferedUpdatesStream.finishedSegment(nextGen);
      }
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "publish sets newSegment delGen=" + nextGen + " seg=" + segString(newSegment));
      }
      newSegment.setBufferedDeletesGen(nextGen);
      segmentInfos.add(newSegment);
      published = true;
      checkpoint();
      if (packet != null && packet.any() && sortMap != null) {
        // TODO: not great we do this heavyish op while holding IW's monitor lock,
        // but it only applies if you are using sorted indices and updating doc values:
        ReadersAndUpdates rld = getPooledInstance(newSegment, true);
        rld.sortMap = sortMap;
        // DON't release this ReadersAndUpdates we need to stick with that sortMap
      }
      FieldInfo fieldInfo = fieldInfos.fieldInfo(config.softDeletesField); // will return null if no soft deletes are present
      // this is a corner case where documents delete them-self with soft deletes. This is used to
      // build delete tombstones etc. in this case we haven't seen any updates to the DV in this fresh flushed segment.
      // if we have seen updates the update code checks if the segment is fully deleted.
      boolean hasInitialSoftDeleted = (fieldInfo != null
          && fieldInfo.getDocValuesGen() == -1
          && fieldInfo.getDocValuesType() != DocValuesType.NONE);
      final boolean isFullyHardDeleted = newSegment.getDelCount() == newSegment.info.maxDoc();
      // we either have a fully hard-deleted segment or one or more docs are soft-deleted. In both cases we need
      // to go and check if they are fully deleted. This has the nice side-effect that we now have accurate numbers
      // for the soft delete right after we flushed to disk.
      if (hasInitialSoftDeleted || isFullyHardDeleted){
        // this operation is only really executed if needed an if soft-deletes are not configured it only be executed
        // if we deleted all docs in this newly flushed segment.
        ReadersAndUpdates rld = getPooledInstance(newSegment, true);
        try {
          if (isFullyDeleted(rld)) {
            dropDeletedSegment(newSegment);
            checkpoint();
          }
        } finally {
          release(rld);
        }
      }

    } finally {
      if (published == false) {
        adjustPendingNumDocs(-newSegment.info.maxDoc());
      }
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
        if (segmentInfos.getIndexCreatedVersionMajor() != sis.getIndexCreatedVersionMajor()) {
          throw new IllegalArgumentException("Cannot use addIndexes(Directory) with indexes that have been created "
              + "by a different Lucene version. The current index was generated by Lucene "
              + segmentInfos.getIndexCreatedVersionMajor()
              + " while one of the directories contains an index that was generated with Lucene "
              + sis.getIndexCreatedVersionMajor());
        }
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
              globalFieldNumberMap.addOrGet(fi.name, fi.number, fi.getDocValuesType(), fi.getPointDataDimensionCount(), fi.getPointIndexDimensionCount(), fi.getPointNumBytes(), fi.isSoftDeletesField());
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
      throw tragedy;
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

  private void validateMergeReader(CodecReader leaf) {
    LeafMetaData segmentMeta = leaf.getMetaData();
    if (segmentInfos.getIndexCreatedVersionMajor() != segmentMeta.getCreatedVersionMajor()) {
      throw new IllegalArgumentException("Cannot merge a segment that has been created with major version "
          + segmentMeta.getCreatedVersionMajor() + " into this index which has been created by major version "
          + segmentInfos.getIndexCreatedVersionMajor());
    }

    if (segmentInfos.getIndexCreatedVersionMajor() >= 7 && segmentMeta.getMinVersion() == null) {
      throw new IllegalStateException("Indexes created on or after Lucene 7 must record the created version major, but " + leaf + " hides it");
    }

    Sort leafIndexSort = segmentMeta.getSort();
    if (config.getIndexSort() != null && leafIndexSort != null
        && config.getIndexSort().equals(leafIndexSort) == false) {
      throw new IllegalArgumentException("cannot change index sort from " + leafIndexSort + " to " + config.getIndexSort());
    }
  }

  public long addIndexes(CodecReader... readers) throws IOException {
    ensureOpen();

    // long so we can detect int overflow:
    long numDocs = 0;
    long seqNo;
    try {
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "flush at addIndexes(CodecReader...)");
      }
      flush(false, true);

      String mergedName = newSegmentName();
      int numSoftDeleted = 0;
      for (CodecReader leaf : readers) {
        numDocs += leaf.numDocs();
        validateMergeReader(leaf);
        if (softDeletesEnabled) {
            Bits liveDocs = leaf.getLiveDocs();
            numSoftDeleted += PendingSoftDeletes.countSoftDeletes(
            DocValuesFieldExistsQuery.getDocValuesDocIdSetIterator(config.getSoftDeletesField(), leaf), liveDocs);
        }
      }
      
      // Best-effort up front check:
      testReserveDocs(numDocs);

      final IOContext context = new IOContext(new MergeInfo(Math.toIntExact(numDocs), -1, false, UNBOUNDED_MAX_MERGE_SEGMENTS));

      // TODO: somehow we should fix this merge so it's
      // abortable so that IW.close(false) is able to stop it
      TrackingDirectoryWrapper trackingDir = new TrackingDirectoryWrapper(directory);

      // We set the min version to null for now, it will be set later by SegmentMerger
      SegmentInfo info = new SegmentInfo(directoryOrig, Version.LATEST, null, mergedName, -1,
                                         false, codec, Collections.emptyMap(), StringHelper.randomId(), new HashMap<>(), config.getIndexSort());

      SegmentMerger merger = new SegmentMerger(Arrays.asList(readers), info, infoStream, trackingDir,
                                               globalFieldNumberMap, 
                                               context);

      if (!merger.shouldMerge()) {
        return docWriter.deleteQueue.getNextSequenceNumber();
      }

      merger.merge();                // merge 'em
      SegmentCommitInfo infoPerCommit = new SegmentCommitInfo(info, 0, numSoftDeleted, -1L, -1L, -1L);

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
          createCompoundFile(infoStream, trackingCFSDir, info, context, this::deleteNewFiles);
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
      throw tragedy;
    }
    maybeMerge();

    return seqNo;
  }

  private SegmentCommitInfo copySegmentAsIs(SegmentCommitInfo info, String segName, IOContext context) throws IOException {
    
    // Same SI as before but we change directory and name
    SegmentInfo newInfo = new SegmentInfo(directoryOrig, info.info.getVersion(), info.info.getMinVersion(), segName, info.info.maxDoc(),
                                          info.info.getUseCompoundFile(), info.info.getCodec(), 
                                          info.info.getDiagnostics(), info.info.getId(), info.info.getAttributes(), info.info.getIndexSort());
    SegmentCommitInfo newInfoPerCommit = new SegmentCommitInfo(newInfo, info.getDelCount(), info.getSoftDelCount(), info.getDelGen(),
                                                               info.getFieldInfosGen(), info.getDocValuesGen());

    newInfo.setFiles(info.info.files());
    newInfoPerCommit.setFieldInfosFiles(info.getFieldInfosFiles());
    newInfoPerCommit.setDocValuesUpdatesFiles(info.getDocValuesUpdatesFiles());

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

    assert copiedFiles.equals(newInfoPerCommit.files()): "copiedFiles=" + copiedFiles + " vs " + newInfoPerCommit.files();
    
    return newInfoPerCommit;
  }
  
  protected void doAfterFlush() throws IOException {}

  protected void doBeforeFlush() throws IOException {}

  @Override
  public final long prepareCommit() throws IOException {
    ensureOpen();
    pendingSeqNo = prepareCommitInternal();
    // we must do this outside of the commitLock else we can deadlock:
    if (maybeMerge.getAndSet(false)) {
      maybeMerge(config.getMergePolicy(), MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);      
    }
    return pendingSeqNo;
  }

  public final boolean flushNextBuffer() throws IOException {
    try {
      if (docWriter.flushOneDWPT()) {
        processEvents(true);
        return true; // we wrote a segment
      }
      return false;
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "flushNextBuffer");
      throw tragedy;
    } finally {
      maybeCloseOnTragicEvent();
    }
  }

  private long prepareCommitInternal() throws IOException {
    startCommitTime = System.nanoTime();
    synchronized(commitLock) {
      ensureOpen(false);
      if (infoStream.isEnabled("IW")) {
        infoStream.message("IW", "prepareCommit: flush");
        infoStream.message("IW", "  index before flush " + segString());
      }

      if (tragedy.get() != null) {
        throw new IllegalStateException("this writer hit an unrecoverable error; cannot commit", tragedy.get());
      }

      if (pendingCommit != null) {
        throw new IllegalStateException("prepareCommit was already called with no corresponding call to commit");
      }

      doBeforeFlush();
      testPoint("startDoFlush");
      SegmentInfos toCommit = null;
      boolean anyChanges = false;
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
              anyChanges = true;
              seqNo = -seqNo;
            }
            if (anyChanges == false) {
              // prevent double increment since docWriter#doFlush increments the flushcount
              // if we flushed anything.
              flushCount.incrementAndGet();
            }
            publishFlushedSegments(true);
            // cannot pass triggerMerges=true here else it can lead to deadlock:
            processEvents(false);
            
            flushSuccess = true;

            applyAllDeletesAndUpdates();
            synchronized(this) {
              writeReaderPool(true);
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
            assert holdsFullFlushLock();
            // Done: finish the full flush!
            docWriter.finishFullFlush(flushSuccess);
            doAfterFlush();
          }
        }
      } catch (VirtualMachineError tragedy) {
        tragicEvent(tragedy, "prepareCommit");
        throw tragedy;
      } finally {
        maybeCloseOnTragicEvent();
      }
     
      try {
        if (anyChanges) {
          maybeMerge.set(true);
        }
        startCommit(toCommit);
        if (pendingCommit == null) {
          return -1;
        } else {
          return seqNo;
        }
      } catch (Throwable t) {
        synchronized (this) {
          if (filesToCommit != null) {
            try {
              deleter.decRef(filesToCommit);
            } catch (Throwable t1) {
              t.addSuppressed(t1);
            } finally {
              filesToCommit = null;
            }
          }
        }
        throw t;
      }
    }
  }

  private final void writeReaderPool(boolean writeDeletes) throws IOException {
    assert Thread.holdsLock(this);
    if (writeDeletes) {
      if (readerPool.commit(segmentInfos)) {
        checkpointNoSIS();
      }
    } else { // only write the docValues
      if (readerPool.writeAllDocValuesUpdates()) {
        checkpoint();
      }
    }
    // now do some best effort to check if a segment is fully deleted
    List<SegmentCommitInfo> toDrop = new ArrayList<>(); // don't modify segmentInfos in-place
    for (SegmentCommitInfo info : segmentInfos) {
      ReadersAndUpdates readersAndUpdates = readerPool.get(info, false);
      if (readersAndUpdates != null) {
        if (isFullyDeleted(readersAndUpdates)) {
          toDrop.add(info);
        }
      }
    }
    for (SegmentCommitInfo info : toDrop) {
      dropDeletedSegment(info);
    }
    if (toDrop.isEmpty() == false) {
      checkpoint();
    }
  }
  
  public final synchronized void setLiveCommitData(Iterable<Map.Entry<String,String>> commitUserData) {
    setLiveCommitData(commitUserData, true);
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
        seqNo = prepareCommitInternal();
      } else {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "commit: already prepared");
        }
        seqNo = pendingSeqNo;
      }

      finishCommit();
    }

    // we must do this outside of the commitLock else we can deadlock:
    if (maybeMerge.getAndSet(false)) {
      maybeMerge(mergePolicy, MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);      
    }
    
    return seqNo;
  }

  @SuppressWarnings("try")
  private final void finishCommit() throws IOException {

    boolean commitCompleted = false;
    String committedSegmentsFileName = null;

    try {
      synchronized(this) {
        ensureOpen(false);

        if (tragedy.get() != null) {
          throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete commit", tragedy.get());
        }

        if (pendingCommit != null) {
          final Collection<String> commitFiles = this.filesToCommit;
          try (Closeable finalizer = () -> deleter.decRef(commitFiles)) {

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

          } finally {
            notifyAll();
            pendingCommit = null;
            this.filesToCommit = null;
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
      }
      throw t;
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
    if (tragedy.get() != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot flush", tragedy.get());
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
          publishFlushedSegments(true);
          flushSuccess = true;
        } finally {
          assert holdsFullFlushLock();
          docWriter.finishFullFlush(flushSuccess);
          processEvents(false);
        }
      }

      if (applyAllDeletes) {
        applyAllDeletesAndUpdates();
      }

      anyChanges |= maybeMerge.getAndSet(false);
      
      synchronized(this) {
        writeReaderPool(applyAllDeletes);
        doAfterFlush();
        success = true;
        return anyChanges;
      }
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "doFlush");
      throw tragedy;
    } finally {
      if (!success) {
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "hit exception during flush");
        }
        maybeCloseOnTragicEvent();
      }
    }
  }
  
  final void applyAllDeletesAndUpdates() throws IOException {
    assert Thread.holdsLock(this) == false;
    flushDeletesCount.incrementAndGet();
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "now apply all deletes for all segments buffered updates bytesUsed=" + bufferedUpdatesStream.ramBytesUsed() + " reader pool bytesUsed=" + readerPool.ramBytesUsed());
    }
    bufferedUpdatesStream.waitApplyAll(this);
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
      if (iter.docID() == deletedDoc) {
        iter.nextDoc();
      }
      // when entering the method, all iterators must already be beyond the
      // deleted document, or right on it, in which case we advance them over
      // and they must be beyond it now.
      assert iter.docID() > deletedDoc : "updateDoc=" + iter.docID() + " deletedDoc=" + deletedDoc;
    }
  }
  
  synchronized private ReadersAndUpdates commitMergedDeletesAndUpdates(MergePolicy.OneMerge merge, MergeState mergeState) throws IOException {

    mergeFinishedGen.incrementAndGet();
    
    testPoint("startCommitMergeDeletes");

    final List<SegmentCommitInfo> sourceSegments = merge.segments;

    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "commitMergeDeletes " + segString(merge.segments));
    }

    // Carefully merge deletes that occurred after we
    // started merging:
    long minGen = Long.MAX_VALUE;

    // Lazy init (only when we find a delete or update to carry over):
    final ReadersAndUpdates mergedDeletesAndUpdates = getPooledInstance(merge.info, true);
    int numDeletesBefore = mergedDeletesAndUpdates.getDelCount();
    // field -> delGen -> dv field updates
    Map<String,Map<Long,DocValuesFieldUpdates>> mappedDVUpdates = new HashMap<>();

    boolean anyDVUpdates = false;

    assert sourceSegments.size() == mergeState.docMaps.length;
    for (int i = 0; i < sourceSegments.size(); i++) {
      SegmentCommitInfo info = sourceSegments.get(i);
      minGen = Math.min(info.getBufferedDeletesGen(), minGen);
      final int maxDoc = info.info.maxDoc();
      final ReadersAndUpdates rld = getPooledInstance(info, false);
      // We hold a ref, from when we opened the readers during mergeInit, so it better still be in the pool:
      assert rld != null: "seg=" + info.info.name;

      MergeState.DocMap segDocMap = mergeState.docMaps[i];
      MergeState.DocMap segLeafDocMap = mergeState.leafDocMaps[i];

      carryOverHardDeletes(mergedDeletesAndUpdates, maxDoc, mergeState.liveDocs[i],  merge.hardLiveDocs.get(i), rld.getHardLiveDocs(),
          segDocMap, segLeafDocMap);

      // Now carry over all doc values updates that were resolved while we were merging, remapping the docIDs to the newly merged docIDs.
      // We only carry over packets that finished resolving; if any are still running (concurrently) they will detect that our merge completed
      // and re-resolve against the newly merged segment:
      Map<String,List<DocValuesFieldUpdates>> mergingDVUpdates = rld.getMergingDVUpdates();
      for (Map.Entry<String,List<DocValuesFieldUpdates>> ent : mergingDVUpdates.entrySet()) {

        String field = ent.getKey();

        Map<Long,DocValuesFieldUpdates> mappedField = mappedDVUpdates.get(field);
        if (mappedField == null) {
          mappedField = new HashMap<>();
          mappedDVUpdates.put(field, mappedField);
        }

        for (DocValuesFieldUpdates updates : ent.getValue()) {

          if (bufferedUpdatesStream.stillRunning(updates.delGen)) {
            continue;
          }
              
          // sanity check:
          assert field.equals(updates.field);

          DocValuesFieldUpdates mappedUpdates = mappedField.get(updates.delGen);
          if (mappedUpdates == null) {
            switch (updates.type) {
              case NUMERIC:
                mappedUpdates = new NumericDocValuesFieldUpdates(updates.delGen, updates.field, merge.info.info.maxDoc());
                break;
              case BINARY:
                mappedUpdates = new BinaryDocValuesFieldUpdates(updates.delGen, updates.field, merge.info.info.maxDoc());
                break;
              default:
                throw new AssertionError();
            }
            mappedField.put(updates.delGen, mappedUpdates);
          }

          DocValuesFieldUpdates.Iterator it = updates.iterator();
          int doc;
          while ((doc = it.nextDoc()) != NO_MORE_DOCS) {
            int mappedDoc = segDocMap.get(segLeafDocMap.get(doc));
            if (mappedDoc != -1) {
              if (it.hasValue()) {
                // not deleted
                mappedUpdates.add(mappedDoc, it);
              } else {
                mappedUpdates.reset(mappedDoc);
              }
              anyDVUpdates = true;
            }
          }
        }
      }
    }

    if (anyDVUpdates) {
      // Persist the merged DV updates onto the RAU for the merged segment:
      for(Map<Long,DocValuesFieldUpdates> d : mappedDVUpdates.values()) {
        for (DocValuesFieldUpdates updates : d.values()) {
          updates.finish();
          mergedDeletesAndUpdates.addDVUpdate(updates);
        }
      }
    }

    if (infoStream.isEnabled("IW")) {
      if (mergedDeletesAndUpdates == null) {
        infoStream.message("IW", "no new deletes or field updates since merge started");
      } else {
        String msg = mergedDeletesAndUpdates.getDelCount() - numDeletesBefore + " new deletes";
        if (anyDVUpdates) {
          msg += " and " + mergedDeletesAndUpdates.getNumDVUpdates() + " new field updates";
          msg += " (" + mergedDeletesAndUpdates.ramBytesUsed.get() + ") bytes";
        }
        msg += " since merge started";
        infoStream.message("IW", msg);
      }
    }

    merge.info.setBufferedDeletesGen(minGen);

    return mergedDeletesAndUpdates;
  }

  private static void carryOverHardDeletes(ReadersAndUpdates mergedReadersAndUpdates, int maxDoc,
                                           Bits mergeLiveDocs, // the liveDocs used to build the segDocMaps
                                           Bits prevHardLiveDocs, // the hard deletes when the merge reader was pulled
                                           Bits currentHardLiveDocs, // the current hard deletes
                                           MergeState.DocMap segDocMap, MergeState.DocMap segLeafDocMap) throws IOException {

    assert mergeLiveDocs == null || mergeLiveDocs.length() == maxDoc;
    // if we mix soft and hard deletes we need to make sure that we only carry over deletes
    // that were not deleted before. Otherwise the segDocMap doesn't contain a mapping.
    // yet this is also required if any MergePolicy modifies the liveDocs since this is
    // what the segDocMap is build on.
    final IntPredicate carryOverDelete = mergeLiveDocs == null || mergeLiveDocs == prevHardLiveDocs
        ? docId -> currentHardLiveDocs.get(docId) == false
        : docId -> mergeLiveDocs.get(docId) && currentHardLiveDocs.get(docId) == false;
    if (prevHardLiveDocs != null) {
      // If we had deletions on starting the merge we must
      // still have deletions now:
      assert currentHardLiveDocs != null;
      assert mergeLiveDocs != null;
      assert prevHardLiveDocs.length() == maxDoc;
      assert currentHardLiveDocs.length() == maxDoc;

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
      if (currentHardLiveDocs != prevHardLiveDocs) {
        // This means this segment received new deletes
        // since we started the merge, so we
        // must merge them:
        for (int j = 0; j < maxDoc; j++) {
          if (prevHardLiveDocs.get(j) == false) {
            // if the document was deleted before, it better still be deleted!
            assert currentHardLiveDocs.get(j) == false;
          } else if (carryOverDelete.test(j)) {
            // the document was deleted while we were merging:
            mergedReadersAndUpdates.delete(segDocMap.get(segLeafDocMap.get(j)));
          }
        }
      }
    } else if (currentHardLiveDocs != null) {
      assert currentHardLiveDocs.length() == maxDoc;
      // This segment had no deletes before but now it
      // does:
      for (int j = 0; j < maxDoc; j++) {
        if (carryOverDelete.test(j)) {
          mergedReadersAndUpdates.delete(segDocMap.get(segLeafDocMap.get(j)));
        }
      }
    }
  }

  @SuppressWarnings("try")
  synchronized private boolean commitMerge(MergePolicy.OneMerge merge, MergeState mergeState) throws IOException {

    testPoint("startCommitMerge");

    if (tragedy.get() != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot complete merge", tragedy.get());
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

    // If the doc store we are using has been closed and
    // is in now compound format (but wasn't when we
    // started), then we will switch to the compound
    // format as well:

    assert !segmentInfos.contains(merge.info);

    final boolean allDeleted = merge.segments.size() == 0 ||
      merge.info.info.maxDoc() == 0 ||
      (mergedUpdates != null && isFullyDeleted(mergedUpdates));

    if (infoStream.isEnabled("IW")) {
      if (allDeleted) {
        infoStream.message("IW", "merged segment " + merge.info + " is 100% deleted; skipping insert");
      }
    }

    final boolean dropSegment = allDeleted;

    // If we merged no segments then we better be dropping
    // the new segment:
    assert merge.segments.size() > 0 || dropSegment;

    assert merge.info.info.maxDoc() != 0 || dropSegment;

    if (mergedUpdates != null) {
      boolean success = false;
      try {
        if (dropSegment) {
          mergedUpdates.dropChanges();
        }
        // Pass false for assertInfoLive because the merged
        // segment is not yet live (only below do we commit it
        // to the segmentInfos):
        release(mergedUpdates, false);
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
    int delDocCount;
    if (dropSegment) {
      // if we drop the segment we have to reduce the pendingNumDocs by merge.totalMaxDocs since we never drop
      // the docs when we apply deletes if the segment is currently merged.
      delDocCount = merge.totalMaxDoc;
    } else {
      delDocCount = merge.totalMaxDoc - merge.info.info.maxDoc();
    }
    assert delDocCount >= 0;
    adjustPendingNumDocs(-delDocCount);

    if (dropSegment) {
      assert !segmentInfos.contains(merge.info);
      readerPool.drop(merge.info);
      // Safe: these files must exist
      deleteNewFiles(merge.info.files());
    }

    try (Closeable finalizer = this::checkpoint) {
      // Must close before checkpoint, otherwise IFD won't be
      // able to delete the held-open files from the merge
      // readers:
      closeMergeReaders(merge, false);
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
      if (merge.isExternal) { // TODO can we simplify this and just throw all the time? this would simplify this a lot
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
      throw t;
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

  final void mergeInit(MergePolicy.OneMerge merge) throws IOException {
    assert Thread.holdsLock(this) == false;
    // Make sure any deletes that must be resolved before we commit the merge are complete:
    bufferedUpdatesStream.waitApplyForMerge(merge.segments, this);

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

    if (tragedy.get() != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot merge", tragedy.get());
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

    // Must move the pending doc values updates to disk now, else the newly merged segment will not see them:
    // TODO: we could fix merging to pull the merged DV iterator so we don't have to move these updates to disk first, i.e. just carry them
    // in memory:
    if (readerPool.writeDocValuesUpdatesForMerge(merge.segments)) {
      checkpoint();
    }
    
    // Bind a new segment name here so even with
    // ConcurrentMergePolicy we keep deterministic segment
    // names.
    final String mergeSegmentName = newSegmentName();
    // We set the min version to null for now, it will be set later by SegmentMerger
    SegmentInfo si = new SegmentInfo(directoryOrig, Version.LATEST, null, mergeSegmentName, -1, false, codec, Collections.emptyMap(), StringHelper.randomId(), new HashMap<>(), config.getIndexSort());
    Map<String,String> details = new HashMap<>();
    details.put("mergeMaxNumSegments", "" + merge.maxNumSegments);
    details.put("mergeFactor", Integer.toString(merge.segments.size()));
    setDiagnostics(si, SOURCE_MERGE, details);
    merge.setMergeInfo(new SegmentCommitInfo(si, 0, 0, -1L, -1L, -1L));

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

  @SuppressWarnings("try")
  private synchronized void closeMergeReaders(MergePolicy.OneMerge merge, boolean suppressExceptions) throws IOException {
    final boolean drop = suppressExceptions == false;
    try (Closeable finalizer = merge::mergeFinished) {
      IOUtils.applyToAll(merge.readers, sr -> {
        final ReadersAndUpdates rld = getPooledInstance(sr.getOriginalSegmentInfo(), false);
        // We still hold a ref so it should not have been removed:
        assert rld != null;
        if (drop) {
          rld.dropChanges();
        } else {
          rld.dropMergingUpdates();
        }
        rld.release(sr);
        release(rld);
        if (drop) {
          readerPool.drop(rld.info);
        }
      });
    } finally {
      Collections.fill(merge.readers, null);
    }
  }

  private void countSoftDeletes(CodecReader reader, Bits wrappedLiveDocs, Bits hardLiveDocs, Counter softDeleteCounter,
                                Counter hardDeleteCounter) throws IOException {
    int hardDeleteCount = 0;
    int softDeletesCount = 0;
    DocIdSetIterator softDeletedDocs = DocValuesFieldExistsQuery.getDocValuesDocIdSetIterator(config.getSoftDeletesField(), reader);
    if (softDeletedDocs != null) {
      int docId;
      while ((docId = softDeletedDocs.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        if (wrappedLiveDocs == null || wrappedLiveDocs.get(docId)) {
          if (hardLiveDocs == null || hardLiveDocs.get(docId)) {
            softDeletesCount++;
          } else {
            hardDeleteCount++;
          }
        }
      }
    }
    softDeleteCounter.addAndGet(softDeletesCount);
    hardDeleteCounter.addAndGet(hardDeleteCount);
  }

  private boolean assertSoftDeletesCount(CodecReader reader, int expectedCount) throws IOException {
    Counter count = Counter.newCounter(false);
    Counter hardDeletes = Counter.newCounter(false);
    countSoftDeletes(reader, reader.getLiveDocs(), null, count, hardDeletes);
    assert count.get() == expectedCount : "soft-deletes count mismatch expected: "
        + expectedCount  + " but actual: " + count.get() ;
    return true;
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
    merge.hardLiveDocs = new ArrayList<>(sourceSegments.size());

    // This is try/finally to make sure merger's readers are
    // closed:
    boolean success = false;
    try {
      int segUpto = 0;
      while(segUpto < sourceSegments.size()) {

        final SegmentCommitInfo info = sourceSegments.get(segUpto);

        // Hold onto the "live" reader; we will use this to
        // commit merged deletes
        final ReadersAndUpdates rld = getPooledInstance(info, true);
        rld.setIsMerging();

        ReadersAndUpdates.MergeReader mr = rld.getReaderForMerge(context);
        SegmentReader reader = mr.reader;

        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "seg=" + segString(info) + " reader=" + reader);
        }

        merge.hardLiveDocs.add(mr.hardLiveDocs);
        merge.readers.add(reader);
        segUpto++;
      }

      // Let the merge wrap readers
      List<CodecReader> mergeReaders = new ArrayList<>();
      Counter softDeleteCount = Counter.newCounter(false);
      for (int r = 0; r < merge.readers.size(); r++) {
        SegmentReader reader = merge.readers.get(r);
        CodecReader wrappedReader = merge.wrapForMerge(reader);
        validateMergeReader(wrappedReader);
        if (softDeletesEnabled) {
          if (reader != wrappedReader) { // if we don't have a wrapped reader we won't preserve any soft-deletes
            Bits hardLiveDocs = merge.hardLiveDocs.get(r);
            if (hardLiveDocs != null) { // we only need to do this accounting if we have mixed deletes
              Bits wrappedLiveDocs = wrappedReader.getLiveDocs();
              Counter hardDeleteCounter = Counter.newCounter(false);
              countSoftDeletes(wrappedReader, wrappedLiveDocs, hardLiveDocs, softDeleteCount, hardDeleteCounter);
              int hardDeleteCount = Math.toIntExact(hardDeleteCounter.get());
              // Wrap the wrapped reader again if we have excluded some hard-deleted docs
              if (hardDeleteCount > 0) {
                Bits liveDocs = wrappedLiveDocs == null ? hardLiveDocs : new Bits() {
                  @Override
                  public boolean get(int index) {
                    return hardLiveDocs.get(index) && wrappedLiveDocs.get(index);
                  }

                  @Override
                  public int length() {
                    return hardLiveDocs.length();
                  }
                };
                wrappedReader = FilterCodecReader.wrapLiveDocs(wrappedReader, liveDocs, wrappedReader.numDocs() - hardDeleteCount);
              }
            } else {
              final int carryOverSoftDeletes = reader.getSegmentInfo().getSoftDelCount() - wrappedReader.numDeletedDocs();
              assert carryOverSoftDeletes >= 0 : "carry-over soft-deletes must be positive";
              assert assertSoftDeletesCount(wrappedReader, carryOverSoftDeletes);
              softDeleteCount.addAndGet(carryOverSoftDeletes);
            }
          }
        }
        mergeReaders.add(wrappedReader);
      }
      final SegmentMerger merger = new SegmentMerger(mergeReaders,
                                                     merge.info.info, infoStream, dirWrapper,
                                                     globalFieldNumberMap, 
                                                     context);
      merge.info.setSoftDelCount(Math.toIntExact(softDeleteCount.get()));
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
      boolean useCompoundFile;
      synchronized (this) { // Guard segmentInfos
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, merge.info, this);
      }

      if (useCompoundFile) {
        success = false;

        Collection<String> filesToRemove = merge.info.files();
        TrackingDirectoryWrapper trackingCFSDir = new TrackingDirectoryWrapper(mergeDirectory);
        try {
          createCompoundFile(infoStream, trackingCFSDir, merge.info.info, context, this::deleteNewFiles);
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
      if (readerPool.isReaderPoolingEnabled() && mergedSegmentWarmer != null) {
        final ReadersAndUpdates rld = getPooledInstance(merge.info, true);
        final SegmentReader sr = rld.getReader(IOContext.READ);
        try {
          mergedSegmentWarmer.warm(sr);
        } finally {
          synchronized(this) {
            rld.release(sr);
            release(rld);
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
    return StreamSupport.stream(infos.spliterator(), false)
        .map(this::segString).collect(Collectors.joining(" "));
  }

  private synchronized String segString(SegmentCommitInfo info) {
    return info.toString(numDeletedDocs(info) - info.getDelCount(softDeletesEnabled));
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
    final SegmentInfos newSIS = new SegmentInfos(sis.getIndexCreatedVersionMajor());
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

    if (tragedy.get() != null) {
      throw new IllegalStateException("this writer hit an unrecoverable error; cannot commit", tragedy.get());
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

        synchronized (this) {

          assert pendingCommit == null;

          assert segmentInfos.getGeneration() == toSync.getGeneration();

          // Exception here means nothing is prepared
          // (this method unwinds everything it did on
          // an exception)
          toSync.prepareCommit(directory);
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "startCommit: wrote pending segments file \"" + IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS, "", toSync.getGeneration()) + "\"");
          }

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
      } catch (Throwable t) {
        synchronized(this) {
          if (!pendingCommitSet) {
            if (infoStream.isEnabled("IW")) {
              infoStream.message("IW", "hit exception committing segments file");
            }
            try {
              // Hit exception
              deleter.decRef(filesToCommit);
            } catch (Throwable t1) {
              t.addSuppressed(t1);
            } finally {
              filesToCommit = null;
            }
          }
        }
        throw t;
      } finally {
        synchronized(this) {
          // Have our master segmentInfos record the
          // generations we just prepared.  We do this
          // on error or success so we don't
          // double-write a segments_N file.
          segmentInfos.updateGeneration(toSync);
        }
      }
    } catch (VirtualMachineError tragedy) {
      tragicEvent(tragedy, "startCommit");
      throw tragedy;
    }
    testPoint("finishStartCommit");
  }

  @FunctionalInterface
  public interface IndexReaderWarmer {
    void warm(LeafReader reader) throws IOException;
  }

  final void onTragicEvent(Throwable tragedy, String location) {
    // This is not supposed to be tragic: IW is supposed to catch this and
    // ignore, because it means we asked the merge to abort:
    assert tragedy instanceof MergePolicy.MergeAbortedException == false;
    // How can it be a tragedy when nothing happened?
    assert tragedy != null;
    if (infoStream.isEnabled("IW")) {
      infoStream.message("IW", "hit tragic " + tragedy.getClass().getSimpleName() + " inside " + location);
    }
    this.tragedy.compareAndSet(null, tragedy); // only set it once
  }

  private void tragicEvent(Throwable tragedy, String location) throws IOException {
    try {
      onTragicEvent(tragedy, location);
    } finally {
      maybeCloseOnTragicEvent();
    }
  }

  private void maybeCloseOnTragicEvent() throws IOException {
    // We cannot hold IW's lock here else it can lead to deadlock:
    assert Thread.holdsLock(this) == false;
    assert Thread.holdsLock(fullFlushLock) == false;
    // if we are already closed (e.g. called by rollback), this will be a no-op.
    if (this.tragedy.get() != null && shouldClose(false)) {
      rollbackInternal();
    }
  }

  public Throwable getTragicException() {
    return tragedy.get();
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
    ensureOpen();
    boolean isCurrent = infos.getVersion() == segmentInfos.getVersion()
      && docWriter.anyChanges() == false
      && bufferedUpdatesStream.any() == false
      && readerPool.anyDocValuesChanges() == false;
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

  static final void createCompoundFile(InfoStream infoStream, TrackingDirectoryWrapper directory, final SegmentInfo info, IOContext context, IOUtils.IOConsumer<Collection<String>> deleteFiles) throws IOException {

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
        deleteFiles.accept(directory.getCreatedFiles());
      }
    }

    // Replace all previous files with the CFS/CFE files:
    info.setFiles(new HashSet<>(directory.getCreatedFiles()));
  }
  
  private synchronized void deleteNewFiles(Collection<String> files) throws IOException {
    deleter.deleteNewFiles(files);
  }
  private synchronized final void flushFailed(SegmentInfo info) throws IOException {
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

  void publishFlushedSegments(boolean forced) throws IOException {
    docWriter.purgeFlushTickets(forced, ticket -> {
      DocumentsWriterPerThread.FlushedSegment newSegment = ticket.getFlushedSegment();
      FrozenBufferedUpdates bufferedUpdates = ticket.getFrozenUpdates();
      ticket.markPublished();
      if (newSegment == null) { // this is a flushed global deletes package - not a segments
        if (bufferedUpdates != null && bufferedUpdates.any()) { // TODO why can this be null?
          publishFrozenUpdates(bufferedUpdates);
          if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "flush: push buffered updates: " + bufferedUpdates);
          }
        }
      } else {
        assert newSegment.segmentInfo != null;
        if (infoStream.isEnabled("IW")) {
          infoStream.message("IW", "publishFlushedSegment seg-private updates=" + newSegment.segmentUpdates);
        }
        if (newSegment.segmentUpdates != null && infoStream.isEnabled("DW")) {
          infoStream.message("IW", "flush: push buffered seg private updates: " + newSegment.segmentUpdates);
        }
        // now publish!
        publishFlushedSegment(newSegment.segmentInfo, newSegment.fieldInfos, newSegment.segmentUpdates,
            bufferedUpdates, newSegment.sortMap);
      }
    });
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
  
  private void processEvents(boolean triggerMerge) throws IOException {
    if (tragedy.get() == null) {
      Event event;
      while ((event = eventQueue.poll()) != null)  {
        event.process(this);
      }
    }
    if (triggerMerge) {
      maybeMerge(getConfig().getMergePolicy(), MergeTrigger.SEGMENT_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS);
    }
  }

  @FunctionalInterface
  private interface Event {
    void process(IndexWriter writer) throws IOException;
  }

  private void reserveDocs(long addedNumDocs) {
    assert addedNumDocs >= 0;
    if (adjustPendingNumDocs(addedNumDocs) > actualMaxDocs) {
      // Reserve failed: put the docs back and throw exc:
      adjustPendingNumDocs(-addedNumDocs);
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

  private long adjustPendingNumDocs(long numDocs) {
    long count = pendingNumDocs.addAndGet(numDocs);
    assert count >= 0 : "pendingNumDocs is negative: " + count;
    return count;
  }

  final boolean isFullyDeleted(ReadersAndUpdates readersAndUpdates) throws IOException {
    if (readersAndUpdates.isFullyDeleted()) {
      assert Thread.holdsLock(this);
      return readersAndUpdates.keepFullyDeletedSegment(config.getMergePolicy()) == false;
    }
    return false;
  }

  @Override
  public final int numDeletesToMerge(SegmentCommitInfo info) throws IOException {
    ensureOpen(false);
    validate(info);
    MergePolicy mergePolicy = config.getMergePolicy();
    final ReadersAndUpdates rld = getPooledInstance(info, false);
    int numDeletesToMerge;
    if (rld != null) {
      numDeletesToMerge = rld.numDeletesToMerge(mergePolicy);
    } else {
      // if we don't have a  pooled instance lets just return the hard deletes, this is safe!
      numDeletesToMerge = info.getDelCount();
    }
    assert numDeletesToMerge <= info.info.maxDoc() :
        "numDeletesToMerge: " + numDeletesToMerge + " > maxDoc: " + info.info.maxDoc();
    return numDeletesToMerge;
  }

  void release(ReadersAndUpdates readersAndUpdates) throws IOException {
    release(readersAndUpdates, true);
  }

  private void release(ReadersAndUpdates readersAndUpdates, boolean assertLiveInfo) throws IOException {
    assert Thread.holdsLock(this);
    if (readerPool.release(readersAndUpdates, assertLiveInfo)) {
      // if we write anything here we have to hold the lock otherwise IDF will delete files underneath us
      assert Thread.holdsLock(this);
      checkpointNoSIS();
    }
  }

  ReadersAndUpdates getPooledInstance(SegmentCommitInfo info, boolean create) {
    ensureOpen(false);
    return readerPool.get(info, create);
  }

  void finished(FrozenBufferedUpdates packet) {
    bufferedUpdatesStream.finished(packet);
  }

  int getPendingUpdatesCount() {
    return bufferedUpdatesStream.getPendingUpdatesCount();
  }

  protected boolean isEnableTestPoints() {
    return false;
  }

  private void validate(SegmentCommitInfo info) {
    if (info.info.dir != directoryOrig) {
      throw new IllegalArgumentException("SegmentCommitInfo must be from the same directory");
    }
  }

  final synchronized boolean segmentCommitInfoExist(SegmentCommitInfo sci) {
    return segmentInfos.contains(sci);
  }

  final synchronized List<SegmentCommitInfo> listOfSegmentCommitInfos() {
    return segmentInfos.asList();
  }

  final synchronized SegmentInfos cloneSegmentInfos() {
    return segmentInfos.clone();
  }

  public synchronized DocStats getDocStats() {
    ensureOpen();
    int numDocs = docWriter.getNumDocs();
    int maxDoc = numDocs;
    for (final SegmentCommitInfo info : segmentInfos) {
      maxDoc += info.info.maxDoc();
      numDocs += info.info.maxDoc() - numDeletedDocs(info);
    }
    assert maxDoc >= numDocs : "maxDoc is less than numDocs: " + maxDoc + " < " + numDocs;
    return new DocStats(maxDoc, numDocs);
  }

  public static final class DocStats {
    public final int maxDoc;
    public final int numDocs;

    private DocStats(int maxDoc, int numDocs) {
      this.maxDoc = maxDoc;
      this.numDocs = numDocs;
    }
  }
}
