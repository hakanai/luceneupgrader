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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.DoublePoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.FloatPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.IntPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexSorter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortFieldProvider;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.DocComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.DoubleComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.FloatComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.IntComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.LongComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators.TermOrdValComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.NumericUtils;

/**
 * Stores information about how to sort documents by terms in an individual field. Fields must be
 * indexed in order to sort by them.
 *
 * <p>Sorting on a numeric field that is indexed with both doc values and points may use an
 * optimization to skip non-competitive documents. This optimization relies on the assumption that
 * the same data is stored in these points and doc values.
 *
 * <p>Sorting on a SORTED(_SET) field that is indexed with both doc values and term index may use an
 * optimization to skip non-competitive documents. This optimization relies on the assumption that
 * the same data is stored in these term index and doc values.
 *
 * <p>Created: Feb 11, 2004 1:25:29 PM
 *
 * @since lucene 1.4
 * @see Sort
 */
public class SortField {

  /** Specifies the type of the terms to be sorted, or special types such as CUSTOM */
  public enum Type {

    /**
     * Sort by document score (relevance). Sort values are Float and higher values are at the front.
     */
    SCORE,

    /**
     * Sort by document number (index order). Sort values are Integer and lower values are at the
     * front.
     */
    DOC,

    /**
     * Sort using term values as Strings. Sort values are String and lower values are at the front.
     */
    STRING,

    /**
     * Sort using term values as encoded Integers. Sort values are Integer and lower values are at
     * the front. Fields must either be not indexed, or indexed with {@link IntPoint}.
     */
    INT,

    /**
     * Sort using term values as encoded Floats. Sort values are Float and lower values are at the
     * front. Fields must either be not indexed, or indexed with {@link FloatPoint}.
     */
    FLOAT,

    /**
     * Sort using term values as encoded Longs. Sort values are Long and lower values are at the
     * front. Fields must either be not indexed, or indexed with {@link LongPoint}.
     */
    LONG,

    /**
     * Sort using term values as encoded Doubles. Sort values are Double and lower values are at the
     * front. Fields must either be not indexed, or indexed with {@link DoublePoint}.
     */
    DOUBLE,

    /**
     * Sort using a custom Comparator. Sort values are any Comparable and sorting is done according
     * to natural order.
     */
    CUSTOM,

    /**
     * Sort using term values as Strings, but comparing by value (using String.compareTo) for all
     * comparisons. This is typically slower than {@link #STRING}, which uses ordinals to do the
     * sorting.
     */
    STRING_VAL,

    /**
     * Force rewriting of SortField using {@link SortField#rewrite(IndexSearcher)} before it can be
     * used for sorting
     */
    REWRITEABLE
  }

  /** Represents sorting by document score (relevance). */
  public static final SortField FIELD_SCORE = new SortField(null, Type.SCORE);

  /** Represents sorting by document number (index order). */
  public static final SortField FIELD_DOC = new SortField(null, Type.DOC);

  private String field;
  private Type type; // defaults to determining type dynamically
  boolean reverse = false; // defaults to natural order

  // Used for CUSTOM sort
  private FieldComparatorSource comparatorSource;

  // Used for 'sortMissingFirst/Last'
  protected Object missingValue = null;

  // Indicates if sort should be optimized with indexed data. Set to true by default.
  @Deprecated private boolean optimizeSortWithIndexedData = true;

  /**
   * Creates a sort by terms in the given field with the type of term values explicitly given.
   *
   * @param field Name of field to sort by. Can be <code>null</code> if <code>type</code> is SCORE
   *     or DOC.
   * @param type Type of values in the terms.
   */
  public SortField(String field, Type type) {
    initFieldType(field, type);
  }

  /**
   * Creates a sort, possibly in reverse, by terms in the given field with the type of term values
   * explicitly given.
   *
   * @param field Name of field to sort by. Can be <code>null</code> if <code>type</code> is SCORE
   *     or DOC.
   * @param type Type of values in the terms.
   * @param reverse True if natural order should be reversed.
   */
  public SortField(String field, Type type, boolean reverse) {
    initFieldType(field, type);
    this.reverse = reverse;
  }

