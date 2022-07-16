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
import java.util.Comparator;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexSorter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SortFieldProvider;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.DocComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.DoubleComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.FloatComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.IntComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.comparators.LongComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;

public class SortField {

  public static enum Type {

    SCORE,

    DOC,

    STRING,

    INT,

    FLOAT,

    LONG,

    DOUBLE,

    CUSTOM,

    STRING_VAL,

    REWRITEABLE
  }

  public static final SortField FIELD_SCORE = new SortField(null, Type.SCORE);

  public static final SortField FIELD_DOC = new SortField(null, Type.DOC);

  private String field;
  private Type type;  // defaults to determining type dynamically
  boolean reverse = false;  // defaults to natural order

  // Used for CUSTOM sort
  private FieldComparatorSource comparatorSource;

  // Used for 'sortMissingFirst/Last'
  protected Object missingValue = null;

  // For numeric sort fields, if true, indicates the DocValues and Point fields
  // with the same name have the exactly same data indexed.
  // This allows to use them to optimize sort and skip non-competitive documents.
  private boolean canUsePoints = false;

  public SortField(String field, Type type) {
    initFieldType(field, type);
  }

  public SortField(String field, Type type, boolean reverse) {
    initFieldType(field, type);
    this.reverse = reverse;
  }

  public static final class Provider extends SortFieldProvider {

    public static final String NAME = "SortField";

    public Provider() {
      super(NAME);
    }

    @Override
    public SortField readSortField(DataInput in) throws IOException {
      SortField sf = new SortField(in.readString(), readType(in), in.readInt() == 1);
      if (in.readInt() == 1) {
        // missing object
        switch (sf.type) {
          case STRING:
            int missingString = in.readInt();
            if (missingString == 1) {
              sf.setMissingValue(STRING_FIRST);
            }
            else {
              sf.setMissingValue(STRING_LAST);
            }
            break;
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
            throw new IllegalArgumentException("Cannot deserialize sort of type " + sf.type);
        }
      }
      return sf;
    }

    @Override
    public void writeSortField(SortField sf, DataOutput out) throws IOException {
      sf.serialize(out);
    }
  }

  protected static Type readType(DataInput in) throws IOException {
    String type = in.readString();
    try {
      return Type.valueOf(type);
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Can't deserialize SortField - unknown type " + type);
    }
  }

  private void serialize(DataOutput out) throws IOException {
    out.writeString(field);
    out.writeString(type.toString());
    out.writeInt(reverse ? 1 : 0);
    if (missingValue == null) {
      out.writeInt(0);
    }
    else {
      out.writeInt(1);
      switch (type) {
        case STRING:
          if (missingValue == STRING_LAST) {
            out.writeInt(0);
          }
          else if (missingValue == STRING_FIRST) {
            out.writeInt(1);
          }
          else {
            throw new IllegalArgumentException("Cannot serialize missing value of " + missingValue + " for type STRING");
          }
          break;
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
          throw new IllegalArgumentException("Cannot serialize SortField of type " + type);
      }
    }
  }

  public final static Object STRING_FIRST = new Object() {
      @Override
      public String toString() {
        return "SortField.STRING_FIRST";
      }
    };
  
  public final static Object STRING_LAST = new Object() {
      @Override
      public String toString() {
        return "SortField.STRING_LAST";
      }
    };

  public Object getMissingValue() {
    return missingValue;
  }

  public void setMissingValue(Object missingValue) {
    if (type == Type.STRING || type == Type.STRING_VAL) {
      if (missingValue != STRING_FIRST && missingValue != STRING_LAST) {
        throw new IllegalArgumentException("For STRING type, missing value must be either STRING_FIRST or STRING_LAST");
      }
    } else if (type == Type.INT) {
      if (missingValue != null && missingValue.getClass() != Integer.class)
        throw new IllegalArgumentException("Missing values for Type.INT can only be of type java.lang.Integer, but got " + missingValue.getClass());
    } else if (type == Type.LONG) {
      if (missingValue != null && missingValue.getClass() != Long.class)
        throw new IllegalArgumentException("Missing values for Type.LONG can only be of type java.lang.Long, but got " + missingValue.getClass());
    } else if (type == Type.FLOAT) {
      if (missingValue != null && missingValue.getClass() != Float.class)
        throw new IllegalArgumentException("Missing values for Type.FLOAT can only be of type java.lang.Float, but got " + missingValue.getClass());
    } else if (type == Type.DOUBLE) {
      if (missingValue != null && missingValue.getClass() != Double.class)
        throw new IllegalArgumentException("Missing values for Type.DOUBLE can only be of type java.lang.Double, but got " + missingValue.getClass());
    } else {
      throw new IllegalArgumentException("Missing value only works for numeric or STRING types");
    }
    this.missingValue = missingValue;
  }

  public SortField(String field, FieldComparatorSource comparator) {
    initFieldType(field, Type.CUSTOM);
    this.comparatorSource = comparator;
  }

  public SortField(String field, FieldComparatorSource comparator, boolean reverse) {
    initFieldType(field, Type.CUSTOM);
    this.reverse = reverse;
    this.comparatorSource = comparator;
  }

