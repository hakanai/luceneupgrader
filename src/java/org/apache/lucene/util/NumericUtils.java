package org.apache.lucene.util;

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


/**
 * This is a helper class to generate prefix-encoded representations for numerical values
 * and supplies converters to represent float/double values as sortable integers/longs.
 *
 * <p>To quickly execute range queries in Apache Lucene, a range is divided recursively
 * into multiple intervals for searching: The center of the range is searched only with
 * the lowest possible precision in the trie, while the boundaries are matched
 * more exactly. This reduces the number of terms dramatically.
 *
 * <p>This class generates terms to achieve this: First the numerical integer values need to
 * be converted to strings. For that integer values (32 bit or 64 bit) are made unsigned
 * and the bits are converted to ASCII chars with each 7 bit. The resulting string is
 * sortable like the original integer value. Each value is also prefixed
 * (in the first char) by the <code>shift</code> value (number of bits removed) used
 * during encoding.
 *
 * <p>To also index floating point numbers, this class supplies two methods to convert them
 * to integer values by changing their bit layout: {@code #doubleToSortableLong},
 * {@code #floatToSortableInt}. You will have no precision loss by
 * converting floating point numbers to integers and back (only that the integer form
 * is not usable). Other data types like dates can easily converted to longs or ints (e.g.
 * date to long: {@code java.util.Date#getTime}).
 *
 * <p>For easy usage, the trie algorithm is implemented for indexing inside
 * {@code NumericTokenStream} that can index <code>int</code>, <code>long</code>,
 * <code>float</code>, and <code>double</code>. For querying,
 * {@code NumericRangeQuery} and {@code NumericRangeFilter} implement the query part
 * for the same data types.
 *
 * <p>This class can also be used, to generate lexicographically sortable (according
 * {@code String#compareTo(String)}) representations of numeric data types for other
 * usages (e.g. sorting).
 *
 * @lucene.internal
 *
 * @since 2.9
 */
public final class NumericUtils {

  private NumericUtils() {} // no instance!
  
  /**
   * The default precision step used by {@code NumericField}, {@code NumericTokenStream},
   * {@code NumericRangeQuery}, and {@code NumericRangeFilter} as default
   */
  public static final int PRECISION_STEP_DEFAULT = 4;
  
  /**
   * Expert: Longs are stored at lower precision by shifting off lower bits. The shift count is
   * stored as <code>SHIFT_START_LONG+shift</code> in the first character
   */
  public static final char SHIFT_START_LONG = (char)0x20;

  /**
   * Expert: The maximum term length (used for <code>char[]</code> buffer size)
   * for encoding <code>long</code> values.
   *
   */
  public static final int BUF_SIZE_LONG = 63/7 + 2;

  /**
   * Expert: Integers are stored at lower precision by shifting off lower bits. The shift count is
   * stored as <code>SHIFT_START_INT+shift</code> in the first character
   */
  public static final char SHIFT_START_INT  = (char)0x60;

  /**
   * Expert: The maximum term length (used for <code>char[]</code> buffer size)
   * for encoding <code>int</code> values.
   *
   */
  public static final int BUF_SIZE_INT = 31/7 + 2;

  /**
   * Expert: Returns prefix coded bits after reducing the precision by <code>shift</code> bits.
   * This is method is used by {@code NumericTokenStream}.
   * @param val the numeric value
   * @param shift how many bits to strip from the right
   * @param buffer that will contain the encoded chars, must be at least of {@code #BUF_SIZE_LONG}
   * length
   * @return number of chars written to buffer
   */
  public static int longToPrefixCoded(final long val, final int shift, final char[] buffer) {
    if (shift>63 || shift<0)
      throw new IllegalArgumentException("Illegal shift value, must be 0..63");
    int nChars = (63-shift)/7 + 1, len = nChars+1;
    buffer[0] = (char)(SHIFT_START_LONG + shift);
    long sortableBits = val ^ 0x8000000000000000L;
    sortableBits >>>= shift;
    while (nChars>=1) {
      // Store 7 bits per character for good efficiency when UTF-8 encoding.
      // The whole number is right-justified so that lucene can prefix-encode
      // the terms more efficiently.
      buffer[nChars--] = (char)(sortableBits & 0x7f);
      sortableBits >>>= 7;
    }
    return len;
  }