  /** A SortFieldProvider for field sorts */
  public static final class Provider extends SortFieldProvider {

    /** The name this Provider is registered under */
    public static final String NAME = "SortField";

    /** Creates a new Provider */
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
            } else {
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
          case CUSTOM:
          case DOC:
          case REWRITEABLE:
          case STRING_VAL:
          case SCORE:
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
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Can't deserialize SortField - unknown type " + type, e);
    }
  }

  private void serialize(DataOutput out) throws IOException {
    out.writeString(field);
    out.writeString(type.toString());
    out.writeInt(reverse ? 1 : 0);
    if (missingValue == null) {
      out.writeInt(0);
    } else {
      out.writeInt(1);
      switch (type) {
        case STRING:
          if (missingValue == STRING_LAST) {
            out.writeInt(0);
          } else if (missingValue == STRING_FIRST) {
            out.writeInt(1);
          } else {
            throw new IllegalArgumentException(
                "Cannot serialize missing value of " + missingValue + " for type STRING");
          }
          break;
        case INT:
          out.writeInt((int) missingValue);
          break;
        case LONG:
          out.writeLong((long) missingValue);
          break;
        case FLOAT:
          out.writeInt(NumericUtils.floatToSortableInt((float) missingValue));
          break;
        case DOUBLE:
          out.writeLong(NumericUtils.doubleToSortableLong((double) missingValue));
          break;
        case CUSTOM:
        case DOC:
        case REWRITEABLE:
        case STRING_VAL:
        case SCORE:
        default:
          throw new IllegalArgumentException("Cannot serialize SortField of type " + type);
      }
    }
  }

  /** Pass this to {@link #setMissingValue} to have missing string values sort first. */
  public static final Object STRING_FIRST =
      new Object() {
        @Override
        public String toString() {
          return "SortField.STRING_FIRST";
        }
      };

  /** Pass this to {@link #setMissingValue} to have missing string values sort last. */
  public static final Object STRING_LAST =
      new Object() {
        @Override
        public String toString() {
          return "SortField.STRING_LAST";
        }
      };

  /**
   * Return the value to use for documents that don't have a value. A value of {@code null}
   * indicates that default should be used.
   */
  public Object getMissingValue() {
    return missingValue;
  }

  /** Set the value to use for documents that don't have a value. */
  public void setMissingValue(Object missingValue) {
    if (type == Type.STRING || type == Type.STRING_VAL) {
      if (missingValue != STRING_FIRST && missingValue != STRING_LAST) {
        throw new IllegalArgumentException(
            "For STRING type, missing value must be either STRING_FIRST or STRING_LAST");
      }
    } else if (type == Type.INT) {
      if (missingValue != null && missingValue.getClass() != Integer.class)
        throw new IllegalArgumentException(
            "Missing values for Type.INT can only be of type java.lang.Integer, but got "
                + missingValue.getClass());
    } else if (type == Type.LONG) {
      if (missingValue != null && missingValue.getClass() != Long.class)
        throw new IllegalArgumentException(
            "Missing values for Type.LONG can only be of type java.lang.Long, but got "
                + missingValue.getClass());
    } else if (type == Type.FLOAT) {
      if (missingValue != null && missingValue.getClass() != Float.class)
        throw new IllegalArgumentException(
            "Missing values for Type.FLOAT can only be of type java.lang.Float, but got "
                + missingValue.getClass());
    } else if (type == Type.DOUBLE) {
      if (missingValue != null && missingValue.getClass() != Double.class)
        throw new IllegalArgumentException(
            "Missing values for Type.DOUBLE can only be of type java.lang.Double, but got "
                + missingValue.getClass());
    } else {
      throw new IllegalArgumentException("Missing value only works for numeric or STRING types");
    }
    this.missingValue = missingValue;
  }

  /**
   * Creates a sort with a custom comparison function.
   *
   * @param field Name of field to sort by; cannot be <code>null</code>.
   * @param comparator Returns a comparator for sorting hits.
   */
  public SortField(String field, FieldComparatorSource comparator) {
    initFieldType(field, Type.CUSTOM);
    this.comparatorSource = comparator;
  }

  /**
   * Creates a sort, possibly in reverse, with a custom comparison function.
   *
   * @param field Name of field to sort by; cannot be <code>null</code>.
   * @param comparator Returns a comparator for sorting hits.
   * @param reverse True if natural order should be reversed.
   */
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

  /**
   * Returns the name of the field. Could return <code>null</code> if the sort is by SCORE or DOC.
   *
   * @return Name of field, possibly <code>null</code>.
   */
  public String getField() {
    return field;
  }

  /**
   * Returns the type of contents in the field.
   *
   * @return One of the constants SCORE, DOC, STRING, INT or FLOAT.
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns whether the sort should be reversed.
   *
   * @return True if natural order should be reversed.
   */
  public boolean getReverse() {
    return reverse;
  }

  /** Returns the {@link FieldComparatorSource} used for custom sorting */
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
        buffer
            .append("<custom:\"")
            .append(field)
            .append("\": ")
            .append(comparatorSource)
            .append('>');
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

  /**
   * Returns true if <code>o</code> is equal to this. If a {@link FieldComparatorSource} was
   * provided, it must properly implement equals (unless a singleton is always used).
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SortField)) return false;
    final SortField other = (SortField) o;
    return (Objects.equals(other.field, this.field)
        && other.type == this.type
        && other.reverse == this.reverse
        && Objects.equals(this.comparatorSource, other.comparatorSource)
        && Objects.equals(this.missingValue, other.missingValue));
  }

  /**
   * Returns a hash code for this {@link SortField} instance. If a {@link FieldComparatorSource} was
   * provided, it must properly implement hashCode (unless a singleton is always used).
   */
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

  /**
   * Returns the {@link FieldComparator} to use for sorting.
   *
   * @lucene.experimental
   * @param numHits number of top hits the queue will store
   * @param pruning controls how can the comparator to skip documents via {@link
   *     LeafFieldComparator#competitiveIterator()}
   * @return {@link FieldComparator} to use when sorting
   */
  public FieldComparator<?> getComparator(final int numHits, Pruning pruning) {
    final FieldComparator<?> fieldComparator;
    switch (type) {
      case SCORE:
        fieldComparator = new FieldComparator.RelevanceComparator(numHits);
        break;

      case DOC:
        fieldComparator = new DocComparator(numHits, reverse, pruning);
        break;

      case INT:
        fieldComparator =
            new IntComparator(numHits, field, (Integer) missingValue, reverse, pruning);
        break;

      case FLOAT:
        fieldComparator =
            new FloatComparator(numHits, field, (Float) missingValue, reverse, pruning);
        break;

      case LONG:
        fieldComparator = new LongComparator(numHits, field, (Long) missingValue, reverse, pruning);
        break;

      case DOUBLE:
        fieldComparator =
            new DoubleComparator(numHits, field, (Double) missingValue, reverse, pruning);
        break;

      case CUSTOM:
        assert comparatorSource != null;
        fieldComparator = comparatorSource.newComparator(field, numHits, pruning, reverse);
        break;

      case STRING:
        fieldComparator =
            new TermOrdValComparator(numHits, field, missingValue == STRING_LAST, reverse, pruning);
        break;

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
    if (getOptimizeSortWithIndexedData() == false) {
      fieldComparator.disableSkipping();
    }
    return fieldComparator;
  }

  /**
   * Rewrites this SortField, returning a new SortField if a change is made. Subclasses should
   * override this define their rewriting behavior when this SortField is of type {@link
   * SortField.Type#REWRITEABLE}
   *
   * @param searcher IndexSearcher to use during rewriting
   * @return New rewritten SortField, or {@code this} if nothing has changed.
   * @throws IOException Can be thrown by the rewriting
   * @lucene.experimental
   */
  public SortField rewrite(IndexSearcher searcher) throws IOException {
    return this;
  }

  /** Whether the relevance score is needed to sort documents. */
  public boolean needsScores() {
    return type == Type.SCORE;
  }

  /**
   * Returns an {@link IndexSorter} used for sorting index segments by this SortField.
   *
   * <p>If the SortField cannot be used for index sorting (for example, if it uses scores or other
   * query-dependent values) then this method should return {@code null}
   *
   * <p>SortFields that implement this method should also implement a companion {@link
   * SortFieldProvider} to serialize and deserialize the sort in index segment headers
   *
   * @lucene.experimental
   */
  public IndexSorter getIndexSorter() {
    switch (type) {
      case STRING:
        return new IndexSorter.StringSorter(
            Provider.NAME, missingValue, reverse, reader -> DocValues.getSorted(reader, field));
      case INT:
        return new IndexSorter.IntSorter(
            Provider.NAME,
            (Integer) missingValue,
            reverse,
            reader -> DocValues.getNumeric(reader, field));
      case LONG:
        return new IndexSorter.LongSorter(
            Provider.NAME,
            (Long) missingValue,
            reverse,
            reader -> DocValues.getNumeric(reader, field));
      case DOUBLE:
        return new IndexSorter.DoubleSorter(
            Provider.NAME,
            (Double) missingValue,
            reverse,
            reader -> DocValues.getNumeric(reader, field));
      case FLOAT:
        return new IndexSorter.FloatSorter(
            Provider.NAME,
            (Float) missingValue,
            reverse,
            reader -> DocValues.getNumeric(reader, field));
      case CUSTOM:
      case DOC:
      case REWRITEABLE:
      case STRING_VAL:
      case SCORE:
      default:
        return null;
    }
  }

  /**
   * Enables/disables numeric sort optimization to use the indexed data.
   *
   * <p>Enabled by default. By default, sorting on a numeric field activates point sort optimization
   * that can efficiently skip over non-competitive hits. Sort optimization has a number of
   * requirements, one of which is that SortField.Type matches the Point type with which the field
   * was indexed (e.g. sort on IntPoint field should use SortField.Type.INT). Another requirement is
   * that the same data is indexed with points and doc values for the field.
   *
   * <p>By default, sorting on a SORTED(_SET) field activates sort optimization that can efficiently
   * skip over non-competitive hits. Sort optimization requires that the same data is indexed with
   * term index and doc values for the field.
   *
   * @param optimizeSortWithIndexedData providing {@code false} disables the optimization, in cases
   *     where these requirements can't be met.
   * @deprecated should only be used for compatibility with 8.x indices that got created with
   *     inconsistent data across fields, or the wrong sort configuration in the index sort
   */
  @Deprecated // Remove in Lucene 10
  public void setOptimizeSortWithIndexedData(boolean optimizeSortWithIndexedData) {
    this.optimizeSortWithIndexedData = optimizeSortWithIndexedData;
  }

  /**
   * Returns whether sort optimization should be optimized with indexed data
   *
   * @return whether sort optimization should be optimized with indexed data
   */
  @Deprecated // Remove in Lucene 10
  public boolean getOptimizeSortWithIndexedData() {
    return optimizeSortWithIndexedData;
  }

  /**
   * Enables/disables numeric sort optimization to use the Points index.
   *
   * <p>Enabled by default. By default, sorting on a numeric field activates point sort optimization
   * that can efficiently skip over non-competitive hits. Sort optimization has a number of
   * requirements, one of which is that SortField.Type matches the Point type with which the field
   * was indexed (e.g. sort on IntPoint field should use SortField.Type.INT). Another requirement is
   * that the same data is indexed with points and doc values for the field.
   *
   * @param optimizeSortWithPoints providing {@code false} disables the optimization, in cases where
   *     these requirements can't be met.
   * @deprecated should only be used for compatibility with 8.x indices that got created with
   *     inconsistent data across fields, or the wrong sort configuration in the index sort. This is
   *     a duplicate method for {@code SortField#setOptimizeSortWithIndexedData}.
   */
  @Deprecated // Remove in Lucene 10
  public void setOptimizeSortWithPoints(boolean optimizeSortWithPoints) {
    setOptimizeSortWithIndexedData(optimizeSortWithPoints);
  }

  /**
   * Returns whether sort optimization should be optimized with points index
   *
   * @return whether sort optimization should be optimized with points index
   * @deprecated This is a duplicate method for {@code SortField#getOptimizeSortWithIndexedData}.
   */
  @Deprecated // Remove in Lucene 10
  public boolean getOptimizeSortWithPoints() {
    return getOptimizeSortWithIndexedData();
  }
}
