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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TrackingIndexWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ThreadInterruptedException;



public class ControlledRealTimeReopenThread<T> extends Thread implements Closeable {
  private final ReferenceManager<T> manager;
  private final long targetMaxStaleNS;
  private final long targetMinStaleNS;
  private final TrackingIndexWriter writer;
  private volatile boolean finish;
  private volatile long waitingGen;
  private volatile long searchingGen;
  private long refreshStartGen;

  private final ReentrantLock reopenLock = new ReentrantLock();
  private final Condition reopenCond = reopenLock.newCondition();
  
  public ControlledRealTimeReopenThread(TrackingIndexWriter writer, ReferenceManager<T> manager, double targetMaxStaleSec, double targetMinStaleSec) {
    if (targetMaxStaleSec < targetMinStaleSec) {
      throw new IllegalArgumentException("targetMaxScaleSec (= " + targetMaxStaleSec + ") < targetMinStaleSec (=" + targetMinStaleSec + ")");
    }
    this.writer = writer;
    this.manager = manager;
    this.targetMaxStaleNS = (long) (1000000000*targetMaxStaleSec);
    this.targetMinStaleNS = (long) (1000000000*targetMinStaleSec);
    manager.addListener(new HandleRefresh());
  }

  private class HandleRefresh implements ReferenceManager.RefreshListener {
    @Override
    public void beforeRefresh() {
    }

    @Override
    public void afterRefresh(boolean didRefresh) {
      refreshDone();
    }
  }

  private synchronized void refreshDone() {
    searchingGen = refreshStartGen;
    notifyAll();
  }

  @Override
  public synchronized void close() {
    //System.out.println("NRT: set finish");

    finish = true;

    // So thread wakes up and notices it should finish:
    reopenLock.lock();
    try {
      reopenCond.signal();
    } finally {
      reopenLock.unlock();
    }

    try {
      join();
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }

    // Max it out so any waiting search threads will return:
    searchingGen = Long.MAX_VALUE;
    notifyAll();
  }

  public void waitForGeneration(long targetGen) throws InterruptedException {
    waitForGeneration(targetGen, -1);
  }

  public synchronized boolean waitForGeneration(long targetGen, int maxMS) throws InterruptedException {
    final long curGen = writer.getGeneration();
    if (targetGen > curGen) {
      throw new IllegalArgumentException("targetGen=" + targetGen + " was never returned by the ReferenceManager instance (current gen=" + curGen + ")");
    }
    if (targetGen > searchingGen) {
      // Notify the reopen thread that the waitingGen has
      // changed, so it may wake up and realize it should
      // not sleep for much or any longer before reopening:
      reopenLock.lock();

      // Need to find waitingGen inside lock as its used to determine
      // stale time
      waitingGen = Math.max(waitingGen, targetGen);

      try {
        reopenCond.signal();
      } finally {
        reopenLock.unlock();
      }

      long startMS = System.nanoTime()/1000000;

      while (targetGen > searchingGen) {
        if (maxMS < 0) {
          wait();
        } else {
          long msLeft = (startMS + maxMS) - (System.nanoTime())/1000000;
          if (msLeft <= 0) {
            return false;
          } else {
            wait(msLeft);
          }
        }
      }
    }

    return true;
  }

  @Override
  public void run() {
    // TODO: maybe use private thread ticktock timer, in
    // case clock shift messes up nanoTime?
    long lastReopenStartNS = System.nanoTime();

    //System.out.println("reopen: start");
    while (!finish) {

      // TODO: try to guestimate how long reopen might
      // take based on past data?

      // Loop until we've waiting long enough before the
      // next reopen:
      while (!finish) {

        // Need lock before finding out if has waiting
        reopenLock.lock();
        try {
          // True if we have someone waiting for reopened searcher:
          boolean hasWaiting = waitingGen > searchingGen;
          final long nextReopenStartNS = lastReopenStartNS + (hasWaiting ? targetMinStaleNS : targetMaxStaleNS);

          final long sleepNS = nextReopenStartNS - System.nanoTime();

          if (sleepNS > 0) {
            reopenCond.awaitNanos(sleepNS);
          } else {
            break;
          }
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        } finally {
          reopenLock.unlock();
        }
      }

      if (finish) {
        break;
      }

      lastReopenStartNS = System.nanoTime();
      // Save the gen as of when we started the reopen; the
      // listener (HandleRefresh above) copies this to
      // searchingGen once the reopen completes:
      refreshStartGen = writer.getAndIncrementGeneration();
      try {
        manager.maybeRefreshBlocking();
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }
}
