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


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;

public class BasicStats extends Similarity.SimWeight {
  final String field;
  protected long numberOfDocuments;
  protected long numberOfFieldTokens;
  protected float avgFieldLength;
  protected long docFreq;
  protected long totalTermFreq;
  
  // -------------------------- Boost-related stuff --------------------------


  protected float boost;
  
  public BasicStats(String field) {
    this.field = field;
    normalize(1f, 1f);
  }
  
  // ------------------------- Getter/setter methods -------------------------
  
  public long getNumberOfDocuments() {
    return numberOfDocuments;
  }
  
  public void setNumberOfDocuments(long numberOfDocuments) {
    this.numberOfDocuments = numberOfDocuments;
  }
  
  public long getNumberOfFieldTokens() {
    return numberOfFieldTokens;
  }
  
  public void setNumberOfFieldTokens(long numberOfFieldTokens) {
    this.numberOfFieldTokens = numberOfFieldTokens;
  }
  
  public float getAvgFieldLength() {
    return avgFieldLength;
  }
  
  public void setAvgFieldLength(float avgFieldLength) {
    this.avgFieldLength = avgFieldLength;
  }
  
  public long getDocFreq() {
    return docFreq;
  }
  
  public void setDocFreq(long docFreq) {
    this.docFreq = docFreq;
  }
  
  public long getTotalTermFreq() {
    return totalTermFreq;
  }
  
  public void setTotalTermFreq(long totalTermFreq) {
    this.totalTermFreq = totalTermFreq;
  }
  
  // -------------------------- Boost-related stuff --------------------------
  
  @Override
  public float getValueForNormalization() {
    float rawValue = rawNormalizationValue();
    return rawValue * rawValue;
  }
  

  protected float rawNormalizationValue() {
    return boost;
  }
  
  @Override
  public void normalize(float queryNorm, float boost) {
    this.boost = boost;
  }
  
  public float getBoost() {
    return boost;
  }
}
