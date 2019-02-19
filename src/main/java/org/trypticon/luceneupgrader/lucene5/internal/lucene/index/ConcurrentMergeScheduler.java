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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.MergePolicy.OneMerge;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.CollectionUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ThreadInterruptedException;

public class ConcurrentMergeScheduler extends MergeScheduler {


  public static final int AUTO_DETECT_MERGES_AND_THREADS = -1;


  public static final String DEFAULT_CPU_CORE_COUNT_PROPERTY = "lucene.cms.override_core_count";


  public static final String DEFAULT_SPINS_PROPERTY = "lucene.cms.override_spins";

  protected final List<MergeThread> mergeThreads = new ArrayList<>();
  
  // Max number of merge threads allowed to be running at
  // once.  When there are more merges then this, we
  // forcefully pause the larger ones, letting the smaller
  // ones run, up until maxMergeCount merges at which point
  // we forcefully pause incoming threads (that presumably
  // are the ones causing so much merging).
  private int maxThreadCount = AUTO_DETECT_MERGES_AND_THREADS;

  // Max number of merges we accept before forcefully
  // throttling the incoming threads
  private int maxMergeCount = AUTO_DETECT_MERGES_AND_THREADS;

  protected int mergeThreadCount;

  private static final double MIN_MERGE_MB_PER_SEC = 5.0;

  private static final double MAX_MERGE_MB_PER_SEC = 10240.0;

  private static final double START_MB_PER_SEC = 20.0;

  private static final double MIN_BIG_MERGE_MB = 50.0;

  protected double targetMBPerSec = START_MB_PER_SEC;

  private boolean doAutoIOThrottle = true;

  private double forceMergeMBPerSec = Double.POSITIVE_INFINITY;

  public ConcurrentMergeScheduler() {
  }

  public synchronized void setMaxMergesAndThreads(int maxMergeCount, int maxThreadCount) {
    if (maxMergeCount == AUTO_DETECT_MERGES_AND_THREADS && maxThreadCount == AUTO_DETECT_MERGES_AND_THREADS) {
      // OK
      this.maxMergeCount = AUTO_DETECT_MERGES_AND_THREADS;
      this.maxThreadCount = AUTO_DETECT_MERGES_AND_THREADS;
    } else if (maxMergeCount == AUTO_DETECT_MERGES_AND_THREADS) {
      throw new IllegalArgumentException("both maxMergeCount and maxThreadCount must be AUTO_DETECT_MERGES_AND_THREADS");
    } else if (maxThreadCount == AUTO_DETECT_MERGES_AND_THREADS) {
      throw new IllegalArgumentException("both maxMergeCount and maxThreadCount must be AUTO_DETECT_MERGES_AND_THREADS");
    } else {
      if (maxThreadCount < 1) {
        throw new IllegalArgumentException("maxThreadCount should be at least 1");
      }
      if (maxMergeCount < 1) {
        throw new IllegalArgumentException("maxMergeCount should be at least 1");
      }
      if (maxThreadCount > maxMergeCount) {
        throw new IllegalArgumentException("maxThreadCount should be <= maxMergeCount (= " + maxMergeCount + ")");
      }
      this.maxThreadCount = maxThreadCount;
      this.maxMergeCount = maxMergeCount;
    }
  }


  public synchronized void setDefaultMaxMergesAndThreads(boolean spins) {
    if (spins) {
      maxThreadCount = 1;
      maxMergeCount = 6;
    } else {
      int coreCount = Runtime.getRuntime().availableProcessors();

      // Let tests override this to help reproducing a failure on a machine that has a different
      // core count than the one where the test originally failed:
      try {
        String value = System.getProperty(DEFAULT_CPU_CORE_COUNT_PROPERTY);
        if (value != null) {
          coreCount = Integer.parseInt(value);
        }
      } catch (Throwable ignored) {
      }

      maxThreadCount = Math.max(1, Math.min(4, coreCount/2));
      maxMergeCount = maxThreadCount+5;
    }
  }

  public synchronized void setForceMergeMBPerSec(double v) {
    forceMergeMBPerSec = v;
    updateMergeThreads();
  }

  public synchronized double getForceMergeMBPerSec() {
    return forceMergeMBPerSec;
  }


