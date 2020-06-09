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

public class AxiomaticF3LOG extends Axiomatic {

  public AxiomaticF3LOG(float s, int queryLen) {
    super(s, queryLen);
  }

  @Override
  public String toString() {
    return "F3LOG";
  }

  @Override
  protected float tf(BasicStats stats, float freq, float docLen) {
    if (freq <= 0.0) return 0f;
    return (float) (1 + Math.log(1 + Math.log(freq)));
  }

  @Override
  protected float ln(BasicStats stats, float freq, float docLen) {
    return 1f;
  }

  @Override
  protected float tfln(BasicStats stats, float freq, float docLen) {
    return 1f;
  }

  @Override
  protected float idf(BasicStats stats, float freq, float docLen) {
    return (float) Math.log((stats.getNumberOfDocuments() + 1.0) / stats.getDocFreq());
  }

  @Override
  protected float gamma(BasicStats stats, float freq, float docLen) {
    return (docLen - this.queryLen) * this.s * this.queryLen / stats.getAvgFieldLength();
  }
}