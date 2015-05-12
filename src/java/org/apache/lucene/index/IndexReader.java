package org.apache.lucene.index;

/**
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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** IndexReader is an abstract class, providing an interface for accessing an
 index.  Search of an index is done entirely through this abstract interface,
 so that any subclass which implements it is searchable.

 <p> Concrete subclasses of IndexReader are usually constructed with a call to
 one of the static <code>open()</code> methods, e.g. {@code
 #open(Directory, boolean)}.

 <p> For efficiency, in this API documents are often referred to via
 <i>document numbers</i>, non-negative integers which each name a unique
 document in the index.  These document numbers are ephemeral--they may change
 as documents are added to and deleted from an index.  Clients should thus not
 rely on a given document having the same number between sessions.

 <p> An IndexReader can be opened on a directory for which an IndexWriter is
 opened already, but it cannot be used to delete documents from the index then.

 <p>
 <b>NOTE</b>: for backwards API compatibility, several methods are not listed 
 as abstract, but have no useful implementations in this base class and 
 instead always throw UnsupportedOperationException.  Subclasses are 
 strongly encouraged to override these methods, but in many cases may not 
 need to.
 </p>

 <p>

 <b>NOTE</b>: as of 2.4, it's possible to open a read-only
 IndexReader using the static open methods that accept the 
 boolean readOnly parameter.  Such a reader has better 
 concurrency as it's not necessary to synchronize on the 
 isDeleted method.  You must specify false if you want to 
 make changes with the resulting IndexReader.
 </p>

 <a name="thread-safety"></a><p><b>NOTE</b>: {@code
 IndexReader} instances are completely thread
 safe, meaning multiple threads can call any of its methods,
 concurrently.  If your application requires external
 synchronization, you should <b>not</b> synchronize on the
 <code>IndexReader</code> instance; use your own
 (non-Lucene) objects instead.
*/
public abstract class IndexReader implements Cloneable,Closeable {

  /**
   * A custom listener that's invoked when the IndexReader
   * is closed.
   *
   * @lucene.experimental
   */
  public interface ReaderClosedListener {
    void onClose(IndexReader reader);
  }

  private final Set<ReaderClosedListener> readerClosedListeners = 
      Collections.synchronizedSet(new LinkedHashSet<ReaderClosedListener>());

