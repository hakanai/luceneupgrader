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

}
