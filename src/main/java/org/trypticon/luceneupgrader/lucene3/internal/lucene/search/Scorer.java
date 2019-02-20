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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.BooleanClause.Occur;

public abstract class Scorer extends DocIdSetIterator {
  private final Similarity similarity;
  protected final Weight weight;

  protected Scorer(Weight weight) {
    this(null, weight);
  }
  

  @Deprecated
  protected Scorer(Similarity similarity) {
    this(similarity, null);
  }
  
  @Deprecated
  protected Scorer(Similarity similarity, Weight weight) {
    this.similarity = similarity;
    this.weight = weight;
  }


  @Deprecated
  public Similarity getSimilarity() {
    return this.similarity;
  }


  public void score(Collector collector) throws IOException {
    collector.setScorer(this);
    int doc;
    while ((doc = nextDoc()) != NO_MORE_DOCS) {
      collector.collect(doc);
    }
  }

  protected boolean score(Collector collector, int max, int firstDocID) throws IOException {
    collector.setScorer(this);
    int doc = firstDocID;
    while (doc < max) {
      collector.collect(doc);
      doc = nextDoc();
    }
    return doc != NO_MORE_DOCS;
  }
  

  public abstract float score() throws IOException;


  public float freq() throws IOException {
    throw new UnsupportedOperationException(this + " does not implement freq()");
  }

  public static abstract class ScorerVisitor<P extends Query, C extends Query, S extends Scorer> {
    public void visitOptional(P parent, C child, S scorer) {}
    
    public void visitRequired(P parent, C child, S scorer) {}
    
    public void visitProhibited(P parent, C child, S scorer) {}
  } 

  public void visitScorers(ScorerVisitor<Query, Query, Scorer> visitor) {
    visitSubScorers(null, Occur.MUST/*must id default*/, visitor);
  }

  public void visitSubScorers(Query parent, Occur relationship,
      ScorerVisitor<Query, Query, Scorer> visitor) {
    if (weight == null)
      throw new UnsupportedOperationException();

    final Query q = weight.getQuery();
    switch (relationship) {
    case MUST:
      visitor.visitRequired(parent, q, this);
      break;
    case MUST_NOT:
      visitor.visitProhibited(parent, q, this);
      break;
    case SHOULD:
      visitor.visitOptional(parent, q, this);
      break;
    }
  }
}
