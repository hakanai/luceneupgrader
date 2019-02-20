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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DirectoryReader; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiFields;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.ReaderUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.DefaultSimilarity;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.NIOFSDirectory;    // javadoc
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ThreadInterruptedException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriter; // javadocs

public class IndexSearcher {
  final IndexReader reader; // package private for testing!
  
  // NOTE: these members might change in incompatible ways
  // in the next release
  protected final IndexReaderContext readerContext;
  protected final List<AtomicReaderContext> leafContexts;
  protected final LeafSlice[] leafSlices;

  // These are only used for multi-threaded search
  private final ExecutorService executor;

  // the default Similarity
  private static final Similarity defaultSimilarity = new DefaultSimilarity();
  
  public static Similarity getDefaultSimilarity() {
    return defaultSimilarity;
  }
  
  private Similarity similarity = defaultSimilarity;

  public IndexSearcher(IndexReader r) {
    this(r, null);
  }


  public IndexSearcher(IndexReader r, ExecutorService executor) {
    this(r.getContext(), executor);
  }

  public IndexSearcher(IndexReaderContext context, ExecutorService executor) {
    assert context.isTopLevel: "IndexSearcher's ReaderContext must be topLevel for reader" + context.reader();
    reader = context.reader();
    this.executor = executor;
    this.readerContext = context;
    leafContexts = context.leaves();
    this.leafSlices = executor == null ? null : slices(leafContexts);
  }

  public IndexSearcher(IndexReaderContext context) {
    this(context, null);
  }
  
  protected LeafSlice[] slices(List<AtomicReaderContext> leaves) {
    LeafSlice[] slices = new LeafSlice[leaves.size()];
    for (int i = 0; i < slices.length; i++) {
      slices[i] = new LeafSlice(leaves.get(i));
    }
    return slices;
  }

  
  public IndexReader getIndexReader() {
    return reader;
  }


  public Document doc(int docID) throws IOException {
    return reader.document(docID);
  }


  public void doc(int docID, StoredFieldVisitor fieldVisitor) throws IOException {
    reader.document(docID, fieldVisitor);
  }


  public Document doc(int docID, Set<String> fieldsToLoad) throws IOException {
    return reader.document(docID, fieldsToLoad);
  }
  
  @Deprecated
  public final Document document(int docID, Set<String> fieldsToLoad) throws IOException {
    return doc(docID, fieldsToLoad);
  }


  public void setSimilarity(Similarity similarity) {
    this.similarity = similarity;
  }

  public Similarity getSimilarity() {
    return similarity;
  }
  
  protected Query wrapFilter(Query query, Filter filter) {
    return (filter == null) ? query : new FilteredQuery(query, filter);
  }


  public TopDocs searchAfter(ScoreDoc after, Query query, int n) throws IOException {
    return search(createNormalizedWeight(query), after, n);
  }
  

  public TopDocs searchAfter(ScoreDoc after, Query query, Filter filter, int n) throws IOException {
    return search(createNormalizedWeight(wrapFilter(query, filter)), after, n);
  }
  

  public TopDocs search(Query query, int n)
    throws IOException {
    return search(query, null, n);
  }



  public TopDocs search(Query query, Filter filter, int n)
    throws IOException {
    return search(createNormalizedWeight(wrapFilter(query, filter)), null, n);
  }


  public void search(Query query, Filter filter, Collector results)
    throws IOException {
    search(leafContexts, createNormalizedWeight(wrapFilter(query, filter)), results);
  }


  public void search(Query query, Collector results)
    throws IOException {
    search(leafContexts, createNormalizedWeight(query), results);
  }
  

  public TopFieldDocs search(Query query, Filter filter, int n,
                             Sort sort) throws IOException {
    return search(createNormalizedWeight(wrapFilter(query, filter)), n, sort, false, false);
  }


  public TopFieldDocs search(Query query, Filter filter, int n,
                             Sort sort, boolean doDocScores, boolean doMaxScore) throws IOException {
    return search(createNormalizedWeight(wrapFilter(query, filter)), n, sort, doDocScores, doMaxScore);
  }


