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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.AlreadyClosedException;

public abstract class ReferenceManager<G> implements Closeable {

  private static final String REFERENCE_MANAGER_IS_CLOSED_MSG = "this ReferenceManager is closed";
  
  protected volatile G current;
  
  private final Lock refreshLock = new ReentrantLock();

  private final List<RefreshListener> refreshListeners = new CopyOnWriteArrayList<>();

  private void ensureOpen() {
    if (current == null) {
      throw new AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG);
    }
  }
  
  private synchronized void swapReference(G newReference) throws IOException {
    ensureOpen();
    final G oldReference = current;
    current = newReference;
    release(oldReference);
  }


  protected abstract void decRef(G reference) throws IOException;
  
  protected abstract G refreshIfNeeded(G referenceToRefresh) throws IOException;

  protected abstract boolean tryIncRef(G reference) throws IOException;

  public final G acquire() throws IOException {
    G ref;

    do {
      if ((ref = current) == null) {
        throw new AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG);
      }
      if (tryIncRef(ref)) {
        return ref;
      }
      if (getRefCount(ref) == 0 && current == ref) {
        assert ref != null;
        /* if we can't increment the reader but we are
           still the current reference the RM is in a
           illegal states since we can't make any progress
           anymore. The reference is closed but the RM still
           holds on to it as the actual instance.
           This can only happen if somebody outside of the RM
           decrements the refcount without a corresponding increment
           since the RM assigns the new reference before counting down
           the reference. */
        throw new IllegalStateException("The managed reference has already closed - this is likely a bug when the reference count is modified outside of the ReferenceManager");
      }
    } while (true);
  }
  
  @Override
  public final synchronized void close() throws IOException {
    if (current != null) {
      // make sure we can call this more than once
      // closeable javadoc says:
      // if this is already closed then invoking this method has no effect.
      swapReference(null);
      afterClose();
    }
  }

  protected abstract int getRefCount(G reference);


  protected void afterClose() throws IOException {
  }

  private void doMaybeRefresh() throws IOException {
    // it's ok to call lock() here (blocking) because we're supposed to get here
    // from either maybeRefreh() or maybeRefreshBlocking(), after the lock has
    // already been obtained. Doing that protects us from an accidental bug
    // where this method will be called outside the scope of refreshLock.
    // Per ReentrantLock's javadoc, calling lock() by the same thread more than
    // once is ok, as long as unlock() is called a matching number of times.
    refreshLock.lock();
    boolean refreshed = false;
    try {
      final G reference = acquire();
      try {
        notifyRefreshListenersBefore();
        G newReference = refreshIfNeeded(reference);
        if (newReference != null) {
          assert newReference != reference : "refreshIfNeeded should return null if refresh wasn't needed";
          try {
            swapReference(newReference);
            refreshed = true;
          } finally {
            if (!refreshed) {
              release(newReference);
            }
          }
        }
      } finally {
        release(reference);
        notifyRefreshListenersRefreshed(refreshed);
      }
      afterMaybeRefresh();
    } finally {
      refreshLock.unlock();
    }
  }

  public final boolean maybeRefresh() throws IOException {
    ensureOpen();

    // Ensure only 1 thread does refresh at once; other threads just return immediately:
    final boolean doTryRefresh = refreshLock.tryLock();
    if (doTryRefresh) {
      try {
        doMaybeRefresh();
      } finally {
        refreshLock.unlock();
      }
    }

    return doTryRefresh;
  }
  
  public final void maybeRefreshBlocking() throws IOException {
    ensureOpen();

    // Ensure only 1 thread does refresh at once
    refreshLock.lock();
    try {
      doMaybeRefresh();
    } finally {
      refreshLock.unlock();
    }
  }


  protected void afterMaybeRefresh() throws IOException {
  }
  
  public final void release(G reference) throws IOException {
    assert reference != null;
    decRef(reference);
  }

  private void notifyRefreshListenersBefore() throws IOException {
    for (RefreshListener refreshListener : refreshListeners) {
      refreshListener.beforeRefresh();
    }
  }

  private void notifyRefreshListenersRefreshed(boolean didRefresh) throws IOException {
    for (RefreshListener refreshListener : refreshListeners) {
      refreshListener.afterRefresh(didRefresh);
    }
  }

  public void addListener(RefreshListener listener) {
    if (listener == null) {
      throw new NullPointerException("Listener cannot be null");
    }
    refreshListeners.add(listener);
  }

  public void removeListener(RefreshListener listener) {
    if (listener == null) {
      throw new NullPointerException("Listener cannot be null");
    }
    refreshListeners.remove(listener);
  }

  public interface RefreshListener {

    void beforeRefresh() throws IOException;


    void afterRefresh(boolean didRefresh) throws IOException;
  }
}
