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

import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.NumericTokenStream; // for javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.DoubleField; // for javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.FloatField; // for javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.IntField; // for javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.LongField; // for javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.NumericUtils; // for javadocs


@Deprecated
public final class NumericRangeFilter<T extends Number> extends MultiTermQueryWrapperFilter<NumericRangeQuery<T>> {

  private NumericRangeFilter(final NumericRangeQuery<T> query) {
    super(query);
  }
  
  public static NumericRangeFilter<Long> newLongRange(final String field, final int precisionStep,
    Long min, Long max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newLongRange(field, precisionStep, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Long> newLongRange(final String field,
    Long min, Long max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newLongRange(field, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Integer> newIntRange(final String field, final int precisionStep,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newIntRange(field, precisionStep, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Integer> newIntRange(final String field,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newIntRange(field, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Double> newDoubleRange(final String field, final int precisionStep,
    Double min, Double max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newDoubleRange(field, precisionStep, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Double> newDoubleRange(final String field,
    Double min, Double max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newDoubleRange(field, min, max, minInclusive, maxInclusive)
    );
  }
  
  public static NumericRangeFilter<Float> newFloatRange(final String field, final int precisionStep,
    Float min, Float max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newFloatRange(field, precisionStep, min, max, minInclusive, maxInclusive)
    );
  }

  public static NumericRangeFilter<Float> newFloatRange(final String field,
    Float min, Float max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeFilter<>(
      NumericRangeQuery.newFloatRange(field, min, max, minInclusive, maxInclusive)
    );
  }

  public boolean includesMin() { return query.includesMin(); }
  
  public boolean includesMax() { return query.includesMax(); }

  public T getMin() { return query.getMin(); }

  public T getMax() { return query.getMax(); }
  
  public int getPrecisionStep() { return query.getPrecisionStep(); }
  
}
