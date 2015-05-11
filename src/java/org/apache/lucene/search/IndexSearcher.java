package org.apache.lucene.search;

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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.ThreadInterruptedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;

/** Implements search over a single IndexReader.
 *
 * <p>Applications usually need only call the inherited
 * {@code #search(Query,int)}
 * or {@code #search(Query,Filter,int)} methods. For
 * performance reasons, if your index is unchanging, you
 * should share a single IndexSearcher instance across
 * multiple searches instead of creating a new one
 * per-search.  If your index has changed and you wish to
 * see the changes reflected in searching, you should
 * use {@code IndexReader#openIfChanged} to obtain a new reader and
 * then create a new IndexSearcher from that.  Also, for
 * low-latency turnaround it's best to use a near-real-time
 * reader ({@code IndexReader#open(IndexWriter,boolean)}).
 * Once you have a new {@code IndexReader}, it's relatively
 * cheap to create a new IndexSearcher from it.
 * 
 * <a name="thread-safety"></a><p><b>NOTE</b>: <code>{@code
 * IndexSearcher}</code> instances are completely
 * thread safe, meaning multiple threads can call any of its
 * methods, concurrently.  If your application requires
 * external synchronization, you should <b>not</b>
 * synchronize on the <code>IndexSearcher</code> instance;
 * use your own (non-Lucene) objects instead.</p>
 */
public class IndexSearcher extends Searcher {
  IndexReader reader;
  private boolean closeReader;
  
  // NOTE: these members might change in incompatible ways
  // in the next release
  protected final IndexReader[] subReaders;
  protected final int[] docStarts;
  
  // These are only used for multi-threaded search
  private final ExecutorService executor;
  protected final IndexSearcher[] subSearchers;

  private final int docBase;

  /** Creates a searcher searching the provided index. */
  public IndexSearcher(IndexReader r) {
    this(r, false, null);
  }

  // Used only when we are an atomic sub-searcher in a parent
  // IndexSearcher that has an ExecutorService, to record
  // our docBase in the parent IndexSearcher:
  private IndexSearcher(IndexReader r, int docBase) {
    reader = r;
    this.executor = null;
    closeReader = false;
    this.docBase = docBase;
    subReaders = new IndexReader[] {r};
    docStarts = new int[] {0};
    subSearchers = null;
  }

  private IndexSearcher(IndexReader r, boolean closeReader, ExecutorService executor) {
    reader = r;
    this.executor = executor;
    this.closeReader = closeReader;

    List<IndexReader> subReadersList = new ArrayList<IndexReader>();
    gatherSubReaders(subReadersList, reader);
    subReaders = subReadersList.toArray(new IndexReader[subReadersList.size()]);
    docStarts = new int[subReaders.length];
    int maxDoc = 0;
    for (int i = 0; i < subReaders.length; i++) {
      docStarts[i] = maxDoc;
      maxDoc += subReaders[i].maxDoc();
    }
    if (executor == null) {
      subSearchers = null;
    } else {
      subSearchers = new IndexSearcher[subReaders.length];
      for (int i = 0; i < subReaders.length; i++) {
        subSearchers[i] = new IndexSearcher(subReaders[i], docStarts[i]);
      }
    }
    docBase = 0;
  }

  protected void gatherSubReaders(List<IndexReader> allSubReaders, IndexReader r) {
    ReaderUtil.gatherSubReaders(allSubReaders, r);
  }

  /** Expert: Returns one greater than the largest possible document number.
   * 
   *
   */
  @Override
  public int maxDoc() {
    return reader.maxDoc();
  }

  /** Returns total docFreq for this term. */
  @Override
  public int docFreq(final Term term) throws IOException {
    if (executor == null) {
      return reader.docFreq(term);
    } else {
      final ExecutionHelper<Integer> runner = new ExecutionHelper<Integer>(executor);
      for(int i = 0; i < subReaders.length; i++) {
        final IndexSearcher searchable = subSearchers[i];
        runner.submit(new Callable<Integer>() {
            public Integer call() throws IOException {
              return searchable.docFreq(term);
            }
          });
      }
      int docFreq = 0;
      for (Integer num : runner) {
        docFreq += num.intValue();
      }
      return docFreq;
    }
  }

