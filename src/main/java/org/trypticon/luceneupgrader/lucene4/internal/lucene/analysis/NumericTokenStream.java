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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.TypeAttribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.DoubleField; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.FloatField; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.IntField; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.LongField; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeFilter; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Attribute;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeFactory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.AttributeReflector;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRefBuilder;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;

public final class NumericTokenStream extends TokenStream {

  public static final String TOKEN_TYPE_FULL_PREC  = "fullPrecNumeric";

  public static final String TOKEN_TYPE_LOWER_PREC = "lowerPrecNumeric";
  

  public interface NumericTermAttribute extends Attribute {
    int getShift();
    long getRawValue();
    int getValueSize();
    
    void init(long value, int valSize, int precisionStep, int shift);

    void setShift(int shift);

    int incShift();
  }
  
  // just a wrapper to prevent adding CTA
  private static final class NumericAttributeFactory extends AttributeFactory {
    private final AttributeFactory delegate;

    NumericAttributeFactory(AttributeFactory delegate) {
      this.delegate = delegate;
    }
  
    @Override
    public AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
      if (CharTermAttribute.class.isAssignableFrom(attClass))
        throw new IllegalArgumentException("NumericTokenStream does not support CharTermAttribute.");
      return delegate.createAttributeInstance(attClass);
    }
  }


  public static final class NumericTermAttributeImpl extends AttributeImpl implements NumericTermAttribute,TermToBytesRefAttribute {
    private long value = 0L;
    private int valueSize = 0, shift = 0, precisionStep = 0;
    private BytesRefBuilder bytes = new BytesRefBuilder();
    
    public NumericTermAttributeImpl() {}

    @Override
    public BytesRef getBytesRef() {
      return bytes.get();
    }
    
    @Override
    public void fillBytesRef() {
      assert valueSize == 64 || valueSize == 32;
      if (valueSize == 64) {
        NumericUtils.longToPrefixCoded(value, shift, bytes);
      } else {
        NumericUtils.intToPrefixCoded((int) value, shift, bytes);
      }
    }

    @Override
    public int getShift() { return shift; }
    @Override
    public void setShift(int shift) { this.shift = shift; }
    @Override
    public int incShift() {
      return (shift += precisionStep);
    }

    @Override
    public long getRawValue() { return value  & ~((1L << shift) - 1L); }
    @Override
    public int getValueSize() { return valueSize; }

    @Override
    public void init(long value, int valueSize, int precisionStep, int shift) {
      this.value = value;
      this.valueSize = valueSize;
      this.precisionStep = precisionStep;
      this.shift = shift;
    }

    @Override
    public void clear() {
      // this attribute has no contents to clear!
      // we keep it untouched as it's fully controlled by outer class.
    }
    
    @Override
    public void reflectWith(AttributeReflector reflector) {
      fillBytesRef();
      reflector.reflect(TermToBytesRefAttribute.class, "bytes", bytes.toBytesRef());
      reflector.reflect(NumericTermAttribute.class, "shift", shift);
      reflector.reflect(NumericTermAttribute.class, "rawValue", getRawValue());
      reflector.reflect(NumericTermAttribute.class, "valueSize", valueSize);
    }
  
    @Override
    public void copyTo(AttributeImpl target) {
      final NumericTermAttribute a = (NumericTermAttribute) target;
      a.init(value, valueSize, precisionStep, shift);
    }
  }
  
  public NumericTokenStream() {
    this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, NumericUtils.PRECISION_STEP_DEFAULT);
  }
  
  public NumericTokenStream(final int precisionStep) {
    this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, precisionStep);
  }

  public NumericTokenStream(AttributeFactory factory, final int precisionStep) {
    super(new NumericAttributeFactory(factory));
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
    this.precisionStep = precisionStep;
    numericAtt.setShift(-precisionStep);
  }

  public NumericTokenStream setLongValue(final long value) {
    numericAtt.init(value, valSize = 64, precisionStep, -precisionStep);
    return this;
  }
  
  public NumericTokenStream setIntValue(final int value) {
    numericAtt.init(value, valSize = 32, precisionStep, -precisionStep);
    return this;
  }
  
  public NumericTokenStream setDoubleValue(final double value) {
    numericAtt.init(NumericUtils.doubleToSortableLong(value), valSize = 64, precisionStep, -precisionStep);
    return this;
  }
  
  public NumericTokenStream setFloatValue(final float value) {
    numericAtt.init(NumericUtils.floatToSortableInt(value), valSize = 32, precisionStep, -precisionStep);
    return this;
  }
  
  @Override
  public void reset() {
    if (valSize == 0)
      throw new IllegalStateException("call set???Value() before usage");
    numericAtt.setShift(-precisionStep);
  }

  @Override
  public boolean incrementToken() {
    if (valSize == 0)
      throw new IllegalStateException("call set???Value() before usage");
    
    // this will only clear all other attributes in this TokenStream
    clearAttributes();

    final int shift = numericAtt.incShift();
    typeAtt.setType((shift == 0) ? TOKEN_TYPE_FULL_PREC : TOKEN_TYPE_LOWER_PREC);
    posIncrAtt.setPositionIncrement((shift == 0) ? 1 : 0);
    return (shift < valSize);
  }

  public int getPrecisionStep() {
    return precisionStep;
  }
  
  // members
  private final NumericTermAttribute numericAtt = addAttribute(NumericTermAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  
  private int valSize = 0; // valSize==0 means not initialized
  private final int precisionStep;
}
