package org.apache.lucene.search;

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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.RamUsageEstimator;

import java.io.IOException;
import java.io.Serializable;
import java.io.PrintStream;

/**
 * Expert: Maintains caches of term values.
 *
 * <p>Created: May 19, 2004 11:13:14 AM
 *
 * @since   lucene 1.4
 *
 */
public interface FieldCache {

  final class CreationPlaceholder {
    Object value;
  }


  /** Expert: Stores term text values and document ordering data. */
  class StringIndex {

    /** All the term values, in natural order. */
    public final String[] lookup;

    /** For each document, an index into the lookup array. */
    public final int[] order;

    /** Creates one of these objects */
    public StringIndex (int[] values, String[] lookup) {
      this.order = values;
      this.lookup = lookup;
    }
  }

  /**
   * Marker interface as super-interface to all parsers. It
   * is used to specify a custom parser to {@code
   * SortField#SortField(String, FieldCache.Parser)}.
   */
  interface Parser extends Serializable {
  }

  /** Interface to parse bytes from document fields.
   *
   */
  interface ByteParser extends Parser {
    /** Return a single Byte representation of this field's value. */
    byte parseByte(String string);
  }

  /** Interface to parse shorts from document fields.
   *
   */
  interface ShortParser extends Parser {
    /** Return a short representation of this field's value. */
    short parseShort(String string);
  }

  /** Interface to parse ints from document fields.
   *
   */
  interface IntParser extends Parser {
    /** Return an integer representation of this field's value. */
    int parseInt(String string);
  }

  /** Interface to parse floats from document fields.
   *
   */
  interface FloatParser extends Parser {
    /** Return an float representation of this field's value. */
    float parseFloat(String string);
  }

  /** Interface to parse long from document fields.
   *
   */
  interface LongParser extends Parser {
    /** Return an long representation of this field's value. */
    long parseLong(String string);
  }

  /** Interface to parse doubles from document fields.
   *
   */
  interface DoubleParser extends Parser {
    /** Return an long representation of this field's value. */
    double parseDouble(String string);
  }

  /** Expert: The cache used internally by sorting and range query classes. */
  FieldCache DEFAULT = new FieldCacheImpl();
  
  /** The default parser for byte values, which are encoded by {@code Byte#toString(byte)} */
  ByteParser DEFAULT_BYTE_PARSER = new ByteParser() {
    public byte parseByte(String value) {
      return Byte.parseByte(value);
    }
    protected Object readResolve() {
      return DEFAULT_BYTE_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_BYTE_PARSER"; 
    }
  };

  /** The default parser for short values, which are encoded by {@code Short#toString(short)} */
  ShortParser DEFAULT_SHORT_PARSER = new ShortParser() {
    public short parseShort(String value) {
      return Short.parseShort(value);
    }
    protected Object readResolve() {
      return DEFAULT_SHORT_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_SHORT_PARSER"; 
    }
  };

  /** The default parser for int values, which are encoded by {@code Integer#toString(int)} */
  IntParser DEFAULT_INT_PARSER = new IntParser() {
    public int parseInt(String value) {
      return Integer.parseInt(value);
    }
    protected Object readResolve() {
      return DEFAULT_INT_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_INT_PARSER"; 
    }
  };

  /** The default parser for float values, which are encoded by {@code Float#toString(float)} */
  FloatParser DEFAULT_FLOAT_PARSER = new FloatParser() {
    public float parseFloat(String value) {
      return Float.parseFloat(value);
    }
    protected Object readResolve() {
      return DEFAULT_FLOAT_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_FLOAT_PARSER"; 
    }
  };

  /** The default parser for long values, which are encoded by {@code Long#toString(long)} */
  LongParser DEFAULT_LONG_PARSER = new LongParser() {
    public long parseLong(String value) {
      return Long.parseLong(value);
    }
    protected Object readResolve() {
      return DEFAULT_LONG_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_LONG_PARSER"; 
    }
  };

  /** The default parser for double values, which are encoded by {@code Double#toString(double)} */
  DoubleParser DEFAULT_DOUBLE_PARSER = new DoubleParser() {
    public double parseDouble(String value) {
      return Double.parseDouble(value);
    }
    protected Object readResolve() {
      return DEFAULT_DOUBLE_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".DEFAULT_DOUBLE_PARSER"; 
    }
  };

