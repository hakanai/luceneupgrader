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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities.Similarity;

abstract class PhraseMatcher {

  protected final DocIdSetIterator approximation;
  private final float matchCost;

  PhraseMatcher(DocIdSetIterator approximation, float matchCost) {
    assert TwoPhaseIterator.unwrap(approximation) == null;
    this.approximation = approximation;
    this.matchCost = matchCost;
  }

  abstract float maxFreq() throws IOException;

  public abstract void reset() throws IOException;

  public abstract boolean nextMatch() throws IOException;

  abstract float sloppyWeight(Similarity.SimScorer simScorer);

  abstract int startPosition();

  abstract int endPosition();

  abstract int startOffset() throws IOException;

  abstract int endOffset() throws IOException;

  public float getMatchCost() {
    return matchCost;
  }

}
