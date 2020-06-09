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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;

import java.util.Arrays;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.PointInSetQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.PointRangeQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.NumericUtils;

public final class LongPoint extends Field {

  private static FieldType getType(int numDims) {
    FieldType type = new FieldType();
    type.setDimensions(numDims, Long.BYTES);
    type.freeze();
    return type;
  }

  @Override
  public void setLongValue(long value) {
    setLongValues(value);
  }

  public void setLongValues(long... point) {
    if (type.pointDataDimensionCount() != point.length) {
      throw new IllegalArgumentException("this field (name=" + name + ") uses " + type.pointDataDimensionCount() + " dimensions; cannot change to (incoming) " + point.length + " dimensions");
    }
    fieldsData = pack(point);
  }

  @Override
  public void setBytesValue(BytesRef bytes) {
    throw new IllegalArgumentException("cannot change value type from long to BytesRef");
  }

  @Override
  public Number numericValue() {
    if (type.pointDataDimensionCount() != 1) {
      throw new IllegalStateException("this field (name=" + name + ") uses " + type.pointDataDimensionCount() + " dimensions; cannot convert to a single numeric value");
    }
    BytesRef bytes = (BytesRef) fieldsData;
    assert bytes.length == Long.BYTES;
    return decodeDimension(bytes.bytes, bytes.offset);
  }

  private static BytesRef pack(long... point) {
    if (point == null) {
      throw new IllegalArgumentException("point must not be null");
    }
    if (point.length == 0) {
      throw new IllegalArgumentException("point must not be 0 dimensions");
    }
    byte[] packed = new byte[point.length * Long.BYTES];
    
    for (int dim = 0; dim < point.length; dim++) {
      encodeDimension(point[dim], packed, dim * Long.BYTES);
    }

    return new BytesRef(packed);
  }

  public LongPoint(String name, long... point) {
    super(name, pack(point), getType(point.length));
  }
  
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(getClass().getSimpleName());
    result.append(" <");
    result.append(name);
    result.append(':');

    BytesRef bytes = (BytesRef) fieldsData;
    for (int dim = 0; dim < type.pointDataDimensionCount(); dim++) {
      if (dim > 0) {
        result.append(',');
      }
      result.append(decodeDimension(bytes.bytes, bytes.offset + dim * Long.BYTES));
    }

    result.append('>');
    return result.toString();
  }
  
  // public helper methods (e.g. for queries)
  
  public static void encodeDimension(long value, byte dest[], int offset) {
    NumericUtils.longToSortableBytes(value, dest, offset);
  }
  
  public static long decodeDimension(byte value[], int offset) {
    return NumericUtils.sortableBytesToLong(value, offset);
  }
  
  // static methods for generating queries
  
  public static Query newExactQuery(String field, long value) {
    return newRangeQuery(field, value, value);
  }

  public static Query newRangeQuery(String field, long lowerValue, long upperValue) {
    return newRangeQuery(field, new long[] { lowerValue }, new long[] { upperValue });
  }

  public static Query newRangeQuery(String field, long[] lowerValue, long[] upperValue) {
    PointRangeQuery.checkArgs(field, lowerValue, upperValue);
    return new PointRangeQuery(field, pack(lowerValue).bytes, pack(upperValue).bytes, lowerValue.length) {
      @Override
      protected String toString(int dimension, byte[] value) {
        return Long.toString(decodeDimension(value, 0));
      }
    };
  }

  public static Query newSetQuery(String field, long... values) {

    // Don't unexpectedly change the user's incoming values array:
    long[] sortedValues = values.clone();
    Arrays.sort(sortedValues);

    final BytesRef encoded = new BytesRef(new byte[Long.BYTES]);

    return new PointInSetQuery(field, 1, Long.BYTES,
                               new PointInSetQuery.Stream() {

                                 int upto;

                                 @Override
                                 public BytesRef next() {
                                   if (upto == sortedValues.length) {
                                     return null;
                                   } else {
                                     encodeDimension(sortedValues[upto], encoded.bytes, 0);
                                     upto++;
                                     return encoded;
                                   }
                                 }
                               }) {
      @Override
      protected String toString(byte[] value) {
        assert value.length == Long.BYTES;
        return Long.toString(decodeDimension(value, 0));
      }
    };
  }
  
  public static Query newSetQuery(String field, Collection<Long> values) {
    Long[] boxed = values.toArray(new Long[0]);
    long[] unboxed = new long[boxed.length];
    for (int i = 0; i < boxed.length; i++) {
      unboxed[i] = boxed[i];
    }
    return newSetQuery(field, unboxed);
  }
}