  /**
   * A parser instance for int values encoded by {@code NumericUtils#intToPrefixCoded(int)}, e.g. when indexed
   * via {@code NumericField}/{@code NumericTokenStream}.
   */
  IntParser NUMERIC_UTILS_INT_PARSER=new IntParser(){
    public int parseInt(String val) {
      final int shift = val.charAt(0)-NumericUtils.SHIFT_START_INT;
      if (shift>0 && shift<=31)
        throw new FieldCacheImpl.StopFillCacheException();
      return NumericUtils.prefixCodedToInt(val);
    }
    protected Object readResolve() {
      return NUMERIC_UTILS_INT_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_INT_PARSER"; 
    }
  };

  /**
   * A parser instance for float values encoded with {@code NumericUtils}, e.g. when indexed
   * via {@code NumericField}/{@code NumericTokenStream}.
   */
  FloatParser NUMERIC_UTILS_FLOAT_PARSER=new FloatParser(){
    public float parseFloat(String val) {
      final int shift = val.charAt(0)-NumericUtils.SHIFT_START_INT;
      if (shift>0 && shift<=31)
        throw new FieldCacheImpl.StopFillCacheException();
      return NumericUtils.sortableIntToFloat(NumericUtils.prefixCodedToInt(val));
    }
    protected Object readResolve() {
      return NUMERIC_UTILS_FLOAT_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_FLOAT_PARSER"; 
    }
  };

  /**
   * A parser instance for long values encoded by {@code NumericUtils#longToPrefixCoded(long)}, e.g. when indexed
   * via {@code NumericField}/{@code NumericTokenStream}.
   */
  LongParser NUMERIC_UTILS_LONG_PARSER = new LongParser(){
    public long parseLong(String val) {
      final int shift = val.charAt(0)-NumericUtils.SHIFT_START_LONG;
      if (shift>0 && shift<=63)
        throw new FieldCacheImpl.StopFillCacheException();
      return NumericUtils.prefixCodedToLong(val);
    }
    protected Object readResolve() {
      return NUMERIC_UTILS_LONG_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_LONG_PARSER"; 
    }
  };

  /**
   * A parser instance for double values encoded with {@code NumericUtils}, e.g. when indexed
   * via {@code NumericField}/{@code NumericTokenStream}.
   */
  DoubleParser NUMERIC_UTILS_DOUBLE_PARSER = new DoubleParser(){
    public double parseDouble(String val) {
      final int shift = val.charAt(0)-NumericUtils.SHIFT_START_LONG;
      if (shift>0 && shift<=63)
        throw new FieldCacheImpl.StopFillCacheException();
      return NumericUtils.sortableLongToDouble(NumericUtils.prefixCodedToLong(val));
    }
    protected Object readResolve() {
      return NUMERIC_UTILS_DOUBLE_PARSER;
    }
    @Override
    public String toString() { 
      return FieldCache.class.getName()+".NUMERIC_UTILS_DOUBLE_PARSER"; 
    }
  };

  /** Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> and returns a bit set at the size of
   * <code>reader.maxDoc()</code>, with turned on bits for each docid that 
   * does have a value for this field.
   */
  Bits getDocsWithField(IndexReader reader, String field)
  throws IOException;

  /** Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> as bytes and returns an array of
   * size <code>reader.maxDoc()</code> of the value each document has in the
   * given field.
   * @param reader  Used to get field values.
   * @param field   Which field contains the bytes.
   * @param parser  Computes byte for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException  If any error occurs.
   */
  byte[] getBytes(IndexReader reader, String field, ByteParser parser, boolean setDocsWithField)
  throws IOException;

  /** Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> as shorts and returns an array of
   * size <code>reader.maxDoc()</code> of the value each document has in the
   * given field.
   * @param reader  Used to get field values.
   * @param field   Which field contains the shorts.
   * @param parser  Computes short for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException  If any error occurs.
   */
  short[] getShorts(IndexReader reader, String field, ShortParser parser, boolean setDocsWithField)
  throws IOException;

  /** Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> as integers and returns an array of
   * size <code>reader.maxDoc()</code> of the value each document has in the
   * given field.
   * @param reader  Used to get field values.
   * @param field   Which field contains the integers.
   * @param parser  Computes integer for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException  If any error occurs.
   */
  int[] getInts(IndexReader reader, String field, IntParser parser, boolean setDocsWithField)
  throws IOException;

  /** Checks the internal cache for an appropriate entry, and if
   * none is found, reads the terms in <code>field</code> as floats and returns an array
   * of size <code>reader.maxDoc()</code> of the value each document
   * has in the given field.
   * @param reader  Used to get field values.
   * @param field   Which field contains the floats.
   * @param parser  Computes float for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException  If any error occurs.
   */
  float[] getFloats(IndexReader reader, String field,
                    FloatParser parser, boolean setDocsWithField) throws IOException;