  public synchronized void enableAutoIOThrottle() {
    doAutoIOThrottle = true;
    targetMBPerSec = START_MB_PER_SEC;
    updateMergeThreads();
  }


  public synchronized void disableAutoIOThrottle() {
    doAutoIOThrottle = false;
    updateMergeThreads();
  }

  public synchronized boolean getAutoIOThrottle() {
    return doAutoIOThrottle;
  }

  public synchronized double getIORateLimitMBPerSec() {
    if (doAutoIOThrottle) {
      return targetMBPerSec;
    } else {
      return Double.POSITIVE_INFINITY;
    }
  }


  public synchronized int getMaxThreadCount() {
    return maxThreadCount;
  }

  public synchronized int getMaxMergeCount() {
    return maxMergeCount;
  }

  synchronized void removeMergeThread() {
    Thread currentThread = Thread.currentThread();
    // Paranoia: don't trust Thread.equals:
    for(int i=0;i<mergeThreads.size();i++) {
      if (mergeThreads.get(i) == currentThread) {
        mergeThreads.remove(i);
        return;
      }
    }
      
    assert false: "merge thread " + currentThread + " was not found";
  }

  protected synchronized void updateMergeThreads() {

    // Only look at threads that are alive & not in the
    // process of stopping (ie have an active merge):
    final List<MergeThread> activeMerges = new ArrayList<>();

    int threadIdx = 0;
    while (threadIdx < mergeThreads.size()) {
      final MergeThread mergeThread = mergeThreads.get(threadIdx);
      if (!mergeThread.isAlive()) {
        // Prune any dead threads
        mergeThreads.remove(threadIdx);
        continue;
      }
      activeMerges.add(mergeThread);
      threadIdx++;
    }

    // Sort the merge threads, largest first:
    CollectionUtil.timSort(activeMerges);

    final int activeMergeCount = activeMerges.size();

    int bigMergeCount = 0;

    for (threadIdx=activeMergeCount-1;threadIdx>=0;threadIdx--) {
      MergeThread mergeThread = activeMerges.get(threadIdx);
      if (mergeThread.merge.estimatedMergeBytes > MIN_BIG_MERGE_MB*1024*1024) {
        bigMergeCount = 1+threadIdx;
        break;
      }
    }

    long now = System.nanoTime();

    StringBuilder message;
    if (verbose()) {
      message = new StringBuilder();
      message.append(String.format(Locale.ROOT, "updateMergeThreads ioThrottle=%s targetMBPerSec=%.1f MB/sec", doAutoIOThrottle, targetMBPerSec));
    } else {
      message = null;
    }

    for (threadIdx=0;threadIdx<activeMergeCount;threadIdx++) {
      MergeThread mergeThread = activeMerges.get(threadIdx);

      OneMerge merge = mergeThread.merge;

      // pause the thread if maxThreadCount is smaller than the number of merge threads.
      final boolean doPause = threadIdx < bigMergeCount - maxThreadCount;

      double newMBPerSec;
      if (doPause) {
        newMBPerSec = 0.0;
      } else if (merge.maxNumSegments != -1) {
        newMBPerSec = forceMergeMBPerSec;
      } else if (doAutoIOThrottle == false) {
        newMBPerSec = Double.POSITIVE_INFINITY;
      } else if (merge.estimatedMergeBytes < MIN_BIG_MERGE_MB*1024*1024) {
        // Don't rate limit small merges:
        newMBPerSec = Double.POSITIVE_INFINITY;
      } else {
        newMBPerSec = targetMBPerSec;
      }

      double curMBPerSec = merge.rateLimiter.getMBPerSec();
      
      if (verbose()) {
        long mergeStartNS = merge.mergeStartNS;
        if (mergeStartNS == -1) {
          // IndexWriter didn't start the merge yet:
          mergeStartNS = now;
        }
        message.append('\n');
        message.append(String.format(Locale.ROOT, "merge thread %s estSize=%.1f MB (written=%.1f MB) runTime=%.1fs (stopped=%.1fs, paused=%.1fs) rate=%s\n",
                                     mergeThread.getName(),
                                     bytesToMB(merge.estimatedMergeBytes),
                                     bytesToMB(merge.rateLimiter.totalBytesWritten),
                                     nsToSec(now - mergeStartNS),
                                     nsToSec(merge.rateLimiter.getTotalStoppedNS()),
                                     nsToSec(merge.rateLimiter.getTotalPausedNS()),
                                     rateToString(merge.rateLimiter.getMBPerSec())));

        if (newMBPerSec != curMBPerSec) {
          if (newMBPerSec == 0.0) {
            message.append("  now stop");
          } else if (curMBPerSec == 0.0) {
            if (newMBPerSec == Double.POSITIVE_INFINITY) {
              message.append("  now resume");
            } else {
              message.append(String.format(Locale.ROOT, "  now resume to %.1f MB/sec", newMBPerSec));
            }
          } else {
            message.append(String.format(Locale.ROOT, "  now change from %.1f MB/sec to %.1f MB/sec", curMBPerSec, newMBPerSec));
          }
        } else if (curMBPerSec == 0.0) {
          message.append("  leave stopped");
        } else {
          message.append(String.format(Locale.ROOT, "  leave running at %.1f MB/sec", curMBPerSec));
        }
      }

      merge.rateLimiter.setMBPerSec(newMBPerSec);
    }
    if (verbose()) {
      message(message.toString());
    }
  }

