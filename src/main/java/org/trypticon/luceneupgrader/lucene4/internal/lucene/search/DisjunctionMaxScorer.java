package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

/*
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

final class DisjunctionMaxScorer extends DisjunctionScorer {
  /* Multiplier applied to non-maximum-scoring subqueries for a document as they are summed into the result. */
  private final float tieBreakerMultiplier;

  /* Used when scoring currently matching doc. */
  private float scoreSum;
  private float scoreMax;

  DisjunctionMaxScorer(Weight weight, float tieBreakerMultiplier, Scorer[] subScorers) {
    super(weight, subScorers);
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }
  
  @Override
  protected void reset() {
    scoreSum = scoreMax = 0;
  }
  
  @Override
  protected void accum(Scorer subScorer) throws IOException {
    float subScore = subScorer.score();
    scoreSum += subScore;
    if (subScore > scoreMax) {
      scoreMax = subScore;
    }
  }
  
  @Override
  protected float getFinal() {
    return scoreMax + (scoreSum - scoreMax) * tieBreakerMultiplier; 
  }
}
