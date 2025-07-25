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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;

/**
 * {@link DocIdSetIterator} that skips non-competitive docs thanks to the indexed impacts. Call
 * {@link #setMinCompetitiveScore(float)} in order to give this iterator the ability to skip
 * low-scoring documents.
 *
 * @lucene.internal
 */
public final class ImpactsDISI extends DocIdSetIterator {

  private final DocIdSetIterator in;
  private final MaxScoreCache maxScoreCache;
  private float minCompetitiveScore = 0;
  private int upTo = DocIdSetIterator.NO_MORE_DOCS;
  private float maxScore = Float.MAX_VALUE;

  /**
   * Sole constructor.
   *
   * @param in the iterator, typically an ImpactsEnum
   * @param maxScoreCache the cache of maximum scores, typically computed from the same ImpactsEnum
   */
  public ImpactsDISI(DocIdSetIterator in, MaxScoreCache maxScoreCache) {
    this.in = in;
    this.maxScoreCache = maxScoreCache;
  }

  /** Get the {@link MaxScoreCache}. */
  public MaxScoreCache getMaxScoreCache() {
    return maxScoreCache;
  }

  /**
   * Set the minimum competitive score.
   *
   * @see Scorer#setMinCompetitiveScore(float)
   */
  public void setMinCompetitiveScore(float minCompetitiveScore) {
    assert minCompetitiveScore >= this.minCompetitiveScore;
    if (minCompetitiveScore > this.minCompetitiveScore) {
      this.minCompetitiveScore = minCompetitiveScore;
      // force upTo and maxScore to be recomputed so that we will skip documents
      // if the current block of documents is not competitive - only if the min
      // competitive score actually increased
      upTo = -1;
    }
  }

  private int advanceTarget(int target) throws IOException {
    if (target <= upTo) {
      // we are still in the current block, which is considered competitive
      // according to impacts, no skipping
      return target;
    }

    upTo = maxScoreCache.advanceShallow(target);
    maxScore = maxScoreCache.getMaxScoreForLevelZero();

    while (true) {
      assert upTo >= target;

      if (maxScore >= minCompetitiveScore) {
        return target;
      }

      if (upTo == NO_MORE_DOCS) {
        return NO_MORE_DOCS;
      }

      final int skipUpTo = maxScoreCache.getSkipUpTo(minCompetitiveScore);
      if (skipUpTo == -1) { // no further skipping
        target = upTo + 1;
      } else if (skipUpTo == NO_MORE_DOCS) {
        return NO_MORE_DOCS;
      } else {
        target = skipUpTo + 1;
      }
      upTo = maxScoreCache.advanceShallow(target);
      maxScore = maxScoreCache.getMaxScoreForLevelZero();
    }
  }

  @Override
  public int advance(int target) throws IOException {
    return in.advance(advanceTarget(target));
  }

  @Override
  public int nextDoc() throws IOException {
    return advance(in.docID() + 1);
  }

  @Override
  public int docID() {
    return in.docID();
  }

  @Override
  public long cost() {
    return in.cost();
  }
}
