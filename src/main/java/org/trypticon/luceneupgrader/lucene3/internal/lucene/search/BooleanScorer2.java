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
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.BooleanClause.Occur;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.BooleanQuery.BooleanWeight;

/* See the description in BooleanScorer.java, comparing
 * BooleanScorer & BooleanScorer2 */

class BooleanScorer2 extends Scorer {
  
  private final List<Scorer> requiredScorers;
  private final List<Scorer> optionalScorers;
  private final List<Scorer> prohibitedScorers;

  private class Coordinator {
    float[] coordFactors = null;
    int maxCoord = 0; // to be increased for each non prohibited scorer
    int nrMatchers; // to be increased by score() of match counting scorers.
    
    void init(BooleanWeight weight, boolean disableCoord) { // use after all scorers have been added.
      coordFactors = new float[optionalScorers.size() + requiredScorers.size() + 1];
      for (int i = 0; i < coordFactors.length; i++) {
        coordFactors[i] = disableCoord ? 1.0f : weight.coord(i, maxCoord);
      }
    }
  }

  private final Coordinator coordinator;


  private final Scorer countingSumScorer;

  private final int minNrShouldMatch;

  private int doc = -1;

  public BooleanScorer2(BooleanWeight weight, boolean disableCoord, Similarity similarity, int minNrShouldMatch,
      List<Scorer> required, List<Scorer> prohibited, List<Scorer> optional, int maxCoord) throws IOException {
    super(weight);
    if (minNrShouldMatch < 0) {
      throw new IllegalArgumentException("Minimum number of optional scorers should not be negative");
    }
    coordinator = new Coordinator();
    this.minNrShouldMatch = minNrShouldMatch;
    coordinator.maxCoord = maxCoord;

    optionalScorers = optional;
    requiredScorers = required;    
    prohibitedScorers = prohibited;
    
    coordinator.init(weight, disableCoord);
    countingSumScorer = makeCountingSumScorer(disableCoord, similarity);
  }
  
  private class SingleMatchScorer extends Scorer {
    private Scorer scorer;
    private int lastScoredDoc = -1;
    // Save the score of lastScoredDoc, so that we don't compute it more than
    // once in score().
    private float lastDocScore = Float.NaN;

    SingleMatchScorer(Scorer scorer) {
      super(scorer.weight);
      this.scorer = scorer;
    }

    @Override
    public float score() throws IOException {
      int doc = docID();
      if (doc >= lastScoredDoc) {
        if (doc > lastScoredDoc) {
          lastDocScore = scorer.score();
          lastScoredDoc = doc;
        }
        coordinator.nrMatchers++;
      }
      return lastDocScore;
    }

    @Override
    public float freq() throws IOException {
      return 1;
    }

    @Override
    public int docID() {
      return scorer.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return scorer.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      return scorer.advance(target);
    }
  }

  private Scorer countingDisjunctionSumScorer(final List<Scorer> scorers,
      int minNrShouldMatch) throws IOException {
    // each scorer from the list counted as a single matcher
    return new DisjunctionSumScorer(weight, scorers, minNrShouldMatch) {
      private int lastScoredDoc = -1;
      // Save the score of lastScoredDoc, so that we don't compute it more than
      // once in score().
      private float lastDocScore = Float.NaN;
      @Override public float score() throws IOException {
        int doc = docID();
        if (doc >= lastScoredDoc) {
          if (doc > lastScoredDoc) {
            lastDocScore = super.score();
            lastScoredDoc = doc;
          }
          coordinator.nrMatchers += super.nrMatchers;
        }
        return lastDocScore;
      }
    };
  }

  private Scorer countingConjunctionSumScorer(boolean disableCoord,
                                              Similarity similarity,
                                              List<Scorer> requiredScorers) throws IOException {
    // each scorer from the list counted as a single matcher
    final int requiredNrMatchers = requiredScorers.size();
    return new ConjunctionScorer(weight, requiredScorers) {
      private int lastScoredDoc = -1;
      // Save the score of lastScoredDoc, so that we don't compute it more than
      // once in score().
      private float lastDocScore = Float.NaN;
      @Override public float score() throws IOException {
        int doc = docID();
        if (doc >= lastScoredDoc) {
          if (doc > lastScoredDoc) {
            lastDocScore = super.score();
            lastScoredDoc = doc;
          }
          coordinator.nrMatchers += requiredNrMatchers;
        }
        // All scorers match, so defaultSimilarity super.score() always has 1 as
        // the coordination factor.
        // Therefore the sum of the scores of the requiredScorers
        // is used as score.
        return lastDocScore;
      }
    };
  }

  private Scorer dualConjunctionSumScorer(boolean disableCoord,
                                          Similarity similarity,
                                          Scorer req1, Scorer req2) throws IOException { // non counting.
    return new ConjunctionScorer(weight, req1, req2);
    // All scorers match, so defaultSimilarity always has 1 as
    // the coordination factor.
    // Therefore the sum of the scores of two scorers
    // is used as score.
  }


