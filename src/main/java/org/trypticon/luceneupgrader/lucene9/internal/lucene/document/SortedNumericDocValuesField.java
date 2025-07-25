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

import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;

/**
 * Field that stores a per-document <code>long</code> values for scoring, sorting or value
 * retrieval. Here's an example usage:
 *
 * <pre class="prettyprint">
 *   document.add(new SortedNumericDocValuesField(name, 5L));
 *   document.add(new SortedNumericDocValuesField(name, 14L));
 * </pre>
 *
 * <p>Note that if you want to encode doubles or floats with proper sort order, you will need to
 * encode them with {@link org.apache.lucene.util.NumericUtils}:
 *
 * <pre class="prettyprint">
 *   document.add(new SortedNumericDocValuesField(name, NumericUtils.floatToSortableInt(-5.3f)));
 * </pre>
 *
 * <p>If you also need to store the value, you should add a separate {@link StoredField} instance.
 */
public class SortedNumericDocValuesField extends Field {

  /** Type for sorted numeric DocValues. */
  public static final FieldType TYPE = new FieldType();

  static {
    TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC);
    TYPE.freeze();
  }

  /**
   * Creates a new DocValues field with the specified 64-bit long value
   *
   * @param name field name
   * @param value 64-bit long value
   * @throws IllegalArgumentException if the field name is null
   */
  public SortedNumericDocValuesField(String name, long value) {
    super(name, TYPE);
    fieldsData = Long.valueOf(value);
  }

  /**
   * Create a range query that matches all documents whose value is between {@code lowerValue} and
   * {@code upperValue} included.
   *
   * <p>You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries) by setting
   * {@code lowerValue = Long.MIN_VALUE} or {@code upperValue = Long.MAX_VALUE}.
   *
   * <p>Ranges are inclusive. For exclusive ranges, pass {@code Math.addExact(lowerValue, 1)} or
   * {@code Math.addExact(upperValue, -1)}.
   *
   * <p>This query also works with fields that have indexed {@link NumericDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a range query that executes on points, such as
   * {@link LongPoint#newRangeQuery}.
   *
   * @see IntField#newRangeQuery
   * @see LongField#newRangeQuery
   * @see FloatField#newRangeQuery
   * @see DoubleField#newRangeQuery
   */
  public static Query newSlowRangeQuery(String field, long lowerValue, long upperValue) {
    return new SortedNumericDocValuesRangeQuery(field, lowerValue, upperValue);
  }

  /**
   * Create a query matching any of the specified values.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a set query that executes on points, such as
   * {@link LongPoint#newSetQuery}.
   *
   * @see IntField#newSetQuery
   * @see LongField#newSetQuery
   * @see FloatField#newSetQuery
   * @see DoubleField#newSetQuery
   */
  public static Query newSlowSetQuery(String field, long... values) {
    return new SortedNumericDocValuesSetQuery(field, values.clone());
  }

  /**
   * Create a query for matching an exact long value.
   *
   * <p>This query also works with fields that have indexed {@link NumericDocValuesField}s.
   *
   * <p><b>NOTE</b>: Such queries cannot efficiently advance to the next match, which makes them
   * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
   * in an {@link IndexOrDocValuesQuery}, alongside a range query that executes on points, such as
   * {@link LongPoint#newExactQuery}.
   *
   * @see IntField#newExactQuery
   * @see LongField#newExactQuery
   * @see FloatField#newExactQuery
   * @see DoubleField#newExactQuery
   */
  public static Query newSlowExactQuery(String field, long value) {
    return newSlowRangeQuery(field, value, value);
  }
}
