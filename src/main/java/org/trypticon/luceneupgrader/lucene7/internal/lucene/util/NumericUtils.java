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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util;


import java.math.BigInteger;
import java.util.Arrays;

public final class NumericUtils {

  private NumericUtils() {} // no instance!
  
  public static long doubleToSortableLong(double value) {
    return sortableDoubleBits(Double.doubleToLongBits(value));
  }

  public static double sortableLongToDouble(long encoded) {
    return Double.longBitsToDouble(sortableDoubleBits(encoded));
  }

  public static int floatToSortableInt(float value) {
    return sortableFloatBits(Float.floatToIntBits(value));
  }

  public static float sortableIntToFloat(int encoded) {
    return Float.intBitsToFloat(sortableFloatBits(encoded));
  }
  
  public static long sortableDoubleBits(long bits) {
    return bits ^ (bits >> 63) & 0x7fffffffffffffffL;
  }
  
  public static int sortableFloatBits(int bits) {
    return bits ^ (bits >> 31) & 0x7fffffff;
  }


  public static void subtract(int bytesPerDim, int dim, byte[] a, byte[] b, byte[] result) {
    int start = dim * bytesPerDim;
    int end = start + bytesPerDim;
    int borrow = 0;
    for(int i=end-1;i>=start;i--) {
      int diff = (a[i]&0xff) - (b[i]&0xff) - borrow;
      if (diff < 0) {
        diff += 256;
        borrow = 1;
      } else {
        borrow = 0;
      }
      result[i-start] = (byte) diff;
    }
    if (borrow != 0) {
      throw new IllegalArgumentException("a < b");
    }
  }

  public static void add(int bytesPerDim, int dim, byte[] a, byte[] b, byte[] result) {
    int start = dim * bytesPerDim;
    int end = start + bytesPerDim;
    int carry = 0;
    for(int i=end-1;i>=start;i--) {
      int digitSum = (a[i]&0xff) + (b[i]&0xff) + carry;
      if (digitSum > 255) {
        digitSum -= 256;
        carry = 1;
      } else {
        carry = 0;
      }
      result[i-start] = (byte) digitSum;
    }
    if (carry != 0) {
      throw new IllegalArgumentException("a + b overflows bytesPerDim=" + bytesPerDim);
    }
  }

  public static void intToSortableBytes(int value, byte[] result, int offset) {
    // Flip the sign bit, so negative ints sort before positive ints correctly:
    value ^= 0x80000000;
    result[offset] =   (byte) (value >> 24);
    result[offset+1] = (byte) (value >> 16);
    result[offset+2] = (byte) (value >>  8);
    result[offset+3] = (byte) value;
  }

  public static int sortableBytesToInt(byte[] encoded, int offset) {
    int x = ((encoded[offset] & 0xFF) << 24)   | 
            ((encoded[offset+1] & 0xFF) << 16) |
            ((encoded[offset+2] & 0xFF) <<  8) | 
             (encoded[offset+3] & 0xFF);
    // Re-flip the sign bit to restore the original value:
    return x ^ 0x80000000;
  }

  public static void longToSortableBytes(long value, byte[] result, int offset) {
    // Flip the sign bit so negative longs sort before positive longs:
    value ^= 0x8000000000000000L;
    result[offset] =   (byte) (value >> 56);
    result[offset+1] = (byte) (value >> 48);
    result[offset+2] = (byte) (value >> 40);
    result[offset+3] = (byte) (value >> 32);
    result[offset+4] = (byte) (value >> 24);
    result[offset+5] = (byte) (value >> 16);
    result[offset+6] = (byte) (value >> 8);
    result[offset+7] = (byte) value;
  }

  public static long sortableBytesToLong(byte[] encoded, int offset) {
    long v = ((encoded[offset] & 0xFFL) << 56)   |
             ((encoded[offset+1] & 0xFFL) << 48) |
             ((encoded[offset+2] & 0xFFL) << 40) |
             ((encoded[offset+3] & 0xFFL) << 32) |
             ((encoded[offset+4] & 0xFFL) << 24) |
             ((encoded[offset+5] & 0xFFL) << 16) |
             ((encoded[offset+6] & 0xFFL) << 8)  |
              (encoded[offset+7] & 0xFFL);
    // Flip the sign bit back
    v ^= 0x8000000000000000L;
    return v;
  }

  public static void bigIntToSortableBytes(BigInteger bigInt, int bigIntSize, byte[] result, int offset) {
    byte[] bigIntBytes = bigInt.toByteArray();
    byte[] fullBigIntBytes;

    if (bigIntBytes.length < bigIntSize) {
      fullBigIntBytes = new byte[bigIntSize];
      System.arraycopy(bigIntBytes, 0, fullBigIntBytes, bigIntSize-bigIntBytes.length, bigIntBytes.length);
      if ((bigIntBytes[0] & 0x80) != 0) {
        // sign extend
        Arrays.fill(fullBigIntBytes, 0, bigIntSize-bigIntBytes.length, (byte) 0xff);
      }
    } else if (bigIntBytes.length == bigIntSize) {
      fullBigIntBytes = bigIntBytes;
    } else {
      throw new IllegalArgumentException("BigInteger: " + bigInt + " requires more than " + bigIntSize + " bytes storage");
    }
    // Flip the sign bit so negative bigints sort before positive bigints:
    fullBigIntBytes[0] ^= 0x80;

    System.arraycopy(fullBigIntBytes, 0, result, offset, bigIntSize);

    assert sortableBytesToBigInt(result, offset, bigIntSize).equals(bigInt): "bigInt=" + bigInt + " converted=" + sortableBytesToBigInt(result, offset, bigIntSize);
  }

  public static BigInteger sortableBytesToBigInt(byte[] encoded, int offset, int length) {
    byte[] bigIntBytes = new byte[length];
    System.arraycopy(encoded, offset, bigIntBytes, 0, length);
    // Flip the sign bit back to the original
    bigIntBytes[0] ^= 0x80;
    return new BigInteger(bigIntBytes);
  }
}
