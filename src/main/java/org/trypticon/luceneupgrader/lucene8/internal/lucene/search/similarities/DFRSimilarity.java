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

import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.Normalization.NoNormalization;

public class DFRSimilarity extends SimilarityBase {
  protected final BasicModel basicModel;
  protected final AfterEffect afterEffect;
  protected final Normalization normalization;
  
  public DFRSimilarity(BasicModel basicModel,
                       AfterEffect afterEffect,
                       Normalization normalization) {
    if (basicModel == null || afterEffect == null || normalization == null) {
      throw new NullPointerException("null parameters not allowed.");
    }
    this.basicModel = basicModel;
    this.afterEffect = afterEffect;
    this.normalization = normalization;
  }

  @Override
  protected double score(BasicStats stats, double freq, double docLen) {
    double tfn = normalization.tfn(stats, freq, docLen);
    double aeTimes1pTfn = afterEffect.scoreTimes1pTfn(stats);
    return stats.getBoost() * basicModel.score(stats, tfn, aeTimes1pTfn);
  }

  @Override
  protected void explain(List<Explanation> subs,
      BasicStats stats, double freq, double docLen) {
    if (stats.getBoost() != 1.0d) {
      subs.add(Explanation.match( (float)stats.getBoost(), "boost, query boost"));
    }
    
    Explanation normExpl = normalization.explain(stats, freq, docLen);
    double tfn = normalization.tfn(stats, freq, docLen);
    double aeTimes1pTfn = afterEffect.scoreTimes1pTfn(stats);
    subs.add(normExpl);
    subs.add(basicModel.explain(stats, tfn, aeTimes1pTfn));
    subs.add(afterEffect.explain(stats, tfn));
  }

  @Override
  protected Explanation explain(
      BasicStats stats, Explanation freq, double docLen) {
    List<Explanation> subs = new ArrayList<>();
    explain(subs, stats, freq.getValue().doubleValue(), docLen);

    return Explanation.match(
        (float) score(stats, freq.getValue().doubleValue(), docLen),
        "score(" + getClass().getSimpleName() + ", freq=" +
            freq.getValue() +"), computed as boost * " +
            "basicModel.score(stats, tfn) * afterEffect.score(stats, tfn) from:",
        subs);
  }

  @Override
  public String toString() {
    return "DFR " + basicModel.toString() + afterEffect.toString()
                  + normalization.toString();
  }
  
  public BasicModel getBasicModel() {
    return basicModel;
  }
  
  public AfterEffect getAfterEffect() {
    return afterEffect;
  }
  
  public Normalization getNormalization() {
    return normalization;
  }
}
