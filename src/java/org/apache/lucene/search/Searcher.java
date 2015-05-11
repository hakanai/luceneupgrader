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
 * An abstract base class for search implementations. Implements the main search
 * methods.
 * 
 * <p>
 * Note that you can only access hits from a Searcher as long as it is not yet
 * closed, otherwise an IOException will be thrown.
 *
 * @deprecated In 4.0 this abstract class is removed/absorbed
 * into IndexSearcher
 */
@Deprecated
public abstract class Searcher implements Searchable {

    /** The Similarity implementation used by this searcher. */
  private Similarity similarity = Similarity.getDefault();

  /** Expert: Set the Similarity implementation used by this Searcher.
   *
   *
   */
  public void setSimilarity(Similarity similarity) {
    this.similarity = similarity;
  }

  /** Expert: Return the Similarity implementation used by this Searcher.
   *
   * <p>This defaults to the current value of {@code Similarity#getDefault()}.
   */
  public Similarity getSimilarity() {
    return this.similarity;
  }

  /**
   * Creates a normalized weight for a top-level {@code Query}.
   * The query is rewritten by this method and {@code Query#createWeight} called,
   * afterwards the {@code Weight} is normalized. The returned {@code Weight}
   * can then directly be used to get a {@code Scorer}.
   * @lucene.internal
   */
  public Weight createNormalizedWeight(Query query) throws IOException {
    query = rewrite(query);
    Weight weight = query.createWeight(this);
    float sum = weight.sumOfSquaredWeights();
    // this is a hack for backwards compatibility:
    float norm = query.getSimilarity(this).queryNorm(sum);
    if (Float.isInfinite(norm) || Float.isNaN(norm))
      norm = 1.0f;
    weight.normalize(norm);
    return weight;
  }

  abstract public void close() throws IOException;

  abstract public Query rewrite(Query query) throws IOException;
  /* End patch for GCJ bug #15411. */
}
