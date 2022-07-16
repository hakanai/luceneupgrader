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
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.SmallFloat;

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
  public final SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    SimScorer weights[] = new SimScorer[termStats.length];
    for (int i = 0; i < termStats.length; i++) {
      BasicStats stats = newStats(collectionStats.field(), boost);
      fillBasicStats(stats, collectionStats, termStats[i]);
      weights[i] = new BasicSimScorer(stats);
    }
    if (weights.length == 1) {
      return weights[0];
    } else {
      return new MultiSimilarity.MultiSimScorer(weights);
    }
  }
  
  protected BasicStats newStats(String field, double boost) {
    return new BasicStats(field, boost);
  }
  
  protected void fillBasicStats(BasicStats stats, CollectionStatistics collectionStats, TermStatistics termStats) {
    // TODO: validate this for real, somewhere else
    assert termStats.totalTermFreq() <= collectionStats.sumTotalTermFreq();
    assert termStats.docFreq() <= collectionStats.sumDocFreq();
 
    // TODO: add sumDocFreq for field (numberOfFieldPostings)
    stats.setNumberOfDocuments(collectionStats.docCount());
    stats.setNumberOfFieldTokens(collectionStats.sumTotalTermFreq());
    stats.setAvgFieldLength(collectionStats.sumTotalTermFreq() / (double) collectionStats.docCount());
    stats.setDocFreq(termStats.docFreq());
    stats.setTotalTermFreq(termStats.totalTermFreq());
  }
  
  protected abstract double score(BasicStats stats, double freq, double docLen);

  protected void explain(
      List<Explanation> subExpls, BasicStats stats, double freq, double docLen) {}
  
  protected Explanation explain(
      BasicStats stats, Explanation freq, double docLen) {
    List<Explanation> subs = new ArrayList<>();
    explain(subs, stats, freq.getValue().floatValue(), docLen);
    
    return Explanation.match(
        (float) score(stats, freq.getValue().floatValue(), docLen),
        "score(" + getClass().getSimpleName() + ", freq=" + freq.getValue() +"), computed from:",
        subs);
  }
  
  @Override
  public abstract String toString();

  // ------------------------------ Norm handling ------------------------------
  
  private static final float[] LENGTH_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      LENGTH_TABLE[i] = SmallFloat.byte4ToInt((byte) i);
    }
  }

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

  // ----------------------------- Static methods ------------------------------
  
  public static double log2(double x) {
    // Put this to a 'util' class if we need more of these.
    return Math.log(x) / LOG_2;
  }
  
  // --------------------------------- Classes ---------------------------------
  
  final class BasicSimScorer extends SimScorer {
    final BasicStats stats;
    
    BasicSimScorer(BasicStats stats) {
      this.stats = stats;
    }

    double getLengthValue(long norm) {
      return LENGTH_TABLE[Byte.toUnsignedInt((byte) norm)];
    }
    
    @Override
    public float score(float freq, long norm) {
      return (float) SimilarityBase.this.score(stats, freq, getLengthValue(norm));
    }

    @Override
    public Explanation explain(Explanation freq, long norm) {
      return SimilarityBase.this.explain(stats, freq, getLengthValue(norm));
    }

  }
}
