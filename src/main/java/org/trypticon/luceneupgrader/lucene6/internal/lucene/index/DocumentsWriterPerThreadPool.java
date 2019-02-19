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

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ThreadInterruptedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

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

    // set by DocumentsWriter after each indexing op finishes
    volatile long lastSeqNo;

    ThreadState(DocumentsWriterPerThread dpwt) {
      this.dwpt = dpwt;
    }
    
    private void reset() {
      assert this.isHeldByCurrentThread();
      this.dwpt = null;
      this.bytesUsed = 0;
      this.flushPending = false;
    }
    
    boolean isInitialized() {
      assert this.isHeldByCurrentThread();
      return dwpt != null;
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

  private final List<ThreadState> threadStates = new ArrayList<>();

  private final List<ThreadState> freeList = new ArrayList<>();

  private boolean aborted;

  synchronized int getActiveThreadStateCount() {
    return threadStates.size();
  }

  synchronized void setAbort() {
    aborted = true;
  }

  synchronized void clearAbort() {
    aborted = false;
    notifyAll();
  }

  private synchronized ThreadState newThreadState() {
    while (aborted) {
      try {
        wait();
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);        
      }
    }
    ThreadState threadState = new ThreadState(null);
    threadState.lock(); // lock so nobody else will get this ThreadState
    threadStates.add(threadState);
    return threadState;
  }

  DocumentsWriterPerThread reset(ThreadState threadState) {
    assert threadState.isHeldByCurrentThread();
    final DocumentsWriterPerThread dwpt = threadState.dwpt;
    threadState.reset();
    return dwpt;
  }
  
  void recycle(DocumentsWriterPerThread dwpt) {
    // don't recycle DWPT by default
  }

  ThreadState getAndLock(Thread requestingThread, DocumentsWriter documentsWriter) {
    ThreadState threadState = null;
    synchronized (this) {
      if (freeList.isEmpty()) {
        // ThreadState is already locked before return by this method:
        return newThreadState();
      } else {
        // Important that we are LIFO here! This way if number of concurrent indexing threads was once high, but has now reduced, we only use a
        // limited number of thread states:
        threadState = freeList.remove(freeList.size()-1);

        if (threadState.dwpt == null) {
          // This thread-state is not initialized, e.g. it
          // was just flushed. See if we can instead find
          // another free thread state that already has docs
          // indexed. This way if incoming thread concurrency
          // has decreased, we don't leave docs
          // indefinitely buffered, tying up RAM.  This
          // will instead get those thread states flushed,
          // freeing up RAM for larger segment flushes:
          for(int i=0;i<freeList.size();i++) {
            ThreadState ts = freeList.get(i);
            if (ts.dwpt != null) {
              // Use this one instead, and swap it with
              // the un-initialized one:
              freeList.set(i, threadState);
              threadState = ts;
              break;
            }
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
      freeList.add(state);
      // In case any thread is waiting, wake one of them up since we just released a thread state; notify() should be sufficient but we do
      // notifyAll defensively:
      notifyAll();
    }
  }
  
  synchronized ThreadState getThreadState(int ord) {
    return threadStates.get(ord);
  }

  // TODO: merge this with getActiveThreadStateCount: they are the same!
  synchronized int getMaxThreadStates() {
    return threadStates.size();
  }
}
