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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util;


import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator;

public final class FixedBitSet extends BitSet implements MutableBits, Accountable {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(FixedBitSet.class);

  private final long[] bits; // Array of longs holding the bits 
  private final int numBits; // The number of bits in use
  private final int numWords; // The exact number of longs needed to hold numBits (<= bits.length)
  
  public static FixedBitSet ensureCapacity(FixedBitSet bits, int numBits) {
    if (numBits < bits.numBits) {
      return bits;
    } else {
      // Depends on the ghost bits being clear!
      // (Otherwise, they may become visible in the new instance)
      int numWords = bits2words(numBits);
      long[] arr = bits.getBits();
      if (numWords >= arr.length) {
        arr = ArrayUtil.grow(arr, numWords + 1);
      }
      return new FixedBitSet(arr, arr.length << 6);
    }
  }

  public static int bits2words(int numBits) {
    return ((numBits - 1) >> 6) + 1; // I.e.: get the word-offset of the last bit and add one (make sure to use >> so 0 returns 0!)
  }

  public static long intersectionCount(FixedBitSet a, FixedBitSet b) {
    // Depends on the ghost bits being clear!
    return BitUtil.pop_intersect(a.bits, b.bits, 0, Math.min(a.numWords, b.numWords));
  }

  public static long unionCount(FixedBitSet a, FixedBitSet b) {
    // Depends on the ghost bits being clear!
    long tot = BitUtil.pop_union(a.bits, b.bits, 0, Math.min(a.numWords, b.numWords));
    if (a.numWords < b.numWords) {
      tot += BitUtil.pop_array(b.bits, a.numWords, b.numWords - a.numWords);
    } else if (a.numWords > b.numWords) {
      tot += BitUtil.pop_array(a.bits, b.numWords, a.numWords - b.numWords);
    }
    return tot;
  }

  public static long andNotCount(FixedBitSet a, FixedBitSet b) {
    // Depends on the ghost bits being clear!
    long tot = BitUtil.pop_andnot(a.bits, b.bits, 0, Math.min(a.numWords, b.numWords));
    if (a.numWords > b.numWords) {
      tot += BitUtil.pop_array(a.bits, b.numWords, a.numWords - b.numWords);
    }
    return tot;
  }

  public FixedBitSet(int numBits) {
    this.numBits = numBits;
    bits = new long[bits2words(numBits)];
    numWords = bits.length;
  }

  public FixedBitSet(long[] storedBits, int numBits) {
    this.numWords = bits2words(numBits);
    if (numWords > storedBits.length) {
      throw new IllegalArgumentException("The given long array is too small  to hold " + numBits + " bits");
    }
    this.numBits = numBits;
    this.bits = storedBits;

    assert verifyGhostBitsClear();
  }

  private boolean verifyGhostBitsClear() {
    for (int i = numWords; i < bits.length; i++) {
      if (bits[i] != 0) return false;
    }
    
    if ((numBits & 0x3f) == 0) return true;
    
    long mask = -1L << numBits;

    return (bits[numWords - 1] & mask) == 0;
  }
  
  @Override
  public int length() {
    return numBits;
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(bits);
  }

  public long[] getBits() {
    return bits;
  }


  @Override
  public int cardinality() {
    // Depends on the ghost bits being clear!
    return (int) BitUtil.pop_array(bits, 0, numWords);
  }

  @Override
  public boolean get(int index) {
    assert index >= 0 && index < numBits: "index=" + index + ", numBits=" + numBits;
    int i = index >> 6;               // div 64
    // signed shift will keep a negative index and force an
    // array-index-out-of-bounds-exception, removing the need for an explicit check.
    long bitmask = 1L << index;
    return (bits[i] & bitmask) != 0;
  }

  public void set(int index) {
    assert index >= 0 && index < numBits: "index=" + index + ", numBits=" + numBits;
    int wordNum = index >> 6;      // div 64
    long bitmask = 1L << index;
    bits[wordNum] |= bitmask;
  }

  public boolean getAndSet(int index) {
    assert index >= 0 && index < numBits: "index=" + index + ", numBits=" + numBits;
    int wordNum = index >> 6;      // div 64
    long bitmask = 1L << index;
    boolean val = (bits[wordNum] & bitmask) != 0;
    bits[wordNum] |= bitmask;
    return val;
  }

