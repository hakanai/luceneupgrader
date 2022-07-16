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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DirectoryReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.ReaderUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.TermStates;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.BM25Similarity;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.NIOFSDirectory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ThreadInterruptedException;

public class IndexSearcher {

  private static QueryCache DEFAULT_QUERY_CACHE;
  private static QueryCachingPolicy DEFAULT_CACHING_POLICY = new UsageTrackingQueryCachingPolicy();
  static {
    final int maxCachedQueries = 1000;
    // min of 32MB or 5% of the heap size
    final long maxRamBytesUsed = Math.min(1L << 25, Runtime.getRuntime().maxMemory() / 20);
    DEFAULT_QUERY_CACHE = new LRUQueryCache(maxCachedQueries, maxRamBytesUsed);
  }
  private static final int TOTAL_HITS_THRESHOLD = 1000;

  final IndexReader reader; // package private for testing!
  
  // NOTE: these members might change in incompatible ways
  // in the next release
  protected final IndexReaderContext readerContext;
  protected final List<LeafReaderContext> leafContexts;
  private final LeafSlice[] leafSlices;

  // These are only used for multi-threaded search
  private final Executor executor;

  // the default Similarity
  private static final Similarity defaultSimilarity = new BM25Similarity();

  private QueryCache queryCache = DEFAULT_QUERY_CACHE;
  private QueryCachingPolicy queryCachingPolicy = DEFAULT_CACHING_POLICY;

  public static Similarity getDefaultSimilarity() {
    return defaultSimilarity;
  }

  public static QueryCache getDefaultQueryCache() {
    return DEFAULT_QUERY_CACHE;
  }

  public static void setDefaultQueryCache(QueryCache defaultQueryCache) {
    DEFAULT_QUERY_CACHE = defaultQueryCache;
  }

  public static QueryCachingPolicy getDefaultQueryCachingPolicy() {
    return DEFAULT_CACHING_POLICY;
  }

  public static void setDefaultQueryCachingPolicy(QueryCachingPolicy defaultQueryCachingPolicy) {
    DEFAULT_CACHING_POLICY = defaultQueryCachingPolicy;
  }

  private Similarity similarity = defaultSimilarity;

  public IndexSearcher(IndexReader r) {
    this(r, null);
  }

  public IndexSearcher(IndexReader r, Executor executor) {
    this(r.getContext(), executor);
  }

  public IndexSearcher(IndexReaderContext context, Executor executor) {
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

  public void setQueryCache(QueryCache queryCache) {
    this.queryCache = queryCache;
  }

  public QueryCache getQueryCache() {
    return queryCache;
  }

  public void setQueryCachingPolicy(QueryCachingPolicy queryCachingPolicy) {
    this.queryCachingPolicy = Objects.requireNonNull(queryCachingPolicy);
  }

  public QueryCachingPolicy getQueryCachingPolicy() {
    return queryCachingPolicy;
  }

  protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
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

  public void setSimilarity(Similarity similarity) {
    this.similarity = similarity;
  }

  public Similarity getSimilarity() {
    return similarity;
  }

  public int count(Query query) throws IOException {
    query = rewrite(query);
    while (true) {
      // remove wrappers that don't matter for counts
      if (query instanceof ConstantScoreQuery) {
        query = ((ConstantScoreQuery) query).getQuery();
      } else {
        break;
      }
    }

    // some counts can be computed in constant time
    if (query instanceof MatchAllDocsQuery) {
      return reader.numDocs();
    } else if (query instanceof TermQuery && reader.hasDeletions() == false) {
      Term term = ((TermQuery) query).getTerm();
      int count = 0;
      for (LeafReaderContext leaf : reader.leaves()) {
        count += leaf.reader().docFreq(term);
      }
      return count;
    }

    // general case: create a collector and count matches
    final CollectorManager<TotalHitCountCollector, Integer> collectorManager = new CollectorManager<TotalHitCountCollector, Integer>() {

      @Override
      public TotalHitCountCollector newCollector() throws IOException {
        return new TotalHitCountCollector();
      }

      @Override
      public Integer reduce(Collection<TotalHitCountCollector> collectors) throws IOException {
        int total = 0;
        for (TotalHitCountCollector collector : collectors) {
          total += collector.getTotalHits();
        }
        return total;
      }

    };
    return search(query, collectorManager);
  }

  public LeafSlice[] getSlices() {
      return leafSlices;
  }
  
  public TopDocs searchAfter(ScoreDoc after, Query query, int numHits) throws IOException {
    final int limit = Math.max(1, reader.maxDoc());
    if (after != null && after.doc >= limit) {
      throw new IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc="
          + after.doc + " limit=" + limit);
    }

    final int cappedNumHits = Math.min(numHits, limit);

    final CollectorManager<TopScoreDocCollector, TopDocs> manager = new CollectorManager<TopScoreDocCollector, TopDocs>() {

      private final HitsThresholdChecker hitsThresholdChecker = (executor == null || leafSlices.length <= 1) ? HitsThresholdChecker.create(Math.max(TOTAL_HITS_THRESHOLD, numHits)) :
          HitsThresholdChecker.createShared(Math.max(TOTAL_HITS_THRESHOLD, numHits));

      private final MaxScoreAccumulator minScoreAcc = (executor == null || leafSlices.length <= 1) ? null : new MaxScoreAccumulator();

      @Override
      public TopScoreDocCollector newCollector() throws IOException {
        return TopScoreDocCollector.create(cappedNumHits, after, hitsThresholdChecker, minScoreAcc);
      }

      @Override
      public TopDocs reduce(Collection<TopScoreDocCollector> collectors) throws IOException {
        final TopDocs[] topDocs = new TopDocs[collectors.size()];
        int i = 0;
        for (TopScoreDocCollector collector : collectors) {
          topDocs[i++] = collector.topDocs();
        }
        return TopDocs.merge(0, cappedNumHits, topDocs, true);
      }

    };

    return search(query, manager);
  }