  /**
   * Expert: Returns prefix coded bits after reducing the precision by <code>shift</code> bits.
   * This is method is used by {@code NumericTokenStream}.
   * @param val the numeric value
   * @param shift how many bits to strip from the right
   * @param buffer that will contain the encoded chars, must be at least of {@code #BUF_SIZE_INT}
   * length
   * @return number of chars written to buffer
   */
  public static int intToPrefixCoded(final int val, final int shift, final char[] buffer) {
    if (shift>31 || shift<0)
      throw new IllegalArgumentException("Illegal shift value, must be 0..31");
    int nChars = (31-shift)/7 + 1, len = nChars+1;
    buffer[0] = (char)(SHIFT_START_INT + shift);
    int sortableBits = val ^ 0x80000000;
    sortableBits >>>= shift;
    while (nChars>=1) {
      // Store 7 bits per character for good efficiency when UTF-8 encoding.
      // The whole number is right-justified so that lucene can prefix-encode
      // the terms more efficiently.
      buffer[nChars--] = (char)(sortableBits & 0x7f);
      sortableBits >>>= 7;
    }
    return len;
  }

  /*
   * Returns a long from prefixCoded characters.
   * Rightmost bits will be zero for lower precision codes.
   * This method can be used to decode e.g. a stored field.
   * @throws NumberFormatException if the supplied string is
   * not correctly prefix encoded.
   *
   */
  public static long prefixCodedToLong(final String prefixCoded) {
    final int shift = prefixCoded.charAt(0)-SHIFT_START_LONG;
    if (shift>63 || shift<0)
      throw new NumberFormatException("Invalid shift value in prefixCoded string (is encoded value really a LONG?)");
    long sortableBits = 0L;
    for (int i=1, len=prefixCoded.length(); i<len; i++) {
      sortableBits <<= 7;
      final char ch = prefixCoded.charAt(i);
      if (ch>0x7f) {
        throw new NumberFormatException(
          "Invalid prefixCoded numerical value representation (char "+
          Integer.toHexString(ch)+" at position "+i+" is invalid)"
        );
      }
      sortableBits |= ch;
    }
    return (sortableBits << shift) ^ 0x8000000000000000L;
  }

  /*
   * Returns an int from prefixCoded characters.
   * Rightmost bits will be zero for lower precision codes.
   * This method can be used to decode e.g. a stored field.
   * @throws NumberFormatException if the supplied string is
   * not correctly prefix encoded.
   *
   */
  public static int prefixCodedToInt(final String prefixCoded) {
    final int shift = prefixCoded.charAt(0)-SHIFT_START_INT;
    if (shift>31 || shift<0)
      throw new NumberFormatException("Invalid shift value in prefixCoded string (is encoded value really an INT?)");
    int sortableBits = 0;
    for (int i=1, len=prefixCoded.length(); i<len; i++) {
      sortableBits <<= 7;
      final char ch = prefixCoded.charAt(i);
      if (ch>0x7f) {
        throw new NumberFormatException(
          "Invalid prefixCoded numerical value representation (char "+
          Integer.toHexString(ch)+" at position "+i+" is invalid)"
        );
      }
      sortableBits |= ch;
    }
    return (sortableBits << shift) ^ 0x80000000;
  }

  /**
   * Converts a <code>double</code> value to a sortable signed <code>long</code>.
   * The value is converted by getting their IEEE 754 floating-point &quot;double format&quot;
   * bit layout and then some bits are swapped, to be able to compare the result as long.
   * By this the precision is not reduced, but the value can easily used as a long.
   * The sort order (including {@code Double#NaN}) is defined by
   * {@code Double#compareTo}; {@code NaN} is greater than positive infinity.
   *
   */
  public static long doubleToSortableLong(double val) {
    long f = Double.doubleToLongBits(val);
    if (f<0) f ^= 0x7fffffffffffffffL;
    return f;
  }

  /**
   * Converts a sortable <code>long</code> back to a <code>double</code>.
   *
   */
  public static double sortableLongToDouble(long val) {
    if (val<0) val ^= 0x7fffffffffffffffL;
    return Double.longBitsToDouble(val);
  }

  /**
   * Converts a <code>float</code> value to a sortable signed <code>int</code>.
   * The value is converted by getting their IEEE 754 floating-point &quot;float format&quot;
   * bit layout and then some bits are swapped, to be able to compare the result as int.
   * By this the precision is not reduced, but the value can easily used as an int.
   * The sort order (including {@code Float#NaN}) is defined by
   * {@code Float#compareTo}; {@code NaN} is greater than positive infinity.
   *
   */
  public static int floatToSortableInt(float val) {
    int f = Float.floatToIntBits(val);
    if (f<0) f ^= 0x7fffffff;
    return f;
  }

  /**
   * Converts a sortable <code>int</code> back to a <code>float</code>.
   *
   */
  public static float sortableIntToFloat(int val) {
    if (val<0) val ^= 0x7fffffff;
    return Float.intBitsToFloat(val);
  }

}
