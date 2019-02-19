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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search.similarities;


import java.util.List;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Explanation;

public abstract class Axiomatic extends SimilarityBase {
  protected final float s;

  protected final float k;

  protected final int queryLen;

  public Axiomatic(float s, int queryLen, float k) {
    if (Float.isFinite(s) == false || Float.isNaN(s) || s < 0 || s > 1) {
      throw new IllegalArgumentException("illegal s value: " + s + ", must be between 0 and 1");
    }
    if (Float.isFinite(k) == false || Float.isNaN(k) || k < 0 || k > 1) {
      throw new IllegalArgumentException("illegal k value: " + k + ", must be between 0 and 1");
    }
    if (queryLen < 0 || queryLen > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("illegal query length value: "
          + queryLen + ", must be larger 0 and smaller than MAX_INT");
    }
    this.s = s;
    this.queryLen = queryLen;
    this.k = k;
  }

  public Axiomatic(float s) {
    this(s, 1, 0.35f);
  }

  public Axiomatic(float s, int queryLen) {
    this(s, queryLen, 0.35f);
  }

  public Axiomatic() {
    this(0.25f, 1, 0.35f);
  }

  @Override
  public float score(BasicStats stats, float freq, float docLen) {
    return tf(stats, freq, docLen)
        * ln(stats, freq, docLen)
        * tfln(stats, freq, docLen)
        * idf(stats, freq, docLen)
        - gamma(stats, freq, docLen);
  }

  @Override
  protected void explain(List<Explanation> subs, BasicStats stats, int doc,
                         float freq, float docLen) {
    if (stats.getBoost() != 1.0f) {
      subs.add(Explanation.match(stats.getBoost(), "boost"));
    }

    subs.add(Explanation.match(this.k, "k"));
    subs.add(Explanation.match(this.s, "s"));
    subs.add(Explanation.match(this.queryLen, "queryLen"));
    subs.add(Explanation.match(tf(stats, freq, docLen), "tf"));
    subs.add(Explanation.match(ln(stats, freq, docLen), "ln"));
    subs.add(Explanation.match(tfln(stats, freq, docLen), "tfln"));
    subs.add(Explanation.match(idf(stats, freq, docLen), "idf"));
    subs.add(Explanation.match(gamma(stats, freq, docLen), "gamma"));
    super.explain(subs, stats, doc, freq, docLen);
  }

  @Override
  public abstract String toString();

  protected abstract float tf(BasicStats stats, float freq, float docLen);

  protected abstract float ln(BasicStats stats, float freq, float docLen);

  protected abstract float tfln(BasicStats stats, float freq, float docLen);

  protected abstract float idf(BasicStats stats, float freq, float docLen);

  protected abstract float gamma(BasicStats stats, float freq, float docLen);
}