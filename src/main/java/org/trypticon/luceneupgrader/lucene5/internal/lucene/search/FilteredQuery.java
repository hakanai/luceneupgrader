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
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.BooleanClause.Occur;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ToStringUtils;


@Deprecated
public class FilteredQuery extends Query {

  private final Query query;
  private final Filter filter;
  private final FilterStrategy strategy;

  public FilteredQuery(Query query, Filter filter) {
    this(query, filter, RANDOM_ACCESS_FILTER_STRATEGY);
  }
  
  public FilteredQuery(Query query, Filter filter, FilterStrategy strategy) {
    this.strategy = Objects.requireNonNull(strategy, "FilterStrategy must not be null");
    this.query = Objects.requireNonNull(query, "Query must not be null");
    this.filter = Objects.requireNonNull(filter, "Filter must not be null");
  }
  
  
  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (getBoost() != 1f) {
      return super.rewrite(reader);
    }
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(query, Occur.MUST);
    builder.add(strategy.rewrite(filter), Occur.FILTER);
    return builder.build();
  }

  public final Query getQuery() {
    return query;
  }

  public final Filter getFilter() {
    return filter;
  }
  
  public FilterStrategy getFilterStrategy() {
    return this.strategy;
  }

  @Override
  public String toString (String s) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("filtered(");
    buffer.append(query.toString(s));
    buffer.append(")->");
    buffer.append(filter);
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (!super.equals(o))
      return false;
    assert o instanceof FilteredQuery;
    final FilteredQuery fq = (FilteredQuery) o;
    return fq.query.equals(this.query) && fq.filter.equals(this.filter) && fq.strategy.equals(this.strategy);
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    hash = hash * 31 + strategy.hashCode();
    hash = hash * 31 + query.hashCode();
    hash = hash * 31 + filter.hashCode();
    return hash;
  }
  
  public static final FilterStrategy RANDOM_ACCESS_FILTER_STRATEGY = new RandomAccessFilterStrategy();
  
  public static final FilterStrategy LEAP_FROG_FILTER_FIRST_STRATEGY = new RandomAccessFilterStrategy() {
    protected boolean useRandomAccess(Bits bits, long filterCost) {
      return false;
    }
  };
  
  public static final FilterStrategy LEAP_FROG_QUERY_FIRST_STRATEGY = LEAP_FROG_FILTER_FIRST_STRATEGY;
  
  public static final FilterStrategy QUERY_FIRST_FILTER_STRATEGY = new RandomAccessFilterStrategy() {
    @Override
    boolean alwaysUseRandomAccess() {
      return true;
    }
  };
  
  public static abstract class FilterStrategy {

    public abstract Query rewrite(Filter filter);

  }

  public static class RandomAccessFilterStrategy extends FilterStrategy {

    @Override
    public Query rewrite(Filter filter) {
      return new RandomAccessFilterWrapperQuery(filter, this);
    }

    protected boolean useRandomAccess(Bits bits, long filterCost) {
      // if the filter matches more than 1% of documents, we use random-access
      return filterCost * 100 > bits.length();
    }

    // back door for QUERY_FIRST_FILTER_STRATEGY, when this returns true we
    // will try to use the random-access API regardless of the iterator
    boolean alwaysUseRandomAccess() {
      return false;
    }
  }

  private static class RandomAccessFilterWrapperQuery extends Query {

    final Filter filter;
    final RandomAccessFilterStrategy strategy;

    private RandomAccessFilterWrapperQuery(Filter filter, RandomAccessFilterStrategy strategy) {
      this.filter = Objects.requireNonNull(filter);
      this.strategy = Objects.requireNonNull(strategy);
    }

    @Override
    public boolean equals(Object obj) {
      if (super.equals(obj) == false) {
        return false;
      }
      RandomAccessFilterWrapperQuery that = (RandomAccessFilterWrapperQuery) obj;
      return filter.equals(that.filter) && strategy.equals(that.strategy);
    }

    @Override
    public int hashCode() {
      return 31 * super.hashCode() + Objects.hash(filter, strategy);
    }

    @Override
    public String toString(String field) {
      return filter.toString(field);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
      return new Weight(this) {

        @Override
        public void extractTerms(Set<Term> terms) {}

        @Override
        public float getValueForNormalization() throws IOException {
          return 0f;
        }

        @Override
        public void normalize(float norm, float topLevelBoost) {}

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
          final Scorer s = scorer(context);
          final boolean match;
          if (s == null) {
            match = false;
          } else {
            final TwoPhaseIterator twoPhase = s.twoPhaseIterator();
            if (twoPhase == null) {
              match = s.iterator().advance(doc) == doc;
            } else {
              match = twoPhase.approximation().advance(doc) == doc && twoPhase.matches();
            }
          }
          if (match) {
            assert s.score() == 0f;
            return Explanation.match(0f, "Match on id " + doc);
          } else {
            return Explanation.match(0f, "No match on id " + doc);
          }
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
          final DocIdSet set = filter.getDocIdSet(context, null);
          if (set == null) {
            return null;
          }
          final Bits bits = set.bits();
          boolean useRandomAccess = bits != null && strategy.alwaysUseRandomAccess();
          final DocIdSetIterator iterator;
          if (useRandomAccess) {
            // we don't need the iterator
            iterator = null;
          } else {
            iterator = set.iterator();
            if (iterator == null) {
              return null;
            }
            if (bits != null) {
              useRandomAccess = strategy.useRandomAccess(bits, iterator.cost());
            }
          }

          if (useRandomAccess) {
            // use the random-access API
            final DocIdSetIterator approximation = DocIdSetIterator.all(context.reader().maxDoc());
            final TwoPhaseIterator twoPhase = new TwoPhaseIterator(approximation) {
              @Override
              public boolean matches() throws IOException {
                final int doc = approximation.docID();
                return bits.get(doc);
              }
              @Override
              public float matchCost() {
                return 10; // TODO use cost of bits.get()
              }
            };
            return new ConstantScoreScorer(this, 0f, twoPhase);
          } else {
            // use the iterator API
            return new ConstantScoreScorer(this, 0f, iterator);
          }
        }

      };
    }

  }

}
