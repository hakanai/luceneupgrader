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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import java.io.Reader;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.NumericTokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeQuery; // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeFilter; // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.SortField; // javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache; // javadocs

public final class NumericField extends AbstractField {


  public static enum DataType { INT, LONG, FLOAT, DOUBLE }

  private transient NumericTokenStream numericTS;
  private DataType type;
  private final int precisionStep;

  public NumericField(String name) {
    this(name, NumericUtils.PRECISION_STEP_DEFAULT, Field.Store.NO, true);
  }
  
  public NumericField(String name, Field.Store store, boolean index) {
    this(name, NumericUtils.PRECISION_STEP_DEFAULT, store, index);
  }
  
  public NumericField(String name, int precisionStep) {
    this(name, precisionStep, Field.Store.NO, true);
  }

  public NumericField(String name, int precisionStep, Field.Store store, boolean index) {
    super(name, store, index ? Field.Index.ANALYZED_NO_NORMS : Field.Index.NO, Field.TermVector.NO);
    if (precisionStep < 1)
      throw new IllegalArgumentException("precisionStep must be >=1");
    this.precisionStep = precisionStep;
    setIndexOptions(IndexOptions.DOCS_ONLY);
  }

  public TokenStream tokenStreamValue()   {
    if (!isIndexed())
      return null;
    if (numericTS == null) {
      // lazy init the TokenStream as it is heavy to instantiate (attributes,...),
      // if not needed (stored field loading)
      numericTS = new NumericTokenStream(precisionStep);
      // initialize value in TokenStream
      if (fieldsData != null) {
        assert type != null;
        final Number val = (Number) fieldsData;
        switch (type) {
          case INT:
            numericTS.setIntValue(val.intValue()); break;
          case LONG:
            numericTS.setLongValue(val.longValue()); break;
          case FLOAT:
            numericTS.setFloatValue(val.floatValue()); break;
          case DOUBLE:
            numericTS.setDoubleValue(val.doubleValue()); break;
          default:
            assert false : "Should never get here";
        }
      }
    }
    return numericTS;
  }
  
  @Override
  public byte[] getBinaryValue(byte[] result){
    return null;
  }

  public Reader readerValue() {
    return null;
  }
    

  public String stringValue()   {
    return (fieldsData == null) ? null : fieldsData.toString();
  }
  
  public Number getNumericValue() {
    return (Number) fieldsData;
  }
  
  public int getPrecisionStep() {
    return precisionStep;
  }
  

  public DataType getDataType() {
    return type;
  }
  
  public NumericField setLongValue(final long value) {
    if (numericTS != null) numericTS.setLongValue(value);
    fieldsData = Long.valueOf(value);
    type = DataType.LONG;
    return this;
  }
  
  public NumericField setIntValue(final int value) {
    if (numericTS != null) numericTS.setIntValue(value);
    fieldsData = Integer.valueOf(value);
    type = DataType.INT;
    return this;
  }
  
  public NumericField setDoubleValue(final double value) {
    if (numericTS != null) numericTS.setDoubleValue(value);
    fieldsData = Double.valueOf(value);
    type = DataType.DOUBLE;
    return this;
  }
  
  public NumericField setFloatValue(final float value) {
    if (numericTS != null) numericTS.setFloatValue(value);
    fieldsData = Float.valueOf(value);
    type = DataType.FLOAT;
    return this;
  }

}
