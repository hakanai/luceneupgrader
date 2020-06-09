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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;

import java.io.IOException;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.document.SortedNumericDocValuesField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;

public final class IndexOrDocValuesQuery extends Query {

  private final Query indexQuery, dvQuery;

  public IndexOrDocValuesQuery(Query indexQuery, Query dvQuery) {
    this.indexQuery = indexQuery;
    this.dvQuery = dvQuery;
  }

  public Query getIndexQuery() {
    return indexQuery;
  }

  public Query getRandomAccessQuery() {
    return dvQuery;
  }

  @Override
  public String toString(String field) {
    return indexQuery.toString(field);
  }

  @Override
  public boolean equals(Object obj) {
    if (sameClassAs(obj) == false) {
      return false;
    }
    IndexOrDocValuesQuery that = (IndexOrDocValuesQuery) obj;
    return indexQuery.equals(that.indexQuery) && dvQuery.equals(that.dvQuery);
  }

  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + indexQuery.hashCode();
    h = 31 * h + dvQuery.hashCode();
    return h;
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    Query indexRewrite = indexQuery.rewrite(reader);
    Query dvRewrite = dvQuery.rewrite(reader);
    if (indexQuery != indexRewrite || dvQuery != dvRewrite) {
      return new IndexOrDocValuesQuery(indexRewrite, dvRewrite);
    }
    return this;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    final Weight indexWeight = indexQuery.createWeight(searcher, needsScores, boost);
    final Weight dvWeight = dvQuery.createWeight(searcher, needsScores, boost);
    return new Weight(this) {
      @Override
      public void extractTerms(Set<Term> terms) {
        indexWeight.extractTerms(terms);
      }

      @Override
      public Matches matches(LeafReaderContext context, int doc) throws IOException {
        // We need to check a single doc, so the dv query should perform better
        return dvWeight.matches(context, doc);
      }

      @Override
      public Explanation explain(LeafReaderContext context, int doc) throws IOException {
        // We need to check a single doc, so the dv query should perform better
        return dvWeight.explain(context, doc);
      }

      @Override
      public BulkScorer bulkScorer(LeafReaderContext context) throws IOException {
        // Bulk scorers need to consume the entire set of docs, so using an
        // index structure should perform better
        return indexWeight.bulkScorer(context);
      }

      @Override
      public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        final ScorerSupplier indexScorerSupplier = indexWeight.scorerSupplier(context);
        final ScorerSupplier dvScorerSupplier = dvWeight.scorerSupplier(context);
        if (indexScorerSupplier == null || dvScorerSupplier == null) {
          return null;
        }
        return new ScorerSupplier() {
          @Override
          public Scorer get(long leadCost) throws IOException {
            // At equal costs, doc values tend to be worse than points since they
            // still need to perform one comparison per document while points can
            // do much better than that given how values are organized. So we give
            // an arbitrary 8x penalty to doc values.
            final long threshold = cost() >>> 3;
            if (threshold <= leadCost) {
              return indexScorerSupplier.get(leadCost);
            } else {
              return dvScorerSupplier.get(leadCost);
            }
          }

          @Override
          public long cost() {
            return indexScorerSupplier.cost();
          }
        };
      }

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        ScorerSupplier scorerSupplier = scorerSupplier(context);
        if (scorerSupplier == null) {
          return null;
        }
        return scorerSupplier.get(Long.MAX_VALUE);
      }

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        // Both index and dv query should return the same values, so we can use
        // the index query's cachehelper here
        return indexWeight.isCacheable(ctx);
      }

    };
  }

}
