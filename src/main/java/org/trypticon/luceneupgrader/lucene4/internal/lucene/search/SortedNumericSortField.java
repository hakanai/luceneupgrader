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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SortedNumericDocValues;

public class SortedNumericSortField extends SortField {
  
  private final SortedNumericSelector.Type selector;
  private final SortField.Type type;
  
  public SortedNumericSortField(String field, SortField.Type type) {
    this(field, type, false);
  }
  
  public SortedNumericSortField(String field, SortField.Type type, boolean reverse) {
    this(field, type, reverse, SortedNumericSelector.Type.MIN);
  }

  public SortedNumericSortField(String field, SortField.Type type, boolean reverse, SortedNumericSelector.Type selector) {
    super(field, SortField.Type.CUSTOM, reverse);
    if (selector == null) {
      throw new NullPointerException();
    }
    if (type == null) {
      throw new NullPointerException();
    }
    this.selector = selector;
    this.type = type;
  }
  
  public SortedNumericSelector.Type getSelector() {
    return selector;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + selector.hashCode();
    result = prime * result + type.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    SortedNumericSortField other = (SortedNumericSortField) obj;
    if (selector != other.selector) return false;
    if (type != other.type) return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<sortednumeric" + ": \"").append(getField()).append("\">");
    if (getReverse()) buffer.append('!');
    if (missingValue != null) {
      buffer.append(" missingValue=");
      buffer.append(missingValue);
    }
    buffer.append(" selector=");
    buffer.append(selector);
    buffer.append(" type=");
    buffer.append(type);

    return buffer.toString();
  }
  
  @Override
  public void setMissingValue(Object missingValue) {
    this.missingValue = missingValue;
  }
  
  @Override
  public FieldComparator<?> getComparator(int numHits, int sortPos) throws IOException {
    switch(type) {
      case INT:
        return new FieldComparator.IntComparator(numHits, getField(), null, (Integer) missingValue) {
          @Override
          protected FieldCache.Ints getIntValues(AtomicReaderContext context, String field) throws IOException {
            final NumericDocValues dv = SortedNumericSelector.wrap(DocValues.getSortedNumeric(context.reader(), field), selector, type);
            return new FieldCache.Ints() {
              @Override
              public int get(int docID) {
                return (int) dv.get(docID);
              }
            };
          } 
        };
      case FLOAT:
        return new FieldComparator.FloatComparator(numHits, getField(), null, (Float) missingValue) {
          @Override
          protected FieldCache.Floats getFloatValues(AtomicReaderContext context, String field) throws IOException {
            final NumericDocValues dv = SortedNumericSelector.wrap(DocValues.getSortedNumeric(context.reader(), field), selector, type);
            return new FieldCache.Floats() {
              @Override
              public float get(int docID) {
                return Float.intBitsToFloat((int)dv.get(docID)); 
              }
            };
          } 
        };
      case LONG:
        return new FieldComparator.LongComparator(numHits, getField(), null, (Long) missingValue) {
          @Override
          protected FieldCache.Longs getLongValues(AtomicReaderContext context, String field) throws IOException {
            final NumericDocValues dv = SortedNumericSelector.wrap(DocValues.getSortedNumeric(context.reader(), field), selector, type);
            return new FieldCache.Longs() {
              @Override
              public long get(int docID) {
                return dv.get(docID);
              }
            };
          }
        };
      case DOUBLE:
        return new FieldComparator.DoubleComparator(numHits, getField(), null, (Double) missingValue) {
          @Override
          protected FieldCache.Doubles getDoubleValues(AtomicReaderContext context, String field) throws IOException {
            final NumericDocValues dv = SortedNumericSelector.wrap(DocValues.getSortedNumeric(context.reader(), field), selector, type);
            return new FieldCache.Doubles() {
              @Override
              public double get(int docID) {
                return Double.longBitsToDouble(dv.get(docID));
              }
            };
          } 
        };
      default:
        throw new AssertionError();
    }
  }
}