  // Sets field & type, and ensures field is not NULL unless
  // type is SCORE or DOC
  private void initFieldType(String field, Type type) {
    this.type = type;
    if (field == null) {
      if (type != Type.SCORE && type != Type.DOC) {
        throw new IllegalArgumentException("field can only be null when type is SCORE or DOC");
      }
    } else {
      this.field = field;
    }
  }

  public String getField() {
    return field;
  }

  public Type getType() {
    return type;
  }

  public boolean getReverse() {
    return reverse;
  }

  public FieldComparatorSource getComparatorSource() {
    return comparatorSource;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    switch (type) {
      case SCORE:
        buffer.append("<score>");
        break;

      case DOC:
        buffer.append("<doc>");
        break;

      case STRING:
        buffer.append("<string" + ": \"").append(field).append("\">");
        break;

      case STRING_VAL:
        buffer.append("<string_val" + ": \"").append(field).append("\">");
        break;

      case INT:
        buffer.append("<int" + ": \"").append(field).append("\">");
        break;

      case LONG:
        buffer.append("<long: \"").append(field).append("\">");
        break;

      case FLOAT:
        buffer.append("<float" + ": \"").append(field).append("\">");
        break;

      case DOUBLE:
        buffer.append("<double" + ": \"").append(field).append("\">");
        break;

      case CUSTOM:
        buffer.append("<custom:\"").append(field).append("\": ").append(comparatorSource).append('>');
        break;
      
      case REWRITEABLE:
        buffer.append("<rewriteable: \"").append(field).append("\">");
        break;

      default:
        buffer.append("<???: \"").append(field).append("\">");
        break;
    }

    if (reverse) buffer.append('!');
    if (missingValue != null) {
      buffer.append(" missingValue=");
      buffer.append(missingValue);
    }

    return buffer.toString();
  }


  public void setCanUsePoints() {
    this.canUsePoints = true;
  }

  public boolean getCanUsePoints() {
    return canUsePoints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SortField)) return false;
    final SortField other = (SortField)o;
    return (
      Objects.equals(other.field, this.field)
      && other.type == this.type
      && other.reverse == this.reverse
      && Objects.equals(this.comparatorSource, other.comparatorSource)
      && Objects.equals(this.missingValue, other.missingValue)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, type, reverse, comparatorSource, missingValue);
  }

  private Comparator<BytesRef> bytesComparator = Comparator.naturalOrder();

  public void setBytesComparator(Comparator<BytesRef> b) {
    bytesComparator = b;
  }

  public Comparator<BytesRef> getBytesComparator() {
    return bytesComparator;
  }

  public FieldComparator<?> getComparator(final int numHits, final int sortPos) {
    final FieldComparator<?> fieldComparator;
    switch (type) {
      case SCORE:
        fieldComparator = new FieldComparator.RelevanceComparator(numHits);
        break;

      case DOC:
        fieldComparator = new DocComparator(numHits, reverse, sortPos);
        break;

      case INT:
        fieldComparator =
            new IntComparator(numHits, field, (Integer) missingValue, reverse, sortPos);
        break;

      case FLOAT:
        fieldComparator =
            new FloatComparator(numHits, field, (Float) missingValue, reverse, sortPos);
        break;

      case LONG:
        fieldComparator = new LongComparator(numHits, field, (Long) missingValue, reverse, sortPos);
        break;

      case DOUBLE:
        fieldComparator =
            new DoubleComparator(numHits, field, (Double) missingValue, reverse, sortPos);
        break;

      case CUSTOM:
        assert comparatorSource != null;
        fieldComparator = comparatorSource.newComparator(field, numHits, sortPos, reverse);
        break;

      case STRING:
        return new FieldComparator.TermOrdValComparator(
            numHits, field, missingValue == STRING_LAST);

      case STRING_VAL:
        fieldComparator =
            new FieldComparator.TermValComparator(numHits, field, missingValue == STRING_LAST);
        break;

      case REWRITEABLE:
        throw new IllegalStateException(
            "SortField needs to be rewritten through Sort.rewrite(..) and SortField.rewrite(..)");

      default:
        throw new IllegalStateException("Illegal sort type: " + type);
    }
    if (getCanUsePoints() == false) {
      // disable skipping functionality of a numeric comparator if we can't use points
      fieldComparator.disableSkipping();
    }
    return fieldComparator;
  }

  public SortField rewrite(IndexSearcher searcher) throws IOException {
    return this;
  }
  
  public boolean needsScores() {
    return type == Type.SCORE;
  }

  public IndexSorter getIndexSorter() {
    switch (type) {
      case STRING:
        return new IndexSorter.StringSorter(Provider.NAME, missingValue, reverse, reader -> DocValues.getSorted(reader, field));
      case INT:
        return new IndexSorter.IntSorter(Provider.NAME, (Integer)missingValue, reverse, reader -> DocValues.getNumeric(reader, field));
      case LONG:
        return new IndexSorter.LongSorter(Provider.NAME, (Long)missingValue, reverse, reader -> DocValues.getNumeric(reader, field));
      case DOUBLE:
        return new IndexSorter.DoubleSorter(Provider.NAME, (Double)missingValue, reverse, reader -> DocValues.getNumeric(reader, field));
      case FLOAT:
        return new IndexSorter.FloatSorter(Provider.NAME, (Float)missingValue, reverse, reader -> DocValues.getNumeric(reader, field));
      default: return null;
    }
  }

}
