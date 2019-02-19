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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

public class NRTManagerReopenThread extends Thread implements NRTManager.WaitingListener, Closeable {
  
  private final NRTManager manager;
  private final long targetMaxStaleNS;
  private final long targetMinStaleNS;
  private boolean finish;
  private long waitingGen;

  public NRTManagerReopenThread(NRTManager manager, double targetMaxStaleSec, double targetMinStaleSec) {
    if (targetMaxStaleSec < targetMinStaleSec) {
      throw new IllegalArgumentException("targetMaxScaleSec (= " + targetMaxStaleSec + ") < targetMinStaleSec (=" + targetMinStaleSec + ")");
    }
    this.manager = manager;
    this.targetMaxStaleNS = (long) (1000000000*targetMaxStaleSec);
    this.targetMinStaleNS = (long) (1000000000*targetMinStaleSec);
    manager.addWaitingListener(this);
  }

  public synchronized void close() {
    //System.out.println("NRT: set finish");
    manager.removeWaitingListener(this);
    this.finish = true;
    notify();
    try {
      join();
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
  }

  public synchronized void waiting(long targetGen) {
    waitingGen = Math.max(waitingGen, targetGen);
    notify();
    //System.out.println(Thread.currentThread().getName() + ": force wakeup waitingGen=" + waitingGen + " applyDeletes=" + applyDeletes);
  }

  @Override
  public void run() {
    // TODO: maybe use private thread ticktock timer, in
    // case clock shift messes up nanoTime?
    long lastReopenStartNS = System.nanoTime();

    //System.out.println("reopen: start");
    try {
      while (true) {

        boolean hasWaiting = false;

        synchronized(this) {
          // TODO: try to guestimate how long reopen might
          // take based on past data?

          while (!finish) {
            //System.out.println("reopen: cycle");

            // True if we have someone waiting for reopen'd searcher:
            hasWaiting = waitingGen > manager.getCurrentSearchingGen();
            final long nextReopenStartNS = lastReopenStartNS + (hasWaiting ? targetMinStaleNS : targetMaxStaleNS);

            final long sleepNS = nextReopenStartNS - System.nanoTime();

            if (sleepNS > 0) {
              //System.out.println("reopen: sleep " + (sleepNS/1000000.0) + " ms (hasWaiting=" + hasWaiting + ")");
              try {
                wait(sleepNS/1000000, (int) (sleepNS%1000000));
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                //System.out.println("NRT: set finish on interrupt");
                finish = true;
                break;
              }
            } else {
              break;
            }
          }

          if (finish) {
            //System.out.println("reopen: finish");
            return;
          }
          //System.out.println("reopen: start hasWaiting=" + hasWaiting);
        }

        lastReopenStartNS = System.nanoTime();
        try {
          //final long t0 = System.nanoTime();
          manager.maybeRefresh();
          //System.out.println("reopen took " + ((System.nanoTime()-t0)/1000000.0) + " msec");
        } catch (IOException ioe) {
          //System.out.println(Thread.currentThread().getName() + ": IOE");
          //ioe.printStackTrace();
          throw new RuntimeException(ioe);
        }
      }
    } catch (Throwable t) {
      //System.out.println("REOPEN EXC");
      //t.printStackTrace(System.out);
      throw new RuntimeException(t);
    }
  }
}
