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

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ThreadInterruptedException;

final class DocumentsWriterPerThreadPool implements Iterable<DocumentsWriterPerThread>, Closeable {

  private final Set<DocumentsWriterPerThread> dwpts = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Deque<DocumentsWriterPerThread> freeList = new ArrayDeque<>();
  private final Supplier<DocumentsWriterPerThread> dwptFactory;
  private int takenWriterPermits = 0;
  private boolean closed;


  DocumentsWriterPerThreadPool(Supplier<DocumentsWriterPerThread> dwptFactory) {
    this.dwptFactory = dwptFactory;
  }

  synchronized int size() {
    return dwpts.size();
  }

  synchronized void lockNewWriters() {
    // this is similar to a semaphore - we need to acquire all permits ie. takenWriterPermits must be == 0
    // any call to lockNewWriters() must be followed by unlockNewWriters() otherwise we will deadlock at some
    // point
    assert takenWriterPermits >= 0;
    takenWriterPermits++;
  }

  synchronized void unlockNewWriters() {
    assert takenWriterPermits > 0;
    takenWriterPermits--;
    if (takenWriterPermits == 0) {
      notifyAll();
    }
  }

  private synchronized DocumentsWriterPerThread newWriter() {
    assert takenWriterPermits >= 0;
    while (takenWriterPermits > 0) {
      // we can't create new DWPTs while not all permits are available
      try {
        wait();
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
    }
    // we must check if we are closed since this might happen while we are waiting for the writer permit
    // and if we miss that we might release a new DWPT even though the pool is closed. Yet, that wouldn't be the
    // end of the world it's violating the contract that we don't release any new DWPT after this pool is closed
    ensureOpen();
    DocumentsWriterPerThread dwpt = dwptFactory.get();
    dwpt.lock(); // lock so nobody else will get this DWPT
    dwpts.add(dwpt);
    return dwpt;
  }

  // TODO: maybe we should try to do load leveling here: we want roughly even numbers
  // of items (docs, deletes, DV updates) to most take advantage of concurrency while flushing

  DocumentsWriterPerThread getAndLock() {
    synchronized (this) {
      ensureOpen();
      // Important that we are LIFO here! This way if number of concurrent indexing threads was once high,
      // but has now reduced, we only use a limited number of DWPTs. This also guarantees that if we have suddenly
      // a single thread indexing
      final Iterator<DocumentsWriterPerThread> descendingIterator = freeList.descendingIterator();
      while (descendingIterator.hasNext()) {
        DocumentsWriterPerThread perThread = descendingIterator.next();
        if (perThread.tryLock()) {
          descendingIterator.remove();
          return perThread;
        }
      }
      // DWPT is already locked before return by this method:
      return newWriter();
    }
  }

  private void ensureOpen() {
    if (closed) {
      throw new AlreadyClosedException("DWPTPool is already closed");
    }
  }

  void marksAsFreeAndUnlock(DocumentsWriterPerThread state) {
    synchronized (this) {
      assert dwpts.contains(state) : "we tried to add a DWPT back to the pool but the pool doesn't know aobut this DWPT";
      freeList.add(state);
    }
    state.unlock();
  }

  @Override
  public synchronized Iterator<DocumentsWriterPerThread> iterator() {
    return Arrays.asList(dwpts.toArray(new DocumentsWriterPerThread[dwpts.size()])).iterator(); // copy on read - this is a quick op since num states is low
  }

  List<DocumentsWriterPerThread> filterAndLock(Predicate<DocumentsWriterPerThread> predicate) {
    List<DocumentsWriterPerThread> list = new ArrayList<>();
    for (DocumentsWriterPerThread perThread : this) {
      if (predicate.test(perThread)) {
        perThread.lock();
        if (isRegistered(perThread)) {
          list.add(perThread);
        } else {
          // somebody else has taken this DWPT out of the pool.
          // unlock and let it go
          perThread.unlock();
        }
      }
    }
    return Collections.unmodifiableList(list);
  }

  synchronized boolean checkout(DocumentsWriterPerThread perThread) {
   assert perThread.isHeldByCurrentThread();
    if (dwpts.remove(perThread)) {
      freeList.remove(perThread);
    } else {
      assert freeList.contains(perThread) == false;
      return false;
    }
    return true;
  }

  synchronized boolean isRegistered(DocumentsWriterPerThread perThread) {
    return dwpts.contains(perThread);
  }

  @Override
  public synchronized void close() {
    this.closed = true;
  }
}
