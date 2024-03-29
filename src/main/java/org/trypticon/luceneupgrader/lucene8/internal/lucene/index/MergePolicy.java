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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.MergeInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.IOSupplier;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ThreadInterruptedException;

public abstract class MergePolicy {

  public static class OneMergeProgress {
    public enum PauseReason {
      STOPPED,
      PAUSED,
      OTHER
    };

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pausing = pauseLock.newCondition();

    private final EnumMap<PauseReason, AtomicLong> pauseTimesNS;
    
    private volatile boolean aborted;

    private Thread owner;

    public OneMergeProgress() {
      // Place all the pause reasons in there immediately so that we can simply update values.
      pauseTimesNS = new EnumMap<PauseReason,AtomicLong>(PauseReason.class);
      for (PauseReason p : PauseReason.values()) {
        pauseTimesNS.put(p, new AtomicLong());
      }
    }

    public void abort() {
      aborted = true;
      wakeup(); // wakeup any paused merge thread.
    }

    public boolean isAborted() {
      return aborted;
    }

    public void pauseNanos(long pauseNanos, PauseReason reason, BooleanSupplier condition) throws InterruptedException {
      if (Thread.currentThread() != owner) {
        throw new RuntimeException("Only the merge owner thread can call pauseNanos(). This thread: "
            + Thread.currentThread().getName() + ", owner thread: "
            + owner);
      }

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

    public void wakeup() {
      pauseLock.lock();
      try {
        pausing.signalAll();
      } finally {
        pauseLock.unlock();
      }
    }

    public Map<PauseReason,Long> getPauseTimes() {
      Set<Entry<PauseReason,AtomicLong>> entries = pauseTimesNS.entrySet();
      return entries.stream()
          .collect(Collectors.toMap(
              (e) -> e.getKey(),
              (e) -> e.getValue().get()));
    }

    final void setMergeThread(Thread owner) {
      assert this.owner == null;
      this.owner = owner;
    }
  }

  public static class OneMerge {
    private final CompletableFuture<Boolean> mergeCompleted = new CompletableFuture<>();
    SegmentCommitInfo info;         // used by IndexWriter
    boolean registerDone;           // used by IndexWriter
    long mergeGen;                  // used by IndexWriter
    boolean isExternal;             // used by IndexWriter
    int maxNumSegments = -1;        // used by IndexWriter

    public volatile long estimatedMergeBytes;       // used by IndexWriter

    // Sum of sizeInBytes of all SegmentInfos; set by IW.mergeInit
    volatile long totalMergeBytes;

    private List<MergeReader> mergeReaders;        // used by IndexWriter

    public final List<SegmentCommitInfo> segments;

    private final OneMergeProgress mergeProgress;

    volatile long mergeStartNS = -1;

    final int totalMaxDoc;
    Throwable error;

    public OneMerge(List<SegmentCommitInfo> segments) {
      if (0 == segments.size()) {
        throw new RuntimeException("segments must include at least one segment");
      }
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = new ArrayList<>(segments);
      totalMaxDoc = segments.stream().mapToInt(i -> i.info.maxDoc()).sum();
      mergeProgress = new OneMergeProgress();
      mergeReaders = Collections.emptyList();
    }

    public void mergeInit() throws IOException {
      mergeProgress.setMergeThread(Thread.currentThread());
    }

    public void mergeFinished(boolean success, boolean segmentDropped) throws IOException {
    }

    final void close(boolean success, boolean segmentDropped, IOUtils.IOConsumer<MergeReader> readerConsumer) throws IOException {
      // this method is final to ensure we never miss a super call to cleanup and finish the merge
      if (mergeCompleted.complete(success) == false) {
        throw new IllegalStateException("merge has already finished");
      }
      try {
        mergeFinished(success, segmentDropped);
      } finally {
        final List<MergeReader> readers = mergeReaders;
        mergeReaders = Collections.emptyList();
        IOUtils.applyToAll(readers, readerConsumer);
      }
    }

    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
      return reader;
    }

