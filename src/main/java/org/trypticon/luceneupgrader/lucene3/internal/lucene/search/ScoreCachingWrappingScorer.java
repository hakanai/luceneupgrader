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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;

public class ScoreCachingWrappingScorer extends Scorer {

  private final Scorer scorer;
  private int curDoc = -1;
  private float curScore;
  
  public ScoreCachingWrappingScorer(Scorer scorer) {
    super(scorer.getSimilarity(), scorer.weight);
    this.scorer = scorer;
  }

  @Override
  protected boolean score(Collector collector, int max, int firstDocID) throws IOException {
    return scorer.score(collector, max, firstDocID);
  }

  @Override
  public Similarity getSimilarity() {
    return scorer.getSimilarity();
  }
  
  @Override
  public float score() throws IOException {
    int doc = scorer.docID();
    if (doc != curDoc) {
      curScore = scorer.score();
      curDoc = doc;
    }
    
    return curScore;
  }

  @Override
  public int docID() {
    return scorer.docID();
  }

  @Override
  public int nextDoc() throws IOException {
    return scorer.nextDoc();
  }
  
  @Override
  public void score(Collector collector) throws IOException {
    scorer.score(collector);
  }
  
  @Override
  public int advance(int target) throws IOException {
    return scorer.advance(target);
  }
  
}
