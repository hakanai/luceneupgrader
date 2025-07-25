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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util;

import java.math.BigInteger;

/** Math static utility methods. */
public final class MathUtil {

  // No instance:
  private MathUtil() {}

  /**
   * Returns {@code x <= 0 ? 0 : Math.floor(Math.log(x) / Math.log(base))}
   *
   * @param base must be {@code > 1}
   */
  public static int log(long x, int base) {
    if (base == 2) {
      // This specialized method is 30x faster.
      return x <= 0 ? 0 : 63 - Long.numberOfLeadingZeros(x);
    } else if (base <= 1) {
      throw new IllegalArgumentException("base must be > 1");
    }
    int ret = 0;
    while (x >= base) {
      x /= base;
      ret++;
    }
    return ret;
  }

  /** Calculates logarithm in a given base with doubles. */
  public static double log(double base, double x) {
    return Math.log(x) / Math.log(base);
  }

  /**
   * Return the greatest common divisor of <code>a</code> and <code>b</code>, consistently with
   * {@link BigInteger#gcd(BigInteger)}.
   *
   * <p><b>NOTE</b>: A greatest common divisor must be positive, but <code>2^64</code> cannot be
   * expressed as a long although it is the GCD of {@link Long#MIN_VALUE} and <code>0</code> and the
   * GCD of {@link Long#MIN_VALUE} and {@link Long#MIN_VALUE}. So in these 2 cases, and only them,
   * this method will return {@link Long#MIN_VALUE}.
   */
  // see
  // http://en.wikipedia.org/wiki/Binary_GCD_algorithm#Iterative_version_in_C.2B.2B_using_ctz_.28count_trailing_zeros.29
  public static long gcd(long a, long b) {
    a = Math.abs(a);
    b = Math.abs(b);
    if (a == 0) {
      return b;
    } else if (b == 0) {
      return a;
    }
    final int commonTrailingZeros = Long.numberOfTrailingZeros(a | b);
    a >>>= Long.numberOfTrailingZeros(a);
    while (true) {
      b >>>= Long.numberOfTrailingZeros(b);
      if (a == b) {
        break;
      } else if (a > b || a == Long.MIN_VALUE) { // MIN_VALUE is treated as 2^64
        final long tmp = a;
        a = b;
        b = tmp;
      }
      if (a == 1) {
        break;
      }
      b -= a;
    }
    return a << commonTrailingZeros;
  }

  /**
   * Calculates inverse hyperbolic sine of a {@code double} value.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>If the argument is NaN, then the result is NaN.
   *   <li>If the argument is zero, then the result is a zero with the same sign as the argument.
   *   <li>If the argument is infinite, then the result is infinity with the same sign as the
   *       argument.
   * </ul>
   */
  public static double asinh(double a) {
    final double sign;
    // check the sign bit of the raw representation to handle -0
    if (Double.doubleToRawLongBits(a) < 0) {
      a = Math.abs(a);
      sign = -1.0d;
    } else {
      sign = 1.0d;
    }

    return sign * Math.log(Math.sqrt(a * a + 1.0d) + a);
  }

  /**
   * Calculates inverse hyperbolic cosine of a {@code double} value.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>If the argument is NaN, then the result is NaN.
   *   <li>If the argument is +1, then the result is a zero.
   *   <li>If the argument is positive infinity, then the result is positive infinity.
   *   <li>If the argument is less than 1, then the result is NaN.
   * </ul>
   */
  public static double acosh(double a) {
    return Math.log(Math.sqrt(a * a - 1.0d) + a);
  }

  /**
   * Calculates inverse hyperbolic tangent of a {@code double} value.
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>If the argument is NaN, then the result is NaN.
   *   <li>If the argument is zero, then the result is a zero with the same sign as the argument.
   *   <li>If the argument is +1, then the result is positive infinity.
   *   <li>If the argument is -1, then the result is negative infinity.
   *   <li>If the argument's absolute value is greater than 1, then the result is NaN.
   * </ul>
   */
  public static double atanh(double a) {
    final double mult;
    // check the sign bit of the raw representation to handle -0
    if (Double.doubleToRawLongBits(a) < 0) {
      a = Math.abs(a);
      mult = -0.5d;
    } else {
      mult = 0.5d;
    }
    return mult * Math.log((1.0d + a) / (1.0d - a));
  }

  /**
   * Return a relative error bound for a sum of {@code numValues} positive doubles, computed using
   * recursive summation, ie. sum = x1 + ... + xn. NOTE: This only works if all values are POSITIVE
   * so that Σ |xi| == |Σ xi|. This uses formula 3.5 from Higham, Nicholas J. (1993), "The accuracy
   * of floating point summation", SIAM Journal on Scientific Computing.
   */
  public static double sumRelativeErrorBound(int numValues) {
    if (numValues <= 1) {
      return 0;
    }
    // u = unit roundoff in the paper, also called machine precision or machine epsilon
    double u = Math.scalb(1.0, -52);
    return (numValues - 1) * u;
  }

  /**
   * Return the maximum possible sum across {@code numValues} non-negative doubles, assuming one sum
   * yielded {@code sum}.
   *
   * @see #sumRelativeErrorBound(int)
   */
  public static double sumUpperBound(double sum, int numValues) {
    if (numValues <= 2) {
      // When there are only two clauses, the sum is always the same regardless
      // of the order.
      return sum;
    }

    // The error of sums depends on the order in which values are summed up. In
    // order to avoid this issue, we compute an upper bound of the value that
    // the sum may take. If the max relative error is b, then it means that two
    // sums are always within 2*b of each other.
    // For conjunctions, we could skip this error factor since the order in which
    // scores are summed up is predictable, but in practice, this wouldn't help
    // much since the delta that is introduced by this error factor is usually
    // cancelled by the float cast.
    double b = MathUtil.sumRelativeErrorBound(numValues);
    return (1.0 + 2 * b) * sum;
  }
}
