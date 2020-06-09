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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BoostQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities.BM25Similarity;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.similarities.Similarity.SimScorer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

public final class FeatureField extends Field {

  private static final FieldType FIELD_TYPE = new FieldType();
  static {
    FIELD_TYPE.setTokenized(false);
    FIELD_TYPE.setOmitNorms(true);
    FIELD_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
  }

  private float featureValue;

  public FeatureField(String fieldName, String featureName, float featureValue) {
    super(fieldName, featureName, FIELD_TYPE);
    setFeatureValue(featureValue);
  }

  public void setFeatureValue(float featureValue) {
    if (Float.isFinite(featureValue) == false) {
      throw new IllegalArgumentException("featureValue must be finite, got: " + featureValue +
          " for feature " + fieldsData + " on field " + name);
    }
    if (featureValue < Float.MIN_NORMAL) {
      throw new IllegalArgumentException("featureValue must be a positive normal float, got: " +
          featureValue + "for feature " + fieldsData + " on field " + name +
          " which is less than the minimum positive normal float: " + Float.MIN_NORMAL);
    }
    this.featureValue = featureValue;
  }

  @Override
  public TokenStream tokenStream(Analyzer analyzer, TokenStream reuse) {
    FeatureTokenStream stream;
    if (reuse instanceof FeatureTokenStream) {
      stream = (FeatureTokenStream) reuse;
    } else {
      stream = new FeatureTokenStream();
    }

    int freqBits = Float.floatToIntBits(featureValue);
    stream.setValues((String) fieldsData, freqBits >>> 15);
    return stream;
  }

  private static final class FeatureTokenStream extends TokenStream {
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAttribute = addAttribute(TermFrequencyAttribute.class);
    private boolean used = true;
    private String value = null;
    private int freq = 0;

    private FeatureTokenStream() {
    }

    void setValues(String value, int freq) {
      this.value = value;
      this.freq = freq;
    }

    @Override
    public boolean incrementToken() {
      if (used) {
        return false;
      }
      clearAttributes();
      termAttribute.append(value);
      freqAttribute.setTermFrequency(freq);
      used = true;
      return true;
    }

    @Override
    public void reset() {
      used = false;
    }

    @Override
    public void close() {
      value = null;
    }
  }

  private static final int MAX_FREQ = Float.floatToIntBits(Float.MAX_VALUE) >>> 15;

  private static float decodeFeatureValue(float freq) {
    if (freq > MAX_FREQ) {
      // This is never used in practice but callers of the SimScorer API might
      // occasionally call it on eg. Float.MAX_VALUE to compute the max score
      // so we need to be consistent.
      return Float.MAX_VALUE;
    }
    int tf = (int) freq; // lossless
    int featureBits = tf << 15;
    return Float.intBitsToFloat(featureBits);
  }

  static abstract class FeatureFunction {
    abstract SimScorer scorer(String field, float w);
    abstract Explanation explain(String field, String feature, float w, int doc, int freq) throws IOException;
    FeatureFunction rewrite(IndexReader reader) throws IOException { return this; }
  }

  static final class LogFunction extends FeatureFunction {

    private final float scalingFactor;

    LogFunction(float a) {
      this.scalingFactor = a;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      LogFunction that = (LogFunction) obj;
      return scalingFactor == that.scalingFactor;
    }

    @Override
    public int hashCode() {
      return Float.hashCode(scalingFactor);
    }

    @Override
    public String toString() {
      return "LogFunction(scalingFactor=" + scalingFactor + ")";
    }

