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

import org.apache.lucene.index.DocumentsWriter.IndexingChain;
import org.apache.lucene.index.IndexWriter.IndexReaderWarmer;
import org.apache.lucene.util.Version;

/**
 * Holds all the configuration of {@code IndexWriter}.  You
 * should instantiate this class, call the setters to set
 * your configuration, then pass it to {@code IndexWriter}.
 * Note that {@code IndexWriter} makes a private clone; if
 * you need to subsequently change settings use {@code
 * IndexWriter#getConfig}.
 *
 * <p>
 * All setter methods return {@code IndexWriterConfig} to allow chaining
 * settings conveniently, for example:
 * 
 * <pre>
 * IndexWriterConfig conf = new IndexWriterConfig(analyzer);
 * conf.setter1().setter2();
 * </pre>
 * 
 * @since 3.1
 */
public final class IndexWriterConfig implements Cloneable {

  /**
   * Specifies the open mode for {@code IndexWriter}:
   * <ul>
   * {@code #CREATE} - creates a new index or overwrites an existing one.
   * {@code #CREATE_OR_APPEND} - creates a new index if one does not exist,
   * otherwise it opens the index and documents will be appended.
   * {@code #APPEND} - opens an existing index.
   * </ul>
   */
  public enum OpenMode { CREATE, APPEND, CREATE_OR_APPEND }
  
  /** Default value is 128. Change using {@code #setTermIndexInterval(int)}. */
  public static final int DEFAULT_TERM_INDEX_INTERVAL = 128;

  /** Denotes a flush trigger is disabled. */
  public final static int DISABLE_AUTO_FLUSH = -1;

  /** Disabled by default (because IndexWriter flushes by RAM usage by default). */
  public final static int DEFAULT_MAX_BUFFERED_DELETE_TERMS = DISABLE_AUTO_FLUSH;

  /** Disabled by default (because IndexWriter flushes by RAM usage by default). */
  public final static int DEFAULT_MAX_BUFFERED_DOCS = DISABLE_AUTO_FLUSH;

  /**
   * Default value is 16 MB (which means flush when buffered docs consume
   * approximately 16 MB RAM).
   */
  public final static double DEFAULT_RAM_BUFFER_SIZE_MB = 16.0;

  /**
   * Default value for the write lock timeout (1,000 ms).
   * 
   *
   */
  public static long WRITE_LOCK_TIMEOUT = 1000;

  /** The maximum number of simultaneous threads that may be
   *  indexing documents at once in IndexWriter; if more
   *  than this many threads arrive they will wait for
   *  others to finish. */
  public final static int DEFAULT_MAX_THREAD_STATES = 8;

  /** Default setting for {@code #setReaderPooling}. */
  public final static boolean DEFAULT_READER_POOLING = false;

  /** Default value is 1. Change using {@code #setReaderTermsIndexDivisor(int)}. */
  public static final int DEFAULT_READER_TERMS_INDEX_DIVISOR = IndexReader.DEFAULT_TERMS_INDEX_DIVISOR;

  private volatile IndexDeletionPolicy delPolicy;
  private volatile IndexCommit commit;
  private volatile OpenMode openMode;
  private volatile int termIndexInterval;
  private volatile MergeScheduler mergeScheduler;
  private volatile long writeLockTimeout;
  private volatile int maxBufferedDeleteTerms;
  private volatile double ramBufferSizeMB;
  private volatile int maxBufferedDocs;
  private volatile IndexingChain indexingChain;
  private volatile IndexReaderWarmer mergedSegmentWarmer;
  private volatile MergePolicy mergePolicy;
  private volatile int maxThreadStates;
  private volatile boolean readerPooling;
  private volatile int readerTermsIndexDivisor;
  
  private Version matchVersion;

  /**
   * Creates a new config that with defaults that match the specified
   * {@code Version} as well as the default {@code
   * Analyzer}. If matchVersion is >= {@code
   * Version#LUCENE_32}, {@code TieredMergePolicy} is used
   * for merging; else {@code LogByteSizeMergePolicy}.
   * Note that {@code TieredMergePolicy} is free to select
   * non-contiguous merges, which means docIDs may not
   * remain montonic over time.  If this is a problem you
   * should switch to {@code LogByteSizeMergePolicy} or
   * {@code LogDocMergePolicy}.
   */
  public IndexWriterConfig(Version matchVersion) {
    this.matchVersion = matchVersion;
    delPolicy = new KeepOnlyLastCommitDeletionPolicy();
    commit = null;
    openMode = OpenMode.CREATE_OR_APPEND;
    termIndexInterval = DEFAULT_TERM_INDEX_INTERVAL;
    mergeScheduler = new ConcurrentMergeScheduler();
    writeLockTimeout = WRITE_LOCK_TIMEOUT;
    maxBufferedDeleteTerms = DEFAULT_MAX_BUFFERED_DELETE_TERMS;
    ramBufferSizeMB = DEFAULT_RAM_BUFFER_SIZE_MB;
    maxBufferedDocs = DEFAULT_MAX_BUFFERED_DOCS;
    indexingChain = DocumentsWriter.defaultIndexingChain;
    mergedSegmentWarmer = null;
    if (matchVersion.onOrAfter(Version.LUCENE_32)) {
      mergePolicy = new TieredMergePolicy();
    } else {
      mergePolicy = new LogByteSizeMergePolicy();
    }
    maxThreadStates = DEFAULT_MAX_THREAD_STATES;
    readerPooling = DEFAULT_READER_POOLING;
    readerTermsIndexDivisor = DEFAULT_READER_TERMS_INDEX_DIVISOR;
  }
  
