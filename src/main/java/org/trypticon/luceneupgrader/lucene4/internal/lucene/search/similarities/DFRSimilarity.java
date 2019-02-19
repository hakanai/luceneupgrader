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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.AfterEffect.NoAfterEffect;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Normalization.NoNormalization;

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
  protected float score(BasicStats stats, float freq, float docLen) {
    float tfn = normalization.tfn(stats, freq, docLen);
    return stats.getTotalBoost() *
        basicModel.score(stats, tfn) * afterEffect.score(stats, tfn);
  }
  
  @Override
  protected void explain(Explanation expl,
      BasicStats stats, int doc, float freq, float docLen) {
    if (stats.getTotalBoost() != 1.0f) {
      expl.addDetail(new Explanation(stats.getTotalBoost(), "boost"));
    }
    
    Explanation normExpl = normalization.explain(stats, freq, docLen);
    float tfn = normExpl.getValue();
    expl.addDetail(normExpl);
    expl.addDetail(basicModel.explain(stats, tfn));
    expl.addDetail(afterEffect.explain(stats, tfn));
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