  private Scorer makeCountingSumScorer(boolean disableCoord,
                                       Similarity similarity) throws IOException { // each scorer counted as a single matcher
    return (requiredScorers.size() == 0)
      ? makeCountingSumScorerNoReq(disableCoord, similarity)
      : makeCountingSumScorerSomeReq(disableCoord, similarity);
  }

  private Scorer makeCountingSumScorerNoReq(boolean disableCoord, Similarity similarity) throws IOException { // No required scorers
    // minNrShouldMatch optional scorers are required, but at least 1
    int nrOptRequired = (minNrShouldMatch < 1) ? 1 : minNrShouldMatch;
    Scorer requiredCountingSumScorer;
    if (optionalScorers.size() > nrOptRequired)
      requiredCountingSumScorer = countingDisjunctionSumScorer(optionalScorers, nrOptRequired);
    else if (optionalScorers.size() == 1)
      requiredCountingSumScorer = new SingleMatchScorer(optionalScorers.get(0));
    else {
      requiredCountingSumScorer = countingConjunctionSumScorer(disableCoord, similarity, optionalScorers);
    }
    return addProhibitedScorers(requiredCountingSumScorer);
  }

  private Scorer makeCountingSumScorerSomeReq(boolean disableCoord, Similarity similarity) throws IOException { // At least one required scorer.
    if (optionalScorers.size() == minNrShouldMatch) { // all optional scorers also required.
      ArrayList<Scorer> allReq = new ArrayList<Scorer>(requiredScorers);
      allReq.addAll(optionalScorers);
      return addProhibitedScorers(countingConjunctionSumScorer(disableCoord, similarity, allReq));
    } else { // optionalScorers.size() > minNrShouldMatch, and at least one required scorer
      Scorer requiredCountingSumScorer =
            requiredScorers.size() == 1
            ? new SingleMatchScorer(requiredScorers.get(0))
            : countingConjunctionSumScorer(disableCoord, similarity, requiredScorers);
      if (minNrShouldMatch > 0) { // use a required disjunction scorer over the optional scorers
        return addProhibitedScorers( 
                      dualConjunctionSumScorer( // non counting
                              disableCoord,
                              similarity,
                              requiredCountingSumScorer,
                              countingDisjunctionSumScorer(
                                      optionalScorers,
                                      minNrShouldMatch)));
      } else { // minNrShouldMatch == 0
        return new ReqOptSumScorer(
                      addProhibitedScorers(requiredCountingSumScorer),
                      optionalScorers.size() == 1
                        ? new SingleMatchScorer(optionalScorers.get(0))
                        // require 1 in combined, optional scorer.
                        : countingDisjunctionSumScorer(optionalScorers, 1));
      }
    }
  }
  

  private Scorer addProhibitedScorers(Scorer requiredCountingSumScorer) throws IOException
  {
    return (prohibitedScorers.size() == 0)
          ? requiredCountingSumScorer // no prohibited
          : new ReqExclScorer(requiredCountingSumScorer,
                              ((prohibitedScorers.size() == 1)
                                ? prohibitedScorers.get(0)
                                : new DisjunctionSumScorer(weight, prohibitedScorers)));
  }


  @Override
  public void score(Collector collector) throws IOException {
    collector.setScorer(this);
    while ((doc = countingSumScorer.nextDoc()) != NO_MORE_DOCS) {
      collector.collect(doc);
    }
  }
  
  @Override
  protected boolean score(Collector collector, int max, int firstDocID) throws IOException {
    doc = firstDocID;
    collector.setScorer(this);
    while (doc < max) {
      collector.collect(doc);
      doc = countingSumScorer.nextDoc();
    }
    return doc != NO_MORE_DOCS;
  }

  @Override
  public int docID() {
    return doc;
  }
  
  @Override
  public int nextDoc() throws IOException {
    return doc = countingSumScorer.nextDoc();
  }
  
  @Override
  public float score() throws IOException {
    coordinator.nrMatchers = 0;
    float sum = countingSumScorer.score();
    return sum * coordinator.coordFactors[coordinator.nrMatchers];
  }

  @Override
  public float freq() throws IOException {
    return countingSumScorer.freq();
  }

  @Override
  public int advance(int target) throws IOException {
    return doc = countingSumScorer.advance(target);
  }

  @Override
  public void visitSubScorers(Query parent, Occur relationship, ScorerVisitor<Query, Query, Scorer> visitor) {
    super.visitSubScorers(parent, relationship, visitor);
    final Query q = weight.getQuery();
    for (Scorer s : optionalScorers) {
      s.visitSubScorers(q, Occur.SHOULD, visitor);
    }
    for (Scorer s : prohibitedScorers) {
      s.visitSubScorers(q, Occur.MUST_NOT, visitor);
    }
    for (Scorer s : requiredScorers) {
      s.visitSubScorers(q, Occur.MUST, visitor);
    }
  }
}
