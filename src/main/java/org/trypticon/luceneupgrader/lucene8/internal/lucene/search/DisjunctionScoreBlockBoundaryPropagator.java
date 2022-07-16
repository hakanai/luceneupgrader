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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

final class DisjunctionScoreBlockBoundaryPropagator {

  private static final Comparator<Scorer> MAX_SCORE_COMPARATOR = Comparator.comparing((Scorer s) -> {
    try {
      return s.getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }).thenComparing(Comparator.comparing(s -> s.iterator().cost()));

  private final Scorer[] scorers;
  private final float[] maxScores;
  private int leadIndex = 0;

  DisjunctionScoreBlockBoundaryPropagator(Collection<Scorer> scorers) throws IOException {
    this.scorers = scorers.toArray(new Scorer[0]);
    for (Scorer scorer : this.scorers) {
      scorer.advanceShallow(0);
    }
    Arrays.sort(this.scorers, MAX_SCORE_COMPARATOR);

    maxScores = new float[this.scorers.length];
    for (int i = 0; i < this.scorers.length; ++i) {
      maxScores[i] = this.scorers[i].getMaxScore(DocIdSetIterator.NO_MORE_DOCS);
    }
  }

  int advanceShallow(int target) throws IOException {
    // For scorers that are below the lead index, just propagate.
    for (int i = 0; i < leadIndex; ++i) {
      Scorer s = scorers[i];
      if (s.docID() < target) {
        s.advanceShallow(target);
      }
    }

    // For scorers above the lead index, we take the minimum
    // boundary.
    Scorer leadScorer = scorers[leadIndex];
    int upTo = leadScorer.advanceShallow(Math.max(leadScorer.docID(), target));

    for (int i = leadIndex + 1; i < scorers.length; ++i) {
      Scorer scorer = scorers[i];
      if (scorer.docID() <= target) {
        upTo = Math.min(scorer.advanceShallow(target), upTo);
      }
    }

    // If the maximum scoring clauses are beyond `target`, then we use their
    // docID as a boundary. It helps not consider them when computing the
    // maximum score and get a lower score upper bound.
    for (int i = scorers.length - 1; i > leadIndex; --i) {
      Scorer scorer = scorers[i];
      if (scorer.docID() > target) {
        upTo = Math.min(upTo, scorer.docID() - 1);
      } else {
        break;
      }
    }

    return upTo;
  }

  void setMinCompetitiveScore(float minScore) throws IOException {
    // Update the lead index if necessary
    while (leadIndex < maxScores.length - 1 && minScore > maxScores[leadIndex]) {
      leadIndex++;
    }
  }

}
