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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermDocs;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Set;

public class ValueSourceQuery extends Query {
  ValueSource valSrc;

  public ValueSourceQuery(ValueSource valSrc) {
    this.valSrc=valSrc;
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    return this;
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    // no terms involved here
  }

  class ValueSourceWeight extends Weight {
    Similarity similarity;
    float queryNorm;
    float queryWeight;

    public ValueSourceWeight(Searcher searcher) {
      this.similarity = getSimilarity(searcher);
    }

    @Override
    public Query getQuery() {
      return ValueSourceQuery.this;
    }

    @Override
    public float getValue() {
      return queryWeight;
    }

    @Override
    public float sumOfSquaredWeights() throws IOException {
      queryWeight = getBoost();
      return queryWeight * queryWeight;
    }

    @Override
    public void normalize(float norm) {
      this.queryNorm = norm;
      queryWeight *= this.queryNorm;
    }

    @Override
    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException {
      return new ValueSourceScorer(similarity, reader, this);
    }

    @Override
    public Explanation explain(IndexReader reader, int doc) throws IOException {
      DocValues vals = valSrc.getValues(reader);
      float sc = queryWeight * vals.floatVal(doc);

      Explanation result = new ComplexExplanation(
        true, sc, ValueSourceQuery.this.toString() + ", product of:");

      result.addDetail(vals.explain(doc));
      result.addDetail(new Explanation(getBoost(), "boost"));
      result.addDetail(new Explanation(queryNorm,"queryNorm"));
      return result;
    }
  }

  private class ValueSourceScorer extends Scorer {
    private final float qWeight;
    private final DocValues vals;
    private final TermDocs termDocs;
    private int doc = -1;

    // constructor
    private ValueSourceScorer(Similarity similarity, IndexReader reader, ValueSourceWeight w) throws IOException {
      super(similarity,w);
      qWeight = w.getValue();
      // this is when/where the values are first created.
      vals = valSrc.getValues(reader);
      termDocs = reader.termDocs(null);
    }

    @Override
    public int nextDoc() throws IOException {
      return doc = termDocs.next() ? termDocs.doc() : NO_MORE_DOCS;
    }
    
    @Override
    public int docID() {
      return doc;
    }
    
    @Override
    public int advance(int target) throws IOException {
      return doc = termDocs.skipTo(target) ? termDocs.doc() : NO_MORE_DOCS;
    }
    
    @Override
    public float score() throws IOException {
      return qWeight * vals.floatVal(termDocs.doc());
    }
  }

  @Override
  public Weight createWeight(Searcher searcher) {
    return new ValueSourceQuery.ValueSourceWeight(searcher);
  }

  @Override
  public String toString(String field) {
    return valSrc.toString() + ToStringUtils.boost(getBoost());
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
    ValueSourceQuery other = (ValueSourceQuery)o;
    return this.getBoost() == other.getBoost()
           && this.valSrc.equals(other.valSrc);
  }

  @Override
  public int hashCode() {
    return (getClass().hashCode() + valSrc.hashCode()) ^ Float.floatToIntBits(getBoost());
  }

}
