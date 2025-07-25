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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.CloseableRandomVectorScorerSupplier;

/**
 * Vectors' writer for a field that allows additional indexing logic to be implemented by the caller
 *
 * @lucene.experimental
 */
public abstract class FlatVectorsWriter extends KnnVectorsWriter {
  /** Scorer for flat vectors */
  protected final FlatVectorsScorer vectorsScorer;

  /** Sole constructor */
  protected FlatVectorsWriter(FlatVectorsScorer vectorsScorer) {
    this.vectorsScorer = vectorsScorer;
  }

  /**
   * @return the {@link FlatVectorsScorer} for this reader.
   */
  public FlatVectorsScorer getFlatVectorScorer() {
    return vectorsScorer;
  }

  /**
   * Add a new field for indexing
   *
   * @param fieldInfo fieldInfo of the field to add
   * @return a writer for the field
   * @throws IOException if an I/O error occurs when adding the field
   */
  @Override
  public abstract FlatFieldVectorsWriter<?> addField(FieldInfo fieldInfo) throws IOException;

  /**
   * Write the field for merging, providing a scorer over the newly merged flat vectors. This way
   * any additional merging logic can be implemented by the user of this class.
   *
   * @param fieldInfo fieldInfo of the field to merge
   * @param mergeState mergeState of the segments to merge
   * @return a scorer over the newly merged flat vectors, which should be closed as it holds a
   *     temporary file handle to read over the newly merged vectors
   * @throws IOException if an I/O error occurs when merging
   */
  public abstract CloseableRandomVectorScorerSupplier mergeOneFieldToIndex(
      FieldInfo fieldInfo, MergeState mergeState) throws IOException;
}
