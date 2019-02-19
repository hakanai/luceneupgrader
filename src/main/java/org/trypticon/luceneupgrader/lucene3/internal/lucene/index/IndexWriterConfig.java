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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.DocumentsWriter.IndexingChain;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriter.IndexReaderWarmer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Similarity;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

public final class IndexWriterConfig implements Cloneable {

  public static enum OpenMode { CREATE, APPEND, CREATE_OR_APPEND }
  
  public static final int DEFAULT_TERM_INDEX_INTERVAL = 128;

  public final static int DISABLE_AUTO_FLUSH = -1;

  public final static int DEFAULT_MAX_BUFFERED_DELETE_TERMS = DISABLE_AUTO_FLUSH;

  public final static int DEFAULT_MAX_BUFFERED_DOCS = DISABLE_AUTO_FLUSH;

  public final static double DEFAULT_RAM_BUFFER_SIZE_MB = 16.0;

  public static long WRITE_LOCK_TIMEOUT = 1000;


  public final static int DEFAULT_MAX_THREAD_STATES = 8;

  public final static boolean DEFAULT_READER_POOLING = false;

  public static final int DEFAULT_READER_TERMS_INDEX_DIVISOR = IndexReader.DEFAULT_TERMS_INDEX_DIVISOR;

  public static void setDefaultWriteLockTimeout(long writeLockTimeout) {
    WRITE_LOCK_TIMEOUT = writeLockTimeout;
  }

  public static long getDefaultWriteLockTimeout() {
    return WRITE_LOCK_TIMEOUT;
  }

  private final Analyzer analyzer;
  private volatile IndexDeletionPolicy delPolicy;
  private volatile IndexCommit commit;
  private volatile OpenMode openMode;
  private volatile Similarity similarity;
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

