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


import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocumentsWriterPerThread.IndexingChain;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexWriter.IndexReaderWarmer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Version;

public class LiveIndexWriterConfig {
  
  private final Analyzer analyzer;
  
  private volatile int maxBufferedDocs;
  private volatile double ramBufferSizeMB;
  private volatile IndexReaderWarmer mergedSegmentWarmer;

  // modified by IndexWriterConfig
  protected volatile IndexDeletionPolicy delPolicy;

  protected volatile IndexCommit commit;

  protected volatile OpenMode openMode;

  protected int createdVersionMajor = Version.LATEST.major;

  protected volatile Similarity similarity;

  protected volatile MergeScheduler mergeScheduler;

  protected volatile IndexingChain indexingChain;

  protected volatile Codec codec;

  protected volatile InfoStream infoStream;

  protected volatile MergePolicy mergePolicy;

  protected volatile boolean readerPooling;

  protected volatile FlushPolicy flushPolicy;

  protected volatile int perThreadHardLimitMB;

  protected volatile boolean useCompoundFile = IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM;
  
  protected boolean commitOnClose = IndexWriterConfig.DEFAULT_COMMIT_ON_CLOSE;

  protected Sort indexSort = null;

  protected Comparator<LeafReader> leafSorter;

  protected Set<String> indexSortFields = Collections.emptySet();

  protected volatile boolean checkPendingFlushOnUpdate = true;

  protected String softDeletesField = null;

  protected volatile long maxFullFlushMergeWaitMillis;

  // used by IndexWriterConfig
  LiveIndexWriterConfig(Analyzer analyzer) {
    this.analyzer = analyzer;
    ramBufferSizeMB = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;
    maxBufferedDocs = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS;
    mergedSegmentWarmer = null;
    delPolicy = new KeepOnlyLastCommitDeletionPolicy();
    commit = null;
    useCompoundFile = IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM;
    openMode = OpenMode.CREATE_OR_APPEND;
    similarity = IndexSearcher.getDefaultSimilarity();
    mergeScheduler = new ConcurrentMergeScheduler();
    indexingChain = DocumentsWriterPerThread.defaultIndexingChain;
    codec = Codec.getDefault();
    if (codec == null) {
      throw new NullPointerException();
    }
    infoStream = InfoStream.getDefault();
    mergePolicy = new TieredMergePolicy();
    flushPolicy = new FlushByRamOrCountsPolicy();
    readerPooling = IndexWriterConfig.DEFAULT_READER_POOLING;
    perThreadHardLimitMB = IndexWriterConfig.DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB;
    maxFullFlushMergeWaitMillis = IndexWriterConfig.DEFAULT_MAX_FULL_FLUSH_MERGE_WAIT_MILLIS;
  }
  
  public Analyzer getAnalyzer() {
    return analyzer;
  }

  public synchronized LiveIndexWriterConfig setRAMBufferSizeMB(double ramBufferSizeMB) {
    if (ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH && ramBufferSizeMB <= 0.0) {
      throw new IllegalArgumentException("ramBufferSize should be > 0.0 MB when enabled");
    }
    if (ramBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH
        && maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH) {
      throw new IllegalArgumentException("at least one of ramBufferSize and maxBufferedDocs must be enabled");
    }
    this.ramBufferSizeMB = ramBufferSizeMB;
    return this;
  }

  public double getRAMBufferSizeMB() {
    return ramBufferSizeMB;
  }
  
  public synchronized LiveIndexWriterConfig setMaxBufferedDocs(int maxBufferedDocs) {
    if (maxBufferedDocs != IndexWriterConfig.DISABLE_AUTO_FLUSH && maxBufferedDocs < 2) {
      throw new IllegalArgumentException("maxBufferedDocs must at least be 2 when enabled");
    }
    if (maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH
        && ramBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH) {
      throw new IllegalArgumentException("at least one of ramBufferSize and maxBufferedDocs must be enabled");
    }
    this.maxBufferedDocs = maxBufferedDocs;
    return this;
  }

  public int getMaxBufferedDocs() {
    return maxBufferedDocs;
  }

  public LiveIndexWriterConfig setMergePolicy(MergePolicy mergePolicy) {
    if (mergePolicy == null) {
      throw new IllegalArgumentException("mergePolicy must not be null");
    }
    this.mergePolicy = mergePolicy;
    return this;
  }

