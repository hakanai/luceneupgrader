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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities;


import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.SmallFloat;


public abstract class TFIDFSimilarity extends Similarity {

  public TFIDFSimilarity() {}

  protected boolean discountOverlaps = true;

  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  public abstract float tf(float freq);

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf(docFreq, docCount)", 
        Explanation.match(df, "docFreq, number of documents containing term"),
        Explanation.match(docCount, "docCount, total number of documents with field"));
  }

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    double idf = 0d; // sum into a double before casting into a float
    List<Explanation> subs = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      subs.add(idfExplain);
      idf += idfExplain.getValue().floatValue();
    }
    return Explanation.match((float) idf, "idf(), sum of:", subs);
  }

  public abstract float idf(long docFreq, long docCount);

  public abstract float lengthNorm(int length);
  
  @Override
  public final long computeNorm(FieldInvertState state) {
    final int numTerms;
    if (state.getIndexOptions() == IndexOptions.DOCS && state.getIndexCreatedVersionMajor() >= 8) {
      numTerms = state.getUniqueTermCount();
    } else if (discountOverlaps) {
      numTerms = state.getLength() - state.getNumOverlap();
    } else {
      numTerms = state.getLength();
    }
    return SmallFloat.intToByte4(numTerms);
  }

  @Override
  public final SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    final Explanation idf = termStats.length == 1
    ? idfExplain(collectionStats, termStats[0])
    : idfExplain(collectionStats, termStats);
    float[] normTable = new float[256];
    for (int i = 1; i < 256; ++i) {
      int length = SmallFloat.byte4ToInt((byte) i);
      float norm = lengthNorm(length);
      normTable[i] = norm;
    }
    normTable[0] = 1f / normTable[255];
    return new TFIDFScorer(boost, idf, normTable);
  }

  
  class TFIDFScorer extends SimScorer {
    private final Explanation idf;
    private final float boost;
    private final float queryWeight;
    final float[] normTable;
    
    public TFIDFScorer(float boost, Explanation idf, float[] normTable) {
      // TODO: Validate?
      this.idf = idf;
      this.boost = boost;
      this.queryWeight = boost * idf.getValue().floatValue();
      this.normTable = normTable;
    }

    @Override
    public float score(float freq, long norm) {
      final float raw = tf(freq) * queryWeight; // compute tf(f)*weight
      float normValue = normTable[(int) (norm & 0xFF)];
      return raw * normValue;  // normalize for field
    }

    @Override
    public Explanation explain(Explanation freq, long norm) {
      return explainScore(freq, norm, normTable);
    }

    private Explanation explainScore(Explanation freq, long encodedNorm, float[] normTable) {
      List<Explanation> subs = new ArrayList<Explanation>();
      if (boost != 1F) {
        subs.add(Explanation.match(boost, "boost"));
      }
      subs.add(idf);
      Explanation tf = Explanation.match(tf(freq.getValue().floatValue()), "tf(freq="+freq.getValue()+"), with freq of:", freq);
      subs.add(tf);

      float norm = normTable[(int) (encodedNorm & 0xFF)];
      
      Explanation fieldNorm = Explanation.match(norm, "fieldNorm");
      subs.add(fieldNorm);
      
      return Explanation.match(
          queryWeight * tf.getValue().floatValue() * norm,
          "score(freq="+freq.getValue()+"), product of:",
          subs);
    }
  }  

}
