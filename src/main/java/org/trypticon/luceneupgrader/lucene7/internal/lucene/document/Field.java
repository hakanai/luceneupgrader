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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;


import java.io.IOException;
import java.io.Reader;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.BytesTermAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexableField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexableFieldType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

public class Field implements IndexableField {

  protected final IndexableFieldType type;

  protected final String name;

  protected Object fieldsData;

  protected TokenStream tokenStream;

  protected Field(String name, IndexableFieldType type) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    this.name = name;
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.type = type;
  }

  public Field(String name, Reader reader, IndexableFieldType type) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (reader == null) {
      throw new NullPointerException("reader must not be null");
    }
    if (type.stored()) {
      throw new IllegalArgumentException("fields with a Reader value cannot be stored");
    }
    if (type.indexOptions() != IndexOptions.NONE && !type.tokenized()) {
      throw new IllegalArgumentException("non-tokenized fields must use String values");
    }
    
    this.name = name;
    this.fieldsData = reader;
    this.type = type;
  }

  public Field(String name, TokenStream tokenStream, IndexableFieldType type) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    if (tokenStream == null) {
      throw new NullPointerException("tokenStream must not be null");
    }
    if (type.indexOptions() == IndexOptions.NONE || !type.tokenized()) {
      throw new IllegalArgumentException("TokenStream fields must be indexed and tokenized");
    }
    if (type.stored()) {
      throw new IllegalArgumentException("TokenStream fields cannot be stored");
    }
    
    this.name = name;
    this.fieldsData = null;
    this.tokenStream = tokenStream;
    this.type = type;
  }
  
  public Field(String name, byte[] value, IndexableFieldType type) {
    this(name, value, 0, value.length, type);
  }

  public Field(String name, byte[] value, int offset, int length, IndexableFieldType type) {
    this(name, value != null ? new BytesRef(value, offset, length) : null, type);
  }

  public Field(String name, BytesRef bytes, IndexableFieldType type) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    if (bytes == null) {
      throw new IllegalArgumentException("bytes must not be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.name = name;
    this.fieldsData = bytes;
    this.type = type;
  }

  // TODO: allow direct construction of int, long, float, double value too..?

  public Field(String name, CharSequence value, IndexableFieldType type) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (!type.stored() && type.indexOptions() == IndexOptions.NONE) {
      throw new IllegalArgumentException("it doesn't make sense to have a field that "
        + "is neither indexed nor stored");
    }
    this.name = name;
    this.fieldsData = value;
    this.type = type;
  }

  @Override
  public String stringValue() {
    if (fieldsData instanceof CharSequence || fieldsData instanceof Number) {
      return fieldsData.toString();
    } else {
      return null;
    }
  }

  @Override
  public CharSequence getCharSequenceValue() {
    return fieldsData instanceof CharSequence ?
        (CharSequence) fieldsData : stringValue();
  }

  @Override
  public Reader readerValue() {
    return fieldsData instanceof Reader ? (Reader) fieldsData : null;
  }
  
  public TokenStream tokenStreamValue() {
    return tokenStream;
  }
  
  public void setStringValue(String value) {
    if (!(fieldsData instanceof String)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to String");
    }
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    fieldsData = value;
  }
  
  public void setReaderValue(Reader value) {
    if (!(fieldsData instanceof Reader)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Reader");
    }
    fieldsData = value;
  }
  
  public void setBytesValue(byte[] value) {
    setBytesValue(new BytesRef(value));
  }

  public void setBytesValue(BytesRef value) {
    if (!(fieldsData instanceof BytesRef)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to BytesRef");
    }
    if (type.indexOptions() != IndexOptions.NONE) {
      throw new IllegalArgumentException("cannot set a BytesRef value on an indexed field");
    }
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    fieldsData = value;
  }

  public void setByteValue(byte value) {
    if (!(fieldsData instanceof Byte)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Byte");
    }
    fieldsData = Byte.valueOf(value);
  }

  public void setShortValue(short value) {
    if (!(fieldsData instanceof Short)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Short");
    }
    fieldsData = Short.valueOf(value);
  }

  public void setIntValue(int value) {
    if (!(fieldsData instanceof Integer)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Integer");
    }
    fieldsData = Integer.valueOf(value);
  }

  public void setLongValue(long value) {
    if (!(fieldsData instanceof Long)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Long");
    }
    fieldsData = Long.valueOf(value);
  }

  public void setFloatValue(float value) {
    if (!(fieldsData instanceof Float)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Float");
    }
    fieldsData = Float.valueOf(value);
  }

  public void setDoubleValue(double value) {
    if (!(fieldsData instanceof Double)) {
      throw new IllegalArgumentException("cannot change value type from " + fieldsData.getClass().getSimpleName() + " to Double");
    }
    fieldsData = Double.valueOf(value);
  }

  public void setTokenStream(TokenStream tokenStream) {
    if (type.indexOptions() == IndexOptions.NONE || !type.tokenized()) {
      throw new IllegalArgumentException("TokenStream fields must be indexed and tokenized");
    }
    this.tokenStream = tokenStream;
  }
  
  @Override
  public String name() {
    return name;
  }

  @Override
  public Number numericValue() {
    if (fieldsData instanceof Number) {
      return (Number) fieldsData;
    } else {
      return null;
    }
  }

  @Override
  public BytesRef binaryValue() {
    if (fieldsData instanceof BytesRef) {
      return (BytesRef) fieldsData;
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(type.toString());
    result.append('<');
    result.append(name);
    result.append(':');

    if (fieldsData != null) {
      result.append(fieldsData);
    }

    result.append('>');
    return result.toString();
  }
  
  @Override
  public IndexableFieldType fieldType() {
    return type;
  }

  @Override
  public TokenStream tokenStream(Analyzer analyzer, TokenStream reuse) {
    if (fieldType().indexOptions() == IndexOptions.NONE) {
      // Not indexed
      return null;
    }

    if (!fieldType().tokenized()) {
      if (stringValue() != null) {
        if (!(reuse instanceof StringTokenStream)) {
          // lazy init the TokenStream as it is heavy to instantiate
          // (attributes,...) if not needed
          reuse = new StringTokenStream();
        }
        ((StringTokenStream) reuse).setValue(stringValue());
        return reuse;
      } else if (binaryValue() != null) {
        if (!(reuse instanceof BinaryTokenStream)) {
          // lazy init the TokenStream as it is heavy to instantiate
          // (attributes,...) if not needed
          reuse = new BinaryTokenStream();
        }
        ((BinaryTokenStream) reuse).setValue(binaryValue());
        return reuse;
      } else {
        throw new IllegalArgumentException("Non-Tokenized Fields must have a String value");
      }
    }

    if (tokenStream != null) {
      return tokenStream;
    } else if (readerValue() != null) {
      return analyzer.tokenStream(name(), readerValue());
    } else if (stringValue() != null) {
      return analyzer.tokenStream(name(), stringValue());
    }

    throw new IllegalArgumentException("Field must have either TokenStream, String, Reader or Number value; got " + this);
  }
  
  private static final class BinaryTokenStream extends TokenStream {
    private final BytesTermAttribute bytesAtt = addAttribute(BytesTermAttribute.class);
    private boolean used = true;
    private BytesRef value;
  
    BinaryTokenStream() {
    }

    public void setValue(BytesRef value) {
      this.value = value;
    }
  
    @Override
    public boolean incrementToken() {
      if (used) {
        return false;
      }
      clearAttributes();
      bytesAtt.setBytesRef(value);
      used = true;
      return true;
    }
  
    @Override
    public void reset() {
      used = false;
    }

    @Override
    public void close() {
      value = null;
    }
  }

  private static final class StringTokenStream extends TokenStream {
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
    private boolean used = true;
    private String value = null;
    
    StringTokenStream() {
    }
    
    void setValue(String value) {
      this.value = value;
    }

    @Override
    public boolean incrementToken() {
      if (used) {
        return false;
      }
      clearAttributes();
      termAttribute.append(value);
      offsetAttribute.setOffset(0, value.length());
      used = true;
      return true;
    }

    @Override
    public void end() throws IOException {
      super.end();
      final int finalOffset = value.length();
      offsetAttribute.setOffset(finalOffset, finalOffset);
    }
    
    @Override
    public void reset() {
      used = false;
    }

    @Override
    public void close() {
      value = null;
    }
  }

  public static enum Store {

    YES,

    NO
  }
}
