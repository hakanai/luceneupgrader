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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SmallFloat;

public class BM25Similarity extends Similarity {
  private final float k1;
  private final float b;

  public BM25Similarity(float k1, float b) {
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (Float.isNaN(b) || b < 0 || b > 1) {
      throw new IllegalArgumentException("illegal b value: " + b + ", must be between 0 and 1");
    }
    this.k1 = k1;
    this.b  = b;
  }
  
  public BM25Similarity() {
    this(1.2f, 0.75f);
  }
  
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D)/(docFreq + 0.5D));
  }
  
  protected float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }
  
  protected float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }
  
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    final long sumTotalTermFreq;
    if (collectionStats.sumTotalTermFreq() == -1) {
      // frequencies are omitted (tf=1), its # of postings
      if (collectionStats.sumDocFreq() == -1) {
        // theoretical case only: remove!
        return 1f;
      }
      sumTotalTermFreq = collectionStats.sumDocFreq();
    } else {
      sumTotalTermFreq = collectionStats.sumTotalTermFreq();
    }
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    return (float) (sumTotalTermFreq / (double) docCount);
  }
  
  protected boolean discountOverlaps = true;

  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }
  
  private static final float[] OLD_LENGTH_TABLE = new float[256];
  private static final float[] LENGTH_TABLE = new float[256];

  static {
    for (int i = 1; i < 256; i++) {
      float f = SmallFloat.byte315ToFloat((byte)i);
      OLD_LENGTH_TABLE[i] = 1.0f / (f*f);
    }
    OLD_LENGTH_TABLE[0] = 1.0f / OLD_LENGTH_TABLE[255]; // otherwise inf

    for (int i = 0; i < 256; i++) {
      LENGTH_TABLE[i] = SmallFloat.byte4ToInt((byte) i);
    }
  }


  @Override
  public final long computeNorm(FieldInvertState state) {
    final int numTerms = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
    int indexCreatedVersionMajor = state.getIndexCreatedVersionMajor();
    if (indexCreatedVersionMajor >= 7) {
      return SmallFloat.intToByte4(numTerms);
    } else {
      return SmallFloat.floatToByte315((float) (1 / Math.sqrt(numTerms)));
    }
  }

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf, computed as log(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5)) from:",
        Explanation.match(df, "docFreq"),
        Explanation.match(docCount, "docCount"));
  }

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    double idf = 0d; // sum into a double before casting into a float
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      details.add(idfExplain);
      idf += idfExplain.getValue();
    }
    return Explanation.match((float) idf, "idf(), sum of:", details);
  }

  @Override
  public final SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);
    float avgdl = avgFieldLength(collectionStats);

    float[] oldCache = new float[256];
    float[] cache = new float[256];
    for (int i = 0; i < cache.length; i++) {
      oldCache[i] = k1 * ((1 - b) + b * OLD_LENGTH_TABLE[i] / avgdl);
      cache[i] = k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl);
    }
    return new BM25Stats(collectionStats.field(), boost, idf, avgdl, oldCache, cache);
  }

  @Override
  public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    BM25Stats bm25stats = (BM25Stats) stats;
    return new BM25DocScorer(bm25stats, context.reader().getMetaData().getCreatedVersionMajor(), context.reader().getNormValues(bm25stats.field));
  }
  
  private class BM25DocScorer extends SimScorer {
    private final BM25Stats stats;
    private final float weightValue; // boost * idf * (k1 + 1)
    private final NumericDocValues norms;
    private final float[] lengthCache;
    private final float[] cache;
    
    BM25DocScorer(BM25Stats stats, int indexCreatedVersionMajor, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.weight * (k1 + 1);
      this.norms = norms;
      if (indexCreatedVersionMajor >= 7) {
        lengthCache = LENGTH_TABLE;
        cache = stats.cache;
      } else {
        lengthCache = OLD_LENGTH_TABLE;
        cache = stats.oldCache;
      }
    }
    
    @Override
    public float score(int doc, float freq) throws IOException {
      // if there are no norms, we act as if b=0
      float norm;
      if (norms == null) {
        norm = k1;
      } else {
        if (norms.advanceExact(doc)) {
          norm = cache[((byte) norms.longValue()) & 0xFF];
        } else {
          norm = cache[0];
        }
      }
      return weightValue * freq / (freq + norm);
    }
    
    @Override
    public Explanation explain(int doc, Explanation freq) throws IOException {
      return explainScore(doc, freq, stats, norms, lengthCache);
    }

    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }
  }
  
  private static class BM25Stats extends SimWeight {
    private final Explanation idf;
    private final float avgdl;
    private final float boost;
    private final float weight;
    private final String field;
    private final float[] oldCache, cache;

    BM25Stats(String field, float boost, Explanation idf, float avgdl, float[] oldCache, float[] cache) {
      this.field = field;
      this.boost = boost;
      this.idf = idf;
      this.avgdl = avgdl;
      this.weight = idf.getValue() * boost;
      this.oldCache = oldCache;
      this.cache = cache;
    }

  }

  private Explanation explainTFNorm(int doc, Explanation freq, BM25Stats stats, NumericDocValues norms, float[] lengthCache) throws IOException {
    List<Explanation> subs = new ArrayList<>();
    subs.add(freq);
    subs.add(Explanation.match(k1, "parameter k1"));
    if (norms == null) {
      subs.add(Explanation.match(0, "parameter b (norms omitted for field)"));
      return Explanation.match(
          (freq.getValue() * (k1 + 1)) / (freq.getValue() + k1),
          "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1) from:", subs);
    } else {
      byte norm;
      if (norms.advanceExact(doc)) {
        norm = (byte) norms.longValue();
      } else {
        norm = 0;
      }
      float doclen = lengthCache[norm & 0xff];
      subs.add(Explanation.match(b, "parameter b"));
      subs.add(Explanation.match(stats.avgdl, "avgFieldLength"));
      subs.add(Explanation.match(doclen, "fieldLength"));
      return Explanation.match(
          (freq.getValue() * (k1 + 1)) / (freq.getValue() + k1 * (1 - b + b * doclen/stats.avgdl)),
          "tfNorm, computed as (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * fieldLength / avgFieldLength)) from:", subs);
    }
  }

  private Explanation explainScore(int doc, Explanation freq, BM25Stats stats, NumericDocValues norms, float[] lengthCache) throws IOException {
    Explanation boostExpl = Explanation.match(stats.boost, "boost");
    List<Explanation> subs = new ArrayList<>();
    if (boostExpl.getValue() != 1.0f)
      subs.add(boostExpl);
    subs.add(stats.idf);
    Explanation tfNormExpl = explainTFNorm(doc, freq, stats, norms, lengthCache);
    subs.add(tfNormExpl);
    return Explanation.match(
        boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue(),
        "score(doc="+doc+",freq="+freq+"), product of:", subs);
  }

  @Override
  public String toString() {
    return "BM25(k1=" + k1 + ",b=" + b + ")";
  }
  
  public final float getK1() {
    return k1;
  }
  
  public final float getB() {
    return b;
  }
}
