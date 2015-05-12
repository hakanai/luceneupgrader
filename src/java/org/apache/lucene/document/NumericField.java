package org.apache.lucene.document;

/**
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

import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.util.NumericUtils;

/**
 * <p>This class provides a {@code Field} that enables indexing
 * of numeric values for efficient range filtering and
 * sorting.  Here's an example usage, adding an int value:
 * <pre>
 *  document.add(new NumericField(name).setIntValue(value));
 * </pre>
 *
 * For optimal performance, re-use the
 * <code>NumericField</code> and {@code Document} instance for more than
 * one document:
 *
 * <pre>
 *  NumericField field = new NumericField(name);
 *  Document document = new Document();
 *  document.add(field);
 *
 *  for(all documents) {
 *    ...
 *    field.setIntValue(value)
 *    writer.addDocument(document);
 *    ...
 *  }
 * </pre>
 *
 * <p>The java native types <code>int</code>, <code>long</code>,
 * <code>float</code> and <code>double</code> are
 * directly supported.  However, any value that can be
 * converted into these native types can also be indexed.
 * For example, date/time values represented by a
 * {@code java.util.Date} can be translated into a long
 * value using the {@code java.util.Date#getTime} method.  If you
 * don't need millisecond precision, you can quantize the
 * value, either by dividing the result of
 * {@code java.util.Date#getTime} or using the separate getters
 * (for year, month, etc.) to construct an <code>int</code> or
 * <code>long</code> value.</p>
 *
 * <p>To perform range querying or filtering against a
 * <code>NumericField</code>, use {@code NumericRangeQuery} or {@code
 * NumericRangeFilter}.  To sort according to a
 * <code>NumericField</code>, use the normal numeric sort types, eg
 * {@code SortField#INT}. <code>NumericField</code> values
 * can also be loaded directly from {@code FieldCache}.</p>
 *
 * <p>By default, a <code>NumericField</code>'s value is not stored but
 * is indexed for range filtering and sorting.  You can use
 * the {@code #NumericField(String,Field.Store,boolean)}
 * constructor if you need to change these defaults.</p>
 *
 * <p>You may add the same field name as a <code>NumericField</code> to
 * the same document more than once.  Range querying and
 * filtering will be the logical OR of all values; so a range query
 * will hit all documents that have at least one value in
 * the range. However sort behavior is not defined.  If you need to sort,
 * you should separately index a single-valued <code>NumericField</code>.</p>
 *
 * <p>A <code>NumericField</code> will consume somewhat more disk space
 * in the index than an ordinary single-valued field.
 * However, for a typical index that includes substantial
 * textual content per document, this increase will likely
 * be in the noise. </p>
 *
 * <p>Within Lucene, each numeric value is indexed as a
 * <em>trie</em> structure, where each term is logically
 * assigned to larger and larger pre-defined brackets (which
 * are simply lower-precision representations of the value).
 * The step size between each successive bracket is called the
 * <code>precisionStep</code>, measured in bits.  Smaller
 * <code>precisionStep</code> values result in larger number
 * of brackets, which consumes more disk space in the index
 * but may result in faster range search performance.  The
 * default value, 4, was selected for a reasonable tradeoff
 * of disk space consumption versus performance.  You can
 * use the expert constructor {@code
 * #NumericField(String,int,Field.Store,boolean)} if you'd
 * like to change the value.  Note that you must also
 * specify a congruent value when creating {@code
 * NumericRangeQuery} or {@code NumericRangeFilter}.
 * For low cardinality fields larger precision steps are good.
 * If the cardinality is &lt; 100, it is fair
 * to use {@code Integer#MAX_VALUE}, which produces one
 * term per value.
 *
 * <p>For more information on the internals of numeric trie
 * indexing, including the <a
 * href="../search/NumericRangeQuery.html#precisionStepDesc"><code>precisionStep</code></a>
 * configuration, see {@code NumericRangeQuery}. The format of
 * indexed values is described in {@code NumericUtils}.
 *
 * <p>If you only need to sort by numeric value, and never
 * run range querying/filtering, you can index using a
 * <code>precisionStep</code> of {@code Integer#MAX_VALUE}.
 * This will minimize disk space consumed. </p>
 *
 * <p>More advanced users can instead use {@code
 * NumericTokenStream} directly, when indexing numbers. This
 * class is a wrapper around this token stream type for
 * easier, more intuitive usage.</p>
 *
 * @since 2.9
 */
public final class NumericField extends AbstractField {

  /** Data type of the value in {@code NumericField}.
   * @since 3.2
   */
  public enum DataType { INT, LONG, FLOAT, DOUBLE }

  private DataType type;

  /**
   * Creates a field for numeric values using the default <code>precisionStep</code>
   * {@code NumericUtils#PRECISION_STEP_DEFAULT} (4). The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * @param name the field name
   * @param store if the field should be stored, {@code Document#getFieldable}
   * then returns {@code NumericField} instances on search results.
   * @param index if the field should be indexed using {@code NumericTokenStream}
   */
  public NumericField(String name, Field.Store store, boolean index) {
    this(name, NumericUtils.PRECISION_STEP_DEFAULT, store, index);
  }

  /**
   * Creates a field for numeric values with the specified
   * <code>precisionStep</code>. The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * @param name the field name
   * @param precisionStep the used <a href="../search/NumericRangeQuery.html#precisionStepDesc">precision step</a>
   * @param store if the field should be stored, {@code Document#getFieldable}
   * then returns {@code NumericField} instances on search results.
   * @param index if the field should be indexed using {@code NumericTokenStream}
   */
  public NumericField(String name, int precisionStep, Field.Store store, boolean index) {
    super(name, store, index ? Field.Index.ANALYZED_NO_NORMS : Field.Index.NO, Field.TermVector.NO);
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
    setIndexOptions(IndexOptions.DOCS_ONLY);
  }

  /** Returns always <code>null</code> for numeric fields */
  @Override
  public byte[] getBinaryValue(byte[] result){
    return null;
  }

  /** Returns the numeric value as a string. This format is also returned if you call {@code Document#get(String)}
   * on search results. It is recommended to use {@code Document#getFieldable} instead
   * that returns {@code NumericField} instances. You can then use {@code #getNumericValue}
   * to return the stored value. */
  public String stringValue()   {
    return (fieldsData == null) ? null : fieldsData.toString();
  }
  
  /** Returns the current numeric value as a subclass of {@code Number}, <code>null</code> if not yet initialized. */
  public Number getNumericValue() {
    return (Number) fieldsData;
  }

  /** Returns the data type of the current value, {@code null} if not yet set.
   * @since 3.2
   */
  public DataType getDataType() {
    return type;
  }
  
  /**
   * Initializes the field with the supplied <code>long</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setLongValue(value))</code>
   */
  public NumericField setLongValue(final long value) {
    fieldsData = value;
    type = DataType.LONG;
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>int</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setIntValue(value))</code>
   */
  public NumericField setIntValue(final int value) {
    fieldsData = value;
    type = DataType.INT;
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>double</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setDoubleValue(value))</code>
   */
  public NumericField setDoubleValue(final double value) {
    fieldsData = value;
    type = DataType.DOUBLE;
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>float</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setFloatValue(value))</code>
   */
  public NumericField setFloatValue(final float value) {
    fieldsData = value;
    type = DataType.FLOAT;
    return this;
  }

}
