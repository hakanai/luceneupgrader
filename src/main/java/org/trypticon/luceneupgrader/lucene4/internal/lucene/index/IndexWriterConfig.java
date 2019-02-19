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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocumentsWriterPerThread.IndexingChain;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriter.IndexReaderWarmer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.PrintStreamInfoStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.SetOnce;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.SetOnce.AlreadySetException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;

import java.io.PrintStream;

public final class IndexWriterConfig extends LiveIndexWriterConfig {

  public static enum OpenMode {
    CREATE,
    
    APPEND,
    
    CREATE_OR_APPEND
  }

  public static final int DEFAULT_TERM_INDEX_INTERVAL = 32; // TODO: this should be private to the codec, not settable here

  public final static int DISABLE_AUTO_FLUSH = -1;

  public final static int DEFAULT_MAX_BUFFERED_DELETE_TERMS = DISABLE_AUTO_FLUSH;

  public final static int DEFAULT_MAX_BUFFERED_DOCS = DISABLE_AUTO_FLUSH;

  public final static double DEFAULT_RAM_BUFFER_SIZE_MB = 16.0;

  public static long WRITE_LOCK_TIMEOUT = 1000;

  public final static boolean DEFAULT_READER_POOLING = false;

  public static final int DEFAULT_READER_TERMS_INDEX_DIVISOR = DirectoryReader.DEFAULT_TERMS_INDEX_DIVISOR;

  public static final int DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB = 1945;
  

  public final static int DEFAULT_MAX_THREAD_STATES = 8;
  

  public final static boolean DEFAULT_USE_COMPOUND_FILE_SYSTEM = true;
  

  public final static boolean DEFAULT_CHECK_INTEGRITY_AT_MERGE = false;
  
  public static void setDefaultWriteLockTimeout(long writeLockTimeout) {
    WRITE_LOCK_TIMEOUT = writeLockTimeout;
  }

  public static long getDefaultWriteLockTimeout() {
    return WRITE_LOCK_TIMEOUT;
  }

  // indicates whether this config instance is already attached to a writer.
  // not final so that it can be cloned properly.
  private SetOnce<IndexWriter> writer = new SetOnce<>();
  
  IndexWriterConfig setIndexWriter(IndexWriter writer) {
    this.writer.set(writer);
    return this;
  }
  
  public IndexWriterConfig(Version matchVersion, Analyzer analyzer) {
    super(analyzer, matchVersion);
  }


  public IndexWriterConfig setOpenMode(OpenMode openMode) {
    if (openMode == null) {
      throw new IllegalArgumentException("openMode must not be null");
    }
    this.openMode = openMode;
    return this;
  }

  @Override
  public OpenMode getOpenMode() {
    return openMode;
  }

  public IndexWriterConfig setIndexDeletionPolicy(IndexDeletionPolicy delPolicy) {
    if (delPolicy == null) {
      throw new IllegalArgumentException("indexDeletionPolicy must not be null");
    }
    this.delPolicy = delPolicy;
    return this;
  }

  @Override
  public IndexDeletionPolicy getIndexDeletionPolicy() {
    return delPolicy;
  }


  public IndexWriterConfig setIndexCommit(IndexCommit commit) {
    this.commit = commit;
    return this;
  }

  @Override
  public IndexCommit getIndexCommit() {
    return commit;
  }


  public IndexWriterConfig setSimilarity(Similarity similarity) {
    if (similarity == null) {
      throw new IllegalArgumentException("similarity must not be null");
    }
    this.similarity = similarity;
    return this;
  }

  @Override
  public Similarity getSimilarity() {
    return similarity;
  }


  public IndexWriterConfig setMergeScheduler(MergeScheduler mergeScheduler) {
    if (mergeScheduler == null) {
      throw new IllegalArgumentException("mergeScheduler must not be null");
    }
    this.mergeScheduler = mergeScheduler;
    return this;
  }

  @Override
  public MergeScheduler getMergeScheduler() {
    return mergeScheduler;
  }


  public IndexWriterConfig setWriteLockTimeout(long writeLockTimeout) {
    this.writeLockTimeout = writeLockTimeout;
    return this;
  }

  @Override
  public long getWriteLockTimeout() {
    return writeLockTimeout;
  }

  public IndexWriterConfig setCodec(Codec codec) {
    if (codec == null) {
      throw new IllegalArgumentException("codec must not be null");
    }
    this.codec = codec;
    return this;
  }

  @Override
  public Codec getCodec() {
    return codec;
  }


  @Override
  public MergePolicy getMergePolicy() {
    return mergePolicy;
  }


  IndexWriterConfig setIndexerThreadPool(DocumentsWriterPerThreadPool threadPool) {
    if (threadPool == null) {
      throw new IllegalArgumentException("threadPool must not be null");
    }
    this.indexerThreadPool = threadPool;
    return this;
  }

  @Override
  DocumentsWriterPerThreadPool getIndexerThreadPool() {
    return indexerThreadPool;
  }


