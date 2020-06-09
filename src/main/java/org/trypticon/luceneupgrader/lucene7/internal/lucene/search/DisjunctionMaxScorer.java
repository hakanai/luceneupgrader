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
import java.util.List;

final class DisjunctionMaxScorer extends DisjunctionScorer {
  /* Multiplier applied to non-maximum-scoring subqueries for a document as they are summed into the result. */
  private final float tieBreakerMultiplier;

  DisjunctionMaxScorer(Weight weight, float tieBreakerMultiplier, List<Scorer> subScorers, boolean needsScores) {
    super(weight, subScorers, needsScores);
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }

  @Override
  protected float score(DisiWrapper topList) throws IOException {
    float scoreSum = 0;
    float scoreMax = Float.NEGATIVE_INFINITY;
    for (DisiWrapper w = topList; w != null; w = w.next) {
      final float subScore = w.scorer.score();
      scoreSum += subScore;
      if (subScore > scoreMax) {
        scoreMax = subScore;
      }
    }
    return scoreMax + (scoreSum - scoreMax) * tieBreakerMultiplier; 
  }
}