  /* Sugar for .getIndexReader().document(docID) */
  @Override
  public Document doc(int docID) throws IOException {
    return reader.document(docID);
  }
  
  /* Sugar for .getIndexReader().document(docID, fieldSelector) */
  @Override
  public Document doc(int docID, FieldSelector fieldSelector) throws IOException {
    return reader.document(docID, fieldSelector);
  }
  
  /** Expert: Set the Similarity implementation used by this Searcher.
   *
   *
   */
  @Override
  public void setSimilarity(Similarity similarity) {
    super.setSimilarity(similarity);
  }

  @Override
  public Similarity getSimilarity() {
    return super.getSimilarity();
  }

  /**
   * Note that the underlying IndexReader is not closed, if
   * IndexSearcher was constructed with IndexSearcher(IndexReader r).
   * If the IndexReader was supplied implicitly by specifying a directory, then
   * the IndexReader is closed.
   */
  @Override
  public void close() throws IOException {
    if (closeReader) {
      reader.close();
    }
  }


  /**
   * Lower-level search API.
   * 
   * <p>
   * {@code Collector#collect(int)} is called for every document. <br>
   * Collector-based access to remote indexes is discouraged.
   * 
   * <p>
   * Applications should only use this if they need <i>all</i> of the matching
   * documents. The high-level search API ({@code Searcher#search(Query,int)}) is
   * usually more efficient, as it skips non-high-scoring hits.
   * 
   * @param weight
   *          to match documents
   * @param filter
   *          if non-null, used to permit documents to be collected.
   * @param collector
   *          to receive hits
   * @throws BooleanQuery.TooManyClauses
   */
  @Override
  public void search(Weight weight, Filter filter, Collector collector)
      throws IOException {

    // TODO: should we make this
    // threaded...?  the Collector could be sync'd?

    // always use single thread:
    for (int i = 0; i < subReaders.length; i++) { // search each subreader
      collector.setNextReader(subReaders[i], docBase + docStarts[i]);
      final Scorer scorer = (filter == null) ?
        weight.scorer(subReaders[i], !collector.acceptsDocsOutOfOrder(), true) :
        FilteredQuery.getFilteredScorer(subReaders[i], getSimilarity(), weight, weight, filter);
      if (scorer != null) {
        scorer.score(collector);
      }
    }
  }

  /** Expert: called to re-write queries into primitive queries.
   * @throws BooleanQuery.TooManyClauses
   */
  @Override
  public Query rewrite(Query original) throws IOException {
    Query query = original;
    for (Query rewrittenQuery = query.rewrite(reader); rewrittenQuery != query;
         rewrittenQuery = query.rewrite(reader)) {
      query = rewrittenQuery;
    }
    return query;
  }

  /**
   * Creates a normalized weight for a top-level {@code Query}.
   * The query is rewritten by this method and {@code Query#createWeight} called,
   * afterwards the {@code Weight} is normalized. The returned {@code Weight}
   * can then directly be used to get a {@code Scorer}.
   * @lucene.internal
   */
  public Weight createNormalizedWeight(Query query) throws IOException {
    return super.createNormalizedWeight(query);
  }


  /**
   * A helper class that wraps a {@code CompletionService} and provides an
   * iterable interface to the completed {@code Callable} instances.
   * 
   * @param <T>
   *          the type of the {@code Callable} return value
   */
  private static final class ExecutionHelper<T> implements Iterator<T>, Iterable<T> {
    private final CompletionService<T> service;
    private int numTasks;

    ExecutionHelper(final Executor executor) {
      this.service = new ExecutorCompletionService<T>(executor);
    }

    public boolean hasNext() {
      return numTasks > 0;
    }

    public void submit(Callable<T> task) {
      this.service.submit(task);
      ++numTasks;
    }

    public T next() {
      if(!this.hasNext())
        throw new NoSuchElementException();
      try {
        return service.take().get();
      } catch (InterruptedException e) {
        throw new ThreadInterruptedException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      } finally {
        --numTasks;
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    public Iterator<T> iterator() {
      // use the shortcut here - this is only used in a private context
      return this;
    }
  }

  @Override
  public String toString() {
    return "IndexSearcher(" + reader + ")";
  }
}
