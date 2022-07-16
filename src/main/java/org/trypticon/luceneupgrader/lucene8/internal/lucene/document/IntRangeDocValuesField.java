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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;

public class IntRangeDocValuesField extends BinaryRangeDocValuesField{
  final String field;
  final int[] min;
  final int[] max;

  public IntRangeDocValuesField(String field, final int[] min, final int[] max) {
    super(field, IntRange.encode(min, max), min.length, IntRange.BYTES);
    checkArgs(min, max);
    this.field = field;
    this.min = min;
    this.max = max;
  }

  public int getMin(int dimension) {
    if (dimension > 4 || dimension > min.length) {
      throw new IllegalArgumentException("Dimension out of valid range");
    }

    return min[dimension];
  }

  public int getMax(int dimension) {
    if (dimension > 4 || dimension > min.length) {
      throw new IllegalArgumentException("Dimension out of valid range");
    }

    return max[dimension];
  }

  private static Query newSlowRangeQuery(String field, final int[] min, final int[] max,
                                        RangeFieldQuery.QueryType queryType) {
    checkArgs(min, max);
    return new IntRangeSlowRangeQuery(field, min, max, queryType);
  }

  public static Query newSlowIntersectsQuery(String field, final int[] min, final int[] max) {
    return newSlowRangeQuery(field, min, max, RangeFieldQuery.QueryType.INTERSECTS);
  }

  private static void checkArgs(final int[] min, final int[] max) {
    if (min == null || max == null || min.length == 0 || max.length == 0) {
      throw new IllegalArgumentException("min/max range values cannot be null or empty");
    }
    if (min.length != max.length) {
      throw new IllegalArgumentException("min/max ranges must agree");
    }

    for (int i = 0; i < min.length; i++) {
      if (min[i] > max[i]) {
        throw new IllegalArgumentException("min should be less than max but min = " + min[i] + " and max = " + max[i]);
      }
    }
  }
}
