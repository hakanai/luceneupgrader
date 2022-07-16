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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities;


import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;

public class MultiSimilarity extends Similarity {
  protected final Similarity sims[];
  
  public MultiSimilarity(Similarity sims[]) {
    this.sims = sims;
  }
  
  @Override
  public long computeNorm(FieldInvertState state) {
    return sims[0].computeNorm(state);
  }

  @Override
  public SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    SimScorer subScorers[] = new SimScorer[sims.length];
    for (int i = 0; i < subScorers.length; i++) {
      subScorers[i] = sims[i].scorer(boost, collectionStats, termStats);
    }
    return new MultiSimScorer(subScorers);
  }
  
  static class MultiSimScorer extends SimScorer {
    private final SimScorer subScorers[];
    
    MultiSimScorer(SimScorer subScorers[]) {
      this.subScorers = subScorers;
    }
    
    @Override
    public float score(float freq, long norm) {
      float sum = 0.0f;
      for (SimScorer subScorer : subScorers) {
        sum += subScorer.score(freq, norm);
      }
      return sum;
    }

    @Override
    public Explanation explain(Explanation freq, long norm) {
      List<Explanation> subs = new ArrayList<>();
      for (SimScorer subScorer : subScorers) {
        subs.add(subScorer.explain(freq, norm));
      }
      return Explanation.match(score(freq.getValue().floatValue(), norm), "sum of:", subs);
    }

  }
}