  public IndexWriterConfig setMaxThreadStates(int maxThreadStates) {
    this.indexerThreadPool = new DocumentsWriterPerThreadPool(maxThreadStates);
    return this;
  }

  @Override
  public int getMaxThreadStates() {
    return indexerThreadPool.getMaxThreadStates();
  }


  public IndexWriterConfig setReaderPooling(boolean readerPooling) {
    this.readerPooling = readerPooling;
    return this;
  }

  @Override
  public boolean getReaderPooling() {
    return readerPooling;
  }


  IndexWriterConfig setIndexingChain(IndexingChain indexingChain) {
    if (indexingChain == null) {
      throw new IllegalArgumentException("indexingChain must not be null");
    }
    this.indexingChain = indexingChain;
    return this;
  }

  @Override
  IndexingChain getIndexingChain() {
    return indexingChain;
  }

  IndexWriterConfig setFlushPolicy(FlushPolicy flushPolicy) {
    if (flushPolicy == null) {
      throw new IllegalArgumentException("flushPolicy must not be null");
    }
    this.flushPolicy = flushPolicy;
    return this;
  }

  public IndexWriterConfig setRAMPerThreadHardLimitMB(int perThreadHardLimitMB) {
    if (perThreadHardLimitMB <= 0 || perThreadHardLimitMB >= 2048) {
      throw new IllegalArgumentException("PerThreadHardLimit must be greater than 0 and less than 2048MB");
    }
    this.perThreadHardLimitMB = perThreadHardLimitMB;
    return this;
  }

  @Override
  public int getRAMPerThreadHardLimitMB() {
    return perThreadHardLimitMB;
  }
  
  @Override
  FlushPolicy getFlushPolicy() {
    return flushPolicy;
  }
  
  @Override
  public InfoStream getInfoStream() {
    return infoStream;
  }
  
  @Override
  public Analyzer getAnalyzer() {
    return super.getAnalyzer();
  }
  
  @Override
  public int getMaxBufferedDeleteTerms() {
    return super.getMaxBufferedDeleteTerms();
  }
  
  @Override
  public int getMaxBufferedDocs() {
    return super.getMaxBufferedDocs();
  }
  
  @Override
  public IndexReaderWarmer getMergedSegmentWarmer() {
    return super.getMergedSegmentWarmer();
  }
  
  @Override
  public double getRAMBufferSizeMB() {
    return super.getRAMBufferSizeMB();
  }
  
  @Override
  public int getReaderTermsIndexDivisor() {
    return super.getReaderTermsIndexDivisor();
  }
  
  @Override
  public int getTermIndexInterval() {
    return super.getTermIndexInterval();
  }
  

  public IndexWriterConfig setInfoStream(InfoStream infoStream) {
    if (infoStream == null) {
      throw new IllegalArgumentException("Cannot set InfoStream implementation to null. "+
        "To disable logging use InfoStream.NO_OUTPUT");
    }
    this.infoStream = infoStream;
    return this;
  }
  

  public IndexWriterConfig setInfoStream(PrintStream printStream) {
    if (printStream == null) {
      throw new IllegalArgumentException("printStream must not be null");
    }
    return setInfoStream(new PrintStreamInfoStream(printStream));
  }
  
  @Override
  public IndexWriterConfig setMergePolicy(MergePolicy mergePolicy) {
    return (IndexWriterConfig) super.setMergePolicy(mergePolicy);
  }
  
  @Override
  public IndexWriterConfig setMaxBufferedDeleteTerms(int maxBufferedDeleteTerms) {
    return (IndexWriterConfig) super.setMaxBufferedDeleteTerms(maxBufferedDeleteTerms);
  }
  
  @Override
  public IndexWriterConfig setMaxBufferedDocs(int maxBufferedDocs) {
    return (IndexWriterConfig) super.setMaxBufferedDocs(maxBufferedDocs);
  }
  
  @Override
  public IndexWriterConfig setMergedSegmentWarmer(IndexReaderWarmer mergeSegmentWarmer) {
    return (IndexWriterConfig) super.setMergedSegmentWarmer(mergeSegmentWarmer);
  }
  
  @Override
  public IndexWriterConfig setRAMBufferSizeMB(double ramBufferSizeMB) {
    return (IndexWriterConfig) super.setRAMBufferSizeMB(ramBufferSizeMB);
  }
  
  @Override
  public IndexWriterConfig setReaderTermsIndexDivisor(int divisor) {
    return (IndexWriterConfig) super.setReaderTermsIndexDivisor(divisor);
  }
  
  @Override
  public IndexWriterConfig setTermIndexInterval(int interval) {
    return (IndexWriterConfig) super.setTermIndexInterval(interval);
  }
  
  @Override
  public IndexWriterConfig setUseCompoundFile(boolean useCompoundFile) {
    return (IndexWriterConfig) super.setUseCompoundFile(useCompoundFile);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(super.toString());
    sb.append("writer=").append(writer).append("\n");
    return sb.toString();
  }
  
}