  public TopDocs searchAfter(ScoreDoc after, Query query, Filter filter, int n, Sort sort) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return search(createNormalizedWeight(wrapFilter(query, filter)), (FieldDoc) after, n, sort, true, false, false);
  }

  public TopFieldDocs search(Query query, int n,
                             Sort sort) throws IOException {
    return search(createNormalizedWeight(query), n, sort, false, false);
  }


  public TopDocs searchAfter(ScoreDoc after, Query query, int n, Sort sort) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return search(createNormalizedWeight(query), (FieldDoc) after, n, sort, true, false, false);
  }


  public TopDocs searchAfter(ScoreDoc after, Query query, Filter filter, int n, Sort sort,
                             boolean doDocScores, boolean doMaxScore) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return search(createNormalizedWeight(wrapFilter(query, filter)), (FieldDoc) after, n, sort, true,
                  doDocScores, doMaxScore);
  }


  protected TopDocs search(Weight weight, ScoreDoc after, int nDocs) throws IOException {
    int limit = reader.maxDoc();
    if (limit == 0) {
      limit = 1;
    }
    if (after != null && after.doc >= limit) {
      throw new IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc="
          + after.doc + " limit=" + limit);
    }
    nDocs = Math.min(nDocs, limit);
    
    if (executor == null) {
      return search(leafContexts, weight, after, nDocs);
    } else {
      final HitQueue hq = new HitQueue(nDocs, false);
      final Lock lock = new ReentrantLock();
      final ExecutionHelper<TopDocs> runner = new ExecutionHelper<>(executor);
    
      for (int i = 0; i < leafSlices.length; i++) { // search each sub
        runner.submit(new SearcherCallableNoSort(lock, this, leafSlices[i], weight, after, nDocs, hq));
      }

      int totalHits = 0;
      float maxScore = Float.NEGATIVE_INFINITY;
      for (final TopDocs topDocs : runner) {
        if(topDocs.totalHits != 0) {
          totalHits += topDocs.totalHits;
          maxScore = Math.max(maxScore, topDocs.getMaxScore());
        }
      }

      final ScoreDoc[] scoreDocs = new ScoreDoc[hq.size()];
      for (int i = hq.size() - 1; i >= 0; i--) // put docs in array
        scoreDocs[i] = hq.pop();

      return new TopDocs(totalHits, scoreDocs, maxScore);
    }
  }


  protected TopDocs search(List<AtomicReaderContext> leaves, Weight weight, ScoreDoc after, int nDocs) throws IOException {
    // single thread
    int limit = reader.maxDoc();
    if (limit == 0) {
      limit = 1;
    }
    nDocs = Math.min(nDocs, limit);
    TopScoreDocCollector collector = TopScoreDocCollector.create(nDocs, after, !weight.scoresDocsOutOfOrder());
    search(leaves, weight, collector);
    return collector.topDocs();
  }


  protected TopFieldDocs search(Weight weight,
                                final int nDocs, Sort sort,
                                boolean doDocScores, boolean doMaxScore) throws IOException {
    return search(weight, null, nDocs, sort, true, doDocScores, doMaxScore);
  }

  protected TopFieldDocs search(Weight weight, FieldDoc after, int nDocs,
                                Sort sort, boolean fillFields,
                                boolean doDocScores, boolean doMaxScore)
      throws IOException {

    if (sort == null) throw new NullPointerException("Sort must not be null");
    
    int limit = reader.maxDoc();
    if (limit == 0) {
      limit = 1;
    }
    nDocs = Math.min(nDocs, limit);

    if (executor == null) {
      // use all leaves here!
      return search(leafContexts, weight, after, nDocs, sort, fillFields, doDocScores, doMaxScore);
    } else {
      final TopFieldCollector topCollector = TopFieldCollector.create(sort, nDocs,
                                                                      after,
                                                                      fillFields,
                                                                      doDocScores,
                                                                      doMaxScore,
                                                                      false);

      final Lock lock = new ReentrantLock();
      final ExecutionHelper<TopFieldDocs> runner = new ExecutionHelper<>(executor);
      for (int i = 0; i < leafSlices.length; i++) { // search each leaf slice
        runner.submit(
                      new SearcherCallableWithSort(lock, this, leafSlices[i], weight, after, nDocs, topCollector, sort, doDocScores, doMaxScore));
      }
      int totalHits = 0;
      float maxScore = Float.NEGATIVE_INFINITY;
      for (final TopFieldDocs topFieldDocs : runner) {
        if (topFieldDocs.totalHits != 0) {
          totalHits += topFieldDocs.totalHits;
          maxScore = Math.max(maxScore, topFieldDocs.getMaxScore());
        }
      }

      final TopFieldDocs topDocs = (TopFieldDocs) topCollector.topDocs();

      return new TopFieldDocs(totalHits, topDocs.scoreDocs, topDocs.fields, topDocs.getMaxScore());
    }
  }
  
  
  protected TopFieldDocs search(List<AtomicReaderContext> leaves, Weight weight, FieldDoc after, int nDocs,
                                Sort sort, boolean fillFields, boolean doDocScores, boolean doMaxScore) throws IOException {
    // single thread
    int limit = reader.maxDoc();
    if (limit == 0) {
      limit = 1;
    }
    nDocs = Math.min(nDocs, limit);

    TopFieldCollector collector = TopFieldCollector.create(sort, nDocs, after,
                                                           fillFields, doDocScores,
                                                           doMaxScore, !weight.scoresDocsOutOfOrder());
    search(leaves, weight, collector);
    return (TopFieldDocs) collector.topDocs();
  }

  protected void search(List<AtomicReaderContext> leaves, Weight weight, Collector collector)
      throws IOException {

    // TODO: should we make this
    // threaded...?  the Collector could be sync'd?
    // always use single thread:
    for (AtomicReaderContext ctx : leaves) { // search each subreader
      try {
        collector.setNextReader(ctx);
      } catch (CollectionTerminatedException e) {
        // there is no doc of interest in this reader context
        // continue with the following leaf
        continue;
      }
      BulkScorer scorer = weight.bulkScorer(ctx, !collector.acceptsDocsOutOfOrder(), ctx.reader().getLiveDocs());
      if (scorer != null) {
        try {
          scorer.score(collector);
        } catch (CollectionTerminatedException e) {
          // collection was terminated prematurely
          // continue with the following leaf
        }
      }
    }
  }


  public Query rewrite(Query original) throws IOException {
    Query query = original;
    for (Query rewrittenQuery = query.rewrite(reader); rewrittenQuery != query;
         rewrittenQuery = query.rewrite(reader)) {
      query = rewrittenQuery;
    }
    return query;
  }


  public Explanation explain(Query query, int doc) throws IOException {
    return explain(createNormalizedWeight(query), doc);
  }


  protected Explanation explain(Weight weight, int doc) throws IOException {
    int n = ReaderUtil.subIndex(doc, leafContexts);
    final AtomicReaderContext ctx = leafContexts.get(n);
    int deBasedDoc = doc - ctx.docBase;
    
    return weight.explain(ctx, deBasedDoc);
  }

  public Weight createNormalizedWeight(Query query) throws IOException {
    query = rewrite(query);
    Weight weight = query.createWeight(this);
    float v = weight.getValueForNormalization();
    float norm = getSimilarity().queryNorm(v);
    if (Float.isInfinite(norm) || Float.isNaN(norm)) {
      norm = 1.0f;
    }
    weight.normalize(norm, 1.0f);
    return weight;
  }
  
  /* sugar for #getReader().getTopReaderContext() */
  public IndexReaderContext getTopReaderContext() {
    return readerContext;
  }

  private static final class SearcherCallableNoSort implements Callable<TopDocs> {

    private final Lock lock;
    private final IndexSearcher searcher;
    private final Weight weight;
    private final ScoreDoc after;
    private final int nDocs;
    private final HitQueue hq;
    private final LeafSlice slice;

    public SearcherCallableNoSort(Lock lock, IndexSearcher searcher, LeafSlice slice,  Weight weight,
        ScoreDoc after, int nDocs, HitQueue hq) {
      this.lock = lock;
      this.searcher = searcher;
      this.weight = weight;
      this.after = after;
      this.nDocs = nDocs;
      this.hq = hq;
      this.slice = slice;
    }

    @Override
    public TopDocs call() throws IOException {
      final TopDocs docs = searcher.search(Arrays.asList(slice.leaves), weight, after, nDocs);
      final ScoreDoc[] scoreDocs = docs.scoreDocs;
      //it would be so nice if we had a thread-safe insert 
      lock.lock();
      try {
        for (int j = 0; j < scoreDocs.length; j++) { // merge scoreDocs into hq
          final ScoreDoc scoreDoc = scoreDocs[j];
          if (scoreDoc == hq.insertWithOverflow(scoreDoc)) {
            break;
          }
        }
      } finally {
        lock.unlock();
      }
      return docs;
    }
  }


  private static final class SearcherCallableWithSort implements Callable<TopFieldDocs> {

    private final Lock lock;
    private final IndexSearcher searcher;
    private final Weight weight;
    private final int nDocs;
    private final TopFieldCollector hq;
    private final Sort sort;
    private final LeafSlice slice;
    private final FieldDoc after;
    private final boolean doDocScores;
    private final boolean doMaxScore;

    public SearcherCallableWithSort(Lock lock, IndexSearcher searcher, LeafSlice slice, Weight weight,
                                    FieldDoc after, int nDocs, TopFieldCollector hq, Sort sort,
                                    boolean doDocScores, boolean doMaxScore) {
      this.lock = lock;
      this.searcher = searcher;
      this.weight = weight;
      this.nDocs = nDocs;
      this.hq = hq;
      this.sort = sort;
      this.slice = slice;
      this.after = after;
      this.doDocScores = doDocScores;
      this.doMaxScore = doMaxScore;
    }

    private final FakeScorer fakeScorer = new FakeScorer();

    @Override
    public TopFieldDocs call() throws IOException {
      assert slice.leaves.length == 1;
      final TopFieldDocs docs = searcher.search(Arrays.asList(slice.leaves),
          weight, after, nDocs, sort, true, doDocScores || sort.needsScores(), doMaxScore);
      lock.lock();
      try {
        final AtomicReaderContext ctx = slice.leaves[0];
        final int base = ctx.docBase;
        hq.setNextReader(ctx);
        hq.setScorer(fakeScorer);
        for(ScoreDoc scoreDoc : docs.scoreDocs) {
          fakeScorer.doc = scoreDoc.doc - base;
          fakeScorer.score = scoreDoc.score;
          hq.collect(scoreDoc.doc-base);
        }

        // Carry over maxScore from sub:
        if (doMaxScore && docs.getMaxScore() > hq.maxScore) {
          hq.maxScore = docs.getMaxScore();
        }
      } finally {
        lock.unlock();
      }
      return docs;
    }
  }

  private static final class ExecutionHelper<T> implements Iterator<T>, Iterable<T> {
    private final CompletionService<T> service;
    private int numTasks;

    ExecutionHelper(final Executor executor) {
      this.service = new ExecutorCompletionService<>(executor);
    }

    @Override
    public boolean hasNext() {
      return numTasks > 0;
    }

    public void submit(Callable<T> task) {
      this.service.submit(task);
      ++numTasks;
    }

    @Override
    public T next() {
      if(!this.hasNext()) 
        throw new NoSuchElementException("next() is called but hasNext() returned false");
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

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
      // use the shortcut here - this is only used in a private context
      return this;
    }
  }

  public static class LeafSlice {
    final AtomicReaderContext[] leaves;
    
    public LeafSlice(AtomicReaderContext... leaves) {
      this.leaves = leaves;
    }
  }

  @Override
  public String toString() {
    return "IndexSearcher(" + reader + "; executor=" + executor + ")";
  }
  
  public TermStatistics termStatistics(Term term, TermContext context) throws IOException {
    return new TermStatistics(term.bytes(), context.docFreq(), context.totalTermFreq());
  }
  
  public CollectionStatistics collectionStatistics(String field) throws IOException {
    final int docCount;
    final long sumTotalTermFreq;
    final long sumDocFreq;

    assert field != null;
    
    Terms terms = MultiFields.getTerms(reader, field);
    if (terms == null) {
      docCount = 0;
      sumTotalTermFreq = 0;
      sumDocFreq = 0;
    } else {
      docCount = terms.getDocCount();
      sumTotalTermFreq = terms.getSumTotalTermFreq();
      sumDocFreq = terms.getSumDocFreq();
    }
    return new CollectionStatistics(field, reader.maxDoc(), docCount, sumTotalTermFreq, sumDocFreq);
  }
}
