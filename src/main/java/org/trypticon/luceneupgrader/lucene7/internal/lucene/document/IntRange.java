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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.document.RangeFieldQuery.QueryType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FutureObjects;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.NumericUtils;

public class IntRange extends Field {
  public static final int BYTES = Integer.BYTES;

  public IntRange(String name, final int[] min, final int[] max) {
    super(name, getType(min.length));
    setRangeValues(min, max);
  }

  private static FieldType getType(int dimensions) {
    if (dimensions > 4) {
      throw new IllegalArgumentException("IntRange does not support greater than 4 dimensions");
    }

    FieldType ft = new FieldType();
    // dimensions is set as 2*dimension size (min/max per dimension)
    ft.setDimensions(dimensions*2, BYTES);
    ft.freeze();
    return ft;
  }

  public void setRangeValues(int[] min, int[] max) {
    checkArgs(min, max);
    if (min.length*2 != type.pointDataDimensionCount() || max.length*2 != type.pointDataDimensionCount()) {
      throw new IllegalArgumentException("field (name=" + name + ") uses " + type.pointDataDimensionCount()/2
          + " dimensions; cannot change to (incoming) " + min.length + " dimensions");
    }

    final byte[] bytes;
    if (fieldsData == null) {
      bytes = new byte[BYTES*2*min.length];
      fieldsData = new BytesRef(bytes);
    } else {
      bytes = ((BytesRef)fieldsData).bytes;
    }
    verifyAndEncode(min, max, bytes);
  }

  private static void checkArgs(final int[] min, final int[] max) {
    if (min == null || max == null || min.length == 0 || max.length == 0) {
      throw new IllegalArgumentException("min/max range values cannot be null or empty");
    }
    if (min.length != max.length) {
      throw new IllegalArgumentException("min/max ranges must agree");
    }
    if (min.length > 4) {
      throw new IllegalArgumentException("IntRange does not support greater than 4 dimensions");
    }
  }

  private static byte[] encode(int[] min, int[] max) {
    checkArgs(min, max);
    byte[] b = new byte[BYTES*2*min.length];
    verifyAndEncode(min, max, b);
    return b;
  }

  static void verifyAndEncode(int[] min, int[] max, byte[] bytes) {
    for (int d=0,i=0,j=min.length*BYTES; d<min.length; ++d, i+=BYTES, j+=BYTES) {
      if (Double.isNaN(min[d])) {
        throw new IllegalArgumentException("invalid min value (" + Double.NaN + ")" + " in IntRange");
      }
      if (Double.isNaN(max[d])) {
        throw new IllegalArgumentException("invalid max value (" + Double.NaN + ")" + " in IntRange");
      }
      if (min[d] > max[d]) {
        throw new IllegalArgumentException("min value (" + min[d] + ") is greater than max value (" + max[d] + ")");
      }
      encode(min[d], bytes, i);
      encode(max[d], bytes, j);
    }
  }

  private static void encode(int val, byte[] bytes, int offset) {
    NumericUtils.intToSortableBytes(val, bytes, offset);
  }

  public int getMin(int dimension) {
    FutureObjects.checkIndex(dimension, type.pointDataDimensionCount()/2);
    return decodeMin(((BytesRef)fieldsData).bytes, dimension);
  }

  public int getMax(int dimension) {
    FutureObjects.checkIndex(dimension, type.pointDataDimensionCount()/2);
    return decodeMax(((BytesRef)fieldsData).bytes, dimension);
  }

  static int decodeMin(byte[] b, int dimension) {
    int offset = dimension*BYTES;
    return NumericUtils.sortableBytesToInt(b, offset);
  }

  static int decodeMax(byte[] b, int dimension) {
    int offset = b.length/2 + dimension*BYTES;
    return NumericUtils.sortableBytesToInt(b, offset);
  }

  public static Query newIntersectsQuery(String field, final int[] min, final int[] max) {
    return newRelationQuery(field, min, max, QueryType.INTERSECTS);
  }

  public static Query newContainsQuery(String field, final int[] min, final int[] max) {
    return newRelationQuery(field, min, max, QueryType.CONTAINS);
  }

  public static Query newWithinQuery(String field, final int[] min, final int[] max) {
    return newRelationQuery(field, min, max, QueryType.WITHIN);
  }

  public static Query newCrossesQuery(String field, final int[] min, final int[] max) {
    return newRelationQuery(field, min, max, QueryType.CROSSES);
  }

  private static Query newRelationQuery(String field, final int[] min, final int[] max, QueryType relation) {
    checkArgs(min, max);
    return new RangeFieldQuery(field, encode(min, max), min.length, relation) {
      @Override
      protected String toString(byte[] ranges, int dimension) {
        return IntRange.toString(ranges, dimension);
      }
    };
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName());
    sb.append(" <");
    sb.append(name);
    sb.append(':');
    byte[] b = ((BytesRef)fieldsData).bytes;
    toString(b, 0);
    for (int d = 0; d < type.pointDataDimensionCount() / 2; ++d) {
      sb.append(' ');
      sb.append(toString(b, d));
    }
    sb.append('>');

    return sb.toString();
  }

  private static String toString(byte[] ranges, int dimension) {
    return "[" + Integer.toString(decodeMin(ranges, dimension)) + " : "
        + Integer.toString(decodeMax(ranges, dimension)) + "]";
  }
}
