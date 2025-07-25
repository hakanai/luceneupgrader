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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.KnnByteVectorField;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.ByteVectorValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.knn.KnnCollectorManager;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Bits;

/**
 * Uses {@link KnnVectorsReader#search(String, byte[], KnnCollector, Bits)} to perform nearest
 * neighbour search.
 *
 * <p>This query also allows for performing a kNN search subject to a filter. In this case, it first
 * executes the filter for each leaf, then chooses a strategy dynamically:
 *
 * <ul>
 *   <li>If the filter cost is less than k, just execute an exact search
 *   <li>Otherwise run a kNN search subject to the filter
 *   <li>If the kNN search visits too many vectors without completing, stop and run an exact search
 * </ul>
 */
public class KnnByteVectorQuery extends AbstractKnnVectorQuery {

  private static final TopDocs NO_RESULTS = TopDocsCollector.EMPTY_TOPDOCS;

  private final byte[] target;

  /**
   * Find the <code>k</code> nearest documents to the target vector according to the vectors in the
   * given field. <code>target</code> vector.
   *
   * @param field a field that has been indexed as a {@link KnnByteVectorField}.
   * @param target the target of the search
   * @param k the number of documents to find
   * @throws IllegalArgumentException if <code>k</code> is less than 1
   */
  public KnnByteVectorQuery(String field, byte[] target, int k) {
    this(field, target, k, null);
  }

  /**
   * Find the <code>k</code> nearest documents to the target vector according to the vectors in the
   * given field. <code>target</code> vector.
   *
   * @param field a field that has been indexed as a {@link KnnByteVectorField}.
   * @param target the target of the search
   * @param k the number of documents to find
   * @param filter a filter applied before the vector search
   * @throws IllegalArgumentException if <code>k</code> is less than 1
   */
  public KnnByteVectorQuery(String field, byte[] target, int k, Query filter) {
    super(field, k, filter);
    this.target = Objects.requireNonNull(target, "target");
  }

  @Override
  protected TopDocs approximateSearch(
      LeafReaderContext context,
      Bits acceptDocs,
      int visitedLimit,
      KnnCollectorManager knnCollectorManager)
      throws IOException {
    KnnCollector knnCollector = knnCollectorManager.newCollector(visitedLimit, context);
    LeafReader reader = context.reader();
    ByteVectorValues byteVectorValues = reader.getByteVectorValues(field);
    if (byteVectorValues == null) {
      ByteVectorValues.checkField(reader, field);
      return NO_RESULTS;
    }
    if (Math.min(knnCollector.k(), byteVectorValues.size()) == 0) {
      return NO_RESULTS;
    }
    reader.searchNearestVectors(field, target, knnCollector, acceptDocs);
    TopDocs results = knnCollector.topDocs();
    return results != null ? results : NO_RESULTS;
  }

  @Override
  VectorScorer createVectorScorer(LeafReaderContext context, FieldInfo fi) throws IOException {
    LeafReader reader = context.reader();
    ByteVectorValues vectorValues = reader.getByteVectorValues(field);
    if (vectorValues == null) {
      ByteVectorValues.checkField(reader, field);
      return null;
    }
    return vectorValues.scorer(target);
  }

  @Override
  public String toString(String field) {
    return getClass().getSimpleName() + ":" + this.field + "[" + target[0] + ",...][" + k + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (super.equals(o) == false) return false;
    KnnByteVectorQuery that = (KnnByteVectorQuery) o;
    return Arrays.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), Arrays.hashCode(target));
  }

  /**
   * @return the target query vector of the search. Each vector element is a byte.
   */
  public byte[] getTargetCopy() {
    return ArrayUtil.copyArray(target);
  }
}
