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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.MergeInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOConsumer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOFunction;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOSupplier;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ThreadInterruptedException;

/**
 * Expert: a MergePolicy determines the sequence of primitive merge operations.
 *
 * <p>Whenever the segments in an index have been altered by {@link IndexWriter}, either the
 * addition of a newly flushed segment, addition of many segments from addIndexes* calls, or a
 * previous merge that may now need to cascade, {@link IndexWriter} invokes {@link #findMerges} to
 * give the MergePolicy a chance to pick merges that are now required. This method returns a {@link
 * MergeSpecification} instance describing the set of merges that should be done, or null if no
 * merges are necessary. When IndexWriter.forceMerge is called, it calls {@link
 * #findForcedMerges(SegmentInfos, int, Map, MergeContext)} and the MergePolicy should then return
 * the necessary merges.
 *
 * <p>Note that the policy can return more than one merge at a time. In this case, if the writer is
 * using {@link SerialMergeScheduler}, the merges will be run sequentially but if it is using {@link
 * ConcurrentMergeScheduler} they will be run concurrently.
 *
 * <p>The default MergePolicy is {@link TieredMergePolicy}.
 *
 * @lucene.experimental
 */
public abstract class MergePolicy {

  /**
   * Progress and state for an executing merge. This class encapsulates the logic to pause and
   * resume the merge thread or to abort the merge entirely.
   *
   * @lucene.experimental
   */
  public static class OneMergeProgress {
    /** Reason for pausing the merge thread. */
    public enum PauseReason {
      /** Stopped (because of throughput rate set to 0, typically). */
      STOPPED,
      /** Temporarily paused because of exceeded throughput rate. */
      PAUSED,
      /** Other reason. */
      OTHER
    };

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pausing = pauseLock.newCondition();

    /** Pause times (in nanoseconds) for each {@link PauseReason}. */
    private final EnumMap<PauseReason, AtomicLong> pauseTimesNS;

    private volatile boolean aborted;

    /**
     * This field is for sanity-check purposes only. Only the same thread that invoked {@link
     * OneMerge#mergeInit()} is permitted to be calling {@link #pauseNanos}. This is always verified
     * at runtime.
     */
    private Thread owner;

    /** Creates a new merge progress info. */
    public OneMergeProgress() {
      // Place all the pause reasons in there immediately so that we can simply update values.
      pauseTimesNS = new EnumMap<PauseReason, AtomicLong>(PauseReason.class);
      for (PauseReason p : PauseReason.values()) {
        pauseTimesNS.put(p, new AtomicLong());
      }
    }

    /** Abort the merge this progress tracks at the next possible moment. */
    public void abort() {
      aborted = true;
      wakeup(); // wakeup any paused merge thread.
    }

    /** Return the aborted state of this merge. */
    public boolean isAborted() {
      return aborted;
    }

    /**
     * Pauses the calling thread for at least <code>pauseNanos</code> nanoseconds unless the merge
     * is aborted or the external condition returns <code>false</code>, in which case control
     * returns immediately.
     *
     * <p>The external condition is required so that other threads can terminate the pausing
     * immediately, before <code>pauseNanos</code> expires. We can't rely on just {@link
     * Condition#awaitNanos(long)} alone because it can return due to spurious wakeups too.
     *
     * @param condition The pause condition that should return false if immediate return from this
     *     method is needed. Other threads can wake up any sleeping thread by calling {@link
     *     #wakeup}, but it'd fall to sleep for the remainder of the requested time if this
     *     condition
     */
    public void pauseNanos(long pauseNanos, PauseReason reason, BooleanSupplier condition)
        throws InterruptedException {
      long start = System.nanoTime();
      AtomicLong timeUpdate = pauseTimesNS.get(reason);
      pauseLock.lock();
      try {
        while (pauseNanos > 0 && !aborted && condition.getAsBoolean()) {
          pauseNanos = pausing.awaitNanos(pauseNanos);
        }
      } finally {
        pauseLock.unlock();
        timeUpdate.addAndGet(System.nanoTime() - start);
      }
    }

