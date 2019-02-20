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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.FieldSelector;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.SearcherManager; // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Similarity;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.VirtualMethod;

public abstract class IndexReader implements Cloneable,Closeable {

  public static interface ReaderClosedListener {
    public void onClose(IndexReader reader);
  }

  private final Set<ReaderClosedListener> readerClosedListeners = 
      Collections.synchronizedSet(new LinkedHashSet<ReaderClosedListener>());


  public final void addReaderClosedListener(ReaderClosedListener listener) {
    ensureOpen();
    readerClosedListeners.add(listener);
  }


  public final void removeReaderClosedListener(ReaderClosedListener listener) {
    ensureOpen();
    readerClosedListeners.remove(listener);
  }

  private final void notifyReaderClosedListeners() {
    synchronized(readerClosedListeners) {
      for(ReaderClosedListener listener : readerClosedListeners) {
        listener.onClose(this);
      }
    }
  }

  private boolean closed = false;
  protected boolean hasChanges;
  
  private final AtomicInteger refCount = new AtomicInteger();

  static int DEFAULT_TERMS_INDEX_DIVISOR = 1;

  public final int getRefCount() {
    return refCount.get();
  }
  
  public final void incRef() {
    ensureOpen();
    refCount.incrementAndGet();
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

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    if (hasChanges) {
      buffer.append('*');
    }
    buffer.append(getClass().getSimpleName());
    buffer.append('(');
    final IndexReader[] subReaders = getSequentialSubReaders();
    if ((subReaders != null) && (subReaders.length > 0)) {
      buffer.append(subReaders[0]);
      for (int i = 1; i < subReaders.length; ++i) {
        buffer.append(" ").append(subReaders[i]);
      }
    }
    buffer.append(')');
    return buffer.toString();
  }

  public final void decRef() throws IOException {
    ensureOpen();
    final int rc = refCount.decrementAndGet();
    if (rc == 0) {
      boolean success = false;
      try {
        commit();
        doClose();
        success = true;
      } finally {
        if (!success) {
          // Put reference back on failure
          refCount.incrementAndGet();
        }
      }
      notifyReaderClosedListeners();
    } else if (rc < 0) {
      throw new IllegalStateException("too many decRef calls: refCount is " + rc + " after decrement");
    }
  }
  
  protected IndexReader() { 
    refCount.set(1);
  }
  
  protected final void ensureOpen() throws AlreadyClosedException {
    if (refCount.get() <= 0) {
      throw new AlreadyClosedException("this IndexReader is closed");
    }
  }
  

