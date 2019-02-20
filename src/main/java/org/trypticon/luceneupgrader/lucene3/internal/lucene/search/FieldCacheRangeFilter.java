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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.NumericUtils;

import java.io.IOException;

public abstract class FieldCacheRangeFilter<T> extends Filter {
  final String field;
  final FieldCache.Parser parser;
  final T lowerVal;
  final T upperVal;
  final boolean includeLower;
  final boolean includeUpper;
  
  private FieldCacheRangeFilter(String field, FieldCache.Parser parser, T lowerVal, T upperVal, boolean includeLower, boolean includeUpper) {
    this.field = field;
    this.parser = parser;
    this.lowerVal = lowerVal;
    this.upperVal = upperVal;
    this.includeLower = includeLower;
    this.includeUpper = includeUpper;
  }
  
  @Override
  public abstract DocIdSet getDocIdSet(IndexReader reader) throws IOException;

  public static FieldCacheRangeFilter<String> newStringRange(String field, String lowerVal, String upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<String>(field, null, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final FieldCache.StringIndex fcsi = FieldCache.DEFAULT.getStringIndex(reader, field);
        final int lowerPoint = fcsi.binarySearchLookup(lowerVal);
        final int upperPoint = fcsi.binarySearchLookup(upperVal);
        
        final int inclusiveLowerPoint, inclusiveUpperPoint;

        // Hints:
        // * binarySearchLookup returns 0, if value was null.
        // * the value is <0 if no exact hit was found, the returned value
        //   is (-(insertion point) - 1)
        if (lowerPoint == 0) {
          assert lowerVal == null;
          inclusiveLowerPoint = 1;
        } else if (includeLower && lowerPoint > 0) {
          inclusiveLowerPoint = lowerPoint;
        } else if (lowerPoint > 0) {
          inclusiveLowerPoint = lowerPoint + 1;
        } else {
          inclusiveLowerPoint = Math.max(1, -lowerPoint - 1);
        }
        
        if (upperPoint == 0) {
          assert upperVal == null;
          inclusiveUpperPoint = Integer.MAX_VALUE;  
        } else if (includeUpper && upperPoint > 0) {
          inclusiveUpperPoint = upperPoint;
        } else if (upperPoint > 0) {
          inclusiveUpperPoint = upperPoint - 1;
        } else {
          inclusiveUpperPoint = -upperPoint - 2;
        }      

        if (inclusiveUpperPoint <= 0 || inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        assert inclusiveLowerPoint > 0 && inclusiveUpperPoint > 0;
        
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected final boolean matchDoc(int doc) {
            return fcsi.order[doc] >= inclusiveLowerPoint && fcsi.order[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Byte> newByteRange(String field, Byte lowerVal, Byte upperVal, boolean includeLower, boolean includeUpper) {
    return newByteRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Byte> newByteRange(String field, FieldCache.ByteParser parser, Byte lowerVal, Byte upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Byte>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final byte inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          final byte i = lowerVal.byteValue();
          if (!includeLower && i == Byte.MAX_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveLowerPoint = (byte) (includeLower ?  i : (i + 1));
        } else {
          inclusiveLowerPoint = Byte.MIN_VALUE;
        }
        if (upperVal != null) {
          final byte i = upperVal.byteValue();
          if (!includeUpper && i == Byte.MIN_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveUpperPoint = (byte) (includeUpper ? i : (i - 1));
        } else {
          inclusiveUpperPoint = Byte.MAX_VALUE;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final byte[] values = FieldCache.DEFAULT.getBytes(reader, field, (FieldCache.ByteParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Short> newShortRange(String field, Short lowerVal, Short upperVal, boolean includeLower, boolean includeUpper) {
    return newShortRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Short> newShortRange(String field, FieldCache.ShortParser parser, Short lowerVal, Short upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Short>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final short inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          short i = lowerVal.shortValue();
          if (!includeLower && i == Short.MAX_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveLowerPoint = (short) (includeLower ? i : (i + 1));
        } else {
          inclusiveLowerPoint = Short.MIN_VALUE;
        }
        if (upperVal != null) {
          short i = upperVal.shortValue();
          if (!includeUpper && i == Short.MIN_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveUpperPoint = (short) (includeUpper ? i : (i - 1));
        } else {
          inclusiveUpperPoint = Short.MAX_VALUE;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final short[] values = FieldCache.DEFAULT.getShorts(reader, field, (FieldCache.ShortParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Integer> newIntRange(String field, Integer lowerVal, Integer upperVal, boolean includeLower, boolean includeUpper) {
    return newIntRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Integer> newIntRange(String field, FieldCache.IntParser parser, Integer lowerVal, Integer upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Integer>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final int inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          int i = lowerVal.intValue();
          if (!includeLower && i == Integer.MAX_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveLowerPoint = includeLower ? i : (i + 1);
        } else {
          inclusiveLowerPoint = Integer.MIN_VALUE;
        }
        if (upperVal != null) {
          int i = upperVal.intValue();
          if (!includeUpper && i == Integer.MIN_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveUpperPoint = includeUpper ? i : (i - 1);
        } else {
          inclusiveUpperPoint = Integer.MAX_VALUE;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final int[] values = FieldCache.DEFAULT.getInts(reader, field, (FieldCache.IntParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Long> newLongRange(String field, Long lowerVal, Long upperVal, boolean includeLower, boolean includeUpper) {
    return newLongRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Long> newLongRange(String field, FieldCache.LongParser parser, Long lowerVal, Long upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Long>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final long inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          long i = lowerVal.longValue();
          if (!includeLower && i == Long.MAX_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveLowerPoint = includeLower ? i : (i + 1L);
        } else {
          inclusiveLowerPoint = Long.MIN_VALUE;
        }
        if (upperVal != null) {
          long i = upperVal.longValue();
          if (!includeUpper && i == Long.MIN_VALUE)
            return DocIdSet.EMPTY_DOCIDSET;
          inclusiveUpperPoint = includeUpper ? i : (i - 1L);
        } else {
          inclusiveUpperPoint = Long.MAX_VALUE;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final long[] values = FieldCache.DEFAULT.getLongs(reader, field, (FieldCache.LongParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Float> newFloatRange(String field, Float lowerVal, Float upperVal, boolean includeLower, boolean includeUpper) {
    return newFloatRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Float> newFloatRange(String field, FieldCache.FloatParser parser, Float lowerVal, Float upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Float>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        // we transform the floating point numbers to sortable integers
        // using NumericUtils to easier find the next bigger/lower value
        final float inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          float f = lowerVal.floatValue();
          if (!includeUpper && f > 0.0f && Float.isInfinite(f))
            return DocIdSet.EMPTY_DOCIDSET;
          int i = NumericUtils.floatToSortableInt(f);
          inclusiveLowerPoint = NumericUtils.sortableIntToFloat( includeLower ?  i : (i + 1) );
        } else {
          inclusiveLowerPoint = Float.NEGATIVE_INFINITY;
        }
        if (upperVal != null) {
          float f = upperVal.floatValue();
          if (!includeUpper && f < 0.0f && Float.isInfinite(f))
            return DocIdSet.EMPTY_DOCIDSET;
          int i = NumericUtils.floatToSortableInt(f);
          inclusiveUpperPoint = NumericUtils.sortableIntToFloat( includeUpper ? i : (i - 1) );
        } else {
          inclusiveUpperPoint = Float.POSITIVE_INFINITY;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final float[] values = FieldCache.DEFAULT.getFloats(reader, field, (FieldCache.FloatParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  public static FieldCacheRangeFilter<Double> newDoubleRange(String field, Double lowerVal, Double upperVal, boolean includeLower, boolean includeUpper) {
    return newDoubleRange(field, null, lowerVal, upperVal, includeLower, includeUpper);
  }
  
  public static FieldCacheRangeFilter<Double> newDoubleRange(String field, FieldCache.DoubleParser parser, Double lowerVal, Double upperVal, boolean includeLower, boolean includeUpper) {
    return new FieldCacheRangeFilter<Double>(field, parser, lowerVal, upperVal, includeLower, includeUpper) {
      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        // we transform the floating point numbers to sortable integers
        // using NumericUtils to easier find the next bigger/lower value
        final double inclusiveLowerPoint, inclusiveUpperPoint;
        if (lowerVal != null) {
          double f = lowerVal.doubleValue();
          if (!includeUpper && f > 0.0 && Double.isInfinite(f))
            return DocIdSet.EMPTY_DOCIDSET;
          long i = NumericUtils.doubleToSortableLong(f);
          inclusiveLowerPoint = NumericUtils.sortableLongToDouble( includeLower ?  i : (i + 1L) );
        } else {
          inclusiveLowerPoint = Double.NEGATIVE_INFINITY;
        }
        if (upperVal != null) {
          double f = upperVal.doubleValue();
          if (!includeUpper && f < 0.0 && Double.isInfinite(f))
            return DocIdSet.EMPTY_DOCIDSET;
          long i = NumericUtils.doubleToSortableLong(f);
          inclusiveUpperPoint = NumericUtils.sortableLongToDouble( includeUpper ? i : (i - 1L) );
        } else {
          inclusiveUpperPoint = Double.POSITIVE_INFINITY;
        }
        
        if (inclusiveLowerPoint > inclusiveUpperPoint)
          return DocIdSet.EMPTY_DOCIDSET;
        
        final double[] values = FieldCache.DEFAULT.getDoubles(reader, field, (FieldCache.DoubleParser) parser);
        return new FieldCacheDocIdSet(reader) {
          @Override
          protected boolean matchDoc(int doc) {
            return values[doc] >= inclusiveLowerPoint && values[doc] <= inclusiveUpperPoint;
          }
        };
      }
    };
  }
  
  @Override
  public final String toString() {
    final StringBuilder sb = new StringBuilder(field).append(":");
    return sb.append(includeLower ? '[' : '{')
      .append((lowerVal == null) ? "*" : lowerVal.toString())
      .append(" TO ")
      .append((upperVal == null) ? "*" : upperVal.toString())
      .append(includeUpper ? ']' : '}')
      .toString();
  }

  @Override
  @SuppressWarnings({"unchecked","rawtypes"})
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FieldCacheRangeFilter)) return false;
    FieldCacheRangeFilter other = (FieldCacheRangeFilter) o;

    if (!this.field.equals(other.field)
        || this.includeLower != other.includeLower
        || this.includeUpper != other.includeUpper
    ) { return false; }
    if (this.lowerVal != null ? !this.lowerVal.equals(other.lowerVal) : other.lowerVal != null) return false;
    if (this.upperVal != null ? !this.upperVal.equals(other.upperVal) : other.upperVal != null) return false;
    if (this.parser != null ? !this.parser.equals(other.parser) : other.parser != null) return false;
    return true;
  }
  
  @Override
  public final int hashCode() {
    int h = field.hashCode();
    h ^= (lowerVal != null) ? lowerVal.hashCode() : 550356204;
    h = (h << 1) | (h >>> 31);  // rotate to distinguish lower from upper
    h ^= (upperVal != null) ? upperVal.hashCode() : -1674416163;
    h ^= (parser != null) ? parser.hashCode() : -1572457324;
    h ^= (includeLower ? 1549299360 : -365038026) ^ (includeUpper ? 1721088258 : 1948649653);
    return h;
  }

  public String getField() { return field; }

  public boolean includesLower() { return includeLower; }
  
  public boolean includesUpper() { return includeUpper; }

  public T getLowerVal() { return lowerVal; }

  public T getUpperVal() { return upperVal; }
  
  public FieldCache.Parser getParser() { return parser; }

}
