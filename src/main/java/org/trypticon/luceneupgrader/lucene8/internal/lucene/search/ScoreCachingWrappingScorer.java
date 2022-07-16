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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public final class ScoreCachingWrappingScorer extends Scorable {

  private int curDoc = -1;
  private float curScore;
  private final Scorable in;

  public static Scorable wrap(Scorable scorer) {
    if (scorer instanceof ScoreCachingWrappingScorer) {
      return scorer;
    }
    return new ScoreCachingWrappingScorer(scorer);
  }

  @Deprecated
  public ScoreCachingWrappingScorer(Scorable scorer) {
    this.in = scorer;
  }

  @Override
  public float score() throws IOException {
    int doc = in.docID();
    if (doc != curDoc) {
      curScore = in.score();
      curDoc = doc;
    }

    return curScore;
  }

  @Override
  public void setMinCompetitiveScore(float minScore) throws IOException {
    in.setMinCompetitiveScore(minScore);
  }

  @Override
  public int docID() {
    return in.docID();
  }

  @Override
  public Collection<ChildScorable> getChildren() {
    return Collections.singleton(new ChildScorable(in, "CACHED"));
  }
}