  public TopDocs search(Query query, int n)
    throws IOException {
    return searchAfter(null, query, n);
  }

  public void search(Query query, Collector results)
    throws IOException {
    query = rewrite(query);
    search(leafContexts, createWeight(query, results.scoreMode(), 1), results);
  }

  public TopFieldDocs search(Query query, int n,
      Sort sort, boolean doDocScores) throws IOException {
    return searchAfter(null, query, n, sort, doDocScores);
  }

  public TopFieldDocs search(Query query, int n, Sort sort) throws IOException {
    return searchAfter(null, query, n, sort, false);
  }

  public TopDocs searchAfter(ScoreDoc after, Query query, int n, Sort sort) throws IOException {
    return searchAfter(after, query, n, sort, false);
  }

  public TopFieldDocs searchAfter(ScoreDoc after, Query query, int numHits, Sort sort,
      boolean doDocScores) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return searchAfter((FieldDoc) after, query, numHits, sort, doDocScores);
  }

  private TopFieldDocs searchAfter(FieldDoc after, Query query, int numHits, Sort sort,
      boolean doDocScores) throws IOException {
    final int limit = Math.max(1, reader.maxDoc());
    if (after != null && after.doc >= limit) {
      throw new IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc="
          + after.doc + " limit=" + limit);
    }
    final int cappedNumHits = Math.min(numHits, limit);
    final Sort rewrittenSort = sort.rewrite(this);

    final CollectorManager<TopFieldCollector, TopFieldDocs> manager = new CollectorManager<TopFieldCollector, TopFieldDocs>() {

      private final HitsThresholdChecker hitsThresholdChecker = (executor == null || leafSlices.length <= 1) ? HitsThresholdChecker.create(Math.max(TOTAL_HITS_THRESHOLD, numHits)) :
          HitsThresholdChecker.createShared(Math.max(TOTAL_HITS_THRESHOLD, numHits));

      private final MaxScoreAccumulator minScoreAcc = (executor == null || leafSlices.length <= 1) ? null : new MaxScoreAccumulator();

      @Override
      public TopFieldCollector newCollector() throws IOException {
        // TODO: don't pay the price for accurate hit counts by default
        return TopFieldCollector.create(rewrittenSort, cappedNumHits, after, hitsThresholdChecker, minScoreAcc);
      }

      @Override
      public TopFieldDocs reduce(Collection<TopFieldCollector> collectors) throws IOException {
        final TopFieldDocs[] topDocs = new TopFieldDocs[collectors.size()];
        int i = 0;
        for (TopFieldCollector collector : collectors) {
          topDocs[i++] = collector.topDocs();
        }
        return TopDocs.merge(rewrittenSort, 0, cappedNumHits, topDocs, true);
      }

    };

    TopFieldDocs topDocs = search(query, manager);
    if (doDocScores) {
      TopFieldCollector.populateScores(topDocs.scoreDocs, this, query);
    }
    return topDocs;
  }

  public <C extends Collector, T> T search(Query query, CollectorManager<C, T> collectorManager) throws IOException {
    if (executor == null || leafSlices.length <= 1) {
      final C collector = collectorManager.newCollector();
      search(query, collector);
      return collectorManager.reduce(Collections.singletonList(collector));
    } else {
      final List<C> collectors = new ArrayList<>(leafSlices.length);
      ScoreMode scoreMode = null;
      for (int i = 0; i < leafSlices.length; ++i) {
        final C collector = collectorManager.newCollector();
        collectors.add(collector);
        if (scoreMode == null) {
          scoreMode = collector.scoreMode();
        } else if (scoreMode != collector.scoreMode()) {
          throw new IllegalStateException("CollectorManager does not always produce collectors with the same score mode");
        }
      }
      if (scoreMode == null) {
        // no segments
        scoreMode = ScoreMode.COMPLETE;
      }
      query = rewrite(query);
      final Weight weight = createWeight(query, scoreMode, 1);
      final List<Future<C>> topDocsFutures = new ArrayList<>(leafSlices.length);
      for (int i = 0; i < leafSlices.length - 1; ++i) {
        final LeafReaderContext[] leaves = leafSlices[i].leaves;
        final C collector = collectors.get(i);
        FutureTask<C> task = new FutureTask<>(() -> {
          search(Arrays.asList(leaves), weight, collector);
          return collector;
        });
        executor.execute(task);
        topDocsFutures.add(task);
      }
      final LeafReaderContext[] leaves = leafSlices[leafSlices.length - 1].leaves;
      final C collector = collectors.get(leafSlices.length - 1);
      // execute the last on the caller thread
      search(Arrays.asList(leaves), weight, collector);
      topDocsFutures.add(CompletableFuture.completedFuture(collector));
      final List<C> collectedCollectors = new ArrayList<>();
      for (Future<C> future : topDocsFutures) {
        try {
          collectedCollectors.add(future.get());
        } catch (InterruptedException e) {
          throw new ThreadInterruptedException(e);
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
      return collectorManager.reduce(collectors);
    }
  }

  protected void search(List<LeafReaderContext> leaves, Weight weight, Collector collector)
      throws IOException {

    // TODO: should we make this
    // threaded...? the Collector could be sync'd?
    // always use single thread:
    for (LeafReaderContext ctx : leaves) { // search each subreader
      final LeafCollector leafCollector;
      try {
        leafCollector = collector.getLeafCollector(ctx);
      } catch (CollectionTerminatedException e) {
        // there is no doc of interest in this reader context
        // continue with the following leaf
        continue;
      }
      BulkScorer scorer = weight.bulkScorer(ctx);
      if (scorer != null) {
        try {
          scorer.score(leafCollector, ctx.reader().getLiveDocs());
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
    query = rewrite(query);
    return explain(createWeight(query, ScoreMode.COMPLETE, 1), doc);
  }

  protected Explanation explain(Weight weight, int doc) throws IOException {
    int n = ReaderUtil.subIndex(doc, leafContexts);
    final LeafReaderContext ctx = leafContexts.get(n);
    int deBasedDoc = doc - ctx.docBase;
    final Bits liveDocs = ctx.reader().getLiveDocs();
    if (liveDocs != null && liveDocs.get(deBasedDoc) == false) {
      return Explanation.noMatch("Document " + doc + " is deleted");
    }
    return weight.explain(ctx, deBasedDoc);
  }

  public Weight createWeight(Query query, ScoreMode scoreMode, float boost) throws IOException {
    final QueryCache queryCache = this.queryCache;
    Weight weight = query.createWeight(this, scoreMode, boost);
    if (scoreMode.needsScores() == false && queryCache != null) {
      weight = queryCache.doCache(weight, queryCachingPolicy);
    }
    return weight;
  }

  /* sugar for #getReader().getTopReaderContext() */
  public IndexReaderContext getTopReaderContext() {
    return readerContext;
  }

  public static class LeafSlice {

    public final LeafReaderContext[] leaves;
    
    public LeafSlice(LeafReaderContext... leaves) {
      this.leaves = leaves;
    }
  }

  @Override
  public String toString() {
    return "IndexSearcher(" + reader + "; executor=" + executor + ")";
  }

  @Deprecated
  public final TermStatistics termStatistics(Term term, TermStates context) throws IOException {
    if (context.docFreq() == 0) {
      return null;
    } else {
      return termStatistics(term, context.docFreq(), context.totalTermFreq());
    }
  }

  public TermStatistics termStatistics(Term term, int docFreq, long totalTermFreq) throws IOException {
    // This constructor will throw an exception if docFreq <= 0.
    return new TermStatistics(term.bytes(), docFreq, totalTermFreq);
  }
  
  public CollectionStatistics collectionStatistics(String field) throws IOException {
    assert field != null;
    long docCount = 0;
    long sumTotalTermFreq = 0;
    long sumDocFreq = 0;
    for (LeafReaderContext leaf : reader.leaves()) {
      final Terms terms = leaf.reader().terms(field);
      if (terms == null) {
        continue;
      }
      docCount += terms.getDocCount();
      sumTotalTermFreq += terms.getSumTotalTermFreq();
      sumDocFreq += terms.getSumDocFreq();
    }
    if (docCount == 0) {
      return null;
    }
    return new CollectionStatistics(field, reader.maxDoc(), docCount, sumTotalTermFreq, sumDocFreq);
  }

  public Executor getExecutor() {
    return executor;
  }
}