  private synchronized void initDynamicDefaults(IndexWriter writer) throws IOException {
    if (maxThreadCount == AUTO_DETECT_MERGES_AND_THREADS) {
      boolean spins = IOUtils.spins(writer.getDirectory());

      // Let tests override this to help reproducing a failure on a machine that has a different
      // core count than the one where the test originally failed:
      try {
        String value = System.getProperty(DEFAULT_SPINS_PROPERTY);
        if (value != null) {
          spins = Boolean.parseBoolean(value);
        }
      } catch (Throwable ignored) {
      }
      setDefaultMaxMergesAndThreads(spins);
      if (verbose()) {
        message("initDynamicDefaults spins=" + spins + " maxThreadCount=" + maxThreadCount + " maxMergeCount=" + maxMergeCount);
      }
    }
  }

  private static String rateToString(double mbPerSec) {
    if (mbPerSec == 0.0) {
      return "stopped";
    } else if (mbPerSec == Double.POSITIVE_INFINITY) {
      return "unlimited";
    } else {
      return String.format(Locale.ROOT, "%.1f MB/sec", mbPerSec);
    }
  }

  @Override
  public void close() {
    sync();
  }

  public void sync() {
    boolean interrupted = false;
    try {
      while (true) {
        MergeThread toSync = null;
        synchronized (this) {
          for (MergeThread t : mergeThreads) {
            // In case a merge thread is calling us, don't try to sync on
            // itself, since that will never finish!
            if (t.isAlive() && t != Thread.currentThread()) {
              toSync = t;
              break;
            }
          }
        }
        if (toSync != null) {
          try {
            toSync.join();
          } catch (InterruptedException ie) {
            // ignore this Exception, we will retry until all threads are dead
            interrupted = true;
          }
        } else {
          break;
        }
      }
    } finally {
      // finally, restore interrupt status:
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  public synchronized int mergeThreadCount() {
    Thread currentThread = Thread.currentThread();
    int count = 0;
    for (MergeThread mergeThread : mergeThreads) {
      if (currentThread != mergeThread && mergeThread.isAlive() && mergeThread.merge.rateLimiter.getAbort() == false) {
        count++;
      }
    }
    return count;
  }

  @Override
  public synchronized void merge(IndexWriter writer, MergeTrigger trigger, boolean newMergesFound) throws IOException {

    assert !Thread.holdsLock(writer);

    initDynamicDefaults(writer);

    if (trigger == MergeTrigger.CLOSING) {
      // Disable throttling on close:
      targetMBPerSec = MAX_MERGE_MB_PER_SEC;
      updateMergeThreads();
    }

    // First, quickly run through the newly proposed merges
    // and add any orthogonal merges (ie a merge not
    // involving segments already pending to be merged) to
    // the queue.  If we are way behind on merging, many of
    // these newly proposed merges will likely already be
    // registered.

    if (verbose()) {
      message("now merge");
      message("  index: " + writer.segString());
    }
    
    // Iterate, pulling from the IndexWriter's queue of
    // pending merges, until it's empty:
    while (true) {

      if (maybeStall(writer) == false) {
        break;
      }

      OneMerge merge = writer.getNextMerge();
      if (merge == null) {
        if (verbose()) {
          message("  no more merges pending; now return");
        }
        return;
      }

      updateIOThrottle(merge);

      boolean success = false;
      try {
        if (verbose()) {
          message("  consider merge " + writer.segString(merge.segments));
        }

        // OK to spawn a new merge thread to handle this
        // merge:
        final MergeThread merger = getMergeThread(writer, merge);
        mergeThreads.add(merger);

        if (verbose()) {
          message("    launch new thread [" + merger.getName() + "]");
        }

        merger.start();
        updateMergeThreads();

        success = true;
      } finally {
        if (!success) {
          writer.mergeFinish(merge);
        }
      }
    }
  }



  protected synchronized boolean maybeStall(IndexWriter writer) {
    long startStallTime = 0;
    while (writer.hasPendingMerges() && mergeThreadCount() >= maxMergeCount) {

      // This means merging has fallen too far behind: we
      // have already created maxMergeCount threads, and
      // now there's at least one more merge pending.
      // Note that only maxThreadCount of
      // those created merge threads will actually be
      // running; the rest will be paused (see
      // updateMergeThreads).  We stall this producer
      // thread to prevent creation of new segments,
      // until merging has caught up:

      if (mergeThreads.contains(Thread.currentThread())) {
        // Never stall a merge thread since this blocks the thread from
        // finishing and calling updateMergeThreads, and blocking it
        // accomplishes nothing anyway (it's not really a segment producer):
        return false;
      }

      if (verbose() && startStallTime == 0) {
        message("    too many merges; stalling...");
      }
      startStallTime = System.currentTimeMillis();
      doStall();
    }

    if (verbose() && startStallTime != 0) {
      message("  stalled for " + (System.currentTimeMillis()-startStallTime) + " msec");
    }

    return true;
  }

  protected synchronized void doStall() {
    try {
      // Defensively wait for only .25 seconds in case we are missing a .notify/All somewhere:
      wait(250);
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
  }

  protected void doMerge(IndexWriter writer, OneMerge merge) throws IOException {
    writer.merge(merge);
  }

  protected synchronized MergeThread getMergeThread(IndexWriter writer, OneMerge merge) throws IOException {
    final MergeThread thread = new MergeThread(writer, merge);
    thread.setDaemon(true);
    thread.setName("Lucene Merge Thread #" + mergeThreadCount++);
    return thread;
  }

  protected class MergeThread extends Thread implements Comparable<MergeThread> {

    final IndexWriter writer;
    final OneMerge merge;

    public MergeThread(IndexWriter writer, OneMerge merge) {
      this.writer = writer;
      this.merge = merge;
    }
    
    @Override
    public int compareTo(MergeThread other) {
      // Larger merges sort first:
      return Long.compare(other.merge.estimatedMergeBytes, merge.estimatedMergeBytes);
    }

    @Override
    public void run() {

      try {

        if (verbose()) {
          message("  merge thread: start");
        }

        doMerge(writer, merge);

        if (verbose()) {
          message("  merge thread: done");
        }

        // Let CMS run new merges if necessary:
        try {
          merge(writer, MergeTrigger.MERGE_FINISHED, true);
        } catch (AlreadyClosedException ace) {
          // OK
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }

      } catch (Throwable exc) {

        if (exc instanceof MergePolicy.MergeAbortedException) {
          // OK to ignore
        } else if (suppressExceptions == false) {
          // suppressExceptions is normally only set during
          // testing.
          handleMergeException(writer.getDirectory(), exc);
        }

      } finally {
        synchronized(ConcurrentMergeScheduler.this) {
          removeMergeThread();

          updateMergeThreads();

          // In case we had stalled indexing, we can now wake up
          // and possibly unstall:
          ConcurrentMergeScheduler.this.notifyAll();
        }
      }
    }
  }

  protected void handleMergeException(Directory dir, Throwable exc) {
    throw new MergePolicy.MergeException(exc, dir);
  }

  private boolean suppressExceptions;

  void setSuppressExceptions() {
    if (verbose()) {
      message("will suppress merge exceptions");
    }
    suppressExceptions = true;
  }

  void clearSuppressExceptions() {
    if (verbose()) {
      message("will not suppress merge exceptions");
    }
    suppressExceptions = false;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName() + ": ");
    sb.append("maxThreadCount=").append(maxThreadCount).append(", ");    
    sb.append("maxMergeCount=").append(maxMergeCount).append(", ");    
    sb.append("ioThrottle=").append(doAutoIOThrottle);
    return sb.toString();
  }

  private boolean isBacklog(long now, OneMerge merge) {
    double mergeMB = bytesToMB(merge.estimatedMergeBytes);
    for (MergeThread mergeThread : mergeThreads) {
      long mergeStartNS = mergeThread.merge.mergeStartNS;
      if (mergeThread.isAlive() && mergeThread.merge != merge &&
          mergeStartNS != -1 &&
          mergeThread.merge.estimatedMergeBytes >= MIN_BIG_MERGE_MB*1024*1024 &&
          nsToSec(now-mergeStartNS) > 3.0) {
        double otherMergeMB = bytesToMB(mergeThread.merge.estimatedMergeBytes);
        double ratio = otherMergeMB / mergeMB;
        if (ratio > 0.3 && ratio < 3.0) {
          return true;
        }
      }
    }

    return false;
  }

  private synchronized void updateIOThrottle(OneMerge newMerge) throws IOException {
    if (doAutoIOThrottle == false) {
      return;
    }

    double mergeMB = bytesToMB(newMerge.estimatedMergeBytes);
    if (mergeMB < MIN_BIG_MERGE_MB) {
      // Only watch non-trivial merges for throttling; this is safe because the MP must eventually
      // have to do larger merges:
      return;
    }

    long now = System.nanoTime();

    // Simplistic closed-loop feedback control: if we find any other similarly
    // sized merges running, then we are falling behind, so we bump up the
    // IO throttle, else we lower it:
    boolean newBacklog = isBacklog(now, newMerge);

    boolean curBacklog = false;

    if (newBacklog == false) {
      if (mergeThreads.size() > maxThreadCount) {
        // If there are already more than the maximum merge threads allowed, count that as backlog:
        curBacklog = true;
      } else {
        // Now see if any still-running merges are backlog'd:
        for (MergeThread mergeThread : mergeThreads) {
          if (isBacklog(now, mergeThread.merge)) {
            curBacklog = true;
            break;
          }
        }
      }
    }

    double curMBPerSec = targetMBPerSec;

    if (newBacklog) {
      // This new merge adds to the backlog: increase IO throttle by 20%
      targetMBPerSec *= 1.20;
      if (targetMBPerSec > MAX_MERGE_MB_PER_SEC) {
        targetMBPerSec = MAX_MERGE_MB_PER_SEC;
      }
      if (verbose()) {
        if (curMBPerSec == targetMBPerSec) {
          message(String.format(Locale.ROOT, "io throttle: new merge backlog; leave IO rate at ceiling %.1f MB/sec", targetMBPerSec));
        } else {
          message(String.format(Locale.ROOT, "io throttle: new merge backlog; increase IO rate to %.1f MB/sec", targetMBPerSec));
        }
      }
    } else if (curBacklog) {
      // We still have an existing backlog; leave the rate as is:
      if (verbose()) {
        message(String.format(Locale.ROOT, "io throttle: current merge backlog; leave IO rate at %.1f MB/sec",
                              targetMBPerSec));
      }
    } else {
      // We are not falling behind: decrease IO throttle by 10%
      targetMBPerSec /= 1.10;
      if (targetMBPerSec < MIN_MERGE_MB_PER_SEC) {
        targetMBPerSec = MIN_MERGE_MB_PER_SEC;
      }
      if (verbose()) {
        if (curMBPerSec == targetMBPerSec) {
          message(String.format(Locale.ROOT, "io throttle: no merge backlog; leave IO rate at floor %.1f MB/sec", targetMBPerSec));
        } else {
          message(String.format(Locale.ROOT, "io throttle: no merge backlog; decrease IO rate to %.1f MB/sec", targetMBPerSec));
        }
      }
    }

    double rate;

    if (newMerge.maxNumSegments != -1) {
      rate = forceMergeMBPerSec;
    } else {
      rate = targetMBPerSec;
    }
    newMerge.rateLimiter.setMBPerSec(rate);
    targetMBPerSecChanged();
  }

  protected void targetMBPerSecChanged() {
  }

  private static double nsToSec(long ns) {
    return ns / 1000000000.0;
  }

  private static double bytesToMB(long bytes) {
    return bytes/1024./1024.;
  }
}
