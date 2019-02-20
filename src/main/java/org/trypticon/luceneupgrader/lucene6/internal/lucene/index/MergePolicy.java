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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.MergeInfo;

public abstract class MergePolicy {

  public static class OneMergeProgress {
    public static enum PauseReason {
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
    SegmentCommitInfo info;         // used by IndexWriter
    boolean registerDone;           // used by IndexWriter
    long mergeGen;                  // used by IndexWriter
    boolean isExternal;             // used by IndexWriter
    int maxNumSegments = -1;        // used by IndexWriter

    public volatile long estimatedMergeBytes;       // used by IndexWriter

    // Sum of sizeInBytes of all SegmentInfos; set by IW.mergeInit
    volatile long totalMergeBytes;

    List<SegmentReader> readers;        // used by IndexWriter

    public final List<SegmentCommitInfo> segments;

    private final OneMergeProgress mergeProgress;

    volatile long mergeStartNS = -1;

    public final int totalMaxDoc;
    Throwable error;


    public OneMerge(List<SegmentCommitInfo> segments) {
      if (0 == segments.size()) {
        throw new RuntimeException("segments must include at least one segment");
      }
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = new ArrayList<>(segments);
      int count = 0;
      for(SegmentCommitInfo info : segments) {
        count += info.info.maxDoc();
      }
      totalMaxDoc = count;

      mergeProgress = new OneMergeProgress();
    }

    public void mergeInit() throws IOException {
      mergeProgress.setMergeThread(Thread.currentThread());
    }
    
    public void mergeFinished() throws IOException {
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
        b.append(" [maxNumSegments=" + maxNumSegments + "]");
      }
      if (isAborted()) {
        b.append(" [ABORTED]");
      }
      return b.toString();
    }
    
    public long totalBytesSize() throws IOException {
      return totalMergeBytes;
    }


    public int totalNumDocs() throws IOException {
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
  }

  public static class MergeException extends RuntimeException {
    private Directory dir;

    public MergeException(String message, Directory dir) {
      super(message);
      this.dir = dir;
    }

    public MergeException(Throwable exc, Directory dir) {
      super(exc);
      this.dir = dir;
    }

    public Directory getDirectory() {
      return dir;
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

  public abstract MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos, IndexWriter writer)
      throws IOException;

  public abstract MergeSpecification findForcedMerges(
          SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge, IndexWriter writer)
      throws IOException;

  public abstract MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, IndexWriter writer) throws IOException;

  public boolean useCompoundFile(SegmentInfos infos, SegmentCommitInfo mergedInfo, IndexWriter writer) throws IOException {
    if (getNoCFSRatio() == 0.0) {
      return false;
    }
    long mergedInfoSize = size(mergedInfo, writer);
    if (mergedInfoSize > maxCFSSegmentSize) {
      return false;
    }
    if (getNoCFSRatio() >= 1.0) {
      return true;
    }
    long totalSize = 0;
    for (SegmentCommitInfo info : infos) {
      totalSize += size(info, writer);
    }
    return mergedInfoSize <= getNoCFSRatio() * totalSize;
  }
  

  protected long size(SegmentCommitInfo info, IndexWriter writer) throws IOException {
    long byteSize = info.sizeInBytes();
    int delCount = writer.numDeletedDocs(info);
    double delRatio = info.info.maxDoc() <= 0 ? 0.0f : (float) delCount / (float) info.info.maxDoc();
    assert delRatio <= 1.0;
    return (info.info.maxDoc() <= 0 ? byteSize : (long) (byteSize * (1.0 - delRatio)));
  }
  

  protected final boolean isMerged(SegmentInfos infos, SegmentCommitInfo info, IndexWriter writer) throws IOException {
    assert writer != null;
    boolean hasDeletions = writer.numDeletedDocs(info) > 0;
    return !hasDeletions &&
      info.info.dir == writer.getDirectory() &&
      useCompoundFile(infos, info, writer) == info.info.getUseCompoundFile();
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

  public final double getMaxCFSSegmentSizeMB() {
    return maxCFSSegmentSize/1024/1024.;
  }


  public void setMaxCFSSegmentSizeMB(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("maxCFSSegmentSizeMB must be >=0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    this.maxCFSSegmentSize = v > Long.MAX_VALUE ? Long.MAX_VALUE : (long) v;
  }
}
