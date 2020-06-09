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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

public class BooleanSimilarity extends Similarity {

  private static final Similarity BM25_SIM = new BM25Similarity();

  public BooleanSimilarity() {}

  @Override
  public long computeNorm(FieldInvertState state) {
    return BM25_SIM.computeNorm(state);
  }

  @Override
  public SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    return new BooleanWeight(boost);
  }

  private static class BooleanWeight extends SimWeight {
    final float boost;

    BooleanWeight(float boost) {
      this.boost = boost;
    }
  }

  @Override
  public SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
    final float boost = ((BooleanWeight) weight).boost;

    return new SimScorer() {

      @Override
      public float score(int doc, float freq) throws IOException {
        return boost;
      }

      @Override
      public Explanation explain(int doc, Explanation freq) throws IOException {
        Explanation queryBoostExpl = Explanation.match(boost, "query boost");
        return Explanation.match(
            queryBoostExpl.getValue(),
            "score(" + getClass().getSimpleName() + ", doc=" + doc + "), computed from:",
            queryBoostExpl);
      }

      @Override
      public float computeSlopFactor(int distance) {
        return 1f;
      }

      @Override
      public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
        return 1f;
      }
    };
  }

}