  public IndexWriterConfig(Version matchVersion, Analyzer analyzer) {
    this.matchVersion = matchVersion;
    this.analyzer = analyzer;
    delPolicy = new KeepOnlyLastCommitDeletionPolicy();
    commit = null;
    openMode = OpenMode.CREATE_OR_APPEND;
    similarity = Similarity.getDefault();
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

  public Analyzer getAnalyzer() {
    return analyzer;
  }


  public IndexWriterConfig setOpenMode(OpenMode openMode) {
    this.openMode = openMode;
    return this;
  }
  
  public OpenMode getOpenMode() {
    return openMode;
  }

  public IndexWriterConfig setIndexDeletionPolicy(IndexDeletionPolicy delPolicy) {
    this.delPolicy = delPolicy == null ? new KeepOnlyLastCommitDeletionPolicy() : delPolicy;
    return this;
  }

  public IndexDeletionPolicy getIndexDeletionPolicy() {
    return delPolicy;
  }


  public IndexWriterConfig setIndexCommit(IndexCommit commit) {
    this.commit = commit;
    return this;
  }

  public IndexCommit getIndexCommit() {
    return commit;
  }


  public IndexWriterConfig setSimilarity(Similarity similarity) {
    this.similarity = similarity == null ? Similarity.getDefault() : similarity;
    return this;
  }

  public Similarity getSimilarity() {
    return similarity;
  }
  

  public IndexWriterConfig setTermIndexInterval(int interval) {
    this.termIndexInterval = interval;
    return this;
  }

  public int getTermIndexInterval() {
    return termIndexInterval;
  }


  public IndexWriterConfig setMergeScheduler(MergeScheduler mergeScheduler) {
    this.mergeScheduler = mergeScheduler == null ? new ConcurrentMergeScheduler() : mergeScheduler;
    return this;
  }

  public MergeScheduler getMergeScheduler() {
    return mergeScheduler;
  }


  public IndexWriterConfig setWriteLockTimeout(long writeLockTimeout) {
    this.writeLockTimeout = writeLockTimeout;
    return this;
  }
  
  public long getWriteLockTimeout() {
    return writeLockTimeout;
  }

  public IndexWriterConfig setMaxBufferedDeleteTerms(int maxBufferedDeleteTerms) {
    if (maxBufferedDeleteTerms != DISABLE_AUTO_FLUSH
        && maxBufferedDeleteTerms < 1)
      throw new IllegalArgumentException(
          "maxBufferedDeleteTerms must at least be 1 when enabled");
    this.maxBufferedDeleteTerms = maxBufferedDeleteTerms;
    return this;
  }

  public int getMaxBufferedDeleteTerms() {
    return maxBufferedDeleteTerms;
  }

  public IndexWriterConfig setRAMBufferSizeMB(double ramBufferSizeMB) {
    if (ramBufferSizeMB > 2048.0) {
      throw new IllegalArgumentException("ramBufferSize " + ramBufferSizeMB
          + " is too large; should be comfortably less than 2048");
    }
    if (ramBufferSizeMB != DISABLE_AUTO_FLUSH && ramBufferSizeMB <= 0.0)
      throw new IllegalArgumentException(
          "ramBufferSize should be > 0.0 MB when enabled");
    if (ramBufferSizeMB == DISABLE_AUTO_FLUSH && maxBufferedDocs == DISABLE_AUTO_FLUSH)
      throw new IllegalArgumentException(
          "at least one of ramBufferSize and maxBufferedDocs must be enabled");
    this.ramBufferSizeMB = ramBufferSizeMB;
    return this;
  }

  public double getRAMBufferSizeMB() {
    return ramBufferSizeMB;
  }

  public IndexWriterConfig setMaxBufferedDocs(int maxBufferedDocs) {
    if (maxBufferedDocs != DISABLE_AUTO_FLUSH && maxBufferedDocs < 2)
      throw new IllegalArgumentException(
          "maxBufferedDocs must at least be 2 when enabled");
    if (maxBufferedDocs == DISABLE_AUTO_FLUSH
        && ramBufferSizeMB == DISABLE_AUTO_FLUSH)
      throw new IllegalArgumentException(
          "at least one of ramBufferSize and maxBufferedDocs must be enabled");
    this.maxBufferedDocs = maxBufferedDocs;
    return this;
  }

  public int getMaxBufferedDocs() {
    return maxBufferedDocs;
  }


  public IndexWriterConfig setMergedSegmentWarmer(IndexReaderWarmer mergeSegmentWarmer) {
    this.mergedSegmentWarmer = mergeSegmentWarmer;
    return this;
  }

  public IndexReaderWarmer getMergedSegmentWarmer() {
    return mergedSegmentWarmer;
  }


  public IndexWriterConfig setMergePolicy(MergePolicy mergePolicy) {
    this.mergePolicy = mergePolicy == null ? new LogByteSizeMergePolicy() : mergePolicy;
    return this;
  }
  
  public MergePolicy getMergePolicy() {
    return mergePolicy;
  }


  public IndexWriterConfig setMaxThreadStates(int maxThreadStates) {
    this.maxThreadStates = maxThreadStates < 1 ? DEFAULT_MAX_THREAD_STATES : maxThreadStates;
    return this;
  }

  public int getMaxThreadStates() {
    return maxThreadStates;
  }


  public IndexWriterConfig setReaderPooling(boolean readerPooling) {
    this.readerPooling = readerPooling;
    return this;
  }

  public boolean getReaderPooling() {
    return readerPooling;
  }


  IndexWriterConfig setIndexingChain(IndexingChain indexingChain) {
    this.indexingChain = indexingChain == null ? DocumentsWriter.defaultIndexingChain : indexingChain;
    return this;
  }
  
  IndexingChain getIndexingChain() {
    return indexingChain;
  }


  public IndexWriterConfig setReaderTermsIndexDivisor(int divisor) {
    if (divisor <= 0 && divisor != -1) {
      throw new IllegalArgumentException("divisor must be >= 1, or -1 (got " + divisor + ")");
    }
    readerTermsIndexDivisor = divisor;
    return this;
  }

  public int getReaderTermsIndexDivisor() {
    return readerTermsIndexDivisor;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("matchVersion=").append(matchVersion).append("\n");
    sb.append("analyzer=").append(analyzer == null ? "null" : analyzer.getClass().getName()).append("\n");
    sb.append("delPolicy=").append(delPolicy.getClass().getName()).append("\n");
    sb.append("commit=").append(commit == null ? "null" : commit).append("\n");
    sb.append("openMode=").append(openMode).append("\n");
    sb.append("similarity=").append(similarity.getClass().getName()).append("\n");
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
