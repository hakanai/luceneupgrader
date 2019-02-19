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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

public final class BroadWord {

// TBD: test smaller8 and smaller16 separately.
  private BroadWord() {} // no instance


  static int bitCount(long x) {
    // Step 0 leaves in each pair of bits the number of ones originally contained in that pair:
    x = x - ((x & 0xAAAAAAAAAAAAAAAAL) >>> 1);
    // Step 1, idem for each nibble:
    x = (x & 0x3333333333333333L) + ((x >>> 2) & 0x3333333333333333L);
    // Step 2, idem for each byte:
    x = (x + (x >>> 4)) & 0x0F0F0F0F0F0F0F0FL;
    // Multiply to sum them all into the high byte, and return the high byte:
    return (int) ((x * L8_L) >>> 56);
  }


  public static int select(long x, int r) {
    long s = x - ((x & 0xAAAAAAAAAAAAAAAAL) >>> 1); // Step 0, pairwise bitsums

    // Correct a small mistake in algorithm 2:
    // Use s instead of x the second time in right shift 2, compare to Algorithm 1 in rank9 above.
    s = (s & 0x3333333333333333L) + ((s >>> 2) & 0x3333333333333333L); // Step 1, nibblewise bitsums

    s = ((s + (s >>> 4)) & 0x0F0F0F0F0F0F0F0FL) * L8_L; // Step 2, bytewise bitsums

    long b = ((smallerUpTo7_8(s, (r * L8_L)) >>> 7) * L8_L) >>> 53; // & (~7L); // Step 3, side ways addition for byte number times 8

    long l = r - (((s << 8) >>> b) & 0xFFL); // Step 4, byte wise rank, subtract the rank with byte at b-8, or zero for b=0;
    assert 0L <= l : l;
    //assert l < 8 : l; //fails when bit r is not available.

    // Select bit l from byte (x >>> b):
    long spr = (((x >>> b) & 0xFFL) * L8_L) & L9_L; // spread the 8 bits of the byte at b over the long at L9 positions

    // long spr_bigger8_zero = smaller8(0L, spr); // inlined smaller8 with 0L argument:
    // FIXME: replace by biggerequal8_one formula from article page 6, line 9. four operators instead of five here.
    long spr_bigger8_zero = ( ( H8_L - (spr & (~H8_L)) ) ^ (~spr) ) & H8_L;
    s = (spr_bigger8_zero >>> 7) * L8_L; // Step 5, sideways byte add the 8 bits towards the high byte

    int res = (int) (b + (((smallerUpTo7_8(s, (l * L8_L)) >>> 7) * L8_L) >>> 56)); // Step 6
    return res;
  }


  public static long smallerUpTo7_8(long x, long y) {
    // See section 4, page 5, line 14 of the Vigna article:
    return ( ( (x | H8_L) - (y & (~H8_L)) ) ^ x ^ ~y) & H8_L;
  }


  public static long smalleru_8(long x, long y) {
    // See section 4, 8th line from the bottom of the page 5, of the Vigna article:
    return ( ( ( (x | H8_L) - (y & ~H8_L) ) | x ^ y) ^ (x | ~y) ) & H8_L;
  }


  public static long notEquals0_8(long x) {
    // See section 4, line 6-8 on page 6, of the Vigna article:
    return (((x | H8_L) - L8_L) | x) & H8_L;
  }


  public static long smallerUpto15_16(long x, long y) {
    return ( ( (x | H16_L) - (y & (~H16_L)) ) ^ x ^ ~y) & H16_L;
  }


  public final static long L8_L = 0x0101010101010101L;
  public final static long L9_L = 0x8040201008040201L;
  public final static long L16_L = 0x0001000100010001L;


  public final static long H8_L = L8_L << 7;
  public final static long H16_L = L16_L << 15;

  public static int selectNaive(long x, int r) {
    assert r >= 1;
    int s = -1;
    while ((x != 0L) && (r > 0)) {
      int ntz = Long.numberOfTrailingZeros(x);
      x >>>= (ntz + 1);
      s += (ntz + 1);
      r -= 1;
    }
    int res = (r > 0) ? 72 : s;
    return res;
  }

}
