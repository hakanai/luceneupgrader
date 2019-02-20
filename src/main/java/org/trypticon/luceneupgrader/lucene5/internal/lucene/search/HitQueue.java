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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.PriorityQueue;

final class HitQueue extends PriorityQueue<ScoreDoc> {

  HitQueue(int size, boolean prePopulate) {
    super(size, prePopulate);
  }

  @Override
  protected ScoreDoc getSentinelObject() {
    // Always set the doc Id to MAX_VALUE so that it won't be favored by
    // lessThan. This generally should not happen since if score is not NEG_INF,
    // TopScoreDocCollector will always add the object to the queue.
    return new ScoreDoc(Integer.MAX_VALUE, Float.NEGATIVE_INFINITY);
  }
  
  @Override
  protected final boolean lessThan(ScoreDoc hitA, ScoreDoc hitB) {
    if (hitA.score == hitB.score)
      return hitA.doc > hitB.doc; 
    else
      return hitA.score < hitB.score;
  }
}
