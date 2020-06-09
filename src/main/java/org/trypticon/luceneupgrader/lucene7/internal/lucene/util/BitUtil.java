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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util; // from org.apache.solr.util rev 555343

public final class BitUtil {

  // magic numbers for bit interleaving
  private static final long MAGIC[] = {
      0x5555555555555555L, 0x3333333333333333L,
      0x0F0F0F0F0F0F0F0FL, 0x00FF00FF00FF00FFL,
      0x0000FFFF0000FFFFL, 0x00000000FFFFFFFFL,
      0xAAAAAAAAAAAAAAAAL
  };
  // shift values for bit interleaving
  private static final short SHIFT[] = {1, 2, 4, 8, 16};

  private BitUtil() {} // no instance

  // The pop methods used to rely on bit-manipulation tricks for speed but it
  // turns out that it is faster to use the Long.bitCount method (which is an
  // intrinsic since Java 6u18) in a naive loop, see LUCENE-2221

  public static long pop_array(long[] arr, int wordOffset, int numWords) {
    long popCount = 0;
    for (int i = wordOffset, end = wordOffset + numWords; i < end; ++i) {
      popCount += Long.bitCount(arr[i]);
    }
    return popCount;
  }

  public static long pop_intersect(long[] arr1, long[] arr2, int wordOffset, int numWords) {
    long popCount = 0;
    for (int i = wordOffset, end = wordOffset + numWords; i < end; ++i) {
      popCount += Long.bitCount(arr1[i] & arr2[i]);
    }
    return popCount;
  }

   public static long pop_union(long[] arr1, long[] arr2, int wordOffset, int numWords) {
     long popCount = 0;
     for (int i = wordOffset, end = wordOffset + numWords; i < end; ++i) {
       popCount += Long.bitCount(arr1[i] | arr2[i]);
     }
     return popCount;
   }

  public static long pop_andnot(long[] arr1, long[] arr2, int wordOffset, int numWords) {
    long popCount = 0;
    for (int i = wordOffset, end = wordOffset + numWords; i < end; ++i) {
      popCount += Long.bitCount(arr1[i] & ~arr2[i]);
    }
    return popCount;
  }

  public static long pop_xor(long[] arr1, long[] arr2, int wordOffset, int numWords) {
    long popCount = 0;
    for (int i = wordOffset, end = wordOffset + numWords; i < end; ++i) {
      popCount += Long.bitCount(arr1[i] ^ arr2[i]);
    }
    return popCount;
  }

  public static int nextHighestPowerOfTwo(int v) {
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v++;
    return v;
  }

   public static long nextHighestPowerOfTwo(long v) {
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v |= v >> 32;
    v++;
    return v;
  }

  public static long interleave(int even, int odd) {
    long v1 = 0x00000000FFFFFFFFL & even;
    long v2 = 0x00000000FFFFFFFFL & odd;
    v1 = (v1 | (v1 << SHIFT[4])) & MAGIC[4];
    v1 = (v1 | (v1 << SHIFT[3])) & MAGIC[3];
    v1 = (v1 | (v1 << SHIFT[2])) & MAGIC[2];
    v1 = (v1 | (v1 << SHIFT[1])) & MAGIC[1];
    v1 = (v1 | (v1 << SHIFT[0])) & MAGIC[0];
    v2 = (v2 | (v2 << SHIFT[4])) & MAGIC[4];
    v2 = (v2 | (v2 << SHIFT[3])) & MAGIC[3];
    v2 = (v2 | (v2 << SHIFT[2])) & MAGIC[2];
    v2 = (v2 | (v2 << SHIFT[1])) & MAGIC[1];
    v2 = (v2 | (v2 << SHIFT[0])) & MAGIC[0];

    return (v2<<1) | v1;
  }

  public static long deinterleave(long b) {
    b &= MAGIC[0];
    b = (b ^ (b >>> SHIFT[0])) & MAGIC[1];
    b = (b ^ (b >>> SHIFT[1])) & MAGIC[2];
    b = (b ^ (b >>> SHIFT[2])) & MAGIC[3];
    b = (b ^ (b >>> SHIFT[3])) & MAGIC[4];
    b = (b ^ (b >>> SHIFT[4])) & MAGIC[5];
    return b;
  }

  public static final long flipFlop(final long b) {
    return ((b & MAGIC[6]) >>> 1) | ((b & MAGIC[0]) << 1 );
  }

   public static int zigZagEncode(int i) {
     return (i >> 31) ^ (i << 1);
   }

   public static long zigZagEncode(long l) {
     return (l >> 63) ^ (l << 1);
   }

   public static int zigZagDecode(int i) {
     return ((i >>> 1) ^ -(i & 1));
   }

   public static long zigZagDecode(long l) {
     return ((l >>> 1) ^ -(l & 1));
   }

}