    /** Request a wakeup for any threads stalled in {@link #pauseNanos}. */
    public void wakeup() {
      pauseLock.lock();
      try {
        pausing.signalAll();
      } finally {
        pauseLock.unlock();
      }
    }

    /** Returns pause reasons and associated times in nanoseconds. */
    public Map<PauseReason, Long> getPauseTimes() {
      Set<Entry<PauseReason, AtomicLong>> entries = pauseTimesNS.entrySet();
      return entries.stream()
          .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue().get()));
    }

    final void setMergeThread(Thread owner) {
      assert this.owner == null;
      this.owner = owner;
    }
  }

  /**
   * OneMerge provides the information necessary to perform an individual primitive merge operation,
   * resulting in a single new segment. The merge spec includes the subset of segments to be merged
   * as well as whether the new segment should use the compound file format.
   *
   * @lucene.experimental
   */
  public static class OneMerge {
    private final CompletableFuture<Boolean> mergeCompleted = new CompletableFuture<>();
    SegmentCommitInfo info; // used by IndexWriter
    boolean registerDone; // used by IndexWriter
    long mergeGen; // used by IndexWriter
    boolean isExternal; // used by IndexWriter
    int maxNumSegments = -1; // used by IndexWriter
    boolean usesPooledReaders; // used by IndexWriter to drop readers while closing

    /** Estimated size in bytes of the merged segment. */
    public volatile long estimatedMergeBytes; // used by IndexWriter

    // Sum of sizeInBytes of all SegmentInfos; set by IW.mergeInit
    volatile long totalMergeBytes;

    private List<MergeReader> mergeReaders; // used by IndexWriter

    /** Segments to be merged. */
    public final List<SegmentCommitInfo> segments;

    /** Control used to pause/stop/resume the merge thread. */
    private final OneMergeProgress mergeProgress;

    volatile long mergeStartNS = -1;

    /** Total number of documents in segments to be merged, not accounting for deletions. */
    final int totalMaxDoc;

    Throwable error;

    /**
     * Sole constructor.
     *
     * @param segments List of {@link SegmentCommitInfo}s to be merged.
     */
    public OneMerge(List<SegmentCommitInfo> segments) {
      if (0 == segments.size()) {
        throw new RuntimeException("segments must include at least one segment");
      }
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = List.copyOf(segments);
      totalMaxDoc = segments.stream().mapToInt(i -> i.info.maxDoc()).sum();
      mergeProgress = new OneMergeProgress();
      mergeReaders = List.of();
      usesPooledReaders = true;
    }

    /**
     * Create a OneMerge directly from CodecReaders. Used to merge incoming readers in {@link
     * IndexWriter#addIndexes(CodecReader...)}. This OneMerge works directly on readers and has an
     * empty segments list.
     *
     * @param codecReaders Codec readers to merge
     */
    public OneMerge(CodecReader... codecReaders) {
      List<MergeReader> readers = new ArrayList<>(codecReaders.length);
      int totalDocs = 0;
      for (CodecReader r : codecReaders) {
        readers.add(new MergeReader(r, r.getLiveDocs()));
        totalDocs += r.numDocs();
      }
      mergeReaders = List.copyOf(readers);
      segments = List.of();
      totalMaxDoc = totalDocs;
      mergeProgress = new OneMergeProgress();
      usesPooledReaders = false;
    }

    /** Constructor for wrapping. */
    protected OneMerge(OneMerge oneMerge) {
      this.segments = oneMerge.segments;
      this.mergeReaders = oneMerge.mergeReaders;
      this.totalMaxDoc = oneMerge.totalMaxDoc;
      this.mergeProgress = new OneMergeProgress();
      this.usesPooledReaders = oneMerge.usesPooledReaders;
    }

    /**
     * Called by {@link IndexWriter} after the merge started and from the thread that will be
     * executing the merge.
     */
    public void mergeInit() throws IOException {
      mergeProgress.setMergeThread(Thread.currentThread());
    }

    /**
     * Called by {@link IndexWriter} after the merge is done and all readers have been closed.
     *
     * @param success true iff the merge finished successfully ie. was committed
     * @param segmentDropped true iff the merged segment was dropped since it was fully deleted
     */
    public void mergeFinished(boolean success, boolean segmentDropped) throws IOException {}

    /** Closes this merge and releases all merge readers */
    final void close(
        boolean success, boolean segmentDropped, IOConsumer<MergeReader> readerConsumer)
        throws IOException {
      // this method is final to ensure we never miss a super call to cleanup and finish the merge
      if (mergeCompleted.complete(success) == false) {
        throw new IllegalStateException("merge has already finished");
      }
      try {
        mergeFinished(success, segmentDropped);
      } finally {
        final List<MergeReader> readers = mergeReaders;
        mergeReaders = List.of();
        IOUtils.applyToAll(readers, readerConsumer);
      }
    }

    /**
     * Wrap a reader prior to merging in order to add/remove fields or documents.
     *
     * <p><b>NOTE:</b> It is illegal to reorder doc IDs here, use {@link
     * #reorder(CodecReader,Directory,Executor)} instead.
     */
    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
      return reader;
    }

    /**
     * Extend this method if you wish to renumber doc IDs. This method will be called when index
     * sorting is disabled on a merged view of the {@link OneMerge}. A {@code null} return value
     * indicates that doc IDs should not be reordered.
     *
     * <p><b>NOTE:</b> Returning a non-null value here disables several optimizations and increases
     * the merging overhead.
     *
     * @param reader The reader to reorder.
     * @param dir The {@link Directory} of the index, which may be used to create temporary files.
     * @param executor An executor that can be used to parallelize the reordering logic. May be
     *     {@code null} if no concurrency is supported.
     * @lucene.experimental
     */
    public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor executor)
        throws IOException {
      return null;
    }

    /**
     * Expert: Sets the {@link SegmentCommitInfo} of the merged segment. Allows sub-classes to e.g.
     * {@link SegmentInfo#addDiagnostics(Map) add diagnostic} properties.
     */
    public void setMergeInfo(SegmentCommitInfo info) {
      this.info = info;
    }

    /**
     * Returns the {@link SegmentCommitInfo} for the merged segment, or null if it hasn't been set
     * yet.
     */
    public SegmentCommitInfo getMergeInfo() {
      return info;
    }

    /** Record that an exception occurred while executing this merge */
    synchronized void setException(Throwable error) {
      this.error = error;
    }

    /** Retrieve previous exception set by {@link #setException}. */
    synchronized Throwable getException() {
      return error;
    }

    /** Returns a readable description of the current merge state. */
    public String segString() {
      StringBuilder b = new StringBuilder();
      final int numSegments = segments.size();
      for (int i = 0; i < numSegments; i++) {
        if (i > 0) {
          b.append(' ');
        }
        b.append(segments.get(i).toString());
      }
      if (info != null) {
        b.append(" into ").append(info.info.name);
      }
      if (maxNumSegments != -1) {
        b.append(" [maxNumSegments=").append(maxNumSegments).append(']');
      }
      if (isAborted()) {
        b.append(" [ABORTED]");
      }
      return b.toString();
    }

    /**
     * Returns the total size in bytes of this merge. Note that this does not indicate the size of
     * the merged segment, but the input total size. This is only set once the merge is initialized
     * by IndexWriter.
     */
    public long totalBytesSize() {
      return totalMergeBytes;
    }

    /**
     * Returns the total number of documents that are included with this merge. Note that this does
     * not indicate the number of documents after the merge.
     */
    public int totalNumDocs() {
      return totalMaxDoc;
    }

    /** Return {@link MergeInfo} describing this merge. */
    public MergeInfo getStoreMergeInfo() {
      return new MergeInfo(totalMaxDoc, estimatedMergeBytes, isExternal, maxNumSegments);
    }

    /** Returns true if this merge was or should be aborted. */
    public boolean isAborted() {
      return mergeProgress.isAborted();
    }

    /**
     * Marks this merge as aborted. The merge thread should terminate at the soonest possible
     * moment.
     */
    public void setAborted() {
      this.mergeProgress.abort();
    }

    /** Checks if merge has been aborted and throws a merge exception if so. */
    public void checkAborted() throws MergeAbortedException {
      if (isAborted()) {
        throw new MergePolicy.MergeAbortedException("merge is aborted: " + segString());
      }
    }

    /**
     * Returns a {@link OneMergeProgress} instance for this merge, which provides statistics of the
     * merge threads (run time vs. sleep time) if merging is throttled.
     */
    public OneMergeProgress getMergeProgress() {
      return mergeProgress;
    }

    /**
     * Waits for this merge to be completed
     *
     * @return true if the merge finished within the specified timeout
     */
    boolean await(long timeout, TimeUnit timeUnit) {
      try {
        mergeCompleted.get(timeout, timeUnit);
        return true;
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (@SuppressWarnings("unused") ExecutionException | TimeoutException e) {
        return false;
      }
    }

    /**
     * Returns true if the merge has finished or false if it's still running or has not been
     * started. This method will not block.
     */
    boolean hasFinished() {
      return mergeCompleted.isDone();
    }

    /**
     * Returns true iff the merge completed successfully or false if the merge succeeded with a
     * failure. This method will not block and return an empty Optional if the merge has not
     * finished yet
     */
    Optional<Boolean> hasCompletedSuccessfully() {
      return Optional.ofNullable(mergeCompleted.getNow(null));
    }

    /** Called just before the merge is applied to IndexWriter's SegmentInfos */
    void onMergeComplete() throws IOException {}

    /** Sets the merge readers for this merge. */
    void initMergeReaders(IOFunction<SegmentCommitInfo, MergeReader> readerFactory)
        throws IOException {
      assert mergeReaders.isEmpty() : "merge readers must be empty";
      assert mergeCompleted.isDone() == false : "merge is already done";
      final ArrayList<MergeReader> readers = new ArrayList<>(segments.size());
      try {
        for (final SegmentCommitInfo info : segments) {
          // Hold onto the "live" reader; we will use this to
          // commit merged deletes
          readers.add(readerFactory.apply(info));
        }
      } finally {
        // ensure we assign this to close them in the case of an exception
        // we do a copy here to ensure that mergeReaders are an immutable list
        this.mergeReaders = List.copyOf(readers);
      }
    }

    /** Returns the merge readers or an empty list if the readers were not initialized yet. */
    List<MergeReader> getMergeReader() {
      return mergeReaders;
    }
  }

  /**
   * A MergeSpecification instance provides the information necessary to perform multiple merges. It
   * simply contains a list of {@link OneMerge} instances.
   */
  public static class MergeSpecification {

    /** The subset of segments to be included in the primitive merge. */
    public final List<OneMerge> merges = new ArrayList<>();

    /** Sole constructor. Use {@link #add(MergePolicy.OneMerge)} to add merges. */
    public MergeSpecification() {}

    /** Adds the provided {@link OneMerge} to this specification. */
    public void add(OneMerge merge) {
      merges.add(merge);
    }

    // TODO: deprecate me (dir is never used!  and is sometimes difficult to provide!)
    /** Returns a description of the merges in this specification. */
    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      b.append("MergeSpec:\n");
      final int count = merges.size();
      for (int i = 0; i < count; i++) {
        b.append("  ").append(1 + i).append(": ").append(merges.get(i).segString());
      }
      return b.toString();
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("MergeSpec:");
      final int count = merges.size();
      for (int i = 0; i < count; i++) {
        b.append("\n  ").append(1 + i).append(": ").append(merges.get(i).segString());
      }
      return b.toString();
    }

    CompletableFuture<Void> getMergeCompletedFutures() {
      return CompletableFuture.allOf(
          merges.stream()
              .map(m -> m.mergeCompleted)
              .collect(Collectors.toList())
              .toArray(CompletableFuture<?>[]::new));
    }

    /** Waits, until interrupted, for all merges to complete. */
    boolean await() {
      try {
        CompletableFuture<Void> future = getMergeCompletedFutures();
        future.get();
        return true;
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (@SuppressWarnings("unused") ExecutionException | CancellationException e) {
        return false;
      }
    }

    /** Waits if necessary for at most the given time for all merges. */
    boolean await(long timeout, TimeUnit unit) {
      try {
        CompletableFuture<Void> future = getMergeCompletedFutures();
        future.get(timeout, unit);
        return true;
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (@SuppressWarnings("unused") ExecutionException | TimeoutException e) {
        return false;
      }
    }
  }

  /** Exception thrown if there are any problems while executing a merge. */
  public static class MergeException extends RuntimeException {
    /** Create a {@code MergeException}. */
    public MergeException(String message) {
      super(message);
    }

    /** Create a {@code MergeException}. */
    public MergeException(Throwable exc) {
      super(exc);
    }
  }

  /**
   * Thrown when a merge was explicitly aborted because {@link IndexWriter#abortMerges} was called.
   * Normally this exception is privately caught and suppressed by {@link IndexWriter}.
   */
  public static class MergeAbortedException extends IOException {
    /** Create a {@link MergeAbortedException}. */
    public MergeAbortedException() {
      super("merge is aborted");
    }

    /** Create a {@link MergeAbortedException} with a specified message. */
    public MergeAbortedException(String message) {
      super(message);
    }
  }

  /**
   * Default ratio for compound file system usage. Set to <code>1.0</code>, always use compound file
   * system.
   */
  protected static final double DEFAULT_NO_CFS_RATIO = 1.0;

  /**
   * Default max segment size in order to use compound file system. Set to {@link Long#MAX_VALUE}.
   */
  protected static final long DEFAULT_MAX_CFS_SEGMENT_SIZE = Long.MAX_VALUE;

  /**
   * If the size of the merge segment exceeds this ratio of the total index size then it will remain
   * in non-compound format
   */
  protected double noCFSRatio;

  /**
   * If the size of the merged segment exceeds this value then it will not use compound file format.
   */
  protected long maxCFSSegmentSize;

  /** Creates a new merge policy instance. */
  protected MergePolicy() {
    this(DEFAULT_NO_CFS_RATIO, DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }

  /**
   * Creates a new merge policy instance with default settings for noCFSRatio and maxCFSSegmentSize.
   * This ctor should be used by subclasses using different defaults than the {@link MergePolicy}
   */
  protected MergePolicy(double defaultNoCFSRatio, long defaultMaxCFSSegmentSize) {
    this.noCFSRatio = defaultNoCFSRatio;
    this.maxCFSSegmentSize = defaultMaxCFSSegmentSize;
  }

  /**
   * Determine what set of merge operations are now necessary on the index. {@link IndexWriter}
   * calls this whenever there is a change to the segments. This call is always synchronized on the
   * {@link IndexWriter} instance so only one thread at a time will call this method.
   *
   * @param mergeTrigger the event that triggered the merge
   * @param segmentInfos the total set of segments in the index
   * @param mergeContext the IndexWriter to find the merges on
   */
  public abstract MergeSpecification findMerges(
      MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
      throws IOException;

  /**
   * Define the set of merge operations to perform on provided codec readers in {@link
   * IndexWriter#addIndexes(CodecReader...)}.
   *
   * <p>The merge operation is required to convert provided readers into segments that can be added
   * to the writer. This API can be overridden in custom merge policies to control the concurrency
   * for addIndexes. Default implementation creates a single merge operation for all provided
   * readers (lowest concurrency). Creating a merge for each reader, would provide the highest level
   * of concurrency possible with the configured merge scheduler.
   *
   * @param readers CodecReader(s) to merge into the main index
   */
  public MergeSpecification findMerges(CodecReader... readers) throws IOException {
    MergeSpecification mergeSpec = new MergeSpecification();
    mergeSpec.add(new OneMerge(readers));
    return mergeSpec;
  }

  /**
   * Determine what set of merge operations is necessary in order to merge to {@code <=} the
   * specified segment count. {@link IndexWriter} calls this when its {@link IndexWriter#forceMerge}
   * method is called. This call is always synchronized on the {@link IndexWriter} instance so only
   * one thread at a time will call this method.
   *
   * @param segmentInfos the total set of segments in the index
   * @param maxSegmentCount requested maximum number of segments in the index
   * @param segmentsToMerge contains the specific SegmentInfo instances that must be merged away.
   *     This may be a subset of all SegmentInfos. If the value is True for a given SegmentInfo,
   *     that means this segment was an original segment present in the to-be-merged index; else, it
   *     was a segment produced by a cascaded merge.
   * @param mergeContext the MergeContext to find the merges on
   */
  public abstract MergeSpecification findForcedMerges(
      SegmentInfos segmentInfos,
      int maxSegmentCount,
      Map<SegmentCommitInfo, Boolean> segmentsToMerge,
      MergeContext mergeContext)
      throws IOException;

  /**
   * Determine what set of merge operations is necessary in order to expunge all deletes from the
   * index.
   *
   * @param segmentInfos the total set of segments in the index
   * @param mergeContext the MergeContext to find the merges on
   */
  public abstract MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException;

  /**
   * Identifies merges that we want to execute (synchronously) on commit. By default, this will
   * return {@link #findMerges natural merges} whose segments are all less than the {@link
   * #maxFullFlushMergeSize() max segment size for full flushes}.
   *
   * <p>Any merges returned here will make {@link IndexWriter#commit()}, {@link
   * IndexWriter#prepareCommit()} or {@link IndexWriter#getReader(boolean, boolean)} block until the
   * merges complete or until {@link IndexWriterConfig#getMaxFullFlushMergeWaitMillis()} has
   * elapsed. This may be used to merge small segments that have just been flushed, reducing the
   * number of segments in the point in time snapshot. If a merge does not complete in the allotted
   * time, it will continue to execute, and eventually finish and apply to future point in time
   * snapshot, but will not be reflected in the current one.
   *
   * <p>If a {@link OneMerge} in the returned {@link MergeSpecification} includes a segment already
   * included in a registered merge, then {@link IndexWriter#commit()} or {@link
   * IndexWriter#prepareCommit()} will throw a {@link IllegalStateException}. Use {@link
   * MergeContext#getMergingSegments()} to determine which segments are currently registered to
   * merge.
   *
   * @param mergeTrigger the event that triggered the merge (COMMIT or GET_READER).
   * @param segmentInfos the total set of segments in the index (while preparing the commit)
   * @param mergeContext the MergeContext to find the merges on, which should be used to determine
   *     which segments are already in a registered merge (see {@link
   *     MergeContext#getMergingSegments()}).
   */
  public MergeSpecification findFullFlushMerges(
      MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
      throws IOException {
    // This returns natural merges that contain segments below the minimum size
    MergeSpecification mergeSpec = findMerges(mergeTrigger, segmentInfos, mergeContext);
    if (mergeSpec == null) {
      return null;
    }
    MergeSpecification newMergeSpec = null;
    for (OneMerge oneMerge : mergeSpec.merges) {
      boolean belowMaxFullFlushSize = true;
      for (SegmentCommitInfo sci : oneMerge.segments) {
        if (size(sci, mergeContext) >= maxFullFlushMergeSize()) {
          belowMaxFullFlushSize = false;
          break;
        }
      }
      if (belowMaxFullFlushSize) {
        if (newMergeSpec == null) {
          newMergeSpec = new MergeSpecification();
        }
        newMergeSpec.add(oneMerge);
      }
    }
    return newMergeSpec;
  }

  /**
   * Returns true if a new segment (regardless of its origin) should use the compound file format.
   * The default implementation returns <code>true</code> iff the size of the given mergedInfo is
   * less or equal to {@link #getMaxCFSSegmentSizeMB()} and the size is less or equal to the
   * TotalIndexSize * {@link #getNoCFSRatio()} otherwise <code>false</code>.
   */
  public boolean useCompoundFile(
      SegmentInfos infos, SegmentCommitInfo mergedInfo, MergeContext mergeContext)
      throws IOException {
    if (getNoCFSRatio() == 0.0) {
      return false;
    }
    long mergedInfoSize = size(mergedInfo, mergeContext);
    if (mergedInfoSize > maxCFSSegmentSize) {
      return false;
    }
    if (getNoCFSRatio() >= 1.0) {
      return true;
    }
    long totalSize = 0;
    for (SegmentCommitInfo info : infos) {
      totalSize += size(info, mergeContext);
    }
    return mergedInfoSize <= getNoCFSRatio() * totalSize;
  }

  /**
   * Return the byte size of the provided {@link SegmentCommitInfo}, pro-rated by percentage of
   * non-deleted documents is set.
   */
  protected long size(SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    long byteSize = info.sizeInBytes();
    int delCount = mergeContext.numDeletesToMerge(info);
    assert assertDelCount(delCount, info);
    double delRatio =
        info.info.maxDoc() <= 0 ? 0d : (double) delCount / (double) info.info.maxDoc();
    assert delRatio <= 1.0;
    return (info.info.maxDoc() <= 0 ? byteSize : (long) (byteSize * (1.0 - delRatio)));
  }

  /**
   * Return the maximum size of segments to be included in full-flush merges by the default
   * implementation of {@link #findFullFlushMerges}.
   */
  protected long maxFullFlushMergeSize() {
    return 0L;
  }

  /** Asserts that the delCount for this SegmentCommitInfo is valid */
  protected final boolean assertDelCount(int delCount, SegmentCommitInfo info) {
    assert delCount >= 0 : "delCount must be positive: " + delCount;
    assert delCount <= info.info.maxDoc()
        : "delCount: " + delCount + " must be leq than maxDoc: " + info.info.maxDoc();
    return true;
  }

  /**
   * Returns true if this single info is already fully merged (has no pending deletes, is in the
   * same dir as the writer, and matches the current compound file setting
   */
  protected final boolean isMerged(
      SegmentInfos infos, SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    assert mergeContext != null;
    int delCount = mergeContext.numDeletesToMerge(info);
    assert assertDelCount(delCount, info);
    return delCount == 0
        && useCompoundFile(infos, info, mergeContext) == info.info.getUseCompoundFile();
  }

  /**
   * Returns current {@code noCFSRatio}.
   *
   * @see #setNoCFSRatio
   */
  public double getNoCFSRatio() {
    return noCFSRatio;
  }

  /**
   * If a merged segment will be more than this percentage of the total size of the index, leave the
   * segment as non-compound file even if compound file is enabled. Set to 1.0 to always use CFS
   * regardless of merge size.
   */
  public void setNoCFSRatio(double noCFSRatio) {
    if (noCFSRatio < 0.0 || noCFSRatio > 1.0) {
      throw new IllegalArgumentException(
          "noCFSRatio must be 0.0 to 1.0 inclusive; got " + noCFSRatio);
    }
    this.noCFSRatio = noCFSRatio;
  }

  /** Returns the largest size allowed for a compound file segment */
  public double getMaxCFSSegmentSizeMB() {
    return maxCFSSegmentSize / 1024. / 1024.;
  }

  /**
   * If a merged segment will be more than this value, leave the segment as non-compound file even
   * if compound file is enabled. Set this to Double.POSITIVE_INFINITY (default) and noCFSRatio to
   * 1.0 to always use CFS regardless of merge size.
   */
  public void setMaxCFSSegmentSizeMB(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("maxCFSSegmentSizeMB must be >=0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    this.maxCFSSegmentSize = v > Long.MAX_VALUE ? Long.MAX_VALUE : (long) v;
  }

  /**
   * Returns true if the segment represented by the given CodecReader should be keep even if it's
   * fully deleted. This is useful for testing of for instance if the merge policy implements
   * retention policies for soft deletes.
   */
  public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier)
      throws IOException {
    return false;
  }

  /**
   * Returns the number of deletes that a merge would claim on the given segment. This method will
   * by default return the sum of the del count on disk and the pending delete count. Yet,
   * subclasses that wrap merge readers might modify this to reflect deletes that are carried over
   * to the target segment in the case of soft deletes.
   *
   * <p>Soft deletes all deletes to survive across merges in order to control when the soft-deleted
   * data is claimed.
   *
   * @see IndexWriter#softUpdateDocument(Term, Iterable, Field...)
   * @see IndexWriterConfig#setSoftDeletesField(String)
   * @param info the segment info that identifies the segment
   * @param delCount the number deleted documents for this segment
   * @param readerSupplier a supplier that allows to obtain a {@link CodecReader} for this segment
   */
  public int numDeletesToMerge(
      SegmentCommitInfo info, int delCount, IOSupplier<CodecReader> readerSupplier)
      throws IOException {
    return delCount;
  }

  /** Builds a String representation of the given SegmentCommitInfo instances */
  protected final String segString(MergeContext mergeContext, Iterable<SegmentCommitInfo> infos) {
    return StreamSupport.stream(infos.spliterator(), false)
        .map(info -> info.toString(mergeContext.numDeletedDocs(info) - info.getDelCount()))
        .collect(Collectors.joining(" "));
  }

  /** Print a debug message to {@link MergeContext}'s {@code infoStream}. */
  protected final void message(String message, MergeContext mergeContext) {
    if (verbose(mergeContext)) {
      mergeContext.getInfoStream().message("MP", message);
    }
  }

  /**
   * Returns <code>true</code> if the info-stream is in verbose mode
   *
   * @see #message(String, MergeContext)
   */
  protected final boolean verbose(MergeContext mergeContext) {
    return mergeContext.getInfoStream().isEnabled("MP");
  }

  /**
   * This interface represents the current context of the merge selection process. It allows to
   * access real-time information like the currently merging segments or how many deletes a segment
   * would claim back if merged. This context might be stateful and change during the execution of a
   * merge policy's selection processes.
   *
   * @lucene.experimental
   */
  public interface MergeContext {

    /**
     * Returns the number of deletes a merge would claim back if the given segment is merged.
     *
     * @see MergePolicy#numDeletesToMerge(SegmentCommitInfo, int, org.apache.lucene.util.IOSupplier)
     * @param info the segment to get the number of deletes for
     */
    int numDeletesToMerge(SegmentCommitInfo info) throws IOException;

    /** Returns the number of deleted documents in the given segments. */
    int numDeletedDocs(SegmentCommitInfo info);

    /** Returns the info stream that can be used to log messages */
    InfoStream getInfoStream();

    /** Returns an unmodifiable set of segments that are currently merging. */
    Set<SegmentCommitInfo> getMergingSegments();
  }

  static final class MergeReader {
    final CodecReader codecReader;
    final SegmentReader reader;
    final Bits hardLiveDocs;

    MergeReader(SegmentReader reader, Bits hardLiveDocs) {
      this.codecReader = reader;
      this.reader = reader;
      this.hardLiveDocs = hardLiveDocs;
    }

    MergeReader(CodecReader reader, Bits hardLiveDocs) {
      if (SegmentReader.class.isAssignableFrom(reader.getClass())) {
        this.reader = (SegmentReader) reader;
      } else {
        this.reader = null;
      }
      this.codecReader = reader;
      this.hardLiveDocs = hardLiveDocs;
    }
  }
}
