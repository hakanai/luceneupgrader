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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.KnnFloatVectorField;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.VectorScorer;

/**
 * This class provides access to per-document floating point vector values indexed as {@link
 * KnnFloatVectorField}.
 *
 * @lucene.experimental
 */
public abstract class FloatVectorValues extends DocIdSetIterator {

  /** Sole constructor */
  protected FloatVectorValues() {}

  /** Return the dimension of the vectors */
  public abstract int dimension();

  /**
   * Return the number of vectors for this field.
   *
   * @return the number of vectors returned by this iterator
   */
  public abstract int size();

  @Override
  public final long cost() {
    return size();
  }

  /**
   * Return the vector value for the current document ID. It is illegal to call this method when the
   * iterator is not positioned: before advancing, or after failing to advance. The returned array
   * may be shared across calls, re-used, and modified as the iterator advances.
   *
   * @return the vector value
   */
  public abstract float[] vectorValue() throws IOException;

  /**
   * Checks the Vector Encoding of a field
   *
   * @throws IllegalStateException if {@code field} has vectors, but using a different encoding
   * @lucene.internal
   * @lucene.experimental
   */
  public static void checkField(LeafReader in, String field) {
    FieldInfo fi = in.getFieldInfos().fieldInfo(field);
    if (fi != null && fi.hasVectorValues() && fi.getVectorEncoding() != VectorEncoding.FLOAT32) {
      throw new IllegalStateException(
          "Unexpected vector encoding ("
              + fi.getVectorEncoding()
              + ") for field "
              + field
              + "(expected="
              + VectorEncoding.FLOAT32
              + ")");
    }
  }

  /**
   * Return a {@link VectorScorer} for the given query vector and the current {@link
   * FloatVectorValues}. The iterator for the scorer is not the same instance as the iterator for
   * this {@link FloatVectorValues}. It is a copy, and iteration over the scorer will not affect the
   * iteration of this {@link FloatVectorValues}.
   *
   * @param query the query vector
   * @return a {@link VectorScorer} instance or null
   */
  public abstract VectorScorer scorer(float[] query) throws IOException;
}
