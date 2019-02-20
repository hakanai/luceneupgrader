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


import java.io.IOException;

public abstract class FilterScorer extends Scorer {
  protected final Scorer in;

  public FilterScorer(Scorer in) {
    super(in.weight);
    this.in = in;
  }

  public FilterScorer(Scorer in, Weight weight) {
    super(weight);
    if (in == null) {
      throw new NullPointerException("wrapped Scorer must not be null");
    }
    this.in = in;
  }
  
  @Override
  public float score() throws IOException {
    return in.score();
  }

  @Override
  public int freq() throws IOException {
    return in.freq();
  }

  @Override
  public final int docID() {
    return in.docID();
  }

  @Override
  public final DocIdSetIterator iterator() {
    return in.iterator();
  }
  
  @Override
  public final TwoPhaseIterator twoPhaseIterator() {
    return in.twoPhaseIterator();
  }
}
