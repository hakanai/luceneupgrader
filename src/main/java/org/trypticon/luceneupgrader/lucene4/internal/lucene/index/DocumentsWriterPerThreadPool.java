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

import java.util.concurrent.locks.ReentrantLock;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ThreadInterruptedException;

final class DocumentsWriterPerThreadPool {
  
  @SuppressWarnings("serial")
  final static class ThreadState extends ReentrantLock {
    DocumentsWriterPerThread dwpt;
    // TODO this should really be part of DocumentsWriterFlushControl
    // write access guarded by DocumentsWriterFlushControl
    volatile boolean flushPending = false;
    // TODO this should really be part of DocumentsWriterFlushControl
    // write access guarded by DocumentsWriterFlushControl
    long bytesUsed = 0;
    // guarded by Reentrant lock
    private boolean isActive = true;

    ThreadState(DocumentsWriterPerThread dpwt) {
      this.dwpt = dpwt;
    }
    
    private void deactivate() {
      assert this.isHeldByCurrentThread();
      isActive = false;
      reset();
    }
    
    private void reset() {
      assert this.isHeldByCurrentThread();
      this.dwpt = null;
      this.bytesUsed = 0;
      this.flushPending = false;
    }
    
    boolean isActive() {
      assert this.isHeldByCurrentThread();
      return isActive;
    }
    
    boolean isInitialized() {
      assert this.isHeldByCurrentThread();
      return isActive() && dwpt != null;
    }
    
    public long getBytesUsedPerThread() {
      assert this.isHeldByCurrentThread();
      // public for FlushPolicy
      return bytesUsed;
    }
    
    public DocumentsWriterPerThread getDocumentsWriterPerThread() {
      assert this.isHeldByCurrentThread();
      // public for FlushPolicy
      return dwpt;
    }
    
    public boolean isFlushPending() {
      return flushPending;
    }
  }

  private final ThreadState[] threadStates;
  private volatile int numThreadStatesActive;

  private final ThreadState[] freeList;
  private int freeCount;

  DocumentsWriterPerThreadPool(int maxNumThreadStates) {
    if (maxNumThreadStates < 1) {
      throw new IllegalArgumentException("maxNumThreadStates must be >= 1 but was: " + maxNumThreadStates);
    }
    threadStates = new ThreadState[maxNumThreadStates];
    numThreadStatesActive = 0;
    for (int i = 0; i < threadStates.length; i++) {
      threadStates[i] = new ThreadState(null);
    }
    freeList = new ThreadState[maxNumThreadStates];
  }

  int getMaxThreadStates() {
    return threadStates.length;
  }
  
  int getActiveThreadState() {
    return numThreadStatesActive;
  }
  

  private ThreadState newThreadState() {
    assert numThreadStatesActive < threadStates.length;
    final ThreadState threadState = threadStates[numThreadStatesActive];
    threadState.lock(); // lock so nobody else will get this ThreadState
    boolean unlock = true;
    try {
      if (threadState.isActive()) {
        // unreleased thread states are deactivated during DW#close()
        numThreadStatesActive++; // increment will publish the ThreadState
        //System.out.println("activeCount=" + numThreadStatesActive);
        assert threadState.dwpt == null;
        unlock = false;
        return threadState;
      }
      // we are closed: unlock since the threadstate is not active anymore
      assert assertUnreleasedThreadStatesInactive();
      throw new AlreadyClosedException("this IndexWriter is closed");
    } finally {
      if (unlock) {
        // in any case make sure we unlock if we fail 
        threadState.unlock();
      }
    }
  }
  
  private synchronized boolean assertUnreleasedThreadStatesInactive() {
    for (int i = numThreadStatesActive; i < threadStates.length; i++) {
      assert threadStates[i].tryLock() : "unreleased threadstate should not be locked";
      try {
        assert !threadStates[i].isInitialized() : "expected unreleased thread state to be inactive";
      } finally {
        threadStates[i].unlock();
      }
    }
    return true;
  }
  
  synchronized void deactivateUnreleasedStates() {
    for (int i = numThreadStatesActive; i < threadStates.length; i++) {
      final ThreadState threadState = threadStates[i];
      threadState.lock();
      try {
        threadState.deactivate();
      } finally {
        threadState.unlock();
      }
    }
    
    // In case any threads are waiting for indexing:
    notifyAll();
  }
  
  DocumentsWriterPerThread reset(ThreadState threadState, boolean closed) {
    assert threadState.isHeldByCurrentThread();
    final DocumentsWriterPerThread dwpt = threadState.dwpt;
    if (!closed) {
      threadState.reset();
    } else {
      threadState.deactivate();
    }
    return dwpt;
  }
  
  void recycle(DocumentsWriterPerThread dwpt) {
    // don't recycle DWPT by default
  }

  ThreadState getAndLock(Thread requestingThread, DocumentsWriter documentsWriter) {
    ThreadState threadState = null;
    synchronized (this) {
      while (true) {
        if (freeCount > 0) {
          // Important that we are LIFO here! This way if number of concurrent indexing threads was once high, but has now reduced, we only use a
          // limited number of thread states:
          threadState = freeList[freeCount-1];

          if (threadState.dwpt == null) {
            // This thread-state is not initialized, e.g. it
            // was just flushed. See if we can instead find
            // another free thread state that already has docs
            // indexed. This way if incoming thread concurrency
            // has decreased, we don't leave docs
            // indefinitely buffered, tying up RAM.  This
            // will instead get those thread states flushed,
            // freeing up RAM for larger segment flushes:
            for(int i=0;i<freeCount;i++) {
              if (freeList[i].dwpt != null) {
                // Use this one instead, and swap it with
                // the un-initialized one:
                ThreadState ts = freeList[i];
                freeList[i] = threadState;
                threadState = ts;
                break;
              }
            }
          }
          freeCount--;
          break;
        } else if (numThreadStatesActive < threadStates.length) {
          // ThreadState is already locked before return by this method:
          return newThreadState();
        } else {
          // Wait until a thread state frees up:
          try {
            wait();
          } catch (InterruptedException ie) {
            throw new ThreadInterruptedException(ie);
          }
        }
      }
    }

    // This could take time, e.g. if the threadState is [briefly] checked for flushing:
    threadState.lock();

    return threadState;
  }

  void release(ThreadState state) {
    state.unlock();
    synchronized (this) {
      assert freeCount < freeList.length;
      freeList[freeCount++] = state;
      // In case any thread is waiting, wake one of them up since we just released a thread state; notify() should be sufficient but we do
      // notifyAll defensively:
      notifyAll();
    }
  }
  
  ThreadState getThreadState(int ord) {
    return threadStates[ord];
  }

  ThreadState minContendedThreadState() {
    ThreadState minThreadState = null;
    final int limit = numThreadStatesActive;
    for (int i = 0; i < limit; i++) {
      final ThreadState state = threadStates[i];
      if (minThreadState == null || state.getQueueLength() < minThreadState.getQueueLength()) {
        minThreadState = state;
      }
    }
    return minThreadState;
  }
  
  int numDeactivatedThreadStates() {
    int count = 0;
    for (int i = 0; i < threadStates.length; i++) {
      final ThreadState threadState = threadStates[i];
      threadState.lock();
      try {
        if (!threadState.isActive) {
          count++;
        }
      } finally {
        threadState.unlock();
      }
    }
    return count;
  }

  void deactivateThreadState(ThreadState threadState) {
    assert threadState.isActive();
    threadState.deactivate();
  }
}
