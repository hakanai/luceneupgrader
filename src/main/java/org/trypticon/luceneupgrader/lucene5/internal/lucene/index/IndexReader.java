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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.DocumentStoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;  // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class IndexReader implements Closeable {
  
  private boolean closed = false;
  private boolean closedByChild = false;
  private final AtomicInteger refCount = new AtomicInteger(1);

  IndexReader() {
    if (!(this instanceof CompositeReader || this instanceof LeafReader))
      throw new Error("IndexReader should never be directly extended, subclass LeafReader or CompositeReader instead.");
  }
  
  public static interface ReaderClosedListener {
    public void onClose(IndexReader reader) throws IOException;
  }

  private final Set<ReaderClosedListener> readerClosedListeners = 
      Collections.synchronizedSet(new LinkedHashSet<ReaderClosedListener>());

  private final Set<IndexReader> parentReaders = 
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<IndexReader,Boolean>()));


  public final void addReaderClosedListener(ReaderClosedListener listener) {
    ensureOpen();
    readerClosedListeners.add(listener);
  }


  public final void removeReaderClosedListener(ReaderClosedListener listener) {
    ensureOpen();
    readerClosedListeners.remove(listener);
  }
  

  public final void registerParentReader(IndexReader reader) {
    ensureOpen();
    parentReaders.add(reader);
  }

  private void notifyReaderClosedListeners(Throwable th) {
    synchronized(readerClosedListeners) {
      for(ReaderClosedListener listener : readerClosedListeners) {
        try {
          listener.onClose(this);
        } catch (Throwable t) {
          if (th == null) {
            th = t;
          } else {
            th.addSuppressed(t);
          }
        }
      }
      IOUtils.reThrowUnchecked(th);
    }
  }

  private void reportCloseToParentReaders() {
    synchronized(parentReaders) {
      for(IndexReader parent : parentReaders) {
        parent.closedByChild = true;
        // cross memory barrier by a fake write:
        parent.refCount.addAndGet(0);
        // recurse:
        parent.reportCloseToParentReaders();
      }
    }
  }

  public final int getRefCount() {
    // NOTE: don't ensureOpen, so that callers can see
    // refCount is 0 (reader is closed)
    return refCount.get();
  }
  
  public final void incRef() {
    if (!tryIncRef()) {
      ensureOpen();
    }
  }
  
  public final boolean tryIncRef() {
    int count;
    while ((count = refCount.get()) > 0) {
      if (refCount.compareAndSet(count, count+1)) {
        return true;
      }
    }
    return false;
  }

  public final void decRef() throws IOException {
    // only check refcount here (don't call ensureOpen()), so we can
    // still close the reader if it was made invalid by a child:
    if (refCount.get() <= 0) {
      throw new AlreadyClosedException("this IndexReader is closed");
    }
    
    final int rc = refCount.decrementAndGet();
    if (rc == 0) {
      closed = true;
      Throwable throwable = null;
      try {
        doClose();
      } catch (Throwable th) {
        throwable = th;
      } finally {
        try {
          reportCloseToParentReaders();
        } finally {
          notifyReaderClosedListeners(throwable);
        }
      }
    } else if (rc < 0) {
      throw new IllegalStateException("too many decRef calls: refCount is " + rc + " after decrement");
    }
  }
  
  protected final void ensureOpen() throws AlreadyClosedException {
    if (refCount.get() <= 0) {
      throw new AlreadyClosedException("this IndexReader is closed");
    }
    // the happens before rule on reading the refCount, which must be after the fake write,
    // ensures that we see the value:
    if (closedByChild) {
      throw new AlreadyClosedException("this IndexReader cannot be used anymore as one of its child readers was closed");
    }
  }
  

  @Override
  public final boolean equals(Object obj) {
    return (this == obj);
  }
  

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }


  public abstract Fields getTermVectors(int docID)
          throws IOException;


  public final Terms getTermVector(int docID, String field)
    throws IOException {
    Fields vectors = getTermVectors(docID);
    if (vectors == null) {
      return null;
    }
    return vectors.terms(field);
  }

  public abstract int numDocs();


  public abstract int maxDoc();

  public final int numDeletedDocs() {
    return maxDoc() - numDocs();
  }


  public abstract void document(int docID, StoredFieldVisitor visitor) throws IOException;
  
  // TODO: we need a separate StoredField, so that the
  // Document returned here contains that class not
  // IndexableField
  public final Document document(int docID) throws IOException {
    final DocumentStoredFieldVisitor visitor = new DocumentStoredFieldVisitor();
    document(docID, visitor);
    return visitor.getDocument();
  }

  public final Document document(int docID, Set<String> fieldsToLoad)
      throws IOException {
    final DocumentStoredFieldVisitor visitor = new DocumentStoredFieldVisitor(
        fieldsToLoad);
    document(docID, visitor);
    return visitor.getDocument();
  }


  public boolean hasDeletions() {
    return numDeletedDocs() > 0;
  }

  @Override
  public final synchronized void close() throws IOException {
    if (!closed) {
      decRef();
      closed = true;
    }
  }
  
  protected abstract void doClose() throws IOException;

  public abstract IndexReaderContext getContext();
  
  public final List<LeafReaderContext> leaves() {
    return getContext().leaves();
  }


  public Object getCoreCacheKey() {
    // Don't call ensureOpen since FC calls this (to evict)
    // on close
    return this;
  }


  public Object getCombinedCoreAndDeletesKey() {
    // Don't call ensureOpen since FC calls this (to evict)
    // on close
    return this;
  }
  

  public abstract int docFreq(Term term) throws IOException;
  
  public abstract long totalTermFreq(Term term) throws IOException;
  
  public abstract long getSumDocFreq(String field) throws IOException;
  
  public abstract int getDocCount(String field) throws IOException;

  public abstract long getSumTotalTermFreq(String field) throws IOException;

}
