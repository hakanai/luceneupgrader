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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.vectorization;

import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;

/** Default provider returning scalar implementations. */
final class DefaultVectorizationProvider extends VectorizationProvider {

  private final VectorUtilSupport vectorUtilSupport;

  DefaultVectorizationProvider() {
    vectorUtilSupport = new DefaultVectorUtilSupport();
  }

  @Override
  public VectorUtilSupport getVectorUtilSupport() {
    return vectorUtilSupport;
  }

  @Override
  public FlatVectorsScorer getLucene99FlatVectorsScorer() {
    return DefaultFlatVectorScorer.INSTANCE;
  }

  @Override
  public PostingDecodingUtil newPostingDecodingUtil(IndexInput input) {
    return new PostingDecodingUtil(input);
  }
}
