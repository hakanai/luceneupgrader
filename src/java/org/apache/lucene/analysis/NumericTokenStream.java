package org.apache.lucene.analysis;

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

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.NumericUtils;

/**
 * <b>Expert:</b> This class provides a {@code TokenStream}
 * for indexing numeric values that can be used by {@code
 * NumericRangeQuery} or {@code NumericRangeFilter}.
 *
 * <p>Note that for simple usage, {@code NumericField} is
 * recommended.  {@code NumericField} disables norms and
 * term freqs, as they are not usually needed during
 * searching.  If you need to change these settings, you
 * should use this class.
 *
 * <p>See {@code NumericField} for capabilities of fields
 * indexed numerically.</p>
 *
 * <p>Here's an example usage, for an <code>int</code> field:
 *
 * <pre>
 *  Field field = new Field(name, new NumericTokenStream(precisionStep).setIntValue(value));
 *  field.setOmitNorms(true);
 *  field.setIndexOptions(IndexOptions.DOCS_ONLY);
 *  document.add(field);
 * </pre>
 *
 * <p>For optimal performance, re-use the TokenStream and Field instance
 * for more than one document:
 *
 * <pre>
 *  NumericTokenStream stream = new NumericTokenStream(precisionStep);
 *  Field field = new Field(name, stream);
 *  field.setOmitNorms(true);
 *  field.setIndexOptions(IndexOptions.DOCS_ONLY);
 *  Document document = new Document();
 *  document.add(field);
 *
 *  for(all documents) {
 *    stream.setIntValue(value)
 *    writer.addDocument(document);
 *  }
 * </pre>
 *
 * <p>This stream is not intended to be used in analyzers;
 * it's more for iterating the different precisions during
 * indexing a specific numeric value.</p>

 * <p><b>NOTE</b>: as token streams are only consumed once
 * the document is added to the index, if you index more
 * than one numeric field, use a separate <code>NumericTokenStream</code>
 * instance for each.</p>
 *
 * <p>See {@code NumericRangeQuery} for more details on the
 * <a
 * href="../search/NumericRangeQuery.html#precisionStepDesc"><code>precisionStep</code></a>
 * parameter as well as how numeric fields work under the hood.</p>
 *
 * @since 2.9
 */
public final class NumericTokenStream extends TokenStream {

  /** The full precision token gets this token type assigned. */
  public static final String TOKEN_TYPE_FULL_PREC  = "fullPrecNumeric";

  /** The lower precision tokens gets this token type assigned. */
  public static final String TOKEN_TYPE_LOWER_PREC = "lowerPrecNumeric";

  /**
   * Creates a token stream for numeric values with the specified
   * <code>precisionStep</code>. The stream is not yet initialized,
   * before using set a value using the various set<em>???</em>Value() methods.
   */
  public NumericTokenStream(final int precisionStep) {
    super();
    this.precisionStep = precisionStep;
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
  }

  /**
   * Initializes the token stream with the supplied <code>long</code> value.
   * @param value the value, for which this TokenStream should enumerate tokens.
   * @return this instance, because of this you can use it the following way:
   * <code>new Field(name, new NumericTokenStream(precisionStep).setLongValue(value))</code>
   */
  public NumericTokenStream setLongValue(final long value) {
    this.value = value;
    valSize = 64;
    shift = 0;
    return this;
  }
  
  /**
   * Initializes the token stream with the supplied <code>int</code> value.
   * @param value the value, for which this TokenStream should enumerate tokens.
   * @return this instance, because of this you can use it the following way:
   * <code>new Field(name, new NumericTokenStream(precisionStep).setIntValue(value))</code>
   */
  public NumericTokenStream setIntValue(final int value) {
    this.value = value;
    valSize = 32;
    shift = 0;
    return this;
  }
  
  /**
   * Initializes the token stream with the supplied <code>double</code> value.
   * @param value the value, for which this TokenStream should enumerate tokens.
   * @return this instance, because of this you can use it the following way:
   * <code>new Field(name, new NumericTokenStream(precisionStep).setDoubleValue(value))</code>
   */
  public NumericTokenStream setDoubleValue(final double value) {
    this.value = NumericUtils.doubleToSortableLong(value);
    valSize = 64;
    shift = 0;
    return this;
  }
  
  /**
   * Initializes the token stream with the supplied <code>float</code> value.
   * @param value the value, for which this TokenStream should enumerate tokens.
   * @return this instance, because of this you can use it the following way:
   * <code>new Field(name, new NumericTokenStream(precisionStep).setFloatValue(value))</code>
   */
  public NumericTokenStream setFloatValue(final float value) {
    this.value = NumericUtils.floatToSortableInt(value);
    valSize = 32;
    shift = 0;
    return this;
  }
  
  @Override
  public void reset() {
    if (valSize == 0)
      throw new IllegalStateException("call set???Value() before usage");
    shift = 0;
  }

  @Override
  public String toString() {
    return "(numeric,valSize=" + valSize + ",precisionStep=" + precisionStep + ')';
  }

  // members
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  
  private int shift = 0, valSize = 0; // valSize==0 means not initialized
  private final int precisionStep;
  
  private long value = 0L;
}