    @Override
    SimScorer scorer(String field, float weight) {
      return new SimScorer() {
        @Override
        public float score(int doc, float freq) {
          return (float) (weight * Math.log(scalingFactor + decodeFeatureValue(freq)));
        }

        @Override
        public float computeSlopFactor(int distance) {
          throw new UnsupportedOperationException();
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    Explanation explain(String field, String feature, float w, int doc, int freq) throws IOException {
      float featureValue = decodeFeatureValue(freq);
      float score = scorer(field, w).score(doc, freq);
      return Explanation.match(score,
          "Log function on the " + field + " field for the " + feature + " feature, computed as w * log(a + S) from:",
          Explanation.match(w, "w, weight of this function"),
          Explanation.match(scalingFactor, "a, scaling factor"),
          Explanation.match(featureValue, "S, feature value"));
    }
  }

  static final class SaturationFunction extends FeatureFunction {

    private final String field, feature;
    private final Float pivot;

    SaturationFunction(String field, String feature, Float pivot) {
      this.field = field;
      this.feature = feature;
      this.pivot = pivot;
    }

    @Override
    public FeatureFunction rewrite(IndexReader reader) throws IOException {
      if (pivot != null) {
        return super.rewrite(reader);
      }
      float newPivot = computePivotFeatureValue(reader, field, feature);
      return new SaturationFunction(field, feature, newPivot);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SaturationFunction that = (SaturationFunction) obj;
      return Objects.equals(field, that.field) &&
          Objects.equals(feature, that.feature) &&
          Objects.equals(pivot, that.pivot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, feature, pivot);
    }

    @Override
    public String toString() {
      return "SaturationFunction(pivot=" + pivot + ")";
    }

    @Override
    SimScorer scorer(String field, float weight) {
      if (pivot == null) {
        throw new IllegalStateException("Rewrite first");
      }
      final float pivot = this.pivot; // unbox
      return new SimScorer() {
        @Override
        public float score(int doc, float freq) {
          float f = decodeFeatureValue(freq);
          // should be f / (f + k) but we rewrite it to
          // 1 - k / (f + k) to make sure it doesn't decrease
          // with f in spite of rounding
          return weight * (1 - pivot / (f + pivot));
        }

        @Override
        public float computeSlopFactor(int distance) {
          throw new UnsupportedOperationException();
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    Explanation explain(String field, String feature, float weight, int doc, int freq) throws IOException {
      float featureValue = decodeFeatureValue(freq);
      float score = scorer(field, weight).score(doc, freq);
      return Explanation.match(score,
          "Saturation function on the " + field + " field for the " + feature + " feature, computed as w * S / (S + k) from:",
          Explanation.match(weight, "w, weight of this function"),
          Explanation.match(pivot, "k, pivot feature value that would give a score contribution equal to w/2"),
          Explanation.match(featureValue, "S, feature value"));
    }
  }

  static final class SigmoidFunction extends FeatureFunction {

    private final float pivot, a;
    private final double pivotPa;

    SigmoidFunction(float pivot, float a) {
      this.pivot = pivot;
      this.a = a;
      this.pivotPa = Math.pow(pivot, a);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      SigmoidFunction that = (SigmoidFunction) obj;
      return pivot == that.pivot
          && a == that.a;
    }

    @Override
    public int hashCode() {
      int h = Float.hashCode(pivot);
      h = 31 * h + Float.hashCode(a);
      return h;
    }

    @Override
    public String toString() {
      return "SigmoidFunction(pivot=" + pivot + ", a=" + a + ")";
    }

    @Override
    SimScorer scorer(String field, float weight) {
      return new SimScorer() {
        @Override
        public float score(int doc, float freq) {
          float f = decodeFeatureValue(freq);
          // should be f^a / (f^a + k^a) but we rewrite it to
          // 1 - k^a / (f + k^a) to make sure it doesn't decrease
          // with f in spite of rounding
          return (float) (weight * (1 - pivotPa / (Math.pow(f, a) + pivotPa)));
        }

        @Override
        public float computeSlopFactor(int distance) {
          throw new UnsupportedOperationException();
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    Explanation explain(String field, String feature, float weight, int doc, int freq) throws IOException {
      float featureValue = decodeFeatureValue(freq);
      float score = scorer(field, weight).score(doc, freq);
      return Explanation.match(score,
          "Sigmoid function on the " + field + " field for the " + feature + " feature, computed as w * S^a / (S^a + k^a) from:",
          Explanation.match(weight, "w, weight of this function"),
          Explanation.match(pivot, "k, pivot feature value that would give a score contribution equal to w/2"),
          Explanation.match(pivot, "a, exponent, higher values make the function grow slower before k and faster after k"),
          Explanation.match(featureValue, "S, feature value"));
    }
  }

  private static final float MAX_WEIGHT = Long.SIZE;

  public static Query newLogQuery(String fieldName, String featureName, float weight, float scalingFactor) {
    if (weight <= 0 || weight > MAX_WEIGHT) {
      throw new IllegalArgumentException("weight must be in (0, " + MAX_WEIGHT + "], got: " + weight);
    }
    if (scalingFactor < 1 || Float.isFinite(scalingFactor) == false) {
      throw new IllegalArgumentException("scalingFactor must be >= 1, got: " + scalingFactor);
    }
    Query q = new FeatureQuery(fieldName, featureName, new LogFunction(scalingFactor));
    if (weight != 1f) {
      q = new BoostQuery(q, weight);
    }
    return q;
  }

  public static Query newSaturationQuery(String fieldName, String featureName, float weight, float pivot) {
    return newSaturationQuery(fieldName, featureName, weight, Float.valueOf(pivot));
  }

  public static Query newSaturationQuery(String fieldName, String featureName) {
    return newSaturationQuery(fieldName, featureName, 1f, null);
  }

  private static Query newSaturationQuery(String fieldName, String featureName, float weight, Float pivot) {
    if (weight <= 0 || weight > MAX_WEIGHT) {
      throw new IllegalArgumentException("weight must be in (0, " + MAX_WEIGHT + "], got: " + weight);
    }
    if (pivot != null && (pivot <= 0 || Float.isFinite(pivot) == false)) {
      throw new IllegalArgumentException("pivot must be > 0, got: " + pivot);
    }
    Query q = new FeatureQuery(fieldName, featureName, new SaturationFunction(fieldName, featureName, pivot));
    if (weight != 1f) {
      q = new BoostQuery(q, weight);
    }
    return q;
  }

  public static Query newSigmoidQuery(String fieldName, String featureName, float weight, float pivot, float exp) {
    if (weight <= 0 || weight > MAX_WEIGHT) {
      throw new IllegalArgumentException("weight must be in (0, " + MAX_WEIGHT + "], got: " + weight);
    }
    if (pivot <= 0 || Float.isFinite(pivot) == false) {
      throw new IllegalArgumentException("pivot must be > 0, got: " + pivot);
    }
    if (exp <= 0 || Float.isFinite(exp) == false) {
      throw new IllegalArgumentException("exp must be > 0, got: " + exp);
    }
    Query q = new FeatureQuery(fieldName, featureName, new SigmoidFunction(pivot, exp));
    if (weight != 1f) {
      q = new BoostQuery(q, weight);
    }
    return q;
  }

  static float computePivotFeatureValue(IndexReader reader, String featureField, String featureName) throws IOException {
    Term term = new Term(featureField, featureName);
    TermContext context = TermContext.build(reader.getContext(), term);
    if (context.docFreq() == 0) {
      // avoid division by 0
      // The return value doesn't matter much here, the term doesn't exist,
      // it will never be used for scoring. Just Make sure to return a legal
      // value.
      return 1;
    }
    float avgFreq = (float) ((double) context.totalTermFreq() / context.docFreq());
    return decodeFeatureValue(avgFreq);
  }
}
