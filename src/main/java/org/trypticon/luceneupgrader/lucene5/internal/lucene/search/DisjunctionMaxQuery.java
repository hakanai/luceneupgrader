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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ToStringUtils;

public final class DisjunctionMaxQuery extends Query implements Iterable<Query> {

  /* The subqueries */
  private final ArrayList<Query> disjuncts = new ArrayList<>();

  /* Multiple of the non-max disjunct scores added into our final score.  Non-zero values support tie-breaking. */
  private final float tieBreakerMultiplier;


  @Deprecated
  public DisjunctionMaxQuery(float tieBreakerMultiplier) {
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }

  public DisjunctionMaxQuery(Collection<Query> disjuncts, float tieBreakerMultiplier) {
    Objects.requireNonNull(disjuncts, "Collection of Querys must not be null");
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    add(disjuncts);
  }


  @Deprecated
  public void add(Query query) {
    disjuncts.add(Objects.requireNonNull(query, "Query must not be null"));
  }


  @Deprecated
  public void add(Collection<Query> disjuncts) {
    this.disjuncts.addAll(Objects.requireNonNull(disjuncts, "Query connection must not be null"));
  }

  @Override
  public Iterator<Query> iterator() {
    return disjuncts.iterator();
  }
  
  public ArrayList<Query> getDisjuncts() {
    return disjuncts;
  }

  public float getTieBreakerMultiplier() {
    return tieBreakerMultiplier;
  }

  protected class DisjunctionMaxWeight extends Weight {

    protected final ArrayList<Weight> weights = new ArrayList<>();  // The Weight's for our subqueries, in 1-1 correspondence with disjuncts
    private final boolean needsScores;

    public DisjunctionMaxWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
      super(DisjunctionMaxQuery.this);
      for (Query disjunctQuery : disjuncts) {
        weights.add(searcher.createWeight(disjunctQuery, needsScores));
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
    public float getValueForNormalization() throws IOException {
      float max = 0.0f, sum = 0.0f;
      for (Weight currentWeight : weights) {
        float sub = currentWeight.getValueForNormalization();
        sum += sub;
        max = Math.max(max, sub);
        
      }
      return (((sum - max) * tieBreakerMultiplier * tieBreakerMultiplier) + max);
    }

    @Override
    public void normalize(float norm, float boost) {
      for (Weight wt : weights) {
        wt.normalize(norm, boost);
      }
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
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
      boolean match = false;
      float max = 0.0f, sum = 0.0f;
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
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    return new DisjunctionMaxWeight(searcher, needsScores);
  }


  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (getBoost() != 1f) {
      return super.rewrite(reader);
    }
    int numDisjunctions = disjuncts.size();
    if (numDisjunctions == 1) {
      return disjuncts.get(0);
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
    int numDisjunctions = disjuncts.size();
    for (int i = 0 ; i < numDisjunctions; i++) {
      Query subquery = disjuncts.get(i);
      if (subquery instanceof BooleanQuery) {   // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subquery.toString(field));
        buffer.append(")");
      }
      else buffer.append(subquery.toString(field));
      if (i != numDisjunctions-1) buffer.append(" | ");
    }
    buffer.append(")");
    if (tieBreakerMultiplier != 0.0f) {
      buffer.append("~");
      buffer.append(tieBreakerMultiplier);
    }
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }


  @Override
  public boolean equals(Object o) {
    if (! (o instanceof DisjunctionMaxQuery) ) return false;
    DisjunctionMaxQuery other = (DisjunctionMaxQuery)o;
    return super.equals(o)
            && this.tieBreakerMultiplier == other.tieBreakerMultiplier
            && this.disjuncts.equals(other.disjuncts);
  }


  @Override
  public int hashCode() {
    int h = super.hashCode();
    h = 31 * h + Float.floatToIntBits(tieBreakerMultiplier);
    h = 31 * h + disjuncts.hashCode();
    return h;
  }


}
