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


import java.util.List;
import java.util.Locale;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Explanation;

public class LMJelinekMercerSimilarity extends LMSimilarity {
  private final float lambda;
  
  public LMJelinekMercerSimilarity(
      CollectionModel collectionModel, float lambda) {
    super(collectionModel);
    this.lambda = lambda;
  }

  public LMJelinekMercerSimilarity(float lambda) {
    this.lambda = lambda;
  }
  
  @Override
  protected float score(BasicStats stats, float freq, float docLen) {
    return stats.getBoost() *
        (float)Math.log(1 +
            ((1 - lambda) * freq / docLen) /
            (lambda * ((LMStats)stats).getCollectionProbability()));
  }
  
  @Override
  protected void explain(List<Explanation> subs, BasicStats stats, int doc,
      float freq, float docLen) {
    if (stats.getBoost() != 1.0f) {
      subs.add(Explanation.match(stats.getBoost(), "boost"));
    }
    subs.add(Explanation.match(lambda, "lambda"));
    super.explain(subs, stats, doc, freq, docLen);
  }

  public float getLambda() {
    return lambda;
  }

  @Override
  public String getName() {
    return String.format(Locale.ROOT, "Jelinek-Mercer(%f)", getLambda());
  }
}
