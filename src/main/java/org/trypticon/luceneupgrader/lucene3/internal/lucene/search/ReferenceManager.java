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
import java.util.concurrent.Semaphore;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.AlreadyClosedException;

public abstract class ReferenceManager<G> implements Closeable {

  private static final String REFERENCE_MANAGER_IS_CLOSED_MSG = "this ReferenceManager is closed";
  
  protected volatile G current;
  
  private final Semaphore reopenLock = new Semaphore(1);
  
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

  protected abstract boolean tryIncRef(G reference);

  public final G acquire() {
    G ref;
    do {
      if ((ref = current) == null) {
        throw new AlreadyClosedException(REFERENCE_MANAGER_IS_CLOSED_MSG);
      }
    } while (!tryIncRef(ref));
    return ref;
  }

  public final synchronized void close() throws IOException {
    if (current != null) {
      // make sure we can call this more than once
      // closeable javadoc says:
      // if this is already closed then invoking this method has no effect.
      swapReference(null);
      afterClose();
    }
  }

  protected void afterClose() throws IOException {
  }

  public final boolean maybeRefresh() throws IOException {
    ensureOpen();

    // Ensure only 1 thread does reopen at once; other threads just return immediately:
    final boolean doTryRefresh = reopenLock.tryAcquire();
    if (doTryRefresh) {
      try {
        final G reference = acquire();
        try {
          G newReference = refreshIfNeeded(reference);
          if (newReference != null) {
            assert newReference != reference : "refreshIfNeeded should return null if refresh wasn't needed";
            boolean success = false;
            try {
              swapReference(newReference);
              success = true;
            } finally {
              if (!success) {
                release(newReference);
              }
            }
          }
        } finally {
          release(reference);
        }
        afterRefresh();
      } finally {
        reopenLock.release();
      }
    }

    return doTryRefresh;
  }

  protected void afterRefresh() throws IOException {
  }
  
  public final void release(G reference) throws IOException {
    assert reference != null;
    decRef(reference);
  }
}