  public LiveIndexWriterConfig setMergedSegmentWarmer(IndexReaderWarmer mergeSegmentWarmer) {
    this.mergedSegmentWarmer = mergeSegmentWarmer;
    return this;
  }

  public IndexReaderWarmer getMergedSegmentWarmer() {
    return mergedSegmentWarmer;
  }
  
  public OpenMode getOpenMode() {
    return openMode;
  }

  public int getIndexCreatedVersionMajor() {
    return createdVersionMajor;
  }

  public IndexDeletionPolicy getIndexDeletionPolicy() {
    return delPolicy;
  }
  
  public IndexCommit getIndexCommit() {
    return commit;
  }

  public Similarity getSimilarity() {
    return similarity;
  }
  
  public MergeScheduler getMergeScheduler() {
    return mergeScheduler;
  }
  
  public Codec getCodec() {
    return codec;
  }

  public MergePolicy getMergePolicy() {
    return mergePolicy;
  }
  
  public boolean getReaderPooling() {
    return readerPooling;
  }

  IndexingChain getIndexingChain() {
    return indexingChain;
  }

  public int getRAMPerThreadHardLimitMB() {
    return perThreadHardLimitMB;
  }
  
  FlushPolicy getFlushPolicy() {
    return flushPolicy;
  }
  
  public InfoStream getInfoStream() {
    return infoStream;
  }
  
  public LiveIndexWriterConfig setUseCompoundFile(boolean useCompoundFile) {
    this.useCompoundFile = useCompoundFile;
    return this;
  }
  
  public boolean getUseCompoundFile() {
    return useCompoundFile ;
  }
  
  public boolean getCommitOnClose() {
    return commitOnClose;
  }

  public Sort getIndexSort() {
    return indexSort;
  }

  public Set<String> getIndexSortFields() {
    return indexSortFields;
  }

  public Comparator<LeafReader> getLeafSorter() {
    return leafSorter;
  }

  public boolean isCheckPendingFlushOnUpdate() {
    return checkPendingFlushOnUpdate;
  }

  public LiveIndexWriterConfig setCheckPendingFlushUpdate(boolean checkPendingFlushOnUpdate) {
    this.checkPendingFlushOnUpdate = checkPendingFlushOnUpdate;
    return this;
  }

  public String getSoftDeletesField() {
    return softDeletesField;
  }

  public long getMaxFullFlushMergeWaitMillis() {
    return maxFullFlushMergeWaitMillis;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("analyzer=").append(analyzer == null ? "null" : analyzer.getClass().getName()).append("\n");
    sb.append("ramBufferSizeMB=").append(getRAMBufferSizeMB()).append("\n");
    sb.append("maxBufferedDocs=").append(getMaxBufferedDocs()).append("\n");
    sb.append("mergedSegmentWarmer=").append(getMergedSegmentWarmer()).append("\n");
    sb.append("delPolicy=").append(getIndexDeletionPolicy().getClass().getName()).append("\n");
    IndexCommit commit = getIndexCommit();
    sb.append("commit=").append(commit == null ? "null" : commit).append("\n");
    sb.append("openMode=").append(getOpenMode()).append("\n");
    sb.append("similarity=").append(getSimilarity().getClass().getName()).append("\n");
    sb.append("mergeScheduler=").append(getMergeScheduler()).append("\n");
    sb.append("codec=").append(getCodec()).append("\n");
    sb.append("infoStream=").append(getInfoStream().getClass().getName()).append("\n");
    sb.append("mergePolicy=").append(getMergePolicy()).append("\n");
    sb.append("readerPooling=").append(getReaderPooling()).append("\n");
    sb.append("perThreadHardLimitMB=").append(getRAMPerThreadHardLimitMB()).append("\n");
    sb.append("useCompoundFile=").append(getUseCompoundFile()).append("\n");
    sb.append("commitOnClose=").append(getCommitOnClose()).append("\n");
    sb.append("indexSort=").append(getIndexSort()).append("\n");
    sb.append("checkPendingFlushOnUpdate=").append(isCheckPendingFlushOnUpdate()).append("\n");
    sb.append("softDeletesField=").append(getSoftDeletesField()).append("\n");
    sb.append("maxFullFlushMergeWaitMillis=").append(getMaxFullFlushMergeWaitMillis()).append("\n");
    sb.append("leafSorter=").append(getLeafSorter()).append("\n");
    return sb.toString();
  }
}
