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


import static org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.SimilarityBase.log2;

public class BasicModelBE extends BasicModel {
  
  public BasicModelBE() {}

  @Override
  public final float score(BasicStats stats, float tfn) {
    double F = stats.getTotalTermFreq() + 1 + tfn;
    // approximation only holds true when F << N, so we use N += F
    double N = F + stats.getNumberOfDocuments();
    return (float)(-log2((N - 1) * Math.E)
        + f(N + F - 1, N + F - tfn - 2) - f(F, F - tfn));
  }
  
  private final double f(double n, double m) {
    return (m + 0.5) * log2(n / m) + (n - m) * log2(n);
  }
  
  @Override
  public String toString() {
    return "Be";
  }
}
