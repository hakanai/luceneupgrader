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


import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Explanation.IDFExplanation;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.SmallFloat;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.VirtualMethod;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;


public abstract class Similarity implements Serializable {

  // NOTE: this static code must precede setting the static defaultImpl:
  private static final VirtualMethod<Similarity> withoutDocFreqMethod =
    new VirtualMethod<Similarity>(Similarity.class, "idfExplain", Term.class, Searcher.class);
  private static final VirtualMethod<Similarity> withDocFreqMethod =
    new VirtualMethod<Similarity>(Similarity.class, "idfExplain", Term.class, Searcher.class, int.class);

  private final boolean hasIDFExplainWithDocFreqAPI =
    VirtualMethod.compareImplementationDistance(getClass(),
        withDocFreqMethod, withoutDocFreqMethod) >= 0; // its ok for both to be overridden

  private static Similarity defaultImpl = new DefaultSimilarity();

  public static final int NO_DOC_ID_PROVIDED = -1;


  public static void setDefault(Similarity similarity) {
    Similarity.defaultImpl = similarity;
  }


  public static Similarity getDefault() {
    return Similarity.defaultImpl;
  }

  private static final float[] NORM_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++)
      NORM_TABLE[i] = SmallFloat.byte315ToFloat((byte)i);
  }

  @Deprecated
  public static float decodeNorm(byte b) {
    return NORM_TABLE[b & 0xFF];  // & 0xFF maps negative bytes to positive above 127
  }


  public float decodeNormValue(byte b) {
    return NORM_TABLE[b & 0xFF];  // & 0xFF maps negative bytes to positive above 127
  }


  @Deprecated
  public static float[] getNormDecoder() {
    return NORM_TABLE;
  }

  public abstract float computeNorm(String field, FieldInvertState state);
  

  @Deprecated
  public final float lengthNorm(String fieldName, int numTokens) {
    throw new UnsupportedOperationException("please use computeNorm instead");
  }


  public abstract float queryNorm(float sumOfSquaredWeights);


  public byte encodeNormValue(float f) {
    return SmallFloat.floatToByte315(f);
  }
  
  @Deprecated
  public static byte encodeNorm(float f) {
    return SmallFloat.floatToByte315(f);
  }



  public float tf(int freq) {
    return tf((float)freq);
  }


  public abstract float sloppyFreq(int distance);


  public abstract float tf(float freq);


  public IDFExplanation idfExplain(final Term term, final Searcher searcher, int docFreq) throws IOException {

    if (!hasIDFExplainWithDocFreqAPI) {
      // Fallback to slow impl
      return idfExplain(term, searcher);
    }
    final int df = docFreq;
    final int max = searcher.maxDoc();
    final float idf = idf(df, max);
    return new IDFExplanation() {
        @Override
        public String explain() {
          return "idf(docFreq=" + df +
          ", maxDocs=" + max + ")";
        }
        @Override
        public float getIdf() {
          return idf;
        }};
  }

  public IDFExplanation idfExplain(final Term term, final Searcher searcher) throws IOException {
    return idfExplain(term, searcher, searcher.docFreq(term));
  }

  public IDFExplanation idfExplain(Collection<Term> terms, Searcher searcher) throws IOException {
    final int max = searcher.maxDoc();
    float idf = 0.0f;
    final StringBuilder exp = new StringBuilder();
    for (final Term term : terms ) {
      final int df = searcher.docFreq(term);
      idf += idf(df, max);
      exp.append(" ");
      exp.append(term.text());
      exp.append("=");
      exp.append(df);
    }
    final float fIdf = idf;
    return new IDFExplanation() {
      @Override
      public float getIdf() {
        return fIdf;
      }
      @Override
      public String explain() {
        return exp.toString();
      }
    };
  }


  public abstract float idf(int docFreq, int numDocs);


  public abstract float coord(int overlap, int maxOverlap);

  public float scorePayload(int docId, String fieldName, int start, int end, byte [] payload, int offset, int length)
  {
    return 1;
  }

}
