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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class CustomScoreQuery extends Query {

  private Query subQuery;
  private ValueSourceQuery[] valSrcQueries; // never null (empty array if there are no valSrcQueries).
  private boolean strict = false; // if true, valueSource part of query does not take part in weights normalization.  
  
  public CustomScoreQuery(Query subQuery) {
    this(subQuery, new ValueSourceQuery[0]);
  }

  public CustomScoreQuery(Query subQuery, ValueSourceQuery valSrcQuery) {
	  this(subQuery, valSrcQuery!=null ? // don't want an array that contains a single null.. 
        new ValueSourceQuery[] {valSrcQuery} : new ValueSourceQuery[0]);
  }

  public CustomScoreQuery(Query subQuery, ValueSourceQuery... valSrcQueries) {
    this.subQuery = subQuery;
    this.valSrcQueries = valSrcQueries!=null?
        valSrcQueries : new ValueSourceQuery[0];
    if (subQuery == null) throw new IllegalArgumentException("<subquery> must not be null!");
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    CustomScoreQuery clone = null;
    
    final Query sq = subQuery.rewrite(reader);
    if (sq != subQuery) {
      clone = (CustomScoreQuery) clone();
      clone.subQuery = sq;
    }

    for(int i = 0; i < valSrcQueries.length; i++) {
      final ValueSourceQuery v = (ValueSourceQuery) valSrcQueries[i].rewrite(reader);
      if (v != valSrcQueries[i]) {
        if (clone == null) clone = (CustomScoreQuery) clone();
        clone.valSrcQueries[i] = v;
      }
    }
    
    return (clone == null) ? this : clone;
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    subQuery.extractTerms(terms);
    for(int i = 0; i < valSrcQueries.length; i++) {
      valSrcQueries[i].extractTerms(terms);
    }
  }

  @Override
  public Object clone() {
    CustomScoreQuery clone = (CustomScoreQuery)super.clone();
    clone.subQuery = (Query) subQuery.clone();
    clone.valSrcQueries = new ValueSourceQuery[valSrcQueries.length];
    for(int i = 0; i < valSrcQueries.length; i++) {
      clone.valSrcQueries[i] = (ValueSourceQuery) valSrcQueries[i].clone();
    }
    return clone;
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder(name()).append("(");
    sb.append(subQuery.toString(field));
    for(int i = 0; i < valSrcQueries.length; i++) {
      sb.append(", ").append(valSrcQueries[i].toString(field));
    }
    sb.append(")");
    sb.append(strict?" STRICT" : "");
    return sb.toString() + ToStringUtils.boost(getBoost());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!super.equals(o))
      return false;
    if (getClass() != o.getClass()) {
      return false;
    }
    CustomScoreQuery other = (CustomScoreQuery)o;
    if (this.getBoost() != other.getBoost() ||
        !this.subQuery.equals(other.subQuery) ||
        this.strict != other.strict ||
        this.valSrcQueries.length != other.valSrcQueries.length) {
      return false;
    }
    return Arrays.equals(valSrcQueries, other.valSrcQueries);
  }

  @Override
  public int hashCode() {
    return (getClass().hashCode() + subQuery.hashCode() + Arrays.hashCode(valSrcQueries))
      ^ Float.floatToIntBits(getBoost()) ^ (strict ? 1234 : 4321);
  }
  
  protected CustomScoreProvider getCustomScoreProvider(IndexReader reader) throws IOException {
    return new CustomScoreProvider(reader);
  }

  //=========================== W E I G H T ============================
  
  private class CustomWeight extends Weight {
    Similarity similarity;
    Weight subQueryWeight;
    Weight[] valSrcWeights;
    boolean qStrict;

    public CustomWeight(Searcher searcher) throws IOException {
      this.similarity = getSimilarity(searcher);
      this.subQueryWeight = subQuery.createWeight(searcher);
      this.valSrcWeights = new Weight[valSrcQueries.length];
      for(int i = 0; i < valSrcQueries.length; i++) {
        this.valSrcWeights[i] = valSrcQueries[i].createWeight(searcher);
      }
      this.qStrict = strict;
    }

    @Override
    public Query getQuery() {
      return CustomScoreQuery.this;
    }

    @Override
    public float getValue() {
      return getBoost();
    }

    @Override
    public float sumOfSquaredWeights() throws IOException {
      float sum = subQueryWeight.sumOfSquaredWeights();
      for(int i = 0; i < valSrcWeights.length; i++) {
        if (qStrict) {
          valSrcWeights[i].sumOfSquaredWeights(); // do not include ValueSource part in the query normalization
        } else {
          sum += valSrcWeights[i].sumOfSquaredWeights();
        }
      }
      sum *= getBoost() * getBoost(); // boost each sub-weight
      return sum ;
    }

    @Override
    public void normalize(float norm) {
      norm *= getBoost(); // incorporate boost
      subQueryWeight.normalize(norm);
      for(int i = 0; i < valSrcWeights.length; i++) {
        if (qStrict) {
          valSrcWeights[i].normalize(1); // do not normalize the ValueSource part
        } else {
          valSrcWeights[i].normalize(norm);
        }
      }
    }

    @Override
    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException {
      // Pass true for "scoresDocsInOrder", because we
      // require in-order scoring, even if caller does not,
      // since we call advance on the valSrcScorers.  Pass
      // false for "topScorer" because we will not invoke
      // score(Collector) on these scorers:
      Scorer subQueryScorer = subQueryWeight.scorer(reader, true, false);
      if (subQueryScorer == null) {
        return null;
      }
      Scorer[] valSrcScorers = new Scorer[valSrcWeights.length];
      for(int i = 0; i < valSrcScorers.length; i++) {
         valSrcScorers[i] = valSrcWeights[i].scorer(reader, true, topScorer);
      }
      return new CustomScorer(similarity, reader, this, subQueryScorer, valSrcScorers);
    }

    @Override
    public Explanation explain(IndexReader reader, int doc) throws IOException {
      Explanation explain = doExplain(reader, doc);
      return explain == null ? new Explanation(0.0f, "no matching docs") : explain;
    }
    
    private Explanation doExplain(IndexReader reader, int doc) throws IOException {
      Explanation subQueryExpl = subQueryWeight.explain(reader, doc);
      if (!subQueryExpl.isMatch()) {
        return subQueryExpl;
      }
      // match
      Explanation[] valSrcExpls = new Explanation[valSrcWeights.length];
      for(int i = 0; i < valSrcWeights.length; i++) {
        valSrcExpls[i] = valSrcWeights[i].explain(reader, doc);
      }
      Explanation customExp = CustomScoreQuery.this.getCustomScoreProvider(reader).customExplain(doc,subQueryExpl,valSrcExpls);
      float sc = getValue() * customExp.getValue();
      Explanation res = new ComplexExplanation(
        true, sc, CustomScoreQuery.this.toString() + ", product of:");
      res.addDetail(customExp);
      res.addDetail(new Explanation(getValue(), "queryBoost")); // actually using the q boost as q weight (== weight value)
      return res;
    }

    @Override
    public boolean scoresDocsOutOfOrder() {
      return false;
    }
    
  }


  //=========================== S C O R E R ============================
  
  private class CustomScorer extends Scorer {
    private final float qWeight;
    private Scorer subQueryScorer;
    private Scorer[] valSrcScorers;
    private final CustomScoreProvider provider;
    private float vScores[]; // reused in score() to avoid allocating this array for each doc 

    // constructor
    private CustomScorer(Similarity similarity, IndexReader reader, CustomWeight w,
        Scorer subQueryScorer, Scorer[] valSrcScorers) throws IOException {
      super(similarity,w);
      this.qWeight = w.getValue();
      this.subQueryScorer = subQueryScorer;
      this.valSrcScorers = valSrcScorers;
      this.vScores = new float[valSrcScorers.length];
      this.provider = CustomScoreQuery.this.getCustomScoreProvider(reader);
    }

    @Override
    public int nextDoc() throws IOException {
      int doc = subQueryScorer.nextDoc();
      if (doc != NO_MORE_DOCS) {
        for (int i = 0; i < valSrcScorers.length; i++) {
          valSrcScorers[i].advance(doc);
        }
      }
      return doc;
    }

    @Override
    public int docID() {
      return subQueryScorer.docID();
    }
    
    @Override
    public float score() throws IOException {
      for (int i = 0; i < valSrcScorers.length; i++) {
        vScores[i] = valSrcScorers[i].score();
      }
      return qWeight * provider.customScore(subQueryScorer.docID(), subQueryScorer.score(), vScores);
    }

    @Override
    public int advance(int target) throws IOException {
      int doc = subQueryScorer.advance(target);
      if (doc != NO_MORE_DOCS) {
        for (int i = 0; i < valSrcScorers.length; i++) {
          valSrcScorers[i].advance(doc);
        }
      }
      return doc;
    }
  }

  @Override
  public Weight createWeight(Searcher searcher) throws IOException {
    return new CustomWeight(searcher);
  }

  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public String name() {
    return "custom";
  }

}
