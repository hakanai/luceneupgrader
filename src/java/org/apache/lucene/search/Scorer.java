package org.apache.lucene.search;

/**
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

import java.io.IOException;

/**
 * Expert: Common scoring functionality for different types of queries.
 *
 * <p>
 * A <code>Scorer</code> iterates over documents matching a
 * query in increasing order of doc Id.
 * </p>
 * <p>
 * Document scores are computed using a given <code>Similarity</code>
 * implementation.
 * </p>
 *
 * <p><b>NOTE</b>: The values Float.Nan,
 * Float.NEGATIVE_INFINITY and Float.POSITIVE_INFINITY are
 * not valid scores.  Certain collectors (eg {@code
 * TopScoreDocCollector}) will not properly collect hits
 * with these scores.
 */
public abstract class Scorer extends DocIdSetIterator {
  private final Similarity similarity;
  protected final Weight weight;

  /**
   * Constructs a Scorer
   * @param similarity The <code>Similarity</code> implementation used by this scorer.
   * @param weight The scorers <code>Weight</code>
   * @deprecated Use {@code #Scorer(Weight)} instead.
   */
  @Deprecated
  protected Scorer(Similarity similarity, Weight weight) {
    this.similarity = similarity;
    this.weight = weight;
  }

  /** Returns the Similarity implementation used by this scorer. 
   * @deprecated Store any Similarity you might need privately in your implementation instead.
   */
  @Deprecated
  public Similarity getSimilarity() {
    return this.similarity;
  }

  /** Returns number of matches for the current document.
   *  This returns a float (not int) because
   *  SloppyPhraseScorer discounts its freq according to how
   *  "sloppy" the match was.
   *
   * @lucene.experimental */
  public float freq() throws IOException {
    throw new UnsupportedOperationException(this + " does not implement freq()");
  }

}
