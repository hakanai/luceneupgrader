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
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.PhraseQuery;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;


public abstract class TFIDFSimilarity extends Similarity {
  
  public TFIDFSimilarity() {}
  

  @Override
  public abstract float coord(int overlap, int maxOverlap);
  

  @Override
  public abstract float queryNorm(float sumOfSquaredWeights);
  

  public abstract float tf(float freq);

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long max = collectionStats.maxDoc();
    final float idf = idf(df, max);
    return Explanation.match(idf, "idf(docFreq=" + df + ", maxDocs=" + max + ")");
  }

  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    final long max = collectionStats.maxDoc();
    float idf = 0.0f;
    List<Explanation> subs = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      final long df = stat.docFreq();
      final float termIdf = idf(df, max);
      subs.add(Explanation.match(termIdf, "idf(docFreq=" + df + ", maxDocs=" + max + ")"));
      idf += termIdf;
    }
    return Explanation.match(idf, "idf(), sum of:", subs);
  }


  public abstract float idf(long docFreq, long numDocs);

  public abstract float lengthNorm(FieldInvertState state);
  
  @Override
  public final long computeNorm(FieldInvertState state) {
    float normValue = lengthNorm(state);
    return encodeNormValue(normValue);
  }
  
  public abstract float decodeNormValue(long norm);

  public abstract long encodeNormValue(float f);
 

  public abstract float sloppyFreq(int distance);

  public abstract float scorePayload(int doc, int start, int end, BytesRef payload);

  @Override
  public final SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
    final Explanation idf = termStats.length == 1
    ? idfExplain(collectionStats, termStats[0])
    : idfExplain(collectionStats, termStats);
    return new IDFStats(collectionStats.field(), idf);
  }

  @Override
  public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    IDFStats idfstats = (IDFStats) stats;
    return new TFIDFSimScorer(idfstats, context.reader().getNormValues(idfstats.field));
  }
  
  private final class TFIDFSimScorer extends SimScorer {
    private final IDFStats stats;
    private final float weightValue;
    private final NumericDocValues norms;
    
    TFIDFSimScorer(IDFStats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.value;
      this.norms = norms;
    }
    
    @Override
    public float score(int doc, float freq) {
      final float raw = tf(freq) * weightValue; // compute tf(f)*weight
      
      return norms == null ? raw : raw * decodeNormValue(norms.get(doc));  // normalize for field
    }
    
    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }

    @Override
    public Explanation explain(int doc, Explanation freq) {
      return explainScore(doc, freq, stats, norms);
    }
  }
  
  private static class IDFStats extends SimWeight {
    private final String field;
    private final Explanation idf;
    private float queryNorm;
    private float boost;
    private float queryWeight;
    private float value;
    
    public IDFStats(String field, Explanation idf) {
      // TODO: Validate?
      this.field = field;
      this.idf = idf;
      normalize(1f, 1f);
    }

    @Override
    public float getValueForNormalization() {
      // TODO: (sorta LUCENE-1907) make non-static class and expose this squaring via a nice method to subclasses?
      return queryWeight * queryWeight;  // sum of squared weights
    }

    @Override
    public void normalize(float queryNorm, float boost) {
      this.boost = boost;
      this.queryNorm = queryNorm;
      queryWeight = queryNorm * boost * idf.getValue();
      value = queryWeight * idf.getValue();         // idf for document
    }
  }  

  private Explanation explainQuery(IDFStats stats) {
    List<Explanation> subs = new ArrayList<>();

    Explanation boostExpl = Explanation.match(stats.boost, "boost");
    if (stats.boost != 1.0f)
      subs.add(boostExpl);
    subs.add(stats.idf);

    Explanation queryNormExpl = Explanation.match(stats.queryNorm,"queryNorm");
    subs.add(queryNormExpl);

    return Explanation.match(
        boostExpl.getValue() * stats.idf.getValue() * queryNormExpl.getValue(),
        "queryWeight, product of:", subs);
  }

  private Explanation explainField(int doc, Explanation freq, IDFStats stats, NumericDocValues norms) {
    Explanation tfExplanation = Explanation.match(tf(freq.getValue()), "tf(freq="+freq.getValue()+"), with freq of:", freq);
    Explanation fieldNormExpl = Explanation.match(
        norms != null ? decodeNormValue(norms.get(doc)) : 1.0f,
        "fieldNorm(doc=" + doc + ")");

    return Explanation.match(
        tfExplanation.getValue() * stats.idf.getValue() * fieldNormExpl.getValue(),
        "fieldWeight in " + doc + ", product of:",
        tfExplanation, stats.idf, fieldNormExpl);
  }

  private Explanation explainScore(int doc, Explanation freq, IDFStats stats, NumericDocValues norms) {
    Explanation queryExpl = explainQuery(stats);
    Explanation fieldExpl = explainField(doc, freq, stats, norms);
    if (queryExpl.getValue() == 1f) {
      return fieldExpl;
    }
    return Explanation.match(
        queryExpl.getValue() * fieldExpl.getValue(),
        "score(doc="+doc+",freq="+freq.getValue()+"), product of:",
        queryExpl, fieldExpl);
  }
}
