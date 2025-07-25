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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;

/**
 * A query that generates the union of documents produced by its subqueries, and that scores each
 * document with the maximum score for that document as produced by any subquery, plus a tie
 * breaking increment for any additional matching subqueries. This is useful when searching for a
 * word in multiple fields with different boost factors (so that the fields cannot be combined
 * equivalently into a single search field). We want the primary score to be the one associated with
 * the highest boost, not the sum of the field scores (as BooleanQuery would give). If the query is
 * "albino elephant" this ensures that "albino" matching one field and "elephant" matching another
 * gets a higher score than "albino" matching both fields. To get this result, use both BooleanQuery
 * and DisjunctionMaxQuery: for each term a DisjunctionMaxQuery searches for it in each field, while
 * the set of these DisjunctionMaxQuery's is combined into a BooleanQuery. The tie breaker
 * capability allows results that include the same term in multiple fields to be judged better than
 * results that include this term in only the best of those multiple fields, without confusing this
 * with the better case of two different terms in the multiple fields.
 */
public final class DisjunctionMaxQuery extends Query implements Iterable<Query> {

  /* The subqueries */
  private final Multiset<Query> disjuncts = new Multiset<>();

  /* Multiple of the non-max disjunct scores added into our final score.  Non-zero values support tie-breaking. */
  private final float tieBreakerMultiplier;

  /**
   * Creates a new DisjunctionMaxQuery
   *
   * @param disjuncts a {@code Collection<Query>} of all the disjuncts to add
   * @param tieBreakerMultiplier the score of each non-maximum disjunct for a document is multiplied
   *     by this weight and added into the final score. If non-zero, the value should be small, on
   *     the order of 0.1, which says that 10 occurrences of word in a lower-scored field that is
   *     also in a higher scored field is just as good as a unique word in the lower scored field
   *     (i.e., one that is not in any higher scored field.
   */
  public DisjunctionMaxQuery(Collection<Query> disjuncts, float tieBreakerMultiplier) {
    Objects.requireNonNull(disjuncts, "Collection of Querys must not be null");
    if (tieBreakerMultiplier < 0 || tieBreakerMultiplier > 1) {
      throw new IllegalArgumentException("tieBreakerMultiplier must be in [0, 1]");
    }
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    this.disjuncts.addAll(disjuncts);
  }

  /**
   * @return An {@code Iterator<Query>} over the disjuncts
   */
  @Override
  public Iterator<Query> iterator() {
    return getDisjuncts().iterator();
  }

  /**
   * @return the disjuncts.
   */
  public Collection<Query> getDisjuncts() {
    return Collections.unmodifiableCollection(disjuncts);
  }

  /**
   * @return tie breaker value for multiple matches.
   */
  public float getTieBreakerMultiplier() {
    return tieBreakerMultiplier;
  }

  /**
   * Expert: the Weight for DisjunctionMaxQuery, used to normalize, score and explain these queries.
   *
   * <p>NOTE: this API and implementation is subject to change suddenly in the next release.
   */
  protected class DisjunctionMaxWeight extends Weight {

    /** The Weights for our subqueries, in 1-1 correspondence with disjuncts */
    protected final ArrayList<Weight> weights =
        new ArrayList<>(); // The Weight's for our subqueries, in 1-1 correspondence with disjuncts

    private final ScoreMode scoreMode;

    /**
     * Construct the Weight for this Query searched by searcher. Recursively construct subquery
     * weights.
     */
    public DisjunctionMaxWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
        throws IOException {
      super(DisjunctionMaxQuery.this);
      for (Query disjunctQuery : disjuncts) {
        weights.add(searcher.createWeight(disjunctQuery, scoreMode, boost));
      }
      this.scoreMode = scoreMode;
    }

    @Override
    public Matches matches(LeafReaderContext context, int doc) throws IOException {
      List<Matches> mis = new ArrayList<>();
      for (Weight weight : weights) {
        Matches mi = weight.matches(context, doc);
        if (mi != null) {
          mis.add(mi);
        }
      }
      return MatchesUtils.fromSubMatches(mis);
    }

    @Override
    public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
      List<ScorerSupplier> scorerSuppliers = new ArrayList<>();
      for (Weight w : weights) {
        ScorerSupplier ss = w.scorerSupplier(context);
        if (ss != null) {
          scorerSuppliers.add(ss);
        }
      }

