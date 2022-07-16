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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexSorter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SortFieldProvider;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.DoubleComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.FloatComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.IntComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.LongComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;

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

  public static final class Provider extends SortFieldProvider {

    public static final String NAME = "SortedNumericSortField";

    public Provider() {
      super(NAME);
    }

    @Override
    public SortField readSortField(DataInput in) throws IOException {
      SortedNumericSortField sf = new SortedNumericSortField(in.readString(), readType(in), in.readInt() == 1, readSelectorType(in));
      if (in.readInt() == 1) {
        switch (sf.type) {
          case INT:
            sf.setMissingValue(in.readInt());
            break;
          case LONG:
            sf.setMissingValue(in.readLong());
            break;
          case FLOAT:
            sf.setMissingValue(NumericUtils.sortableIntToFloat(in.readInt()));
            break;
          case DOUBLE:
            sf.setMissingValue(NumericUtils.sortableLongToDouble(in.readLong()));
            break;
          default:
            throw new AssertionError();
        }
      }
      return sf;
    }

    @Override
    public void writeSortField(SortField sf, DataOutput out) throws IOException {
      assert sf instanceof SortedNumericSortField;
      ((SortedNumericSortField)sf).serialize(out);
    }
  }

  private static SortedNumericSelector.Type readSelectorType(DataInput in) throws IOException {
    int selectorType = in.readInt();
    if (selectorType >= SortedNumericSelector.Type.values().length) {
      throw new IllegalArgumentException("Can't deserialize SortedNumericSortField - unknown selector type " + selectorType);
    }
    return SortedNumericSelector.Type.values()[selectorType];
  }

  private void serialize(DataOutput out) throws IOException {
    out.writeString(getField());
    out.writeString(type.toString());
    out.writeInt(reverse ? 1 : 0);
    out.writeInt(selector.ordinal());
    if (missingValue == null) {
      out.writeInt(0);
    }
    else {
      out.writeInt(1);
      // oh for switch expressions...
      switch (type) {
        case INT:
          out.writeInt((int)missingValue);
          break;
        case LONG:
          out.writeLong((long)missingValue);
          break;
        case FLOAT:
          out.writeInt(NumericUtils.floatToSortableInt((float)missingValue));
          break;
        case DOUBLE:
          out.writeLong(NumericUtils.doubleToSortableLong((double)missingValue));
          break;
        default:
          throw new AssertionError();
      }
    }
  }

  public SortField.Type getNumericType() {
    return type;
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
  public FieldComparator<?> getComparator(int numHits, int sortPos) {
    final FieldComparator<?> fieldComparator;
    switch (type) {
      case INT:
        fieldComparator =
            new IntComparator(numHits, getField(), (Integer) missingValue, reverse, sortPos) {
              @Override
              public LeafFieldComparator getLeafComparator(LeafReaderContext context)
                  throws IOException {
                return new IntLeafComparator(context) {
                  @Override
                  protected NumericDocValues getNumericDocValues(
                      LeafReaderContext context, String field) throws IOException {
                    return SortedNumericSelector.wrap(
                        DocValues.getSortedNumeric(context.reader(), field), selector, type);
                  }
                  // we can use sort optimization with points if selector is MIN or MAX,
                  // because we can still build successful iterator over points in this case.
                  @Override
                  protected PointValues getPointValues(LeafReaderContext context, String field)
                      throws IOException {
                    if (selector == SortedNumericSelector.Type.MAX
                        || selector == SortedNumericSelector.Type.MIN) {
                      return super.getPointValues(context, field);
                    } else {
                      return null;
                    }
                  }
                };
              }
            };
        break;
      case FLOAT:
        fieldComparator =
            new FloatComparator(numHits, getField(), (Float) missingValue, reverse, sortPos) {
              @Override
              public LeafFieldComparator getLeafComparator(LeafReaderContext context)
                  throws IOException {
                return new FloatLeafComparator(context) {
                  @Override
                  protected NumericDocValues getNumericDocValues(
                      LeafReaderContext context, String field) throws IOException {
                    return SortedNumericSelector.wrap(
                        DocValues.getSortedNumeric(context.reader(), field), selector, type);
                  }
                  // we can use sort optimization with points if selector is MIN or MAX,
                  // because we can still build successful iterator over points in this case.
                  @Override
                  protected PointValues getPointValues(LeafReaderContext context, String field)
                      throws IOException {
                    if (selector == SortedNumericSelector.Type.MAX
                        || selector == SortedNumericSelector.Type.MIN) {
                      return super.getPointValues(context, field);
                    } else {
                      return null;
                    }
                  }
                };
              }
            };
        break;
      case LONG:
        fieldComparator =
            new LongComparator(numHits, getField(), (Long) missingValue, reverse, sortPos) {
              @Override
              public LeafFieldComparator getLeafComparator(LeafReaderContext context)
                  throws IOException {
                return new LongLeafComparator(context) {
                  @Override
                  protected NumericDocValues getNumericDocValues(
                      LeafReaderContext context, String field) throws IOException {
                    return SortedNumericSelector.wrap(
                        DocValues.getSortedNumeric(context.reader(), field), selector, type);
                  }
                  // we can use sort optimization with points if selector is MIN or MAX,
                  // because we can still build successful iterator over points in this case.
                  @Override
                  protected PointValues getPointValues(LeafReaderContext context, String field)
                      throws IOException {
                    if (selector == SortedNumericSelector.Type.MAX
                        || selector == SortedNumericSelector.Type.MIN) {
                      return super.getPointValues(context, field);
                    } else {
                      return null;
                    }
                  }
                };
              }
            };
        break;
      case DOUBLE:
        fieldComparator =
            new DoubleComparator(numHits, getField(), (Double) missingValue, reverse, sortPos) {
              @Override
              public LeafFieldComparator getLeafComparator(LeafReaderContext context)
                  throws IOException {
                return new DoubleLeafComparator(context) {
                  @Override
                  protected NumericDocValues getNumericDocValues(
                      LeafReaderContext context, String field) throws IOException {
                    return SortedNumericSelector.wrap(
                        DocValues.getSortedNumeric(context.reader(), field), selector, type);
                  }
                  // we can use sort optimization with points if selector is MIN or MAX,
                  // because we can still build successful iterator over points in this case.
                  @Override
                  protected PointValues getPointValues(LeafReaderContext context, String field)
                      throws IOException {
                    if (selector == SortedNumericSelector.Type.MAX
                        || selector == SortedNumericSelector.Type.MIN) {
                      return super.getPointValues(context, field);
                    } else {
                      return null;
                    }
                  }
                };
              }
            };
        break;
      case CUSTOM:
      case DOC:
      case REWRITEABLE:
      case STRING_VAL:
      case SCORE:
      case STRING:
      default:
        throw new AssertionError();
    }
    if (getCanUsePoints() == false) {
      fieldComparator.disableSkipping();
    }
    return fieldComparator;
  }

  private NumericDocValues getValue(LeafReader reader) throws IOException {
    return SortedNumericSelector.wrap(DocValues.getSortedNumeric(reader, getField()), selector, type);
  }

  @Override
  public IndexSorter getIndexSorter() {
    switch(type) {
      case INT:
        return new IndexSorter.IntSorter(Provider.NAME, (Integer)missingValue, reverse, this::getValue);
      case LONG:
        return new IndexSorter.LongSorter(Provider.NAME, (Long)missingValue, reverse, this::getValue);
      case DOUBLE:
        return new IndexSorter.DoubleSorter(Provider.NAME, (Double)missingValue, reverse, this::getValue);
      case FLOAT:
        return new IndexSorter.FloatSorter(Provider.NAME, (Float)missingValue, reverse, this::getValue);
      default:
        throw new AssertionError();
    }
  }
}