  public static IndexReader open(final Directory directory) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, true, DEFAULT_TERMS_INDEX_DIVISOR);
  }


  @Deprecated
  public static IndexReader open(final Directory directory, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }

  public static IndexReader open(final IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    return writer.getReader(applyAllDeletes);
  }


  public static IndexReader open(final IndexCommit commit) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, true, DEFAULT_TERMS_INDEX_DIVISOR);
  }


  @Deprecated
  public static IndexReader open(final IndexCommit commit, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }


  @Deprecated
  public static IndexReader open(final Directory directory, IndexDeletionPolicy deletionPolicy, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, deletionPolicy, null, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }


  @Deprecated
  public static IndexReader open(final Directory directory, IndexDeletionPolicy deletionPolicy, boolean readOnly, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, deletionPolicy, null, readOnly, termInfosIndexDivisor);
  }


  @Deprecated
  public static IndexReader open(final IndexCommit commit, IndexDeletionPolicy deletionPolicy, boolean readOnly) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), deletionPolicy, commit, readOnly, DEFAULT_TERMS_INDEX_DIVISOR);
  }


  @Deprecated
  public static IndexReader open(final IndexCommit commit, IndexDeletionPolicy deletionPolicy, boolean readOnly, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), deletionPolicy, commit, readOnly, termInfosIndexDivisor);
  }


  public static IndexReader open(final Directory directory, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(directory, null, null, true, termInfosIndexDivisor);
  }


  public static IndexReader open(final IndexCommit commit, int termInfosIndexDivisor) throws CorruptIndexException, IOException {
    return DirectoryReader.open(commit.getDirectory(), null, commit, true, termInfosIndexDivisor);
  }

  public static IndexReader openIfChanged(IndexReader oldReader) throws IOException {
    if (oldReader.hasNewReopenAPI1) {
      final IndexReader newReader = oldReader.doOpenIfChanged();
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen();
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  @Deprecated
  public static IndexReader openIfChanged(IndexReader oldReader, boolean readOnly) throws IOException {
    if (oldReader.hasNewReopenAPI2) {
      final IndexReader newReader = oldReader.doOpenIfChanged(readOnly);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(readOnly);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  // TODO: should you be able to specify readOnly?
  public static IndexReader openIfChanged(IndexReader oldReader, IndexCommit commit) throws IOException {
    if (oldReader.hasNewReopenAPI3) {
      final IndexReader newReader = oldReader.doOpenIfChanged(commit);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(commit);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  public static IndexReader openIfChanged(IndexReader oldReader, IndexWriter writer, boolean applyAllDeletes) throws IOException {
    if (oldReader.hasNewReopenAPI4) {
      final IndexReader newReader = oldReader.doOpenIfChanged(writer, applyAllDeletes);
      assert newReader != oldReader;
      return newReader;
    } else {
      final IndexReader newReader = oldReader.reopen(writer, applyAllDeletes);
      if (newReader == oldReader) {
        return null;
      } else {
        return newReader;
      }
    }
  }

  @Deprecated
  public IndexReader reopen() throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }


  @Deprecated
  public IndexReader reopen(boolean openReadOnly) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, openReadOnly);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }


  @Deprecated
  public IndexReader reopen(IndexCommit commit) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, commit);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  @Deprecated
  public IndexReader reopen(IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    final IndexReader newReader = IndexReader.openIfChanged(this, writer, applyAllDeletes);
    if (newReader == null) {
      return this;
    } else {
      return newReader;
    }
  }

  protected IndexReader doOpenIfChanged() throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen().");
  }
  
  @Deprecated
  protected IndexReader doOpenIfChanged(boolean openReadOnly) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen().");
  }

  protected IndexReader doOpenIfChanged(final IndexCommit commit) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support reopen(IndexCommit).");
  }

  protected IndexReader doOpenIfChanged(IndexWriter writer, boolean applyAllDeletes) throws CorruptIndexException, IOException {
    return writer.getReader(applyAllDeletes);
  }

  @Override
  public synchronized Object clone() {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }
  
  @Deprecated
  public synchronized IndexReader clone(boolean openReadOnly) throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }


  public Directory directory() {
    ensureOpen();
    throw new UnsupportedOperationException("This reader does not support this method.");  
  }

  @Deprecated
  public static long lastModified(final Directory directory2) throws CorruptIndexException, IOException {
    return ((Long) new SegmentInfos.FindSegmentsFile(directory2) {
        @Override
        public Object doBody(String segmentFileName) throws IOException {
          return Long.valueOf(directory2.fileModified(segmentFileName));
        }
      }.run()).longValue();
  }

  @Deprecated
  public static long getCurrentVersion(Directory directory) throws CorruptIndexException, IOException {
    return SegmentInfos.readCurrentVersion(directory);
  }

  @Deprecated
  public static Map<String,String> getCommitUserData(Directory directory) throws CorruptIndexException, IOException {
    SegmentInfos sis = new SegmentInfos();
    sis.read(directory);
    return sis.getUserData();
  }

  public long getVersion() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }

  @Deprecated
  public Map<String,String> getCommitUserData() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }


  public boolean isCurrent() throws CorruptIndexException, IOException {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }

  @Deprecated
  public boolean isOptimized() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
  
  abstract public TermFreqVector[] getTermFreqVectors(int docNumber)
          throws IOException;


  abstract public TermFreqVector getTermFreqVector(int docNumber, String field)
          throws IOException;

  abstract public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException;

  abstract public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException;

  public static boolean indexExists(Directory directory) throws IOException {
    try {
      new SegmentInfos().read(directory);
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  public abstract int numDocs();


  public abstract int maxDoc();

  public final int numDeletedDocs() {
    return maxDoc() - numDocs();
  }

  public final Document document(int n) throws CorruptIndexException, IOException {
    ensureOpen();
    if (n < 0 || n >= maxDoc()) {
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc() + " (got docID=" + n + ")");
    }
    return document(n, null);
  }

  // TODO (1.5): When we convert to JDK 1.5 make this Set<String>
  public abstract Document document(int n, FieldSelector fieldSelector) throws CorruptIndexException, IOException;
  
  public abstract boolean isDeleted(int n);

  public abstract boolean hasDeletions();

  public boolean hasNorms(String field) throws IOException {
    // backward compatible implementation.
    // SegmentReader has an efficient implementation.
    ensureOpen();
    return norms(field) != null;
  }


  public abstract byte[] norms(String field) throws IOException;


  public abstract void norms(String field, byte[] bytes, int offset)
    throws IOException;


  @Deprecated
  public final synchronized  void setNorm(int doc, String field, byte value)
          throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doSetNorm(doc, field, value);
  }


  @Deprecated
  protected abstract void doSetNorm(int doc, String field, byte value)
          throws CorruptIndexException, IOException;


  @Deprecated
  public final void setNorm(int doc, String field, float value)
          throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    setNorm(doc, field, Similarity.getDefault().encodeNormValue(value));
  }


  public abstract TermEnum terms() throws IOException;


  public abstract TermEnum terms(Term t) throws IOException;


  public abstract int docFreq(Term t) throws IOException;


  public TermDocs termDocs(Term term) throws IOException {
    ensureOpen();
    TermDocs termDocs = termDocs();
    termDocs.seek(term);
    return termDocs;
  }


  public abstract TermDocs termDocs() throws IOException;


  public final TermPositions termPositions(Term term) throws IOException {
    ensureOpen();
    TermPositions termPositions = termPositions();
    termPositions.seek(term);
    return termPositions;
  }


  public abstract TermPositions termPositions() throws IOException;




  @Deprecated
  public final synchronized void deleteDocument(int docNum) throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doDelete(docNum);
  }



  @Deprecated
  protected abstract void doDelete(int docNum) throws CorruptIndexException, IOException;



  @Deprecated
  public final int deleteDocuments(Term term) throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    TermDocs docs = termDocs(term);
    if (docs == null) return 0;
    int n = 0;
    try {
      while (docs.next()) {
        deleteDocument(docs.doc());
        n++;
      }
    } finally {
      docs.close();
    }
    return n;
  }


  @Deprecated
  public final synchronized void undeleteAll() throws StaleReaderException, CorruptIndexException, LockObtainFailedException, IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doUndeleteAll();
  }


  @Deprecated
  protected abstract void doUndeleteAll() throws CorruptIndexException, IOException;


  @Deprecated
  protected synchronized void acquireWriteLock() throws IOException {
    /* NOOP */
  }
  
  @Deprecated
  public final synchronized void flush() throws IOException {
    ensureOpen();
    commit();
  }

  @Deprecated
  public final synchronized void flush(Map<String, String> commitUserData) throws IOException {
    ensureOpen();
    commit(commitUserData);
  }
  
  @Deprecated
  protected final synchronized void commit() throws IOException {
    commit(null);
  }
  
  @Deprecated
  public final synchronized void commit(Map<String, String> commitUserData) throws IOException {
    // Don't call ensureOpen since we commit() on close
    doCommit(commitUserData);
    hasChanges = false;
  }


  @Deprecated
  protected abstract void doCommit(Map<String, String> commitUserData) throws IOException;

  public final synchronized void close() throws IOException {
    if (!closed) {
      decRef();
      closed = true;
    }
  }
  
  protected abstract void doClose() throws IOException;

  public abstract FieldInfos getFieldInfos();

  public IndexCommit getIndexCommit() throws IOException {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
  

  public static Collection<IndexCommit> listCommits(Directory dir) throws IOException {
    return DirectoryReader.listCommits(dir);
  }


  public IndexReader[] getSequentialSubReaders() {
    ensureOpen();
    return null;
  }

  public Object getCoreCacheKey() {
    // Don't can ensureOpen since FC calls this (to evict)
    // on close
    return this;
  }

  public Object getDeletesCacheKey() {
    return this;
  }


  public long getUniqueTermCount() throws IOException {
    throw new UnsupportedOperationException("this reader does not implement getUniqueTermCount()");
  }

  // Back compat for reopen()
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod1 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen");
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod1 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged");
  @Deprecated
  private final boolean hasNewReopenAPI1 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod1, reopenMethod1) >= 0; // its ok for both to be overridden

  // Back compat for reopen(boolean openReadOnly)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod2 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", boolean.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod2 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", boolean.class);
  @Deprecated
  private final boolean hasNewReopenAPI2 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod2, reopenMethod2) >= 0; // its ok for both to be overridden

  // Back compat for reopen(IndexCommit commit)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod3 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", IndexCommit.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod3 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", IndexCommit.class);
  @Deprecated
  private final boolean hasNewReopenAPI3 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod3, reopenMethod3) >= 0; // its ok for both to be overridden

  // Back compat for reopen(IndexWriter writer, boolean applyDeletes)
  @Deprecated
  private static final VirtualMethod<IndexReader> reopenMethod4 =
    new VirtualMethod<IndexReader>(IndexReader.class, "reopen", IndexWriter.class, boolean.class);
  @Deprecated
  private static final VirtualMethod<IndexReader> doOpenIfChangedMethod4 =
    new VirtualMethod<IndexReader>(IndexReader.class, "doOpenIfChanged", IndexWriter.class, boolean.class);
  @Deprecated
  private final boolean hasNewReopenAPI4 =
    VirtualMethod.compareImplementationDistance(getClass(),
        doOpenIfChangedMethod4, reopenMethod4) >= 0; // its ok for both to be overridden


  public int getTermInfosIndexDivisor() {
    throw new UnsupportedOperationException("This reader does not support this method.");
  }
}
