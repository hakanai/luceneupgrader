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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergePolicy.OneMergeProgress;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergePolicy.OneMergeProgress.PauseReason;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.RateLimiter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ThreadInterruptedException;

/**
 * This is the {@link RateLimiter} that {@link IndexWriter} assigns to each running merge, to give
 * {@link MergeScheduler}s ionice like control.
 *
 * @lucene.internal
 */
public class MergeRateLimiter extends RateLimiter {

  private static final int MIN_PAUSE_CHECK_MSEC = 25;

  private static final long MIN_PAUSE_NS = TimeUnit.MILLISECONDS.toNanos(2);
  private static final long MAX_PAUSE_NS = TimeUnit.MILLISECONDS.toNanos(250);

  private volatile double mbPerSec;
  private volatile long minPauseCheckBytes;

  private AtomicLong lastNS = new AtomicLong(0);

  private AtomicLong totalBytesWritten = new AtomicLong();

  private final OneMergeProgress mergeProgress;

  /** Sole constructor. */
  public MergeRateLimiter(OneMergeProgress mergeProgress) {
    // Initially no IO limit; use setter here so minPauseCheckBytes is set:
    this.mergeProgress = mergeProgress;
    setMBPerSec(Double.POSITIVE_INFINITY);
  }

  @Override
  public void setMBPerSec(double mbPerSec) {
    // Synchronized to make updates to mbPerSec and minPauseCheckBytes atomic.
    synchronized (this) {
      // 0.0 is allowed: it means the merge is paused
      if (mbPerSec < 0.0) {
        throw new IllegalArgumentException("mbPerSec must be positive; got: " + mbPerSec);
      }
      this.mbPerSec = mbPerSec;

      // NOTE: Double.POSITIVE_INFINITY casts to Long.MAX_VALUE
      this.minPauseCheckBytes =
          Math.min(1024 * 1024, (long) ((MIN_PAUSE_CHECK_MSEC / 1000.0) * mbPerSec * 1024 * 1024));
      assert minPauseCheckBytes >= 0;
    }

    mergeProgress.wakeup();
  }

  @Override
  public double getMBPerSec() {
    return mbPerSec;
  }

  /** Returns total bytes written by this merge. */
  public long getTotalBytesWritten() {
    return totalBytesWritten.get();
  }

  @Override
  public long pause(long bytes) throws MergePolicy.MergeAbortedException {
    totalBytesWritten.addAndGet(bytes);

    // While loop because we may wake up and check again when our rate limit
    // is changed while we were pausing:
    long paused = 0;
    long delta;
    while ((delta = maybePause(bytes)) >= 0) {
      // Keep waiting.
      paused += delta;
    }

    return paused;
  }

  /** Total NS merge was stopped. */
  public long getTotalStoppedNS() {
    return mergeProgress.getPauseTimes().get(PauseReason.STOPPED);
  }

  /** Total NS merge was paused to rate limit IO. */
  public long getTotalPausedNS() {
    return mergeProgress.getPauseTimes().get(PauseReason.PAUSED);
  }

  /**
   * Returns the number of nanoseconds spent in a paused state or <code>-1</code> if no pause was
   * applied. If the thread needs pausing, this method delegates to the linked {@link
   * OneMergeProgress}.
   */
  private long maybePause(long bytes) throws MergePolicy.MergeAbortedException {
    // Now is a good time to abort the merge:
    if (mergeProgress.isAborted()) {
      throw new MergePolicy.MergeAbortedException("Merge aborted.");
    }

    final double rate = mbPerSec; // read from volatile rate once.
    final double secondsToPause = (bytes / 1024. / 1024.) / rate;

    AtomicLong curPauseNSSetter = new AtomicLong();
    // While we use updateAndGet to avoid a race condition between multiple threads, this doesn't
    // mean
    // that multiple threads will end up getting paused at the same time.
    // We only pause the calling thread. This means if the upstream caller (e.g.
    // ConcurrentMergeScheduler)
    // is using multiple intra-threads, they will all be paused independently.
    lastNS.updateAndGet(
        last -> {
          long curNS = System.nanoTime();
          // Time we should sleep until; this is purely instantaneous
          // rate (just adds seconds onto the last time we had paused to);
          // maybe we should also offer decayed recent history one?
          long targetNS = last + (long) (1000000000 * secondsToPause);
          long curPauseNS = targetNS - curNS;
          // We don't bother with thread pausing if the pause is smaller than 2 msec.
          if (curPauseNS <= MIN_PAUSE_NS) {
            // Set to curNS, not targetNS, to enforce the instant rate, not
            // the "averaged over all history" rate:
            curPauseNSSetter.set(0);
            return curNS;
          }
          curPauseNSSetter.set(curPauseNS);
          return last;
        });

    if (curPauseNSSetter.get() == 0) {
      return -1;
    }
    long curPauseNS = curPauseNSSetter.get();
    // Defensive: don't sleep for too long; the loop above will call us again if
    // we should keep sleeping and the rate may be adjusted in between.
    if (curPauseNS > MAX_PAUSE_NS) {
      curPauseNS = MAX_PAUSE_NS;
    }

    long start = System.nanoTime();
    try {
      mergeProgress.pauseNanos(
          curPauseNS,
          rate == 0.0 ? PauseReason.STOPPED : PauseReason.PAUSED,
          () -> rate == mbPerSec);
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
    return System.nanoTime() - start;
  }

  @Override
  public long getMinPauseCheckBytes() {
    return minPauseCheckBytes;
  }
}