  @Override
  public void clear(int index) {
    assert index >= 0 && index < numBits: "index=" + index + ", numBits=" + numBits;
    int wordNum = index >> 6;
    long bitmask = 1L << index;
    bits[wordNum] &= ~bitmask;
  }

  public boolean getAndClear(int index) {
    assert index >= 0 && index < numBits: "index=" + index + ", numBits=" + numBits;
    int wordNum = index >> 6;      // div 64
    long bitmask = 1L << index;
    boolean val = (bits[wordNum] & bitmask) != 0;
    bits[wordNum] &= ~bitmask;
    return val;
  }

  @Override
  public int nextSetBit(int index) {
    // Depends on the ghost bits being clear!
    assert index >= 0 && index < numBits : "index=" + index + ", numBits=" + numBits;
    int i = index >> 6;
    long word = bits[i] >> index;  // skip all the bits to the right of index

    if (word!=0) {
      return index + Long.numberOfTrailingZeros(word);
    }

    while(++i < numWords) {
      word = bits[i];
      if (word != 0) {
        return (i<<6) + Long.numberOfTrailingZeros(word);
      }
    }

    return DocIdSetIterator.NO_MORE_DOCS;
  }

  @Override
  public int prevSetBit(int index) {
    assert index >= 0 && index < numBits: "index=" + index + " numBits=" + numBits;
    int i = index >> 6;
    final int subIndex = index & 0x3f;  // index within the word
    long word = (bits[i] << (63-subIndex));  // skip all the bits to the left of index

    if (word != 0) {
      return (i << 6) + subIndex - Long.numberOfLeadingZeros(word); // See LUCENE-3197
    }

    while (--i >= 0) {
      word = bits[i];
      if (word !=0 ) {
        return (i << 6) + 63 - Long.numberOfLeadingZeros(word);
      }
    }

    return -1;
  }

  @Override
  public void or(DocIdSetIterator iter) throws IOException {
    if (BitSetIterator.getFixedBitSetOrNull(iter) != null) {
      assertUnpositioned(iter);
      final FixedBitSet bits = BitSetIterator.getFixedBitSetOrNull(iter); 
      or(bits);
    } else {
      super.or(iter);
    }
  }

  public void or(FixedBitSet other) {
    or(other.bits, other.numWords);
  }
  
  private void or(final long[] otherArr, final int otherNumWords) {
    assert otherNumWords <= numWords : "numWords=" + numWords + ", otherNumWords=" + otherNumWords;
    final long[] thisArr = this.bits;
    int pos = Math.min(numWords, otherNumWords);
    while (--pos >= 0) {
      thisArr[pos] |= otherArr[pos];
    }
  }
  
  public void xor(FixedBitSet other) {
    xor(other.bits, other.numWords);
  }
  
  public void xor(DocIdSetIterator iter) throws IOException {
    assertUnpositioned(iter);
    if (BitSetIterator.getFixedBitSetOrNull(iter) != null) {
      final FixedBitSet bits = BitSetIterator.getFixedBitSetOrNull(iter); 
      xor(bits);
    } else {
      int doc;
      while ((doc = iter.nextDoc()) < numBits) {
        flip(doc);
      }
    }
  }

  private void xor(long[] otherBits, int otherNumWords) {
    assert otherNumWords <= numWords : "numWords=" + numWords + ", other.numWords=" + otherNumWords;
    final long[] thisBits = this.bits;
    int pos = Math.min(numWords, otherNumWords);
    while (--pos >= 0) {
      thisBits[pos] ^= otherBits[pos];
    }
  }

  public boolean intersects(FixedBitSet other) {
    // Depends on the ghost bits being clear!
    int pos = Math.min(numWords, other.numWords);
    while (--pos>=0) {
      if ((bits[pos] & other.bits[pos]) != 0) return true;
    }
    return false;
  }

  public void and(FixedBitSet other) {
    and(other.bits, other.numWords);
  }
  
  private void and(final long[] otherArr, final int otherNumWords) {
    final long[] thisArr = this.bits;
    int pos = Math.min(this.numWords, otherNumWords);
    while(--pos >= 0) {
      thisArr[pos] &= otherArr[pos];
    }
    if (this.numWords > otherNumWords) {
      Arrays.fill(thisArr, otherNumWords, this.numWords, 0L);
    }
  }

