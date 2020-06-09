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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.store;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ThreadInterruptedException;

public abstract class RateLimiter {

  public abstract void setMBPerSec(double mbPerSec);

  public abstract double getMBPerSec();
  
  public abstract long pause(long bytes) throws IOException;
  
  public abstract long getMinPauseCheckBytes();

  public static class SimpleRateLimiter extends RateLimiter {

    private final static int MIN_PAUSE_CHECK_MSEC = 5;

    private volatile double mbPerSec;
    private volatile long minPauseCheckBytes;
    private long lastNS;

    // TODO: we could also allow eg a sub class to dynamically
    // determine the allowed rate, eg if an app wants to
    // change the allowed rate over time or something

    public SimpleRateLimiter(double mbPerSec) {
      setMBPerSec(mbPerSec);
      lastNS = System.nanoTime();
    }

    @Override
    public void setMBPerSec(double mbPerSec) {
      this.mbPerSec = mbPerSec;
      minPauseCheckBytes = (long) ((MIN_PAUSE_CHECK_MSEC / 1000.0) * mbPerSec * 1024 * 1024);
    }

    @Override
    public long getMinPauseCheckBytes() {
      return minPauseCheckBytes;
    }

    @Override
    public double getMBPerSec() {
      return this.mbPerSec;
    }
    
    @Override
    public long pause(long bytes) {

      long startNS = System.nanoTime();

      double secondsToPause = (bytes/1024./1024.) / mbPerSec;

      long targetNS;

      // Sync'd to read + write lastNS:
      synchronized (this) {

        // Time we should sleep until; this is purely instantaneous
        // rate (just adds seconds onto the last time we had paused to);
        // maybe we should also offer decayed recent history one?
        targetNS = lastNS + (long) (1000000000 * secondsToPause);

        if (startNS >= targetNS) {
          // OK, current time is already beyond the target sleep time,
          // no pausing to do.

          // Set to startNS, not targetNS, to enforce the instant rate, not
          // the "averaaged over all history" rate:
          lastNS = startNS;
          return 0;
        }

        lastNS = targetNS;
      }

      long curNS = startNS;

      // While loop because Thread.sleep doesn't always sleep
      // enough:
      while (true) {
        final long pauseNS = targetNS - curNS;
        if (pauseNS > 0) {
          try {
            // NOTE: except maybe on real-time JVMs, minimum realistic sleep time
            // is 1 msec; if you pass just 1 nsec the default impl rounds
            // this up to 1 msec:
            int sleepNS;
            int sleepMS;
            if (pauseNS > 100000L * Integer.MAX_VALUE) {
              // Not really practical (sleeping for 25 days) but we shouldn't overflow int:
              sleepMS = Integer.MAX_VALUE;
              sleepNS = 0;
            } else {
              sleepMS = (int) (pauseNS/1000000);
              sleepNS = (int) (pauseNS % 1000000);
            }
            Thread.sleep(sleepMS, sleepNS);
          } catch (InterruptedException ie) {
            throw new ThreadInterruptedException(ie);
          }
          curNS = System.nanoTime();
          continue;
        }
        break;
      }

      return curNS - startNS;
    }
  }
}
