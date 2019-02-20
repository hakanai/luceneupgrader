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

abstract class PhraseScorer extends Scorer {
  protected byte[] norms;
  protected float value;

  PhrasePositions min, max;

  private float freq; //phrase frequency in current doc as computed by phraseFreq().

  PhraseScorer(Weight weight, PhraseQuery.PostingsAndFreq[] postings,
      Similarity similarity, byte[] norms) {
    super(similarity, weight);
    this.norms = norms;
    this.value = weight.getValue();

    // convert tps to a list of phrase positions.
    // note: phrase-position differs from term-position in that its position
    // reflects the phrase offset: pp.pos = tp.pos - offset.
    // this allows to easily identify a matching (exact) phrase 
    // when all PhrasePositions have exactly the same position.
    if (postings.length > 0) {
      min = new PhrasePositions(postings[0].postings, postings[0].position, 0, postings[0].terms);
      max = min;
      max.doc = -1;
      for (int i = 1; i < postings.length; i++) {
        PhrasePositions pp = new PhrasePositions(postings[i].postings, postings[i].position, i, postings[i].terms);
        max.next = pp;
        max = pp;
        max.doc = -1;
      }
      max.next = min; // make it cyclic for easier manipulation
    }
  }

  @Override
  public int docID() {
    return max.doc; 
  }

  @Override
  public int nextDoc() throws IOException {
    return advance(max.doc);
  }

  private boolean advanceMin(int target) throws IOException {
    if (!min.skipTo(target)) { 
      max.doc = NO_MORE_DOCS; // for further calls to docID() 
      return false;
    }
    min = min.next; // cyclic
    max = max.next; // cyclic
    return true;
  }

  @Override
  public float score() throws IOException {
    //System.out.println("scoring " + max.doc);
    float raw = getSimilarity().tf(freq) * value; // raw score
    return norms == null ? raw : raw * getSimilarity().decodeNormValue(norms[max.doc]); // normalize
  }

  @Override
  public int advance(int target) throws IOException {
    freq = 0.0f;
    if (!advanceMin(target)) {
      return NO_MORE_DOCS;
    }        
    boolean restart=false;
    while (freq == 0.0f) {
      while (min.doc < max.doc || restart) {
        restart = false;
        if (!advanceMin(max.doc)) {
          return NO_MORE_DOCS;
        }        
      }
      // found a doc with all of the terms
      freq = phraseFreq(); // check for phrase
      restart = true;
    } 

    // found a match
    return max.doc;
  }
  
  @Override
  public final float freq() {
    return freq;
  }

  abstract float phraseFreq() throws IOException;

  @Override
  public String toString() { return "scorer(" + weight + ")"; }
 
}
