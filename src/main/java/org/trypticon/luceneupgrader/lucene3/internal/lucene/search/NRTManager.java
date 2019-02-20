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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader; // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

public class NRTManager extends ReferenceManager<IndexSearcher> {
  private static final long MAX_SEARCHER_GEN = Long.MAX_VALUE;
  private final TrackingIndexWriter writer;
  private final List<WaitingListener> waitingListeners = new CopyOnWriteArrayList<WaitingListener>();
  private final ReentrantLock genLock = new ReentrantLock();;
  private final Condition newGeneration = genLock.newCondition();
  private final SearcherFactory searcherFactory;

  private volatile long searchingGen;

  public NRTManager(TrackingIndexWriter writer, SearcherFactory searcherFactory) throws IOException {
    this(writer, searcherFactory, true);
  }


  public NRTManager(TrackingIndexWriter writer, SearcherFactory searcherFactory, boolean applyAllDeletes) throws IOException {
    this.writer = writer;
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = SearcherManager.getSearcher(searcherFactory, IndexReader.open(writer.getIndexWriter(), applyAllDeletes));
  }

  @Override
  protected void decRef(IndexSearcher reference) throws IOException {
    reference.getIndexReader().decRef();
  }
  
  @Override
  protected boolean tryIncRef(IndexSearcher reference) {
    return reference.getIndexReader().tryIncRef();
  }


  public static interface WaitingListener {
    public void waiting(long targetGen);
  }


  public void addWaitingListener(WaitingListener l) {
    waitingListeners.add(l);
  }

  public void removeWaitingListener(WaitingListener l) {
    waitingListeners.remove(l);
  }


  public static class TrackingIndexWriter {
    private final IndexWriter writer;
    private final AtomicLong indexingGen = new AtomicLong(1);

    public TrackingIndexWriter(IndexWriter writer) {
      this.writer = writer;
    }

    public long updateDocument(Term t, Document d, Analyzer a) throws IOException {
      writer.updateDocument(t, d, a);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long updateDocument(Term t, Document d) throws IOException {
      writer.updateDocument(t, d);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long updateDocuments(Term t, Collection<Document> docs, Analyzer a) throws IOException {
      writer.updateDocuments(t, docs, a);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long updateDocuments(Term t, Collection<Document> docs) throws IOException {
      writer.updateDocuments(t, docs);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long deleteDocuments(Term t) throws IOException {
      writer.deleteDocuments(t);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long deleteDocuments(Term... terms) throws IOException {
      writer.deleteDocuments(terms);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long deleteDocuments(Query q) throws IOException {
      writer.deleteDocuments(q);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long deleteDocuments(Query... queries) throws IOException {
      writer.deleteDocuments(queries);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long deleteAll() throws IOException {
      writer.deleteAll();
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addDocument(Document d, Analyzer a) throws IOException {
      writer.addDocument(d, a);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addDocuments(Collection<Document> docs, Analyzer a) throws IOException {
      writer.addDocuments(docs, a);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addDocument(Document d) throws IOException {
      writer.addDocument(d);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addDocuments(Collection<Document> docs) throws IOException {
      writer.addDocuments(docs);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addIndexes(Directory... dirs) throws CorruptIndexException, IOException {
      writer.addIndexes(dirs);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long addIndexes(IndexReader... readers) throws CorruptIndexException, IOException {
      writer.addIndexes(readers);
      // Return gen as of when indexing finished:
      return indexingGen.get();
    }

    public long getGeneration() {
      return indexingGen.get();
    }

    public IndexWriter getIndexWriter() {
      return writer;
    }

    long getAndIncrementGeneration() {
      return indexingGen.getAndIncrement();
    }
  }

  public void waitForGeneration(long targetGen) {
    waitForGeneration(targetGen, -1, TimeUnit.NANOSECONDS);
  }

  public void waitForGeneration(long targetGen, long time, TimeUnit unit) {
    try {
      final long curGen = writer.getGeneration();
      if (targetGen > curGen) {
        throw new IllegalArgumentException("targetGen=" + targetGen + " was never returned by this NRTManager instance (current gen=" + curGen + ")");
      }
      genLock.lockInterruptibly();
      try {
        if (targetGen > searchingGen) {
          for (WaitingListener listener : waitingListeners) {
            listener.waiting(targetGen);
          }
          while (targetGen > searchingGen) {
            if (!waitOnGenCondition(time, unit)) {
              return;
            }
          }
        }
      } finally {
        genLock.unlock();
      }
    } catch (InterruptedException ie) {
      throw new ThreadInterruptedException(ie);
    }
  }
  
  private boolean waitOnGenCondition(long time, TimeUnit unit)
      throws InterruptedException {
    assert genLock.isHeldByCurrentThread();
    if (time < 0) {
      newGeneration.await();
      return true;
    } else {
      return newGeneration.await(time, unit);
    }
  }

  public long getCurrentSearchingGen() {
    return searchingGen;
  }

  private long lastRefreshGen;

  @Override
  protected IndexSearcher refreshIfNeeded(IndexSearcher referenceToRefresh) throws IOException {
    // Record gen as of when reopen started:
    lastRefreshGen = writer.getAndIncrementGeneration();
    final IndexReader r = referenceToRefresh.getIndexReader();
    IndexSearcher newSearcher = null;
    if (!r.isCurrent()) {
      final IndexReader newReader = IndexReader.openIfChanged(r);
      if (newReader != null) {
        newSearcher = SearcherManager.getSearcher(searcherFactory, newReader);
      }
    }

    return newSearcher;
  }

  @Override
  protected void afterRefresh() {
    genLock.lock();
    try {
      if (searchingGen != MAX_SEARCHER_GEN) {
        // update searchingGen:
        assert lastRefreshGen >= searchingGen;
        searchingGen = lastRefreshGen;
      }
      // wake up threads if we have a new generation:
      newGeneration.signalAll();
    } finally {
      genLock.unlock();
    }
  }

  @Override
  protected synchronized void afterClose() throws IOException {
    genLock.lock();
    try {
      // max it out to make sure nobody can wait on another gen
      searchingGen = MAX_SEARCHER_GEN; 
      newGeneration.signalAll();
    } finally {
      genLock.unlock();
    }
  }

  public boolean isSearcherCurrent() throws IOException {
    final IndexSearcher searcher = acquire();
    try {
      return searcher.getIndexReader().isCurrent();
    } finally {
      release(searcher);
    }
  }
}