      if (scorerSuppliers.isEmpty()) {
        return null;
      } else if (scorerSuppliers.size() == 1) {
        return scorerSuppliers.get(0);
      } else {
        final Weight thisWeight = this;
        return new ScorerSupplier() {

          private long cost = -1;

          @Override
          public Scorer get(long leadCost) throws IOException {
            List<Scorer> scorers = new ArrayList<>();
            for (ScorerSupplier ss : scorerSuppliers) {
              scorers.add(ss.get(leadCost));
            }
            return new DisjunctionMaxScorer(thisWeight, tieBreakerMultiplier, scorers, scoreMode);
          }

          @Override
          public long cost() {
            if (cost == -1) {
              long cost = 0;
              for (ScorerSupplier ss : scorerSuppliers) {
                cost += ss.cost();
              }
              this.cost = cost;
            }
            return cost;
          }

          @Override
          public void setTopLevelScoringClause() throws IOException {
            if (tieBreakerMultiplier == 0) {
              for (ScorerSupplier ss : scorerSuppliers) {
                // sub scorers need to be able to skip too as calls to setMinCompetitiveScore get
                // propagated
                ss.setTopLevelScoringClause();
              }
            }
          }
        };
      }
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
      ScorerSupplier supplier = scorerSupplier(context);
      if (supplier == null) {
        return null;
      }
      supplier.setTopLevelScoringClause();
      return supplier.get(Long.MAX_VALUE);
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      if (weights.size()
          > AbstractMultiTermQueryConstantScoreWrapper.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD) {
        // Disallow caching large dismax queries to not encourage users
        // to build large dismax queries as a workaround to the fact that
        // we disallow caching large TermInSetQueries.
        return false;
      }
      for (Weight w : weights) {
        if (w.isCacheable(ctx) == false) return false;
      }
      return true;
    }

    /** Explain the score we computed for doc */
    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
      boolean match = false;
      double max = 0;
      double otherSum = 0;
      List<Explanation> subsOnMatch = new ArrayList<>();
      List<Explanation> subsOnNoMatch = new ArrayList<>();
      for (Weight wt : weights) {
        Explanation e = wt.explain(context, doc);
        if (e.isMatch()) {
          match = true;
          subsOnMatch.add(e);
          double score = e.getValue().doubleValue();
          if (score >= max) {
            otherSum += max;
            max = score;
          } else {
            otherSum += score;
          }
        } else if (match == false) {
          subsOnNoMatch.add(e);
        }
      }
      if (match) {
        final float score = (float) (max + otherSum * tieBreakerMultiplier);
        final String desc =
            tieBreakerMultiplier == 0.0f
                ? "max of:"
                : "max plus " + tieBreakerMultiplier + " times others of:";
        return Explanation.match(score, desc, subsOnMatch);
      } else {
        return Explanation.noMatch("No matching clause", subsOnNoMatch);
      }
    }
  } // end of DisjunctionMaxWeight inner class

  /** Create the Weight used to score us */
  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return new DisjunctionMaxWeight(searcher, scoreMode, boost);
  }

  /**
   * Optimize our representation and our subqueries representations
   *
   * @return an optimized copy of us (which may not be a copy if there is nothing to optimize)
   */
  @Override
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    if (disjuncts.isEmpty()) {
      return new MatchNoDocsQuery("empty DisjunctionMaxQuery");
    }

    if (disjuncts.size() == 1) {
      return disjuncts.iterator().next();
    }

    if (tieBreakerMultiplier == 1.0f) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      for (Query sub : disjuncts) {
        builder.add(sub, BooleanClause.Occur.SHOULD);
      }
      return builder.build();
    }

    boolean actuallyRewritten = false;
    List<Query> rewrittenDisjuncts = new ArrayList<>();
    for (Query sub : disjuncts) {
      Query rewrittenSub = sub.rewrite(indexSearcher);
      actuallyRewritten |= rewrittenSub != sub;
      rewrittenDisjuncts.add(rewrittenSub);
    }

    if (actuallyRewritten) {
      return new DisjunctionMaxQuery(rewrittenDisjuncts, tieBreakerMultiplier);
    }

    return super.rewrite(indexSearcher);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this);
    for (Query q : disjuncts) {
      q.visit(v);
    }
  }

  /**
   * Prettyprint us.
   *
   * @param field the field to which we are applied
   * @return a string that shows what we do, of the form "(disjunct1 | disjunct2 | ... |
   *     disjunctn)^boost"
   */
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("(");
    Iterator<Query> it = disjuncts.iterator();
    for (int i = 0; it.hasNext(); i++) {
      Query subquery = it.next();
      if (subquery instanceof BooleanQuery) { // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subquery.toString(field));
        buffer.append(")");
      } else buffer.append(subquery.toString(field));
      if (i != disjuncts.size() - 1) buffer.append(" | ");
    }
    buffer.append(")");
    if (tieBreakerMultiplier != 0.0f) {
      buffer.append("~");
      buffer.append(tieBreakerMultiplier);
    }
    return buffer.toString();
  }

  /**
   * Return true if we represent the same query as other
   *
   * @param other another object
   * @return true if other is a DisjunctionMaxQuery with the same boost and the same subqueries, in
   *     the same order, as us
   */
  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) && equalsTo(getClass().cast(other));
  }

  private boolean equalsTo(DisjunctionMaxQuery other) {
    return tieBreakerMultiplier == other.tieBreakerMultiplier
        && Objects.equals(disjuncts, other.disjuncts);
  }

  /**
   * Compute a hash code for hashing us
   *
   * @return the hash code
   */
  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + Float.floatToIntBits(tieBreakerMultiplier);
    h = 31 * h + Objects.hashCode(disjuncts);
    return h;
  }
}
