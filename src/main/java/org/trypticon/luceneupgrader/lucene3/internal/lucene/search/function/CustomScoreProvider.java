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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache; // for javadocs

public class CustomScoreProvider {

  protected final IndexReader reader;

  public CustomScoreProvider(IndexReader reader) {
    this.reader = reader;
  }

  public float customScore(int doc, float subQueryScore, float valSrcScores[]) throws IOException {
    if (valSrcScores.length == 1) {
      return customScore(doc, subQueryScore, valSrcScores[0]);
    }
    if (valSrcScores.length == 0) {
      return customScore(doc, subQueryScore, 1);
    }
    float score = subQueryScore;
    for(int i = 0; i < valSrcScores.length; i++) {
      score *= valSrcScores[i];
    }
    return score;
  }

  public float customScore(int doc, float subQueryScore, float valSrcScore) throws IOException {
    return subQueryScore * valSrcScore;
  }

  public Explanation customExplain(int doc, Explanation subQueryExpl, Explanation valSrcExpls[]) throws IOException {
    if (valSrcExpls.length == 1) {
      return customExplain(doc, subQueryExpl, valSrcExpls[0]);
    }
    if (valSrcExpls.length == 0) {
      return subQueryExpl;
    }
    float valSrcScore = 1;
    for (int i = 0; i < valSrcExpls.length; i++) {
      valSrcScore *= valSrcExpls[i].getValue();
    }
    Explanation exp = new Explanation( valSrcScore * subQueryExpl.getValue(), "custom score: product of:");
    exp.addDetail(subQueryExpl);
    for (int i = 0; i < valSrcExpls.length; i++) {
      exp.addDetail(valSrcExpls[i]);
    }
    return exp;
  }
  
  public Explanation customExplain(int doc, Explanation subQueryExpl, Explanation valSrcExpl) throws IOException {
    float valSrcScore = 1;
    if (valSrcExpl != null) {
      valSrcScore *= valSrcExpl.getValue();
    }
    Explanation exp = new Explanation( valSrcScore * subQueryExpl.getValue(), "custom score: product of:");
    exp.addDetail(subQueryExpl);
    exp.addDetail(valSrcExpl);
    return exp;
  }

}
