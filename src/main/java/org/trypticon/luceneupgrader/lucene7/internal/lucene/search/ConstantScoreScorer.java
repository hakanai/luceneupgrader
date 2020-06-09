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

public final class ConstantScoreScorer extends Scorer {

  private final float score;
  private final TwoPhaseIterator twoPhaseIterator;
  private final DocIdSetIterator disi;

  public ConstantScoreScorer(Weight weight, float score, DocIdSetIterator disi) {
    super(weight);
    this.score = score;
    this.twoPhaseIterator = null;
    this.disi = disi;
  }

  public ConstantScoreScorer(Weight weight, float score, TwoPhaseIterator twoPhaseIterator) {
    super(weight);
    this.score = score;
    this.twoPhaseIterator = twoPhaseIterator;
    this.disi = TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator);
  }

  @Override
  public DocIdSetIterator iterator() {
    return disi;
  }

  @Override
  public TwoPhaseIterator twoPhaseIterator() {
    return twoPhaseIterator;
  }

  @Override
  public int docID() {
    return disi.docID();
  }

  @Override
  public float score() throws IOException {
    return score;
  }

}