  @Override
  public Object clone() {
    // Shallow clone is the only thing that's possible, since parameters like
    // analyzer, index commit etc. do not implement Cloneable.
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      // should not happen
      throw new RuntimeException(e);
    }
  }

  /** Returns the {@code OpenMode} set by {@code #setOpenMode(OpenMode)}. */
  public OpenMode getOpenMode() {
    return openMode;
  }

  /**
   * Expert: allows an optional {@code IndexDeletionPolicy} implementation to be
   * specified. You can use this to control when prior commits are deleted from
   * the index. The default policy is {@code KeepOnlyLastCommitDeletionPolicy}
   * which removes all prior commits as soon as a new commit is done (this
   * matches behavior before 2.2). Creating your own policy can allow you to
   * explicitly keep previous "point in time" commits alive in the index for
   * some time, to allow readers to refresh to the new commit without having the
   * old commit deleted out from under them. This is necessary on filesystems
   * like NFS that do not support "delete on last close" semantics, which
   * Lucene's "point in time" search normally relies on.
   * <p>
   * <b>NOTE:</b> the deletion policy cannot be null. If <code>null</code> is
   * passed, the deletion policy will be set to the default.
   *
   * <p>Only takes effect when IndexWriter is first created. 
   */
  public void setIndexDeletionPolicy(IndexDeletionPolicy delPolicy) {
    this.delPolicy = delPolicy == null ? new KeepOnlyLastCommitDeletionPolicy() : delPolicy;
  }

  /**
   * Returns the {@code IndexDeletionPolicy} specified in
   * {@code #setIndexDeletionPolicy(IndexDeletionPolicy)} or the default
   * {@code KeepOnlyLastCommitDeletionPolicy}/
   */
  public IndexDeletionPolicy getIndexDeletionPolicy() {
    return delPolicy;
  }

  /**
   * Returns the {@code IndexCommit} as specified in
   * {@code #setIndexCommit(IndexCommit)} or the default, <code>null</code>
   * which specifies to open the latest index commit point.
   */
  public IndexCommit getIndexCommit() {
    return commit;
  }

  /**
   * Returns the interval between indexed terms.
   * 
   *
   */
  public int getTermIndexInterval() {
    return termIndexInterval;
  }

  /**
   * Returns the {@code MergeScheduler} that was set by
   * {@code #setMergeScheduler(MergeScheduler)}
   */
  public MergeScheduler getMergeScheduler() {
    return mergeScheduler;
  }

  /**
   * Returns allowed timeout when acquiring the write lock.
   * 
   *
   */
  public long getWriteLockTimeout() {
    return writeLockTimeout;
  }

  /** Returns the value set by {@code #setRAMBufferSizeMB(double)} if enabled. */
  public double getRAMBufferSizeMB() {
    return ramBufferSizeMB;
  }

  /** Returns the current merged segment warmer. See {@code IndexReaderWarmer}. */
  public IndexReaderWarmer getMergedSegmentWarmer() {
    return mergedSegmentWarmer;
  }

  /**
   * Expert: {@code MergePolicy} is invoked whenever there are changes to the
   * segments in the index. Its role is to select which merges to do, if any,
   * and return a {@code MergePolicy.MergeSpecification} describing the merges.
   * It also selects merges to do for forceMerge. (The default is
   * {@code LogByteSizeMergePolicy}.
   *
   * <p>Only takes effect when IndexWriter is first created. */
  public void setMergePolicy(MergePolicy mergePolicy) {
    this.mergePolicy = mergePolicy == null ? new LogByteSizeMergePolicy() : mergePolicy;
  }
  
  /**
   * Returns the current MergePolicy in use by this writer.
   * 
   *
   */
  public MergePolicy getMergePolicy() {
    return mergePolicy;
  }

  /** Returns true if IndexWriter should pool readers even
   *  if {@code IndexWriter#getReader} has not been called. */
  public boolean getReaderPooling() {
    return readerPooling;
  }

  /** Returns the indexing chain set on {@code #setIndexingChain(IndexingChain)}. */
  IndexingChain getIndexingChain() {
    return indexingChain;
  }

  /** */
  public int getReaderTermsIndexDivisor() {
    return readerTermsIndexDivisor;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("matchVersion=").append(matchVersion).append("\n");
    sb.append("delPolicy=").append(delPolicy.getClass().getName()).append("\n");
    sb.append("commit=").append(commit == null ? "null" : commit).append("\n");
    sb.append("openMode=").append(openMode).append("\n");
    sb.append("termIndexInterval=").append(termIndexInterval).append("\n");
    sb.append("mergeScheduler=").append(mergeScheduler.getClass().getName()).append("\n");
    sb.append("default WRITE_LOCK_TIMEOUT=").append(WRITE_LOCK_TIMEOUT).append("\n");
    sb.append("writeLockTimeout=").append(writeLockTimeout).append("\n");
    sb.append("maxBufferedDeleteTerms=").append(maxBufferedDeleteTerms).append("\n");
    sb.append("ramBufferSizeMB=").append(ramBufferSizeMB).append("\n");
    sb.append("maxBufferedDocs=").append(maxBufferedDocs).append("\n");
    sb.append("mergedSegmentWarmer=").append(mergedSegmentWarmer).append("\n");
    sb.append("mergePolicy=").append(mergePolicy).append("\n");
    sb.append("maxThreadStates=").append(maxThreadStates).append("\n");
    sb.append("readerPooling=").append(readerPooling).append("\n");
    sb.append("readerTermsIndexDivisor=").append(readerTermsIndexDivisor).append("\n");
    return sb.toString();
  }
}