  public void andNot(FixedBitSet other) {
    andNot(other.bits, other.numWords);
  }
  
  private void andNot(final long[] otherArr, final int otherNumWords) {
    final long[] thisArr = this.bits;
    int pos = Math.min(this.numWords, otherNumWords);
    while(--pos >= 0) {
      thisArr[pos] &= ~otherArr[pos];
    }
  }

  public boolean scanIsEmpty() {
    // This 'slow' implementation is still faster than any external one could be
    // (e.g.: (bitSet.length() == 0 || bitSet.nextSetBit(0) == -1))
    // especially for small BitSets
    // Depends on the ghost bits being clear!
    final int count = numWords;
    
    for (int i = 0; i < count; i++) {
      if (bits[i] != 0) return false;
    }
    
    return true;
  }


  public void flip(int startIndex, int endIndex) {
    assert startIndex >= 0 && startIndex < numBits;
    assert endIndex >= 0 && endIndex <= numBits;
    if (endIndex <= startIndex) {
      return;
    }

    int startWord = startIndex >> 6;
    int endWord = (endIndex-1) >> 6;

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex since only the lowest 6 bits are used

    if (startWord == endWord) {
      bits[startWord] ^= (startmask & endmask);
      return;
    }

    bits[startWord] ^= startmask;

    for (int i=startWord+1; i<endWord; i++) {
      bits[i] = ~bits[i];
    }

    bits[endWord] ^= endmask;
  }

  public void flip(int index) {
    assert index >= 0 && index < numBits: "index=" + index + " numBits=" + numBits;
    int wordNum = index >> 6;      // div 64
    long bitmask = 1L << index; // mod 64 is implicit
    bits[wordNum] ^= bitmask;
  }


  public void set(int startIndex, int endIndex) {
    assert startIndex >= 0 && startIndex < numBits : "startIndex=" + startIndex + ", numBits=" + numBits;
    assert endIndex >= 0 && endIndex <= numBits : "endIndex=" + endIndex + ", numBits=" + numBits;
    if (endIndex <= startIndex) {
      return;
    }

    int startWord = startIndex >> 6;
    int endWord = (endIndex-1) >> 6;

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex since only the lowest 6 bits are used

    if (startWord == endWord) {
      bits[startWord] |= (startmask & endmask);
      return;
    }

    bits[startWord] |= startmask;
    Arrays.fill(bits, startWord+1, endWord, -1L);
    bits[endWord] |= endmask;
  }

  @Override
  public void clear(int startIndex, int endIndex) {
    assert startIndex >= 0 && startIndex < numBits : "startIndex=" + startIndex + ", numBits=" + numBits;
    assert endIndex >= 0 && endIndex <= numBits : "endIndex=" + endIndex + ", numBits=" + numBits;
    if (endIndex <= startIndex) {
      return;
    }

    int startWord = startIndex >> 6;
    int endWord = (endIndex-1) >> 6;

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex since only the lowest 6 bits are used

    // invert masks since we are clearing
    startmask = ~startmask;
    endmask = ~endmask;

    if (startWord == endWord) {
      bits[startWord] &= (startmask | endmask);
      return;
    }

    bits[startWord] &= startmask;
    Arrays.fill(bits, startWord+1, endWord, 0L);
    bits[endWord] &= endmask;
  }

  @Override
  public FixedBitSet clone() {
    long[] bits = new long[this.bits.length];
    System.arraycopy(this.bits, 0, bits, 0, numWords);
    return new FixedBitSet(bits, numBits);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FixedBitSet)) {
      return false;
    }
    FixedBitSet other = (FixedBitSet) o;
    if (numBits != other.numBits) {
      return false;
    }
    // Depends on the ghost bits being clear!
    return Arrays.equals(bits, other.bits);
  }

  @Override
  public int hashCode() {
    // Depends on the ghost bits being clear!
    long h = 0;
    for (int i = numWords; --i>=0;) {
      h ^= bits[i];
      h = (h << 1) | (h >>> 63); // rotate left
    }
    // fold leftmost bits into right and add a constant to prevent
    // empty sets from returning 0, which is too common.
    return (int) ((h>>32) ^ h) + 0x98761234;
  }
}