  /**
   * Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> as longs and returns an array of
   * size <code>reader.maxDoc()</code> of the value each document has in the
   * given field.
   *
   * @param reader Used to get field values.
   * @param field  Which field contains the longs.
   * @param parser Computes integer for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException If any error occurs.
   */
  long[] getLongs(IndexReader reader, String field, LongParser parser, boolean setDocsWithField)
          throws IOException;

  /**
   * Checks the internal cache for an appropriate entry, and if none is found,
   * reads the terms in <code>field</code> as doubles and returns an array of
   * size <code>reader.maxDoc()</code> of the value each document has in the
   * given field.
   *
   * @param reader Used to get field values.
   * @param field  Which field contains the doubles.
   * @param parser Computes integer for string values.
   * @param setDocsWithField  If true then {@code #getDocsWithField} will
   *        also be computed and stored in the FieldCache.
   * @return The values in the given field for each document.
   * @throws IOException If any error occurs.
   */
  double[] getDoubles(IndexReader reader, String field, DoubleParser parser, boolean setDocsWithField)
          throws IOException;

  /** Checks the internal cache for an appropriate entry, and if none
   * is found, reads the term values in <code>field</code> and returns an array
   * of size <code>reader.maxDoc()</code> containing the value each document
   * has in the given field.
   * @param reader  Used to get field values.
   * @param field   Which field contains the strings.
   * @return The values in the given field for each document.
   * @throws IOException  If any error occurs.
   */
  String[] getStrings(IndexReader reader, String field)
  throws IOException;

  /** Checks the internal cache for an appropriate entry, and if none
   * is found reads the term values in <code>field</code> and returns
   * an array of them in natural order, along with an array telling
   * which element in the term array each document uses.
   * @param reader  Used to get field values.
   * @param field   Which field contains the strings.
   * @return Array of terms and index into the array for each document.
   * @throws IOException  If any error occurs.
   */
  StringIndex getStringIndex(IndexReader reader, String field)
  throws IOException;

  /**
   * EXPERT: A unique Identifier/Description for each item in the FieldCache. 
   * Can be useful for logging/debugging.
   * @lucene.experimental
   */
  abstract class CacheEntry {
    public abstract Object getReaderKey();
    public abstract String getFieldName();
    public abstract Class<?> getCacheType();
    public abstract Object getCustom();
    public abstract Object getValue();
    private String size = null;
    protected final void setEstimatedSize(String size) {
      this.size = size;
    }

    /** 
     * Computes (and stores) the estimated size of the cache Value 
     *
     */
    public void estimateSize() {
      long size = RamUsageEstimator.sizeOf(getValue());
      setEstimatedSize(RamUsageEstimator.humanReadableUnits(size));
    }

    /**
     * The most recently estimated size of the value, null unless 
     * estimateSize has been called.
     */
    public final String getEstimatedSize() {
      return size;
    }
    
    
    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("'").append(getReaderKey()).append("'=>");
      b.append("'").append(getFieldName()).append("',");
      b.append(getCacheType()).append(",").append(getCustom());
      b.append("=>").append(getValue().getClass().getName()).append("#");
      b.append(System.identityHashCode(getValue()));
      
      String s = getEstimatedSize();
      if(null != s) {
        b.append(" (size =~ ").append(s).append(')');
      }

      return b.toString();
    }
  
  }

  /**
   * EXPERT: Generates an array of CacheEntry objects representing all items 
   * currently in the FieldCache.
   * <p>
   * NOTE: These CacheEntry objects maintain a strong reference to the 
   * Cached Values.  Maintaining references to a CacheEntry the IndexReader 
   * associated with it has garbage collected will prevent the Value itself
   * from being garbage collected when the Cache drops the WeakReference.
   * </p>
   * @lucene.experimental
   */
  CacheEntry[] getCacheEntries();

  /**
   * Expert: drops all cache entries associated with this
   * reader.  NOTE: this reader must precisely match the
   * reader that the cache entry is keyed on. If you pass a
   * top-level reader, it usually will have no effect as
   * Lucene now caches at the segment reader level.
   */
  void purge(IndexReader r);

  /**
   * If non-null, FieldCacheImpl will warn whenever
   * entries are created that are not sane according to
   * {@code org.apache.lucene.util.FieldCacheSanityChecker}.
   */
  void setInfoStream(PrintStream stream);

  /** counterpart of {@code #setInfoStream(PrintStream)} */
  PrintStream getInfoStream();
}
