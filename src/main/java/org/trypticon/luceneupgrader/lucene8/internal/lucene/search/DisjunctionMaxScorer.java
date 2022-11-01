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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;

import java.io.IOException;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.MathUtil;

final class DisjunctionMaxScorer extends DisjunctionScorer {
  private final List<Scorer> subScorers;
  /* Multiplier applied to non-maximum-scoring subqueries for a document as they are summed into the result. */
  private final float tieBreakerMultiplier;

  private final DisjunctionScoreBlockBoundaryPropagator disjunctionBlockPropagator;

  DisjunctionMaxScorer(Weight weight, float tieBreakerMultiplier, List<Scorer> subScorers, ScoreMode scoreMode) throws IOException {
    super(weight, subScorers, scoreMode);
    this.subScorers = subScorers;
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    if (tieBreakerMultiplier < 0 || tieBreakerMultiplier > 1) {
      throw new IllegalArgumentException("tieBreakerMultiplier must be in [0, 1]");
    }
    if (scoreMode == ScoreMode.TOP_SCORES) {
      this.disjunctionBlockPropagator = new DisjunctionScoreBlockBoundaryPropagator(subScorers);
    } else {
      this.disjunctionBlockPropagator = null;
    }
  }

  @Override
  protected float score(DisiWrapper topList) throws IOException {
    float scoreMax = 0;
    double otherScoreSum = 0;
    for (DisiWrapper w = topList; w != null; w = w.next) {
      float subScore = w.scorer.score();
      if (subScore >= scoreMax) {
        otherScoreSum += scoreMax;
        scoreMax = subScore;
      } else {
        otherScoreSum += subScore;
      }
    }
    return (float) (scoreMax + otherScoreSum * tieBreakerMultiplier);
  }

  @Override
  public int advanceShallow(int target) throws IOException {
    return disjunctionBlockPropagator.advanceShallow(target);
  }

  @Override
  public float getMaxScore(int upTo) throws IOException {
    float scoreMax = 0;
    double otherScoreSum = 0;
    for (Scorer scorer : subScorers) {
      if (scorer.docID() <= upTo) {
        float subScore = scorer.getMaxScore(upTo);
        if (subScore >= scoreMax) {
          otherScoreSum += scoreMax;
          scoreMax = subScore;
        } else {
          otherScoreSum += subScore;
        }
      }
    }

    if (tieBreakerMultiplier == 0) {
      return scoreMax;
    } else {
      // The error of sums depends on the order in which values are summed up. In
      // order to avoid this issue, we compute an upper bound of the value that
      // the sum may take. If the max relative error is b, then it means that two
      // sums are always within 2*b of each other.
      otherScoreSum *= (1 + 2 * MathUtil.sumRelativeErrorBound(subScorers.size() - 1));
      return (float) (scoreMax + otherScoreSum * tieBreakerMultiplier);
    }
  }

  @Override
  public void setMinCompetitiveScore(float minScore) throws IOException {
    getBlockMaxApprox().setMinCompetitiveScore(minScore);
    disjunctionBlockPropagator.setMinCompetitiveScore(minScore);
    if (tieBreakerMultiplier == 0) {
      // TODO: we could even remove some scorers from the priority queue?
      for (Scorer scorer : subScorers) {
        scorer.setMinCompetitiveScore(minScore);
      }
    }
  }
}