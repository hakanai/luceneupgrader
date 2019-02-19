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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.SmallFloat;

public abstract class SimilarityBase extends Similarity {
  private static final double LOG_2 = Math.log(2);
  

  protected boolean discountOverlaps = true;
  
  public SimilarityBase() {}
  

  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }
  
  @Override
  public final SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
    BasicStats stats[] = new BasicStats[termStats.length];
    for (int i = 0; i < termStats.length; i++) {
      stats[i] = newStats(collectionStats.field());
      fillBasicStats(stats[i], collectionStats, termStats[i]);
    }
    return stats.length == 1 ? stats[0] : new MultiSimilarity.MultiStats(stats);
  }
  
  protected BasicStats newStats(String field) {
    return new BasicStats(field);
  }
  
  protected void fillBasicStats(BasicStats stats, CollectionStatistics collectionStats, TermStatistics termStats) {
    // #positions(field) must be >= #positions(term)
    assert collectionStats.sumTotalTermFreq() == -1 || collectionStats.sumTotalTermFreq() >= termStats.totalTermFreq();
    long numberOfDocuments = collectionStats.maxDoc();
    
    long docFreq = termStats.docFreq();
    long totalTermFreq = termStats.totalTermFreq();

    // codec does not supply totalTermFreq: substitute docFreq
    if (totalTermFreq == -1) {
      totalTermFreq = docFreq;
    }

    final long numberOfFieldTokens;
    final float avgFieldLength;

    long sumTotalTermFreq = collectionStats.sumTotalTermFreq();

    if (sumTotalTermFreq <= 0) {
      // field does not exist;
      // We have to provide something if codec doesnt supply these measures,
      // or if someone omitted frequencies for the field... negative values cause
      // NaN/Inf for some scorers.
      numberOfFieldTokens = docFreq;
      avgFieldLength = 1;
    } else {
      numberOfFieldTokens = sumTotalTermFreq;
      avgFieldLength = (float)numberOfFieldTokens / numberOfDocuments;
    }
 
    // TODO: add sumDocFreq for field (numberOfFieldPostings)
    stats.setNumberOfDocuments(numberOfDocuments);
    stats.setNumberOfFieldTokens(numberOfFieldTokens);
    stats.setAvgFieldLength(avgFieldLength);
    stats.setDocFreq(docFreq);
    stats.setTotalTermFreq(totalTermFreq);
  }
  
  protected abstract float score(BasicStats stats, float freq, float docLen);
  
  protected void explain(
      List<Explanation> subExpls, BasicStats stats, int doc, float freq, float docLen) {}
  
  protected Explanation explain(
      BasicStats stats, int doc, Explanation freq, float docLen) {
    List<Explanation> subs = new ArrayList<>();
    explain(subs, stats, doc, freq.getValue(), docLen);
    
    return Explanation.match(
        score(stats, freq.getValue(), docLen),
        "score(" + getClass().getSimpleName() + ", doc=" + doc + ", freq=" + freq.getValue() +"), computed from:",
        subs);
  }
  
  @Override
  public SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    if (stats instanceof MultiSimilarity.MultiStats) {
      // a multi term query (e.g. phrase). return the summation, 
      // scoring almost as if it were boolean query
      SimWeight subStats[] = ((MultiSimilarity.MultiStats) stats).subStats;
      SimScorer subScorers[] = new SimScorer[subStats.length];
      for (int i = 0; i < subScorers.length; i++) {
        BasicStats basicstats = (BasicStats) subStats[i];
        subScorers[i] = new BasicSimScorer(basicstats, context.reader().getNormValues(basicstats.field));
      }
      return new MultiSimilarity.MultiSimScorer(subScorers);
    } else {
      BasicStats basicstats = (BasicStats) stats;
      return new BasicSimScorer(basicstats, context.reader().getNormValues(basicstats.field));
    }
  }
  
  @Override
  public abstract String toString();

  // ------------------------------ Norm handling ------------------------------
  
  private static final float[] NORM_TABLE = new float[256];

  static {
    for (int i = 1; i < 256; i++) {
      float floatNorm = SmallFloat.byte315ToFloat((byte)i);
      NORM_TABLE[i] = 1.0f / (floatNorm * floatNorm);
    }
    NORM_TABLE[0] = 1.0f / NORM_TABLE[255]; // otherwise inf
  }

  @Override
  public long computeNorm(FieldInvertState state) {
    final float numTerms;
    if (discountOverlaps)
      numTerms = state.getLength() - state.getNumOverlap();
    else
      numTerms = state.getLength();
    return encodeNormValue(state.getBoost(), numTerms);
  }
  

  protected float decodeNormValue(byte norm) {
    return NORM_TABLE[norm & 0xFF];  // & 0xFF maps negative bytes to positive above 127
  }
  
  protected byte encodeNormValue(float boost, float length) {
    return SmallFloat.floatToByte315((boost / (float) Math.sqrt(length)));
  }
  
  // ----------------------------- Static methods ------------------------------
  
  public static double log2(double x) {
    // Put this to a 'util' class if we need more of these.
    return Math.log(x) / LOG_2;
  }
  
  // --------------------------------- Classes ---------------------------------
  

  private class BasicSimScorer extends SimScorer {
    private final BasicStats stats;
    private final NumericDocValues norms;
    
    BasicSimScorer(BasicStats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.norms = norms;
    }
    
    @Override
    public float score(int doc, float freq) {
      // We have to supply something in case norms are omitted
      return SimilarityBase.this.score(stats, freq,
          norms == null ? 1F : decodeNormValue((byte)norms.get(doc)));
    }
    @Override
    public Explanation explain(int doc, Explanation freq) {
      return SimilarityBase.this.explain(stats, doc, freq,
          norms == null ? 1F : decodeNormValue((byte)norms.get(doc)));
    }

    @Override
    public float computeSlopFactor(int distance) {
      return 1.0f / (distance + 1);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return 1f;
    }
  }
}
