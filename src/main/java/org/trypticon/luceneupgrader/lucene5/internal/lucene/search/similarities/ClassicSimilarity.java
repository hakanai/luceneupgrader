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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.SmallFloat;

public class ClassicSimilarity extends TFIDFSimilarity {
  
  private static final float[] NORM_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      NORM_TABLE[i] = SmallFloat.byte315ToFloat((byte)i);
    }
  }

  public ClassicSimilarity() {}
  
  @Override
  public float coord(int overlap, int maxOverlap) {
    return overlap / (float)maxOverlap;
  }

  @Override
  public float queryNorm(float sumOfSquaredWeights) {
    return (float)(1.0 / Math.sqrt(sumOfSquaredWeights));
  }
  
  @Override
  public final long encodeNormValue(float f) {
    return SmallFloat.floatToByte315(f);
  }

  @Override
  public final float decodeNormValue(long norm) {
    return NORM_TABLE[(int) (norm & 0xFF)];  // & 0xFF maps negative bytes to positive above 127
  }


  @Override
  public float lengthNorm(FieldInvertState state) {
    final int numTerms;
    if (discountOverlaps)
      numTerms = state.getLength() - state.getNumOverlap();
    else
      numTerms = state.getLength();
    return state.getBoost() * ((float) (1.0 / Math.sqrt(numTerms)));
  }

  @Override
  public float tf(float freq) {
    return (float)Math.sqrt(freq);
  }
    
  @Override
  public float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }
  
  @Override
  public float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }

  @Override
  public float idf(long docFreq, long numDocs) {
    return (float)(Math.log(numDocs/(double)(docFreq+1)) + 1.0);
  }
    

  protected boolean discountOverlaps = true;


  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  @Override
  public String toString() {
    return "DefaultSimilarity";
  }
}
