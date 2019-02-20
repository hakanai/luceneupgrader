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

import java.util.List;
import java.io.IOException;

class DisjunctionSumScorer extends DisjunctionScorer {
  private final int minimumNrMatchers;
  
  private int doc = -1;

  protected int nrMatchers = -1;

  private double score = Float.NaN;
  

  public DisjunctionSumScorer(Weight weight, List<Scorer> subScorers, int minimumNrMatchers) throws IOException {
    super(null, weight, subScorers.toArray(new Scorer[subScorers.size()]), subScorers.size());

    if (minimumNrMatchers <= 0) {
      throw new IllegalArgumentException("Minimum nr of matchers must be positive");
    }
    if (numScorers <= 1) {
      throw new IllegalArgumentException("There must be at least 2 subScorers");
    }

    this.minimumNrMatchers = minimumNrMatchers;
  }
  

  public DisjunctionSumScorer(Weight weight, List<Scorer> subScorers) throws IOException {
    this(weight, subScorers, 1);
  }

  @Override
  public int nextDoc() throws IOException {
    assert doc != NO_MORE_DOCS;
    while(true) {
      while (subScorers[0].docID() == doc) {
        if (subScorers[0].nextDoc() != NO_MORE_DOCS) {
          heapAdjust(0);
        } else {
          heapRemoveRoot();
          if (numScorers < minimumNrMatchers) {
            return doc = NO_MORE_DOCS;
          }
        }
      }
      afterNext();
      if (nrMatchers >= minimumNrMatchers) {
        break;
      }
    }
    
    return doc;
  }
  
  private void afterNext() throws IOException {
    final Scorer sub = subScorers[0];
    doc = sub.docID();
    if (doc == NO_MORE_DOCS) {
      nrMatchers = Integer.MAX_VALUE; // stop looping
    } else {
      score = sub.score();
      nrMatchers = 1;
      countMatches(1);
      countMatches(2);
    }
  }
  
  // TODO: this currently scores, but so did the previous impl
  // TODO: remove recursion.
  // TODO: if we separate scoring, out of here, modify this
  // and afterNext() to terminate when nrMatchers == minimumNrMatchers
  // then also change freq() to just always compute it from scratch
  private void countMatches(int root) throws IOException {
    if (root < numScorers && subScorers[root].docID() == doc) {
      nrMatchers++;
      score += subScorers[root].score();
      countMatches((root<<1)+1);
      countMatches((root<<1)+2);
    }
  }
  

  @Override
  public float score() throws IOException { 
    return (float)score; 
  }
   
  @Override
  public int docID() {
    return doc;
  }

  @Override
  public float freq() throws IOException {
    return nrMatchers;
  }

  @Override
  public int advance(int target) throws IOException {
    if (numScorers == 0) return doc = NO_MORE_DOCS;
    while (subScorers[0].docID() < target) {
      if (subScorers[0].advance(target) != NO_MORE_DOCS) {
        heapAdjust(0);
      } else {
        heapRemoveRoot();
        if (numScorers == 0) {
          return doc = NO_MORE_DOCS;
        }
      }
    }
    
    afterNext();

    if (nrMatchers >= minimumNrMatchers) {
      return doc;
    } else {
      return nextDoc();
    }
  }
}
