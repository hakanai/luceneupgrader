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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

final class DisjunctionSumScorer extends DisjunctionScorer {
  private double score;
  private final float[] coord;
  

  DisjunctionSumScorer(Weight weight, Scorer[] subScorers, float[] coord) {
    super(weight, subScorers);
    this.coord = coord;
  }
  
  @Override
  protected void reset() {
    score = 0;
  }
  
  @Override
  protected void accum(Scorer subScorer) throws IOException {
    score += subScorer.score();
  }
  
  @Override
  protected float getFinal() {
    return (float)score * coord[freq]; 
  }
}
