package org.apache.lucene.index;

/**
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

import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.ThreadInterruptedException;
import org.apache.lucene.util.TwoPhaseCommit;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
  An <code>IndexWriter</code> creates and maintains an index.

  <p>The <code>create</code> argument to the {@code
  #IndexWriter(Directory, Analyzer, boolean, MaxFieldLength) constructor} determines 
  whether a new index is created, or whether an existing index is
  opened.  Note that you can open an index with <code>create=true</code>
  even while readers are using the index.  The old readers will 
  continue to search the "point in time" snapshot they had opened, 
  and won't see the newly created index until they re-open.  There are
  also {@code #IndexWriter(Directory, Analyzer, MaxFieldLength) constructors}
  with no <code>create</code> argument which will create a new index
  if there is not already an index at the provided path and otherwise 
  open the existing index.</p>

  <p>In either case, documents are added with {@code #addDocument(Document)
  addDocument} and removed with {@code #deleteDocuments(Term)} or {@code
  #deleteDocuments(Query)}. A document can be updated with {@code
  #updateDocument(Term, Document) updateDocument} (which just deletes
  and then adds the entire document). When finished adding, deleting 
  and updating documents, {@code #close() close} should be called.</p>

  <a name="flush"></a>
  <p>These changes are buffered in memory and periodically
  flushed to the {@code Directory} (during the above method
  calls).  A flush is triggered when there are enough
  buffered deletes (see {@code #setMaxBufferedDeleteTerms})
  or enough added documents since the last flush, whichever
  is sooner.  For the added documents, flushing is triggered
  either by RAM usage of the documents (see {@code
  #setRAMBufferSizeMB}) or the number of added documents.
  The default is to flush when RAM usage hits 16 MB.  For
  best indexing speed you should flush by RAM usage with a
  large RAM buffer.  Note that flushing just moves the
  internal buffered state in IndexWriter into the index, but
  these changes are not visible to IndexReader until either
  {@code #commit()} or {@code #close} is called.  A flush may
  also trigger one or more segment merges which by default
  run with a background thread so as not to block the
  addDocument calls (see <a href="#mergePolicy">below</a>
  for changing the {@code MergeScheduler}).</p>

  <p>Opening an <code>IndexWriter</code> creates a lock file for the directory in use. Trying to open
  another <code>IndexWriter</code> on the same directory will lead to a
  {@code LockObtainFailedException}. The {@code LockObtainFailedException}
  is also thrown if an IndexReader on the same directory is used to delete documents
  from the index.</p>
  
  <a name="deletionPolicy"></a>
  <p>Expert: <code>IndexWriter</code> allows an optional
  {@code IndexDeletionPolicy} implementation to be
  specified.  You can use this to control when prior commits
  are deleted from the index.  The default policy is {@code
  KeepOnlyLastCommitDeletionPolicy} which removes all prior
  commits as soon as a new commit is done (this matches
  behavior before 2.2).  Creating your own policy can allow
  you to explicitly keep previous "point in time" commits
  alive in the index for some time, to allow readers to
  refresh to the new commit without having the old commit
  deleted out from under them.  This is necessary on
  filesystems like NFS that do not support "delete on last
  close" semantics, which Lucene's "point in time" search
  normally relies on. </p>

  <a name="mergePolicy"></a> <p>Expert:
  <code>IndexWriter</code> allows you to separately change
  the {@code MergePolicy} and the {@code MergeScheduler}.
  The {@code MergePolicy} is invoked whenever there are
  changes to the segments in the index.  Its role is to
  select which merges to do, if any, and return a {@code
  MergePolicy.MergeSpecification} describing the merges.
  The default is {@code LogByteSizeMergePolicy}.  Then, the {@code
  MergeScheduler} is invoked with the requested merges and
  it decides when and how to run the merges.  The default is
  {@code ConcurrentMergeScheduler}. </p>

  <a name="OOME"></a><p><b>NOTE</b>: if you hit an
  OutOfMemoryError then IndexWriter will quietly record this
  fact and block all future segment commits.  This is a
  defensive measure in case any internal state (buffered
  documents and deletions) were corrupted.  Any subsequent
  calls to {@code #commit()} will throw an
  IllegalStateException.  The only course of action is to
  call {@code #close()}, which internally will call {@code
  #rollback()}, to undo any changes to the index since the
  last commit.  You can also just call {@code #rollback()}
  directly.</p>

  <a name="thread-safety"></a><p><b>NOTE</b>: {@code
  IndexWriter} instances are completely thread
  safe, meaning multiple threads can call any of its
  methods, concurrently.  If your application requires
  external synchronization, you should <b>not</b>
  synchronize on the <code>IndexWriter</code> instance as
  this may cause deadlock; use your own (non-Lucene) objects
  instead. </p>
  
  <p><b>NOTE</b>: If you call
  <code>Thread.interrupt()</code> on a thread that's within
  IndexWriter, IndexWriter will try to catch this (eg, if
  it's in a wait() or Thread.sleep()), and will then throw
  the unchecked exception {@code ThreadInterruptedException}
  and <b>clear</b> the interrupt status on the thread.</p>
*/

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

  private long writeLockTimeout;

  /**
   * Name of the write lock in the index.
   */
  public static final String WRITE_LOCK_NAME = "write.lock";

  /**
   * Default value is 10,000. Change using {@code #setMaxFieldLength(int)}.
   * 
   * @deprecated see {@code IndexWriterConfig}
   */
  @Deprecated
  public final static int DEFAULT_MAX_FIELD_LENGTH = MaxFieldLength.UNLIMITED.getLimit();

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

  // for testing
  boolean anyNonBulkMerges;

  /** Holds shared SegmentReader instances. IndexWriter uses
   *  SegmentReaders for 1) applying deletes, 2) doing
   *  merges, 3) handing out a real-time reader.  This pool
   *  reuses instances of the SegmentReaders in all these
   *  places if it is in "near real-time mode" (getReader()
   *  has been called on this instance). */

  class ReaderPool {

    private final Map<SegmentInfo,SegmentReader> readerMap = new HashMap<SegmentInfo,SegmentReader>();

    /** Forcefully clear changes for the specified segments.  This is called on successful merge. */
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

    /**
     * Release the segment reader (i.e. decRef it and close if there
     * are no more references.
     * @return true if this release altered the index (eg
     * the SegmentReader had pending changes to del docs and
     * was closed).  Caller must call checkpoint() if so.
     * @param sr ...
     * @throws IOException
     */
    public synchronized boolean release(SegmentReader sr) throws IOException {
      return release(sr, false);
    }
    
    /**
     * Release the segment reader (i.e. decRef it and close if there
     * are no more references.
     * @return true if this release altered the index (eg
     * the SegmentReader had pending changes to del docs and
     * was closed).  Caller must call checkpoint() if so.
     * @param sr ...
     * @throws IOException
     */
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

    /** Remove all our references to readers, and commits
     *  any pending changes. */
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
    
    /**
     * Commit all segment reader in the pool.
     * @throws IOException
     */
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
    
    /**
     * Returns a ref to a clone.  NOTE: this clone is not
     * enrolled in the pool, so you should simply close()
     * it when you're done (ie, do not call release()).
     */
    public synchronized SegmentReader getReadOnlyClone(SegmentInfo info, boolean doOpenStores, int termInfosIndexDivisor) throws IOException {
      SegmentReader sr = get(info, doOpenStores, BufferedIndexInput.BUFFER_SIZE, termInfosIndexDivisor);
      try {
        return (SegmentReader) sr.clone(true);
      } finally {
        sr.decRef();
      }
    }
   
    /**
     * Obtain a SegmentReader from the readerPool.  The reader
     * must be returned by calling {@code #release(SegmentReader)}
     *
     * @param info ...
     * @param doOpenStores ...
     * @throws IOException
     */
    public synchronized SegmentReader get(SegmentInfo info, boolean doOpenStores) throws IOException {
      return get(info, doOpenStores, BufferedIndexInput.BUFFER_SIZE, config.getReaderTermsIndexDivisor());
    }

    /**
     * Obtain a SegmentReader from the readerPool.  The reader
     * must be returned by calling {@code #release(SegmentReader)}
     * 
     *
     * @param info ...
     * @param doOpenStores ...
     * @param readBufferSize ...
     * @param termsIndexDivisor ...
     * @throws IOException
     */
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
  
  
  
  /**
   * Obtain the number of deleted docs for a pooled reader.
   * If the reader isn't being pooled, the segmentInfo's 
   * delCount is returned.
   */
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
  
  /**
   * Used internally to throw an {@code
   * AlreadyClosedException} if this IndexWriter has been
   * closed.
   * @throws AlreadyClosedException if this IndexWriter is closed
   */
  protected final void ensureOpen(boolean includePendingClose) throws AlreadyClosedException {
    if (closed || (includePendingClose && closing)) {
      throw new AlreadyClosedException("this IndexWriter is closed");
    }
  }

  protected final void ensureOpen() throws AlreadyClosedException {
    ensureOpen(true);
  }

  /**
   * Prints a message to the infoStream (if non-null),
   * prefixed with the identifying information for this
   * writer and the thread that's calling it.
   */
  public void message(String message) {
    if (infoStream != null)
      infoStream.println("IW " + messageID + " [" + new Date() + "; " + Thread.currentThread().getName() + "]: " + message);
  }

  /**
   * Constructs a new IndexWriter per the settings given in <code>conf</code>.
   * Note that the passed in {@code IndexWriterConfig} is
   * privately cloned; if you need to make subsequent "live"
   * changes to the configuration use {@code #getConfig}.
   * <p>
   * 
   * @param d
   *          the index directory. The index is either created or appended
   *          according <code>conf.getOpenMode()</code>.
   * @param conf
   *          the configuration settings according to which IndexWriter should
   *          be initialized.
   * @throws CorruptIndexException
   *           if the index is corrupt
   * @throws LockObtainFailedException
   *           if another writer has this index open (<code>write.lock</code>
   *           could not be obtained)
   * @throws IOException
   *           if the directory cannot be read/written to, or if it does not
   *           exist and <code>conf.getOpenMode()</code> is
   *           <code>OpenMode.APPEND</code> or if there is any other low-level
   *           IO error
   */
  public IndexWriter(Directory d, IndexWriterConfig conf)
      throws IOException {
    config = (IndexWriterConfig) conf.clone();
    directory = d;
    infoStream = null;
    writeLockTimeout = conf.getWriteLockTimeout();
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

  /**
   * Returns the private {@code IndexWriterConfig}, cloned
   * from the {@code IndexWriterConfig} passed to
   * {@code #IndexWriter(Directory, IndexWriterConfig)}.
   * <p>
   * <b>NOTE:</b> some settings may be changed on the
   * returned {@code IndexWriterConfig}, and will take
   * effect in the current IndexWriter instance.  See the
   * javadocs for the specific setters in {@code
   * IndexWriterConfig} for details.
   */
  public IndexWriterConfig getConfig() {
    ensureOpen(false);
    return config;
  }

  /** If non-null, information about merges, deletes and a
   * message when maxFieldLength is reached will be printed
   * to this.
   */
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

  /** Returns true if verbose is enabled (i.e., infoStream != null). */
  public boolean verbose() {
    return infoStream != null;
  }

  /**
   * Commits all changes to an index and closes all
   * associated files.  Note that this may be a costly
   * operation, so, try to re-use a single writer instead of
   * closing and opening a new one.  See {@code #commit()} for
   * caveats about write caching done by some IO devices.
   *
   * <p> If an Exception is hit during close, eg due to disk
   * full or some other reason, then both the on-disk index
   * and the internal state of the IndexWriter instance will
   * be consistent.  However, the close will not be complete
   * even though part of it (flushing buffered documents)
   * may have succeeded, so the write lock will still be
   * held.</p>
   * 
   * <p> If you can correct the underlying cause (eg free up
   * some disk space) then you can call close() again.
   * Failing that, if you want to force the write lock to be
   * released (dangerous, because you may then lose buffered
   * docs in the IndexWriter instance) then you can do
   * something like this:</p>
   *
   * <pre>
   * try {
   *   writer.close();
   * } finally {
   *   if (IndexWriter.isLocked(directory)) {
   *     IndexWriter.unlock(directory);
   *   }
   * }
   * </pre>
   *
   * after which, you must be certain not to use the writer
   * instance anymore.</p>
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer, again.  See <a
   * href="#OOME">above</a> for details.</p>
   *
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public void close() throws IOException {
    close(true);
  }

  /**
   * Closes the index with or without waiting for currently
   * running merges to finish.  This is only meaningful when
   * using a MergeScheduler that runs merges in background
   * threads.
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer, again.  See <a
   * href="#OOME">above</a> for details.</p>
   *
   * <p><b>NOTE</b>: it is dangerous to always call
   * close(false), especially when IndexWriter is not open
   * for very long, because this can result in "merge
   * starvation" whereby long merges will never have a
   * chance to finish.  This will cause too many segments in
   * your index over time.</p>
   *
   * @param waitForMerges if true, this call will block
   * until all merges complete; else, it will ask all
   * running merges to abort, wait until those merges have
   * finished (which should be at most a few seconds), and
   * then return.
   */
  public void close(boolean waitForMerges) throws IOException {

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

  private void closeInternal(boolean waitForMerges) throws IOException {

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

  /** Returns the Directory used by this index. */
  public Directory getDirectory() {     
    // Pass false because the flush during closing calls getDirectory
    ensureOpen(false);
    return directory;
  }

  /**
   * The maximum number of terms that will be indexed for a single field in a
   * document.  This limits the amount of memory required for indexing, so that
   * collections with very large files will not crash the indexing process by
   * running out of memory.<p/>
   * Note that this effectively truncates large documents, excluding from the
   * index terms that occur further in the document.  If you know your source
   * documents are large, be sure to set this value high enough to accommodate
   * the expected size.  If you set it to Integer.MAX_VALUE, then the only limit
   * is your memory, but you should anticipate an OutOfMemoryError.<p/>
   * By default, no more than 10,000 terms will be indexed for a field.
   *
   *
   * @deprecated remove in 4.0
   */
  @Deprecated
  private int maxFieldLength = DEFAULT_MAX_FIELD_LENGTH;

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

  /** If non-null, information about merges will be printed to this.
   */
  private PrintStream infoStream;

  /**
   * Forces merge policy to merge segments until there's <=
   * maxNumSegments.  The actual merges to be
   * executed are determined by the {@code MergePolicy}.
   *
   * <p>This is a horribly costly operation, especially when
   * you pass a small {@code maxNumSegments}; usually you
   * should only call this if the index is static (will no
   * longer be changed).</p>
   *
   * <p>Note that this requires up to 2X the index size free
   * space in your Directory (3X if you're using compound
   * file format).  For example, if your index size is 10 MB
   * then you need up to 20 MB free for this to complete (30
   * MB if you're using compound file format).  Also,
   * it's best to call {@code #commit()} afterwards,
   * to allow IndexWriter to free up disk space.</p>
   *
   * <p>If some but not all readers re-open while merging
   * is underway, this will cause > 2X temporary
   * space to be consumed as those new readers will then
   * hold open the temporary segments at that time.  It is
   * best not to re-open readers while merging is running.</p>
   *
   * <p>The actual temporary usage could be much less than
   * these figures (it depends on many factors).</p>
   *
   * <p>In general, once the this completes, the total size of the
   * index will be less than the size of the starting index.
   * It could be quite a bit smaller (if there were many
   * pending deletes) or just slightly smaller.</p>
   *
   * <p>If an Exception is hit, for example
   * due to disk full, the index will not be corrupt and no
   * documents will have been lost.  However, it may have
   * been partially merged (some segments were merged but
   * not all), and it's possible that one of the segments in
   * the index will be in non-compound format even when
   * using compound file format.  This will occur when the
   * Exception is hit during conversion of the segment into
   * compound format.</p>
   *
   * <p>This call will merge those segments present in
   * the index when the call started.  If other threads are
   * still adding documents and flushing segments, those
   * newly created segments will not be merged unless you
   * call forceMerge again.</p>
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer.  See <a
   * href="#OOME">above</a> for details.</p>
   *
   * <p><b>NOTE</b>: if you call {@code #close(boolean)}
   * with <tt>false</tt>, which aborts all running merges,
   * then any thread still running this method might hit a
   * {@code MergePolicy.MergeAbortedException}.
   *
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   *
   *
   * @param maxNumSegments maximum number of segments left
   * in the index after merging finishes
  */
  public void forceMerge(int maxNumSegments) throws IOException {
    forceMerge(maxNumSegments, true);
  }

  /** Just like {@code #forceMerge(int)}, except you can
   *  specify whether the call should block until
   *  all merging completes.  This is only meaningful with a
   *  {@code MergeScheduler} that is able to run merges in
   *  background threads.
   *
   *  <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   *  you should immediately close the writer.  See <a
   *  href="#OOME">above</a> for details.</p>
   */
  public void forceMerge(int maxNumSegments, boolean doWait) throws IOException {
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
            for (final MergePolicy.OneMerge merge : mergeExceptions) {
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

  /** Returns true if any merges in pendingMerges or
   *  runningMerges are maxNumSegments merges. */
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


  /**
   * Expert: asks the mergePolicy whether any merges are
   * necessary now and if so, runs the requested merges and
   * then iterate (test again if merges are needed) until no
   * more merges are returned by the mergePolicy.
   *
   * Explicit calls to maybeMerge() are usually not
   * necessary. The most common case is when merge policy
   * parameters have changed.
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer.  See <a
   * href="#OOME">above</a> for details.</p>
   */
  public final void maybeMerge() throws IOException {
    maybeMerge(-1);
  }

  private void maybeMerge(int maxNumSegments) throws IOException {
    ensureOpen(false);
    updatePendingMerges(maxNumSegments);
    mergeScheduler.merge(this);
  }

  private synchronized void updatePendingMerges(int maxNumSegments)
    throws IOException {
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

  /** Expert: to be used by a {@code MergePolicy} to avoid
   *  selecting merges for segments already being merged.
   *  The returned collection is not cloned, and thus is
   *  only safe to access if you hold IndexWriter's lock
   *  (which you do when IndexWriter invokes the
   *  MergePolicy).
   *
   *  <p>Do not alter the returned collection! */
  public synchronized Collection<SegmentInfo> getMergingSegments() {
    return mergingSegments;
  }

  /** Expert: the {@code MergeScheduler} calls this method
   *  to retrieve the next merge requested by the
   *  MergePolicy
   *
   * @lucene.experimental
   */
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

  /**
   * Wait for any currently outstanding merges to finish.
   *
   * <p>It is guaranteed that any merges started prior to calling this method 
   *    will have completed once this method completes.</p>
   */
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

  /**
   * Called whenever the SegmentInfos has been updated and
   * the index files referenced exist (correctly) in the
   * index directory.
   */
  synchronized void checkpoint() throws IOException {
    changeCount++;
    segmentInfos.changed();
    deleter.checkpoint(segmentInfos, false);
  }

  private synchronized void resetMergeExceptions() {
    mergeExceptions = new ArrayList<MergePolicy.OneMerge>();
    mergeGen++;
  }

  /**
   * A hook for extending classes to execute operations after pending added and
   * deleted documents have been flushed to the Directory but before the change
   * is committed (new segments_N file written).
   */
  protected void doAfterFlush() throws IOException {}

  /**
   * A hook for extending classes to execute operations before pending added and
   * deleted documents are flushed to the Directory.
   */
  protected void doBeforeFlush() throws IOException {}

  /** <p>Expert: prepare for commit, specifying
   *  commitUserData Map (String -> String).  This does the
   *  first phase of 2-phase commit. This method does all
   *  steps necessary to commit changes since this writer
   *  was opened: flushes pending added and deleted docs,
   *  syncs the index files, writes most of next segments_N
   *  file.  After calling this you must call either {@code
   *  #commit()} to finish the commit, or {@code
   *  #rollback()} to revert the commit and undo all changes
   *  done since the writer was opened.</p>
   * 
   *  <p>You can also just call {@code #commit(Map)} directly
   *  without prepareCommit first in which case that method
   *  will internally call prepareCommit.
   *
   *  <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   *  you should immediately close the writer.  See <a
   *  href="#OOME">above</a> for details.</p>
   *
   *  @param commitUserData Opaque Map (String->String)
   *  that's recorded into the segments file in the index,
   *  and retrievable by {@code
   *  IndexCommit#getUserData}.  Note that when
   *  IndexWriter commits itself during {@code #close}, the
   *  commitUserData is unchanged (just carried over from
   *  the prior commit).  If this is null then the previous
   *  commitUserData is kept.  Also, the commitUserData will
   *  only "stick" if there are actually changes in the
   *  index to commit.
   */
  public final void prepareCommit(Map<String, String> commitUserData)
      throws IOException {
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

  /**
   * <p>Commits all pending changes (added & deleted
   * documents, segment merges, added
   * indexes, etc.) to the index, and syncs all referenced
   * index files, such that a reader will see the changes
   * and the index updates will survive an OS or machine
   * crash or power loss.  Note that this does not wait for
   * any running background merges to finish.  This may be a
   * costly operation, so you should test the cost in your
   * application and do it only when really necessary.</p>
   *
   * <p> Note that this operation calls Directory.sync on
   * the index files.  That call should not return until the
   * file contents & metadata are on stable storage.  For
   * FSDirectory, this calls the OS's fsync.  But, beware:
   * some hardware devices may in fact cache writes even
   * during fsync, and return before the bits are actually
   * on stable storage, to give the appearance of faster
   * performance.  If you have such a device, and it does
   * not have a battery backup (for example) then on power
   * loss it may still lose data.  Lucene cannot guarantee
   * consistency on such devices.  </p>
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer.  See <a
   * href="#OOME">above</a> for details.</p>
   *
   *
   *
   */
  public final void commit() throws IOException {
    commit(null);
  }

  /** Commits all changes to the index, specifying a
   *  commitUserData Map (String -> String).  This just
   *  calls {@code #prepareCommit(Map)} (if you didn't
   *  already call it) and then {@code #finishCommit}.
   *
   * <p><b>NOTE</b>: if this method hits an OutOfMemoryError
   * you should immediately close the writer.  See <a
   * href="#OOME">above</a> for details.</p>
   */
  public final void commit(Map<String,String> commitUserData) throws IOException {

    ensureOpen();

    commitInternal(commitUserData);
  }

  private void commitInternal(Map<String,String> commitUserData) throws IOException {

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

  private synchronized void finishCommit() throws IOException {

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

  /**
   * Flush all in-memory buffered updates (adds and deletes)
   * to the Directory.
   * @param triggerMerge if true, we may merge segments (if
   *  deletes or docs were flushed) if necessary
   * @param applyAllDeletes whether pending deletes should also
   */
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
      maybeMerge();
    }
  }

  // TODO: this method should not have to be entirely
  // synchronized, ie, merges should be allowed to commit
  // even while a flush is happening
  private synchronized boolean doFlush(boolean applyAllDeletes) throws IOException {

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
             (long) 0 > (1024*1024*config.getRAMBufferSizeMB()/2))) {
          applyAllDeletes = true;
          if (infoStream != null) {
            message("force apply deletes bytesUsed=" + (long) 0 + " vs ramBuffer=" + (1024*1024*config.getRAMBufferSizeMB()));
          }
        }
      }

      if (applyAllDeletes) {
        if (infoStream != null) {
          message("apply all deletes during flush");
        }
        
        flushDeletesCount.incrementAndGet();
        final BufferedDeletesStream.ApplyDeletesResult result = bufferedDeletesStream
          .applyDeletes(segmentInfos.asList());
        if (result.anyDeletes) {
          checkpoint();
        }
        if (result.allDeleted != null) {
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

        assert true;
        flushControl.clearDeletes();
      } else if (infoStream != null) {
        message("don't apply deletes now delTermCount=" + 0 + " bytesUsed=" + (long) 0);
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

  private void ensureValidMerge(MergePolicy.OneMerge merge) throws IOException {
    for(SegmentInfo info : merge.segments) {
      if (!segmentInfos.contains(info)) {
        throw new MergePolicy.MergeException("MergePolicy selected a segment (" + info.name + ") that is not in the current index " + segString(), directory);
      }
    }
  }

  /** Carefully merges deletes for the segments we just
   *  merged.  This is tricky because, although merging will
   *  clear all deletes (compacts the documents), new
   *  deletes may have been flushed to the segments since
   *  the merge was started.  This method "carries over"
   *  such new deletes onto the newly merged segment, and
   *  saves the resulting deletes file (incrementing the
   *  delete generation for merge.info).  If no deletes were
   *  flushed, no new deletes file is saved. */
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
      message("merged segment " + merge.info + " is 100% deleted; skipping insert");
    }

    final boolean dropSegment = allDeleted;
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
  
  private void handleMergeException(Throwable t, MergePolicy.OneMerge merge) throws IOException {

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

  /**
   * Merges the indicated segments, replacing them in the stack with a
   * single segment.
   * 
   * @lucene.experimental
   */
  public void merge(MergePolicy.OneMerge merge)
    throws IOException {

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

  /** Hook that's called when the specified merge is complete. */
  void mergeSuccess(MergePolicy.OneMerge merge) {
  }
  
  /** Checks whether this merge involves any segments
   *  already participating in a merge.  If not, this merge
   *  is "registered", meaning we record that its segments
   *  are now participating in a merge, and true is
   *  returned.  Else (the merge conflicts) false is
   *  returned. */
  final synchronized boolean registerMerge(MergePolicy.OneMerge merge) throws IOException {

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

  /** Does initial setup for a merge, which is fast but holds
   *  the synchronized lock on IndexWriter instance.  */
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
    final BufferedDeletesStream.ApplyDeletesResult result = bufferedDeletesStream.applyDeletes(merge.segments);

    if (result.anyDeletes) {
      checkpoint();
    }

    if (result.allDeleted != null) {
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

  /** Does fininishing for a merge, which is fast but holds
   *  the synchronized lock on IndexWriter instance. */
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

  private synchronized void closeMergeReaders(MergePolicy.OneMerge merge, boolean suppressExceptions) throws IOException {
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

  /** Does the actual (time-consuming) work of the merge,
   *  but without holding synchronized lock on IndexWriter
   *  instance */
  private int mergeMiddle(MergePolicy.OneMerge merge)
    throws IOException {
    
    merge.checkAborted(directory);

    final String mergedName = merge.info.name;
    
    int mergedDocCount = 0;

    List<SegmentInfo> sourceSegments = merge.segments;

    SegmentMerger merger = new SegmentMerger(directory, config.getTermIndexInterval(), mergedName, merge,
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

  /** @lucene.internal */
  public synchronized String segString() throws IOException {
    return segString(segmentInfos);
  }

  /** @lucene.internal */
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

  /** @lucene.internal */
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

  boolean getKeepFullyDeletedSegments() {
    return false;
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

  /** Walk through all files referenced by the current
   *  segmentInfos and ask the Directory to sync each file,
   *  if it wasn't already.  If that succeeds, then we
   *  prepare a new segments_N file but do not fully commit
   *  it. */
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

  /**
   * Specifies maximum field length (in number of tokens/terms) in
   * {@code IndexWriter} constructors. {@code #setMaxFieldLength(int)} overrides
   * the value set by the constructor.
   * 
   * @deprecated use {@code LimitTokenCountAnalyzer} instead.
   */
  @Deprecated
  public static final class MaxFieldLength {

    private int limit;
    private String name;

    /**
     * Private type-safe-enum-pattern constructor.
     * 
     * @param name instance name
     * @param limit maximum field length
     */
    private MaxFieldLength(String name, int limit) {
      this.name = name;
      this.limit = limit;
    }

    public int getLimit() {
      return limit;
    }
    
    @Override
    public String toString()
    {
      return name + ":" + limit;
    }

    /** Sets the maximum field length to {@code Integer#MAX_VALUE}. */
    public static final MaxFieldLength UNLIMITED
        = new MaxFieldLength("UNLIMITED", Integer.MAX_VALUE);

  }

  /** If {@code #getReader} has been called (ie, this writer
   *  is in near real-time mode), then after a merge
   *  completes, this class can be invoked to warm the
   *  reader on the newly merged segment, before the merge
   *  commits.  This is not required for near real-time
   *  search, but will reduce search latency on opening a
   *  new near real-time reader after a merge completes.
   *
   * @lucene.experimental
   *
   * <p><b>NOTE</b>: warm is called before any deletes have
   * been carried over to the merged segment. */
  public static abstract class IndexReaderWarmer {
    public abstract void warm(IndexReader reader) throws IOException;
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

  synchronized boolean isClosed() {
    return closed;
  }

  // Called by DirectoryReader.doClose
  synchronized void deletePendingFiles() throws IOException {
    deleter.deletePendingFiles();
  }

  // decides when flushes happen
  final class FlushControl {

    private boolean flushPending;
    private boolean flushDeletes;

    private synchronized boolean setFlushPending(String reason, boolean doWait) {
      if (flushPending) {
        if (doWait) {
          while(flushPending) {
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
        return true;
      }
    }

    public synchronized void setFlushPendingNoWait(String reason) {
      setFlushPending(reason, false);
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
      notifyAll();
    }

    public synchronized void clearDeletes() {
    }

  }

  final FlushControl flushControl = new FlushControl();
}
