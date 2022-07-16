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
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Impact;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Impacts;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.ImpactsSource;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.Similarity.SimScorer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ArrayUtil;

final class MaxScoreCache {

  private final ImpactsSource impactsSource;
  private final SimScorer scorer;
  private float[] maxScoreCache;
  private int[] maxScoreCacheUpTo;

  public MaxScoreCache(ImpactsSource impactsSource, SimScorer scorer) {
    this.impactsSource = impactsSource;
    this.scorer = scorer;
    maxScoreCache = new float[0];
    maxScoreCacheUpTo = new int[0];
  }

  private void ensureCacheSize(int size) {
    if (maxScoreCache.length < size) {
      int oldLength = maxScoreCache.length;
      maxScoreCache = ArrayUtil.grow(maxScoreCache, size);
      maxScoreCacheUpTo = ArrayUtil.growExact(maxScoreCacheUpTo, maxScoreCache.length);
      Arrays.fill(maxScoreCacheUpTo, oldLength, maxScoreCacheUpTo.length, -1);
    }
  }

  private float computeMaxScore(List<Impact> impacts) {
    float maxScore = 0;
    for (Impact impact : impacts) {
      maxScore = Math.max(scorer.score(impact.freq, impact.norm), maxScore);
    }
    return maxScore;
  }

  int getLevel(int upTo) throws IOException {
    final Impacts impacts = impactsSource.getImpacts();
    for (int level = 0, numLevels = impacts.numLevels(); level < numLevels; ++level) {
      final int impactsUpTo = impacts.getDocIdUpTo(level);
      if (upTo <= impactsUpTo) {
        return level;
      }
    }
    return -1;
  }

  float getMaxScoreForLevel(int level) throws IOException {
    final Impacts impacts = impactsSource.getImpacts();
    ensureCacheSize(level + 1);
    final int levelUpTo = impacts.getDocIdUpTo(level);
    if (maxScoreCacheUpTo[level] < levelUpTo) {
      maxScoreCache[level] = computeMaxScore(impacts.getImpacts(level));
      maxScoreCacheUpTo[level] = levelUpTo;
    }
    return maxScoreCache[level];
  }

  private int getSkipLevel(Impacts impacts, float minScore) throws IOException {
    final int numLevels = impacts.numLevels();
    for (int level = 0; level < numLevels; ++level) {
      if (getMaxScoreForLevel(level) >= minScore) {
        return level - 1;
      }
    }
    return numLevels - 1;
  }

  int getSkipUpTo(float minScore) throws IOException {
    final Impacts impacts = impactsSource.getImpacts();
    final int level = getSkipLevel(impacts, minScore);
    if (level == -1) {
      return -1;
    }
    return impacts.getDocIdUpTo(level);
  }

}
