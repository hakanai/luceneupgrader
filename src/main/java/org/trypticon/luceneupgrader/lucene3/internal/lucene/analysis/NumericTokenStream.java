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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.NumericField; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeQuery; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeFilter; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.TypeAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;


public final class NumericTokenStream extends TokenStream {

  public static final String TOKEN_TYPE_FULL_PREC  = "fullPrecNumeric";

  public static final String TOKEN_TYPE_LOWER_PREC = "lowerPrecNumeric";


  public NumericTokenStream() {
    this(NumericUtils.PRECISION_STEP_DEFAULT);
  }
  

  public NumericTokenStream(final int precisionStep) {
    super();
    this.precisionStep = precisionStep;
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
  }


  public NumericTokenStream(AttributeSource source, final int precisionStep) {
    super(source);
    this.precisionStep = precisionStep;
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
  }


  public NumericTokenStream(AttributeFactory factory, final int precisionStep) {
    super(factory);
    this.precisionStep = precisionStep;
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
  }


  public NumericTokenStream setLongValue(final long value) {
    this.value = value;
    valSize = 64;
    shift = 0;
    return this;
  }
  

  public NumericTokenStream setIntValue(final int value) {
    this.value = value;
    valSize = 32;
    shift = 0;
    return this;
  }
  

  public NumericTokenStream setDoubleValue(final double value) {
    this.value = NumericUtils.doubleToSortableLong(value);
    valSize = 64;
    shift = 0;
    return this;
  }
  

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
  public boolean incrementToken() {
    if (valSize == 0)
      throw new IllegalStateException("call set???Value() before usage");
    if (shift >= valSize)
      return false;

    clearAttributes();
    final char[] buffer;
    switch (valSize) {
      case 64:
        buffer = termAtt.resizeBuffer(NumericUtils.BUF_SIZE_LONG);
        termAtt.setLength(NumericUtils.longToPrefixCoded(value, shift, buffer));
        break;
      
      case 32:
        buffer = termAtt.resizeBuffer(NumericUtils.BUF_SIZE_INT);
        termAtt.setLength(NumericUtils.intToPrefixCoded((int) value, shift, buffer));
        break;
      
      default:
        // should not happen
        throw new IllegalArgumentException("valSize must be 32 or 64");
    }
    
    typeAtt.setType((shift == 0) ? TOKEN_TYPE_FULL_PREC : TOKEN_TYPE_LOWER_PREC);
    posIncrAtt.setPositionIncrement((shift == 0) ? 1 : 0);
    shift += precisionStep;
    return true;
  }
  
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("(numeric,valSize=").append(valSize);
    sb.append(",precisionStep=").append(precisionStep).append(')');
    return sb.toString();
  }

  public int getPrecisionStep() {
    return precisionStep;
  }
  
  // members
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  
  private int shift = 0, valSize = 0; // valSize==0 means not initialized
  private final int precisionStep;
  
  private long value = 0L;
}
