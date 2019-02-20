package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.IdentityHashMap;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocumentsWriterPerThreadPool.ThreadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ThreadInterruptedException;

final class DocumentsWriterStallControl {
  
  private volatile boolean stalled;
  private int numWaiting; // only with assert
  private boolean wasStalled; // only with assert
  private final Map<Thread, Boolean> waiting = new IdentityHashMap<>(); // only with assert
  
  synchronized void updateStalled(boolean stalled) {
    this.stalled = stalled;
    if (stalled) {
      wasStalled = true;
    }
    notifyAll();
  }
  
  void waitIfStalled() {
    if (stalled) {
      synchronized (this) {
        if (stalled) { // react on the first wakeup call!
          // don't loop here, higher level logic will re-stall!
          try {
            assert incWaiters();
            wait();
            assert  decrWaiters();
          } catch (InterruptedException e) {
            throw new ThreadInterruptedException(e);
          }
        }
      }
    }
  }
  
  boolean anyStalledThreads() {
    return stalled;
  }
  
  
  private boolean incWaiters() {
    numWaiting++;
    assert waiting.put(Thread.currentThread(), Boolean.TRUE) == null;
    
    return numWaiting > 0;
  }
  
  private boolean decrWaiters() {
    numWaiting--;
    assert waiting.remove(Thread.currentThread()) != null;
    return numWaiting >= 0;
  }
  
  synchronized boolean hasBlocked() { // for tests
    return numWaiting > 0;
  }
  
  boolean isHealthy() { // for tests
    return !stalled; // volatile read!
  }
  
  synchronized boolean isThreadQueued(Thread t) { // for tests
    return waiting.containsKey(t);
  }

  synchronized boolean wasStalled() { // for tests
    return wasStalled;
  }
}
