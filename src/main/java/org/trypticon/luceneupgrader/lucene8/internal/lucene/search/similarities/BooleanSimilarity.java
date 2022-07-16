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

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;

public class BooleanSimilarity extends Similarity {

  private static final Similarity BM25_SIM = new BM25Similarity();

  public BooleanSimilarity() {}

  @Override
  public long computeNorm(FieldInvertState state) {
    return BM25_SIM.computeNorm(state);
  }

  @Override
  public SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    return new BooleanWeight(boost);
  }

  private static class BooleanWeight extends SimScorer {
    final float boost;

    BooleanWeight(float boost) {
      this.boost = boost;
    }

    @Override
    public float score(float freq, long norm) {
      return boost;
    }

    @Override
    public Explanation explain(Explanation freq, long norm) {
      Explanation queryBoostExpl = Explanation.match(boost, "boost, query boost");
      return Explanation.match(
          queryBoostExpl.getValue(),
          "score(" + getClass().getSimpleName() + "), computed from:",
          queryBoostExpl);
    }
  }

}