    public void setMergeInfo(SegmentCommitInfo info) {
      this.info = info;
    }

    public SegmentCommitInfo getMergeInfo() {
      return info;
    }

    synchronized void setException(Throwable error) {
      this.error = error;
    }

    synchronized Throwable getException() {
      return error;
    }

    public String segString() {
      StringBuilder b = new StringBuilder();
      final int numSegments = segments.size();
      for(int i=0;i<numSegments;i++) {
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
    
    public long totalBytesSize() {
      return totalMergeBytes;
    }

    public int totalNumDocs() {
      int total = 0;
      for (SegmentCommitInfo info : segments) {
        total += info.info.maxDoc();
      }
      return total;
    }

    public MergeInfo getStoreMergeInfo() {
      return new MergeInfo(totalMaxDoc, estimatedMergeBytes, isExternal, maxNumSegments);
    }

    public boolean isAborted() {
      return mergeProgress.isAborted();
    }

    public void setAborted() {
      this.mergeProgress.abort();
    }

    public void checkAborted() throws MergeAbortedException {
      if (isAborted()) {
        throw new MergePolicy.MergeAbortedException("merge is aborted: " + segString());
      }
    }

    public OneMergeProgress getMergeProgress() {
      return mergeProgress;
    }

    boolean await(long timeout, TimeUnit timeUnit) {
      try {
        mergeCompleted.get(timeout, timeUnit);
        return true;
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (ExecutionException | TimeoutException e) {
        return false;
      }
    }

    boolean hasFinished() {
      return mergeCompleted.isDone();
    }

    Optional<Boolean> hasCompletedSuccessfully() {
      return Optional.ofNullable(mergeCompleted.getNow(null));
    }


    void onMergeComplete() throws IOException {
    }

    void initMergeReaders(IOUtils.IOFunction<SegmentCommitInfo, MergeReader> readerFactory) throws IOException {
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
        this.mergeReaders = Collections.unmodifiableList(readers);
      }
    }

    List<MergeReader> getMergeReader() {
      return mergeReaders;
    }

  }


  public static class MergeSpecification {


    public final List<OneMerge> merges = new ArrayList<>();

    public MergeSpecification() {
    }

    public void add(OneMerge merge) {
      merges.add(merge);
    }

    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      b.append("MergeSpec:\n");
      final int count = merges.size();
      for(int i=0;i<count;i++) {
        b.append("  ").append(1 + i).append(": ").append(merges.get(i).segString());
      }
      return b.toString();
    }

    boolean await(long timeout, TimeUnit unit) {
      try {
        CompletableFuture<Void> future = CompletableFuture.allOf(merges.stream()
            .map(m -> m.mergeCompleted).collect(Collectors.toList()).toArray(new CompletableFuture<?>[merges.size()]));
        future.get(timeout, unit);
        return true;
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (ExecutionException | TimeoutException e) {
        return false;
      }
    }
  }

  public static class MergeException extends RuntimeException {
    public MergeException(String message) {
      super(message);
    }

    public MergeException(Throwable exc) {
      super(exc);
    }
  }

  public static class MergeAbortedException extends IOException {
    public MergeAbortedException() {
      super("merge is aborted");
    }

    public MergeAbortedException(String message) {
      super(message);
    }
  }
  
  protected static final double DEFAULT_NO_CFS_RATIO = 1.0;

  protected static final long DEFAULT_MAX_CFS_SEGMENT_SIZE = Long.MAX_VALUE;

  protected double noCFSRatio = DEFAULT_NO_CFS_RATIO;
  
  protected long maxCFSSegmentSize = DEFAULT_MAX_CFS_SEGMENT_SIZE;

  public MergePolicy() {
    this(DEFAULT_NO_CFS_RATIO, DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }
  
  protected MergePolicy(double defaultNoCFSRatio, long defaultMaxCFSSegmentSize) {
    this.noCFSRatio = defaultNoCFSRatio;
    this.maxCFSSegmentSize = defaultMaxCFSSegmentSize;
  }

  public abstract MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
      throws IOException;

  public abstract MergeSpecification findForcedMerges(
      SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge, MergeContext mergeContext)
      throws IOException;

  public abstract MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException;

  public MergeSpecification findFullFlushMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
    return null;
  }

  public boolean useCompoundFile(SegmentInfos infos, SegmentCommitInfo mergedInfo, MergeContext mergeContext) throws IOException {
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
  
  protected long size(SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    long byteSize = info.sizeInBytes();
    int delCount = mergeContext.numDeletesToMerge(info);
    assert assertDelCount(delCount, info);
    double delRatio = info.info.maxDoc() <= 0 ? 0d : (double) delCount / (double) info.info.maxDoc();
    assert delRatio <= 1.0;
    return (info.info.maxDoc() <= 0 ? byteSize : (long) (byteSize * (1.0 - delRatio)));
  }

  protected final boolean assertDelCount(int delCount, SegmentCommitInfo info) {
    assert delCount >= 0: "delCount must be positive: " + delCount;
    assert delCount <= info.info.maxDoc() : "delCount: " + delCount
        + " must be leq than maxDoc: " + info.info.maxDoc();
    return true;
  }
  
  protected final boolean isMerged(SegmentInfos infos, SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    assert mergeContext != null;
    int delCount = mergeContext.numDeletesToMerge(info);
    assert assertDelCount(delCount, info);
    return delCount == 0 &&
      useCompoundFile(infos, info, mergeContext) == info.info.getUseCompoundFile();
  }
  
  public double getNoCFSRatio() {
    return noCFSRatio;
  }

  public void setNoCFSRatio(double noCFSRatio) {
    if (noCFSRatio < 0.0 || noCFSRatio > 1.0) {
      throw new IllegalArgumentException("noCFSRatio must be 0.0 to 1.0 inclusive; got " + noCFSRatio);
    }
    this.noCFSRatio = noCFSRatio;
  }

  public double getMaxCFSSegmentSizeMB() {
    return maxCFSSegmentSize/1024/1024.;
  }

  public void setMaxCFSSegmentSizeMB(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("maxCFSSegmentSizeMB must be >=0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    this.maxCFSSegmentSize = v > Long.MAX_VALUE ? Long.MAX_VALUE : (long) v;
  }

  public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier) throws IOException {
    return false;
  }

  public int numDeletesToMerge(SegmentCommitInfo info, int delCount,
                               IOSupplier<CodecReader> readerSupplier) throws IOException {
    return delCount;
  }

  protected final String segString(MergeContext mergeContext, Iterable<SegmentCommitInfo> infos) {
    return StreamSupport.stream(infos.spliterator(), false)
        .map(info -> info.toString(mergeContext.numDeletedDocs(info) - info.getDelCount()))
        .collect(Collectors.joining(" "));
  }

  protected final void message(String message, MergeContext mergeContext) {
    if (verbose(mergeContext)) {
      mergeContext.getInfoStream().message("MP", message);
    }
  }

  protected final boolean verbose(MergeContext mergeContext) {
    return mergeContext.getInfoStream().isEnabled("MP");
  }

  public interface MergeContext {

    int numDeletesToMerge(SegmentCommitInfo info) throws IOException;

    int numDeletedDocs(SegmentCommitInfo info);

    InfoStream getInfoStream();

    Set<SegmentCommitInfo> getMergingSegments();
  }

  final static class MergeReader {
    final SegmentReader reader;
    final Bits hardLiveDocs;

    MergeReader(SegmentReader reader, Bits hardLiveDocs) {
      this.reader = reader;
      this.hardLiveDocs = hardLiveDocs;
    }
  }
}
