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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.DirectoryReader; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexWriter; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.MultiFields;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.ReaderUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.DefaultSimilarity;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.NIOFSDirectory;    // javadoc
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ThreadInterruptedException;

public class IndexSearcher {

  private static final Similarity NON_SCORING_SIMILARITY = new Similarity() {

    @Override
    public long computeNorm(FieldInvertState state) {
      throw new UnsupportedOperationException("This Similarity may only be used for searching, not indexing");
    }

    @Override
    public SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
      return new SimWeight() {

        @Override
        public float getValueForNormalization() {
          return 1f;
        }

        @Override
        public void normalize(float queryNorm, float boost) {}

      };
    }

    @Override
    public SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
      return new SimScorer() {

        @Override
        public float score(int doc, float freq) {
          return 0f;
        }

        @Override
        public float computeSlopFactor(int distance) {
          return 1f;
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
          return 1f;
        }

      };
    }

  };

  private static QueryCache DEFAULT_QUERY_CACHE;
  private static QueryCachingPolicy DEFAULT_CACHING_POLICY = new UsageTrackingQueryCachingPolicy();
  static {
    final int maxCachedQueries = 1000;
    // min of 32MB or 5% of the heap size
    final long maxRamBytesUsed = Math.min(1L << 25, Runtime.getRuntime().maxMemory() / 20);
    DEFAULT_QUERY_CACHE = new LRUQueryCache(maxCachedQueries, maxRamBytesUsed);
  }

  final IndexReader reader; // package private for testing!
  
  // NOTE: these members might change in incompatible ways
  // in the next release
  protected final IndexReaderContext readerContext;
  protected final List<LeafReaderContext> leafContexts;
  protected final LeafSlice[] leafSlices;

  // These are only used for multi-threaded search
  private final ExecutorService executor;

  // the default Similarity
  private static final Similarity defaultSimilarity = new DefaultSimilarity();

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


  public Similarity getSimilarity(boolean needsScores) {
    return needsScores ? similarity : NON_SCORING_SIMILARITY;
  }
  

  @Deprecated
  protected Query wrapFilter(Query query, Filter filter) {
    return (filter == null) ? query : new FilteredQuery(query, filter);
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

    // general case: create a collecor and count matches
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


  public TopDocs searchAfter(final ScoreDoc after, Query query, int numHits) throws IOException {
    final int limit = Math.max(1, reader.maxDoc());
    if (after != null && after.doc >= limit) {
      throw new IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc="
          + after.doc + " limit=" + limit);
    }
    numHits = Math.min(numHits, limit);

    final int cappedNumHits = Math.min(numHits, limit);

    final CollectorManager<TopScoreDocCollector, TopDocs> manager = new CollectorManager<TopScoreDocCollector, TopDocs>() {

      @Override
      public TopScoreDocCollector newCollector() throws IOException {
        return TopScoreDocCollector.create(cappedNumHits, after);
      }

      @Override
      public TopDocs reduce(Collection<TopScoreDocCollector> collectors) throws IOException {
        final TopDocs[] topDocs = new TopDocs[collectors.size()];
        int i = 0;
        for (TopScoreDocCollector collector : collectors) {
          topDocs[i++] = collector.topDocs();
        }
        return TopDocs.merge(cappedNumHits, topDocs);
      }

    };

    return search(query, manager);
  }


  @Deprecated
  public final TopDocs searchAfter(ScoreDoc after, Query query, Filter filter, int n) throws IOException {
    return searchAfter(after, wrapFilter(query, filter), n);
  }
  

  public TopDocs search(Query query, int n)
    throws IOException {
    return searchAfter(null, query, n);
  }



  @Deprecated
  public final TopDocs search(Query query, Filter filter, int n)
    throws IOException {
    return search(wrapFilter(query, filter), n);
  }


  @Deprecated
  public final void search(Query query, Filter filter, Collector results)
    throws IOException {
    search(wrapFilter(query, filter), results);
  }


  public void search(Query query, Collector results)
    throws IOException {
    search(leafContexts, createNormalizedWeight(query, results.needsScores()), results);
  }
  

  @Deprecated
  public final TopFieldDocs search(Query query, Filter filter, int n,
                             Sort sort) throws IOException {
    return search(query, filter, n, sort, false, false);
  }


  @Deprecated
  public final TopFieldDocs search(Query query, Filter filter, int n,
                             Sort sort, boolean doDocScores, boolean doMaxScore) throws IOException {
    return searchAfter(null, query, filter, n, sort, doDocScores, doMaxScore);
  }


  public final TopFieldDocs search(Query query, int n,
                             Sort sort, boolean doDocScores, boolean doMaxScore) throws IOException {
    return searchAfter(null, query, n, sort, doDocScores, doMaxScore);
  }


  @Deprecated
  public final TopFieldDocs searchAfter(ScoreDoc after, Query query, Filter filter, int n, Sort sort) throws IOException {
    return searchAfter(after, query, filter, n, sort, false, false);
  }

  public TopFieldDocs search(Query query, int n,
                             Sort sort) throws IOException {
    return search(query, null, n, sort, false, false);
  }


  public TopDocs searchAfter(ScoreDoc after, Query query, int n, Sort sort) throws IOException {
    return searchAfter(after, query, null, n, sort, false, false);
  }


  @Deprecated
  public final TopFieldDocs searchAfter(ScoreDoc after, Query query, Filter filter, int numHits, Sort sort,
      boolean doDocScores, boolean doMaxScore) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return searchAfter((FieldDoc) after, wrapFilter(query, filter), numHits, sort, doDocScores, doMaxScore);
  }


  public final TopFieldDocs searchAfter(ScoreDoc after, Query query, int numHits, Sort sort,
      boolean doDocScores, boolean doMaxScore) throws IOException {
    if (after != null && !(after instanceof FieldDoc)) {
      // TODO: if we fix type safety of TopFieldDocs we can
      // remove this
      throw new IllegalArgumentException("after must be a FieldDoc; got " + after);
    }
    return searchAfter((FieldDoc) after, query, numHits, sort, doDocScores, doMaxScore);
  }

  private TopFieldDocs searchAfter(final FieldDoc after, Query query, int numHits, final Sort sort,
      final boolean doDocScores, final boolean doMaxScore) throws IOException {
    final int limit = Math.max(1, reader.maxDoc());
    if (after != null && after.doc >= limit) {
      throw new IllegalArgumentException("after.doc exceeds the number of documents in the reader: after.doc="
          + after.doc + " limit=" + limit);
    }
    final int cappedNumHits = Math.min(numHits, limit);

    final CollectorManager<TopFieldCollector, TopFieldDocs> manager = new CollectorManager<TopFieldCollector, TopFieldDocs>() {

      @Override
      public TopFieldCollector newCollector() throws IOException {
        final boolean fillFields = true;
        return TopFieldCollector.create(sort, cappedNumHits, after, fillFields, doDocScores, doMaxScore);
      }

      @Override
      public TopFieldDocs reduce(Collection<TopFieldCollector> collectors) throws IOException {
        final TopFieldDocs[] topDocs = new TopFieldDocs[collectors.size()];
        int i = 0;
        for (TopFieldCollector collector : collectors) {
          topDocs[i++] = collector.topDocs();
        }
        return TopDocs.merge(sort, cappedNumHits, topDocs);
      }

    };

    return search(query, manager);
  }

  public <C extends Collector, T> T search(Query query, CollectorManager<C, T> collectorManager) throws IOException {
    if (executor == null) {
      final C collector = collectorManager.newCollector();
      search(query, collector);
      return collectorManager.reduce(Collections.singletonList(collector));
    } else {
      final List<C> collectors = new ArrayList<>(leafSlices.length);
      boolean needsScores = false;
      for (int i = 0; i < leafSlices.length; ++i) {
        final C collector = collectorManager.newCollector();
        collectors.add(collector);
        needsScores |= collector.needsScores();
      }

      final Weight weight = createNormalizedWeight(query, needsScores);
      final List<Future<C>> topDocsFutures = new ArrayList<>(leafSlices.length);
      for (int i = 0; i < leafSlices.length; ++i) {
        final LeafReaderContext[] leaves = leafSlices[i].leaves;
        final C collector = collectors.get(i);
        topDocsFutures.add(executor.submit(new Callable<C>() {
          @Override
          public C call() throws Exception {
            search(Arrays.asList(leaves), weight, collector);
            return collector;
          }
        }));
      }

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
    // threaded...?  the Collector could be sync'd?
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
    return explain(createNormalizedWeight(query, true), doc);
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

  public Weight createNormalizedWeight(Query query, boolean needsScores) throws IOException {
    query = rewrite(query);
    Weight weight = createWeight(query, needsScores);
    float v = weight.getValueForNormalization();
    float norm = getSimilarity(needsScores).queryNorm(v);
    if (Float.isInfinite(norm) || Float.isNaN(norm)) {
      norm = 1.0f;
    }
    weight.normalize(norm, 1.0f);
    return weight;
  }

  public Weight createWeight(Query query, boolean needsScores) throws IOException {
    final QueryCache queryCache = this.queryCache;
    Weight weight = query.createWeight(this, needsScores);
    if (needsScores == false && queryCache != null) {
      weight = queryCache.doCache(weight, queryCachingPolicy);
    }
    return weight;
  }

  /* sugar for #getReader().getTopReaderContext() */
  public IndexReaderContext getTopReaderContext() {
    return readerContext;
  }

  public static class LeafSlice {
    final LeafReaderContext[] leaves;
    
    public LeafSlice(LeafReaderContext... leaves) {
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
