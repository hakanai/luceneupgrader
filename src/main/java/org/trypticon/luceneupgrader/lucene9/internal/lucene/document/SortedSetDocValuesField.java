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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.document;

import java.util.Collection;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.MultiTermQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TermInSetQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;

/**
 * Field that stores a set of per-document {@link BytesRef} values, indexed for
 * faceting,grouping,joining. Here's an example usage:
 *
 * <pre class="prettyprint">
 *   document.add(new SortedSetDocValuesField(name, new BytesRef("hello")));
 *   document.add(new SortedSetDocValuesField(name, new BytesRef("world")));
 * </pre>
 *
 * <p>If you also need to store the value, you should add a separate {@link StoredField} instance.
 *
 * <p>Each value can be at most 32766 bytes long.
 */
public class SortedSetDocValuesField extends Field {

  /** Type for sorted bytes DocValues */
  public static final FieldType TYPE = new FieldType();

  static {
    TYPE.setDocValuesType(DocValuesType.SORTED_SET);
    TYPE.freeze();
  }

  /**
   * Create a new sorted DocValues field.
   *
   * @param name field name
   * @param bytes binary content
   * @throws IllegalArgumentException if the field name is null
   */
  public SortedSetDocValuesField(String name, BytesRef bytes) {
    super(name, TYPE);
    fieldsData = bytes;
  }

  /**
   * Create a range query that matches all documents whose value is between {@code lowerValue} and
   * {@code upperValue}.
   *
   * <p>This query also works with fields that have indexed {@link SortedDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a range query that executes on points, such as
   * {@link BinaryPoint#newRangeQuery}.
   */
  public static Query newSlowRangeQuery(
      String field,
      BytesRef lowerValue,
      BytesRef upperValue,
      boolean lowerInclusive,
      boolean upperInclusive) {
    return new SortedSetDocValuesRangeQuery(
        field, lowerValue, upperValue, lowerInclusive, upperInclusive);
  }

  /**
   * Create a query for matching an exact {@link BytesRef} value.
   *
   * <p>This query also works with fields that have indexed {@link SortedDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a range query that executes on points, such as
   * {@link BinaryPoint#newExactQuery}.
   */
  public static Query newSlowExactQuery(String field, BytesRef value) {
    return newSlowRangeQuery(field, value, value, true, true);
  }

  /**
   * Create a query matching any of the specified values.
   *
   * <p>This query also works with fields that have indexed {@link SortedDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a set query that executes on postings, such as
   * {@link TermInSetQuery}.
   *
   * @deprecated Use {@link #newSlowSetQuery(String, Collection)} instead.
   */
  @Deprecated(forRemoval = true, since = "9.10")
  public static Query newSlowSetQuery(String field, BytesRef... values) {
    return new TermInSetQuery(MultiTermQuery.DOC_VALUES_REWRITE, field, values);
  }

  /**
   * Create a query matching any of the specified values.
   *
   * <p>This query also works with fields that have indexed {@link SortedDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a set query that executes on postings, such as
   * {@link TermInSetQuery}.
   */
  public static Query newSlowSetQuery(String field, Collection<BytesRef> values) {
    return new TermInSetQuery(MultiTermQuery.DOC_VALUES_REWRITE, field, values);
  }
}
