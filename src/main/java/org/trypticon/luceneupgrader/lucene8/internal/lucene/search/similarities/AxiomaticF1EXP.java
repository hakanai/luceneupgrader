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


import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;

public class AxiomaticF1EXP extends Axiomatic {
  public AxiomaticF1EXP(float s, float k) {
    super(s, 1, k);
  }

  public AxiomaticF1EXP(float s) {
    this(s, 0.35f);
  }

  public AxiomaticF1EXP() {
    super();
  }

  @Override
  public String toString() {
    return "F1EXP";
  }

  @Override
  protected double tf(BasicStats stats, double freq, double docLen) {
    freq += 1; // otherwise gives negative scores for freqs < 1
    return 1 + Math.log(1 + Math.log(freq));
  }

  @Override
  protected double ln(BasicStats stats, double freq, double docLen) {
    return (stats.getAvgFieldLength() + this.s) / (stats.getAvgFieldLength() + docLen * this.s);
  }

  @Override
  protected double tfln(BasicStats stats, double freq, double docLen) {
    return 1.0;
  }

  @Override
  protected double idf(BasicStats stats, double freq, double docLen) {
    return Math.pow((stats.getNumberOfDocuments() + 1.0) / stats.getDocFreq(), this.k);
  }

  @Override
  protected double gamma(BasicStats stats, double freq, double docLen) {
    return 0.0;
  }

  @Override
  protected Explanation tfExplain(BasicStats stats, double freq, double docLen){
    return Explanation.match((float) tf(stats, freq, docLen),
        "tf, term frequency computed as 1 + log(1 + log(freq)) from:",
        Explanation.match((float) freq,
            "freq, number of occurrences of term in the document"));
  };

  @Override
  protected Explanation lnExplain(BasicStats stats, double freq, double docLen){
    return Explanation.match((float) ln(stats, freq, docLen),
        "ln, document length computed as (avgdl + s) / (avgdl + dl * s) from:",
        Explanation.match((float) stats.getAvgFieldLength(),
            "avgdl, average length of field across all documents"),
        Explanation.match((float) docLen,
            "dl, length of field"));
  };

  protected Explanation tflnExplain(BasicStats stats, double freq, double docLen){
    return Explanation.match((float) tfln(stats, freq, docLen),
        "tfln, mixed term frequency and document length, equals to 1");
  };

  protected Explanation idfExplain(BasicStats stats, double freq, double docLen){
    return Explanation.match((float) idf(stats, freq, docLen),
        "idf, inverted document frequency computed as " +
            "Math.pow((N + 1) / n, k) from:",
        Explanation.match((float) stats.getNumberOfDocuments(),
            "N, total number of documents with field"),
        Explanation.match((float) stats.getDocFreq(),
            "n, number of documents containing term"));
  };
}