  private void notifyReaderClosedListeners() {
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

  /** Expert: returns the current refCount for this reader */
  public final int getRefCount() {
    return refCount.get();
  }
  
  /**
   * Expert: increments the refCount of this IndexReader
   * instance.  RefCounts are used to determine when a
   * reader can be closed safely, i.e. as soon as there are
   * no more references.  Be sure to always call a
   * corresponding {@code #decRef}, in a finally clause;
   * otherwise the reader may never be closed.  Note that
   * {@code #close} simply calls decRef(), which means that
   * the IndexReader will not really be closed until {@code
   * #decRef} has been called for all outstanding
   * references.
   *
   *
   *
   */
  public final void incRef() {
    ensureOpen();
    refCount.incrementAndGet();
  }

  /** {@inheritDoc} */
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

  /**
   * Expert: decreases the refCount of this IndexReader
   * instance.  If the refCount drops to 0, then pending
   * changes (if any) are committed to the index and this
   * reader is closed.  If an exception is hit, the refCount
   * is unchanged.
   *
   * @throws IOException in case an IOException occurs in commit() or doClose()
   *
   *
   */
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
  
  /**
   * @throws AlreadyClosedException if this IndexReader is closed
   */
  protected final void ensureOpen() throws AlreadyClosedException {
    if (refCount.get() <= 0) {
      throw new AlreadyClosedException("this IndexReader is closed");
    }
  }

  /**
   * Efficiently clones the IndexReader (sharing most
   * internal state).
   * <p>
   * On cloning a reader with pending changes (deletions,
   * norms), the original reader transfers its write lock to
   * the cloned reader.  This means only the cloned reader
   * may make further changes to the index, and commit the
   * changes to the index on close, but the old reader still
   * reflects all changes made up until it was cloned.
   * <p>
   * Like {@code #openIfChanged(IndexReader)}, it's safe to make changes to
   * either the original or the cloned reader: all shared
   * mutable state obeys "copy on write" semantics to ensure
   * the changes are not seen by other readers.
   * <p>
   */
  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  public synchronized Object clone() {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }
  
  /**
   * Clones the IndexReader and optionally changes readOnly.  A readOnly 
   * reader cannot open a writeable reader.  
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@code #clone()} instead.
   */
  @Deprecated
  public synchronized IndexReader clone(boolean openReadOnly) throws IOException {
    throw new UnsupportedOperationException("This reader does not implement clone()");
  }

  /** 
   * Returns the directory associated with this index.  The Default 
   * implementation returns the directory specified by subclasses when 
   * delegating to the IndexReader(Directory) constructor, or throws an 
   * UnsupportedOperationException if one was not specified.
   * @throws UnsupportedOperationException if no directory
   */
  public Directory directory() {
    ensureOpen();
    throw new UnsupportedOperationException("This reader does not support this method.");  
  }


  /**
   * Return an array of term frequency vectors for the specified document.
   * The array contains a vector for each vectorized field in the document.
   * Each vector contains terms and frequencies for all terms in a given vectorized field.
   * If no such fields existed, the method returns null. The term vectors that are
   * returned may either be of type {@code TermFreqVector}
   * or of type {@code TermPositionVector} if
   * positions or offsets have been stored.
   * 
   * @param docNumber document for which term frequency vectors are returned
   * @return array of term frequency vectors. May be null if no term vectors have been
   *  stored for the specified document.
   * @throws IOException if index cannot be accessed
   *
   */
  abstract public TermFreqVector[] getTermFreqVectors(int docNumber)
          throws IOException;


  /**
   * Return a term frequency vector for the specified document and field. The
   * returned vector contains terms and frequencies for the terms in
   * the specified field of this document, if the field had the storeTermVector
   * flag set. If termvectors had been stored with positions or offsets, a 
   * {@code TermPositionVector} is returned.
   * 
   * @param docNumber document for which the term frequency vector is returned
   * @param field field for which the term frequency vector is returned.
   * @return term frequency vector May be null if field does not exist in the specified
   * document or term vector was not stored.
   * @throws IOException if index cannot be accessed
   *
   */
  abstract public TermFreqVector getTermFreqVector(int docNumber, String field)
          throws IOException;

  /**
   * Load the Term Vector into a user-defined data structure instead of relying on the parallel arrays of
   * the {@code TermFreqVector}.
   * @param docNumber The number of the document to load the vector for
   * @param field The name of the field to load
   * @param mapper The {@code TermVectorMapper} to process the vector.  Must not be null
   * @throws IOException if term vectors cannot be accessed or if they do not exist on the field and doc. specified.
   * 
   */
  abstract public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException;

  /**
   * Map all the term vectors for all fields in a Document
   * @param docNumber The number of the document to load the vector for
   * @param mapper The {@code TermVectorMapper} to process the vector.  Must not be null
   * @throws IOException if term vectors cannot be accessed or if they do not exist on the field and doc. specified.
   */
  abstract public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException;

  /**
   * Returns <code>true</code> if an index exists at the specified directory.
   * @param  directory the directory to check for an index
   * @return <code>true</code> if an index exists; <code>false</code> otherwise
   * @throws IOException if there is a problem with accessing the index
   */
  public static boolean indexExists(Directory directory) {
    try {
      new SegmentInfos().read(directory);
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  /** Returns the number of documents in this index. */
  public abstract int numDocs();

  /** Returns one greater than the largest possible document number.
   * This may be used to, e.g., determine how big to allocate an array which
   * will have an element for every document number in an index.
   */
  public abstract int maxDoc();

  /** Returns the number of deleted documents. */
  public final int numDeletedDocs() {
    return maxDoc() - numDocs();
  }

  /**
   * Returns the stored fields of the <code>n</code><sup>th</sup>
   * <code>Document</code> in this index.
   * <p>
   * <b>NOTE:</b> for performance reasons, this method does not check if the
   * requested document is deleted, and therefore asking for a deleted document
   * may yield unspecified results. Usually this is not required, however you
   * can call {@code #isDeleted(int)} with the requested document ID to verify
   * the document is not deleted.
   * 
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public final Document document(int n) throws IOException {
    ensureOpen();
    if (n < 0 || n >= maxDoc()) {
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc() + " (got docID=" + n + ")");
    }
    return document(n, null);
  }

  /**
   * Get the {@code org.apache.lucene.document.Document} at the <code>n</code>
   * <sup>th</sup> position. The {@code FieldSelector} may be used to determine
   * what {@code org.apache.lucene.document.Field}s to load and how they should
   * be loaded. <b>NOTE:</b> If this Reader (more specifically, the underlying
   * <code>FieldsReader</code>) is closed before the lazy
   * {@code org.apache.lucene.document.Field} is loaded an exception may be
   * thrown. If you want the value of a lazy
   * {@code org.apache.lucene.document.Field} to be available after closing you
   * must explicitly load it or fetch the Document again with a new loader.
   * <p>
   * <b>NOTE:</b> for performance reasons, this method does not check if the
   * requested document is deleted, and therefore asking for a deleted document
   * may yield unspecified results. Usually this is not required, however you
   * can call {@code #isDeleted(int)} with the requested document ID to verify
   * the document is not deleted.
   * 
   * @param n Get the document at the <code>n</code><sup>th</sup> position
   * @param fieldSelector The {@code FieldSelector} to use to determine what
   *        Fields should be loaded on the Document. May be null, in which case
   *        all Fields will be loaded.
   * @return The stored fields of the
   *         {@code org.apache.lucene.document.Document} at the nth position
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   *
   *
   *
   *
   */
  // TODO (1.5): When we convert to JDK 1.5 make this Set<String>
  public abstract Document document(int n, FieldSelector fieldSelector) throws IOException;
  
  /** Returns true if document <i>n</i> has been deleted */
  public abstract boolean isDeleted(int n);

  /** Returns true if any documents have been deleted */
  public abstract boolean hasDeletions();

  /** Returns true if there are norms stored for this field. */
  public boolean hasNorms(String field) throws IOException {
    // backward compatible implementation.
    // SegmentReader has an efficient implementation.
    ensureOpen();
    return norms(field) != null;
  }

  /** Returns the byte-encoded normalization factor for the named field of
   *  every document.  This is used by the search code to score documents.
   *  Returns null if norms were not indexed for this field.
   *
   *
   */
  public abstract byte[] norms(String field) throws IOException;

  /** Reads the byte-encoded normalization factor for the named field of every
   *  document.  This is used by the search code to score documents.
   *
   *
   */
  public abstract void norms(String field, byte[] bytes, int offset)
    throws IOException;

  /** Expert: Resets the normalization factor for the named field of the named
   * document.  The norm represents the product of the field's {@code
   * org.apache.lucene.document.Fieldable#setBoost(float) boost} and its {@code Similarity#lengthNorm(String,
   * int) length normalization}.  Thus, to preserve the length normalization
   * values when resetting this, one should base the new value upon the old.
   *
   * <b>NOTE:</b> If this field does not index norms, then
   * this method throws {@code IllegalStateException}.
   *
   *
   *
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @throws IllegalStateException if the field does not index norms
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  public final synchronized  void setNorm(int doc, String field, byte value)
          throws IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doSetNorm(doc, field, value);
  }

  /** Implements setNorm in subclass.
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  protected abstract void doSetNorm(int doc, String field, byte value)
          throws IOException;

  /** Returns an enumeration of all the terms in the index. The
   * enumeration is ordered by Term.compareTo(). Each term is greater
   * than all that precede it in the enumeration. Note that after
   * calling terms(), {@code TermEnum#next()} must be called
   * on the resulting enumeration before calling other methods such as
   * {@code TermEnum#term()}.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermEnum terms() throws IOException;

  /** Returns an enumeration of all terms starting at a given term. If
   * the given term does not exist, the enumeration is positioned at the
   * first term greater than the supplied term. The enumeration is
   * ordered by Term.compareTo(). Each term is greater than all that
   * precede it in the enumeration.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermEnum terms(Term t) throws IOException;

  /** Returns the number of documents containing the term <code>t</code>.
   * @throws IOException if there is a low-level IO error
   */
  public abstract int docFreq(Term t) throws IOException;

  /** Returns an enumeration of all the documents which contain
   * <code>term</code>. For each document, the document number, the frequency of
   * the term in that document is also provided, for use in
   * search scoring.  If term is null, then all non-deleted
   * docs are returned with freq=1.
   * Thus, this method implements the mapping:
   * <p><ul>
   * Term &nbsp;&nbsp; =&gt; &nbsp;&nbsp; &lt;docNum, freq&gt;<sup>*</sup>
   * </ul>
   * <p>The enumeration is ordered by document number.  Each document number
   * is greater than all that precede it in the enumeration.
   * @throws IOException if there is a low-level IO error
   */
  public TermDocs termDocs(Term term) throws IOException {
    ensureOpen();
    TermDocs termDocs = termDocs();
    termDocs.seek(term);
    return termDocs;
  }

  /** Returns an unpositioned {@code TermDocs} enumerator.
   * <p>
   * Note: the TermDocs returned is unpositioned. Before using it, ensure
   * that you first position it with {@code TermDocs#seek(Term)} or
   * {@code TermDocs#seek(TermEnum)}.
   * 
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermDocs termDocs() throws IOException;

  /** Returns an unpositioned {@code TermPositions} enumerator.
   * @throws IOException if there is a low-level IO error
   */
  public abstract TermPositions termPositions() throws IOException;



  /** Deletes the document numbered <code>docNum</code>.  Once a document is
   * deleted it will not appear in TermDocs or TermPostitions enumerations.
   * Attempts to read its field with the {@code #document}
   * method will result in an error.  The presence of this document may still be
   * reflected in the {@code #docFreq} statistic, though
   * this will be corrected eventually as the index is further modified.
   *
   * @throws StaleReaderException if the index has changed
   * since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@code IndexWriter#deleteDocuments(Term)} instead
   */
  @Deprecated
  public final synchronized void deleteDocument(int docNum) throws IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doDelete(docNum);
  }


  /** Implements deletion of the document numbered <code>docNum</code>.
   * Applications should call {@code #deleteDocument(int)} or {@code #deleteDocuments(Term)}.
   * @deprecated Write support will be removed in Lucene 4.0.
   * Use {@code IndexWriter#deleteDocuments(Term)} instead
   */
  @Deprecated
  protected abstract void doDelete(int docNum) throws IOException;


  /** Undeletes all documents currently marked as deleted in
   * this index.
   *
   * <p>NOTE: this method can only recover documents marked
   * for deletion but not yet removed from the index; when
   * and how Lucene removes deleted documents is an
   * implementation detail, subject to change from release
   * to release.  However, you can use {@code
   * #numDeletedDocs} on the current IndexReader instance to
   * see how many documents will be un-deleted.
   *
   * @throws StaleReaderException if the index has changed
   *  since this reader was opened
   * @throws LockObtainFailedException if another writer
   *  has this index open (<code>write.lock</code> could not
   *  be obtained)
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  public final synchronized void undeleteAll() throws IOException {
    ensureOpen();
    acquireWriteLock();
    hasChanges = true;
    doUndeleteAll();
  }

  /** Implements actual undeleteAll() in subclass.
   * @deprecated Write support will be removed in Lucene 4.0.
   * There will be no replacement for this method.
   */
  @Deprecated
  protected abstract void doUndeleteAll() throws IOException;

  /** Does nothing by default. Subclasses that require a write lock for
   *  index modifications must implement this method.
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected synchronized void acquireWriteLock() throws IOException {
    /* NOOP */
  }

  /**
   * Commit changes resulting from delete, undeleteAll, or
   * setNorm operations
   *
   * If an exception is hit, then either no changes or all
   * changes will have been committed to the index
   * (transactional semantics).
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected final synchronized void commit() throws IOException {
    commit(null);
  }
  
  /**
   * Commit changes resulting from delete, undeleteAll, or
   * setNorm operations
   *
   * If an exception is hit, then either no changes or all
   * changes will have been committed to the index
   * (transactional semantics).
   * @throws IOException if there is a low-level IO error
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  public final synchronized void commit(Map<String, String> commitUserData) throws IOException {
    // Don't call ensureOpen since we commit() on close
    doCommit(commitUserData);
    hasChanges = false;
  }

  /** Implements commit.
   * @deprecated Write support will be removed in Lucene 4.0.
   */
  @Deprecated
  protected abstract void doCommit(Map<String, String> commitUserData) throws IOException;

  /**
   * Closes files associated with this index.
   * Also saves any new deletions to disk.
   * No other methods should be called after this has been called.
   * @throws IOException if there is a low-level IO error
   */
  public final synchronized void close() throws IOException {
    if (!closed) {
      decRef();
      closed = true;
    }
  }
  
  /** Implements close. */
  protected abstract void doClose() throws IOException;

  /**
   * Get the {@code FieldInfos} describing all fields in
   * this reader.  NOTE: do not make any changes to the
   * returned FieldInfos!
   *
   * @lucene.experimental
   */
  public abstract FieldInfos getFieldInfos();

  /** Returns all commit points that exist in the Directory.
   *  Normally, because the default is {@code
   *  KeepOnlyLastCommitDeletionPolicy}, there would be only
   *  one commit point.  But if you're using a custom {@code
   *  IndexDeletionPolicy} then there could be many commits.
   *  Once you have a given commit, you can open a reader on
   *  it by calling {@code IndexReader#open(IndexCommit,boolean)}
   *  There must be at least one commit in
   *  the Directory, else this method throws {@code
   *  IndexNotFoundException}.  Note that if a commit is in
   *  progress while this method is running, that commit
   *  may or may not be returned.
   *  
   *  @return a sorted list of {@code IndexCommit}s, from oldest
   *  to latest. */
  public static Collection<IndexCommit> listCommits(Directory dir) throws IOException {
    return DirectoryReader.listCommits(dir);
  }

  /** Expert: returns the sequential sub readers that this
   *  reader is logically composed of.  For example,
   *  IndexSearcher uses this API to drive searching by one
   *  sub reader at a time.  If this reader is not composed
   *  of sequential child readers, it should return null.
   *  If this method returns an empty array, that means this
   *  reader is a null reader (for example a MultiReader
   *  that has no sub readers).
   *  <p>
   *  NOTE: You should not try using sub-readers returned by
   *  this method to make any changes (setNorm, deleteDocument,
   *  etc.). While this might succeed for one composite reader
   *  (like MultiReader), it will most likely lead to index
   *  corruption for other readers (like DirectoryReader obtained
   *  through {@code #open}. Use the parent reader directly. */
  public IndexReader[] getSequentialSubReaders() {
    ensureOpen();
    return null;
  }

}
