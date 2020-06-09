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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;

public final class DisjunctionMaxQuery extends Query implements Iterable<Query> {

  /* The subqueries */
  private final Query[] disjuncts;

  /* Multiple of the non-max disjunct scores added into our final score.  Non-zero values support tie-breaking. */
  private final float tieBreakerMultiplier;

  public DisjunctionMaxQuery(Collection<Query> disjuncts, float tieBreakerMultiplier) {
    Objects.requireNonNull(disjuncts, "Collection of Querys must not be null");
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    this.disjuncts = disjuncts.toArray(new Query[disjuncts.size()]);
  }

  @Override
  public Iterator<Query> iterator() {
    return getDisjuncts().iterator();
  }
  
  public List<Query> getDisjuncts() {
    return Collections.unmodifiableList(Arrays.asList(disjuncts));
  }

  public float getTieBreakerMultiplier() {
    return tieBreakerMultiplier;
  }

  protected class DisjunctionMaxWeight extends Weight {

    protected final ArrayList<Weight> weights = new ArrayList<>();  // The Weight's for our subqueries, in 1-1 correspondence with disjuncts
    private final boolean needsScores;

    public DisjunctionMaxWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
      super(DisjunctionMaxQuery.this);
      for (Query disjunctQuery : disjuncts) {
        weights.add(searcher.createWeight(disjunctQuery, needsScores, boost));
      }
      this.needsScores = needsScores;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      for (Weight weight : weights) {
        weight.extractTerms(terms);
      }
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
    public Scorer scorer(LeafReaderContext context) throws IOException {
      List<Scorer> scorers = new ArrayList<>();
      for (Weight w : weights) {
        // we will advance() subscorers
        Scorer subScorer = w.scorer(context);
        if (subScorer != null) {
          scorers.add(subScorer);
        }
      }
      if (scorers.isEmpty()) {
        // no sub-scorers had any documents
        return null;
      } else if (scorers.size() == 1) {
        // only one sub-scorer in this segment
        return scorers.get(0);
      } else {
        return new DisjunctionMaxScorer(this, tieBreakerMultiplier, scorers, needsScores);
      }
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      if (weights.size() > TermInSetQuery.BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD) {
        // Disallow caching large dismax queries to not encourage users
        // to build large dismax queries as a workaround to the fact that
        // we disallow caching large TermInSetQueries.
        return false;
      }
      for (Weight w : weights) {
        if (w.isCacheable(ctx) == false)
          return false;
      }
      return true;
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
      boolean match = false;
      float max = Float.NEGATIVE_INFINITY, sum = 0.0f;
      List<Explanation> subs = new ArrayList<>();
      for (Weight wt : weights) {
        Explanation e = wt.explain(context, doc);
        if (e.isMatch()) {
          match = true;
          subs.add(e);
          sum += e.getValue();
          max = Math.max(max, e.getValue());
        }
      }
      if (match) {
        final float score = max + (sum - max) * tieBreakerMultiplier;
        final String desc = tieBreakerMultiplier == 0.0f ? "max of:" : "max plus " + tieBreakerMultiplier + " times others of:";
        return Explanation.match(score, desc, subs);
      } else {
        return Explanation.noMatch("No matching clause");
      }
    }
    
  }  // end of DisjunctionMaxWeight inner class

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return new DisjunctionMaxWeight(searcher, needsScores, boost);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (disjuncts.length == 1) {
      return disjuncts[0];
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
      Query rewrittenSub = sub.rewrite(reader);
      actuallyRewritten |= rewrittenSub != sub;
      rewrittenDisjuncts.add(rewrittenSub);
    }

    if (actuallyRewritten) {
      return new DisjunctionMaxQuery(rewrittenDisjuncts, tieBreakerMultiplier);
    }

    return super.rewrite(reader);
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("(");
    for (int i = 0 ; i < disjuncts.length; i++) {
      Query subquery = disjuncts[i];
      if (subquery instanceof BooleanQuery) {   // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subquery.toString(field));
        buffer.append(")");
      }
      else buffer.append(subquery.toString(field));
      if (i != disjuncts.length-1) buffer.append(" | ");
    }
    buffer.append(")");
    if (tieBreakerMultiplier != 0.0f) {
      buffer.append("~");
      buffer.append(tieBreakerMultiplier);
    }
    return buffer.toString();
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }
  
  private boolean equalsTo(DisjunctionMaxQuery other) {
    return tieBreakerMultiplier == other.tieBreakerMultiplier && 
           Arrays.equals(disjuncts, other.disjuncts);
  }

  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + Float.floatToIntBits(tieBreakerMultiplier);
    h = 31 * h + Arrays.hashCode(disjuncts);
    return h;
  }


}
