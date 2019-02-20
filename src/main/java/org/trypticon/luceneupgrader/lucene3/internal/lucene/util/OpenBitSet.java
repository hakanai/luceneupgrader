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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.DocIdSet;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.DocIdSetIterator;

import java.io.Serializable;
import java.util.Arrays;

public class OpenBitSet extends DocIdSet implements Cloneable, Serializable, Bits {
  protected long[] bits;
  protected int wlen;   // number of words (elements) used in the array

  // Used only for assert:
  private long numBits;


  public OpenBitSet(long numBits) {
    this.numBits = numBits;
    bits = new long[bits2words(numBits)];
    wlen = bits.length;
  }

  public OpenBitSet() {
    this(64);
  }


  public OpenBitSet(long[] bits, int numWords) {
    this.bits = bits;
    this.wlen = numWords;
    this.numBits = wlen * 64;
  }
  
  @Override
  public DocIdSetIterator iterator() {
    return new OpenBitSetIterator(bits, wlen);
  }

    @Override
  public boolean isCacheable() {
    return true;
  }

    public long capacity() { return bits.length << 6; }

  public long size() {
      return capacity();
  }

  public int length() {
    return bits.length << 6;
  }

    public boolean isEmpty() { return cardinality()==0; }

    public long[] getBits() { return bits; }

    public void setBits(long[] bits) { this.bits = bits; }

    public int getNumWords() { return wlen; }

    public void setNumWords(int nWords) { this.wlen=nWords; }



    public boolean get(int index) {
    int i = index >> 6;               // div 64
    // signed shift will keep a negative index and force an
    // array-index-out-of-bounds-exception, removing the need for an explicit check.
    if (i>=bits.length) return false;

    int bit = index & 0x3f;           // mod 64
    long bitmask = 1L << bit;
    return (bits[i] & bitmask) != 0;
  }



  public boolean fastGet(int index) {
    assert index >= 0 && index < numBits;
    int i = index >> 6;               // div 64
    // signed shift will keep a negative index and force an
    // array-index-out-of-bounds-exception, removing the need for an explicit check.
    int bit = index & 0x3f;           // mod 64
    long bitmask = 1L << bit;
    return (bits[i] & bitmask) != 0;
  }



  public boolean get(long index) {
    int i = (int)(index >> 6);             // div 64
    if (i>=bits.length) return false;
    int bit = (int)index & 0x3f;           // mod 64
    long bitmask = 1L << bit;
    return (bits[i] & bitmask) != 0;
  }


  public boolean fastGet(long index) {
    assert index >= 0 && index < numBits;
    int i = (int)(index >> 6);               // div 64
    int bit = (int)index & 0x3f;           // mod 64
    long bitmask = 1L << bit;
    return (bits[i] & bitmask) != 0;
  }

  /*
  // alternate implementation of get()
  public boolean get1(int index) {
    int i = index >> 6;                // div 64
    int bit = index & 0x3f;            // mod 64
    return ((bits[i]>>>bit) & 0x01) != 0;
    // this does a long shift and a bittest (on x86) vs
    // a long shift, and a long AND, (the test for zero is prob a no-op)
    // testing on a P4 indicates this is slower than (bits[i] & bitmask) != 0;
  }
  */



  public int getBit(int index) {
    assert index >= 0 && index < numBits;
    int i = index >> 6;                // div 64
    int bit = index & 0x3f;            // mod 64
    return ((int)(bits[i]>>>bit)) & 0x01;
  }


  /*
  public boolean get2(int index) {
    int word = index >> 6;            // div 64
    int bit = index & 0x0000003f;     // mod 64
    return (bits[word] << bit) < 0;   // hmmm, this would work if bit order were reversed
    // we could right shift and check for parity bit, if it was available to us.
  }
  */

    public void set(long index) {
    int wordNum = expandingWordNum(index);
    int bit = (int)index & 0x3f;
    long bitmask = 1L << bit;
    bits[wordNum] |= bitmask;
  }


  public void fastSet(int index) {
    assert index >= 0 && index < numBits;
    int wordNum = index >> 6;      // div 64
    int bit = index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] |= bitmask;
  }

  public void fastSet(long index) {
    assert index >= 0 && index < numBits;
    int wordNum = (int)(index >> 6);
    int bit = (int)index & 0x3f;
    long bitmask = 1L << bit;
    bits[wordNum] |= bitmask;
  }


  public void set(long startIndex, long endIndex) {
    if (endIndex <= startIndex) return;

    int startWord = (int)(startIndex>>6);

    // since endIndex is one past the end, this is index of the last
    // word to be changed.
    int endWord   = expandingWordNum(endIndex-1);

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex due to wrap

    if (startWord == endWord) {
      bits[startWord] |= (startmask & endmask);
      return;
    }

    bits[startWord] |= startmask;
    Arrays.fill(bits, startWord+1, endWord, -1L);
    bits[endWord] |= endmask;
  }



  protected int expandingWordNum(long index) {
    int wordNum = (int)(index >> 6);
    if (wordNum>=wlen) {
      ensureCapacity(index+1);
      wlen = wordNum+1;
    }
    assert (numBits = Math.max(numBits, index+1)) >= 0;
    return wordNum;
  }



  public void fastClear(int index) {
    assert index >= 0 && index < numBits;
    int wordNum = index >> 6;
    int bit = index & 0x03f;
    long bitmask = 1L << bit;
    bits[wordNum] &= ~bitmask;
    // hmmm, it takes one more instruction to clear than it does to set... any
    // way to work around this?  If there were only 63 bits per word, we could
    // use a right shift of 10111111...111 in binary to position the 0 in the
    // correct place (using sign extension).
    // Could also use Long.rotateRight() or rotateLeft() *if* they were converted
    // by the JVM into a native instruction.
    // bits[word] &= Long.rotateLeft(0xfffffffe,bit);
  }


  public void fastClear(long index) {
    assert index >= 0 && index < numBits;
    int wordNum = (int)(index >> 6); // div 64
    int bit = (int)index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] &= ~bitmask;
  }

    public void clear(long index) {
    int wordNum = (int)(index >> 6); // div 64
    if (wordNum>=wlen) return;
    int bit = (int)index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] &= ~bitmask;
  }


  public void clear(int startIndex, int endIndex) {
    if (endIndex <= startIndex) return;

    int startWord = (startIndex>>6);
    if (startWord >= wlen) return;

    // since endIndex is one past the end, this is index of the last
    // word to be changed.
    int endWord   = ((endIndex-1)>>6);

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex due to wrap

    // invert masks since we are clearing
    startmask = ~startmask;
    endmask = ~endmask;

    if (startWord == endWord) {
      bits[startWord] &= (startmask | endmask);
      return;
    }

    bits[startWord] &= startmask;

    int middle = Math.min(wlen, endWord);
    Arrays.fill(bits, startWord+1, middle, 0L);
    if (endWord < wlen) {
      bits[endWord] &= endmask;
    }
  }



  public void clear(long startIndex, long endIndex) {
    if (endIndex <= startIndex) return;

    int startWord = (int)(startIndex>>6);
    if (startWord >= wlen) return;

    // since endIndex is one past the end, this is index of the last
    // word to be changed.
    int endWord   = (int)((endIndex-1)>>6);

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex due to wrap

    // invert masks since we are clearing
    startmask = ~startmask;
    endmask = ~endmask;

    if (startWord == endWord) {
      bits[startWord] &= (startmask | endmask);
      return;
    }

    bits[startWord] &= startmask;

    int middle = Math.min(wlen, endWord);
    Arrays.fill(bits, startWord+1, middle, 0L);
    if (endWord < wlen) {
      bits[endWord] &= endmask;
    }
  }




  public boolean getAndSet(int index) {
    assert index >= 0 && index < numBits;
    int wordNum = index >> 6;      // div 64
    int bit = index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    boolean val = (bits[wordNum] & bitmask) != 0;
    bits[wordNum] |= bitmask;
    return val;
  }


  public boolean getAndSet(long index) {
    assert index >= 0 && index < numBits;
    int wordNum = (int)(index >> 6);      // div 64
    int bit = (int)index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    boolean val = (bits[wordNum] & bitmask) != 0;
    bits[wordNum] |= bitmask;
    return val;
  }


  public void fastFlip(int index) {
    assert index >= 0 && index < numBits;
    int wordNum = index >> 6;      // div 64
    int bit = index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] ^= bitmask;
  }


  public void fastFlip(long index) {
    assert index >= 0 && index < numBits;
    int wordNum = (int)(index >> 6);   // div 64
    int bit = (int)index & 0x3f;       // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] ^= bitmask;
  }

    public void flip(long index) {
    int wordNum = expandingWordNum(index);
    int bit = (int)index & 0x3f;       // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] ^= bitmask;
  }


  public boolean flipAndGet(int index) {
    assert index >= 0 && index < numBits;
    int wordNum = index >> 6;      // div 64
    int bit = index & 0x3f;     // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] ^= bitmask;
    return (bits[wordNum] & bitmask) != 0;
  }


  public boolean flipAndGet(long index) {
    assert index >= 0 && index < numBits;
    int wordNum = (int)(index >> 6);   // div 64
    int bit = (int)index & 0x3f;       // mod 64
    long bitmask = 1L << bit;
    bits[wordNum] ^= bitmask;
    return (bits[wordNum] & bitmask) != 0;
  }


  public void flip(long startIndex, long endIndex) {
    if (endIndex <= startIndex) return;
    int startWord = (int)(startIndex>>6);

    // since endIndex is one past the end, this is index of the last
    // word to be changed.
    int endWord   = expandingWordNum(endIndex-1);

    long startmask = -1L << startIndex;
    long endmask = -1L >>> -endIndex;  // 64-(endIndex&0x3f) is the same as -endIndex due to wrap

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


  /*
  public static int pop(long v0, long v1, long v2, long v3) {
    // derived from pop_array by setting last four elems to 0.
    // exchanges one pop() call for 10 elementary operations
    // saving about 7 instructions... is there a better way?
      long twosA=v0 & v1;
      long ones=v0^v1;

      long u2=ones^v2;
      long twosB =(ones&v2)|(u2&v3);
      ones=u2^v3;

      long fours=(twosA&twosB);
      long twos=twosA^twosB;

      return (pop(fours)<<2)
             + (pop(twos)<<1)
             + pop(ones);

  }
  */


    public long cardinality() {
    return BitUtil.pop_array(bits,0,wlen);
  }


  public static long intersectionCount(OpenBitSet a, OpenBitSet b) {
    return BitUtil.pop_intersect(a.bits, b.bits, 0, Math.min(a.wlen, b.wlen));
 }

  public static long unionCount(OpenBitSet a, OpenBitSet b) {
    long tot = BitUtil.pop_union(a.bits, b.bits, 0, Math.min(a.wlen, b.wlen));
    if (a.wlen < b.wlen) {
      tot += BitUtil.pop_array(b.bits, a.wlen, b.wlen-a.wlen);
    } else if (a.wlen > b.wlen) {
      tot += BitUtil.pop_array(a.bits, b.wlen, a.wlen-b.wlen);
    }
    return tot;
  }


  public static long andNotCount(OpenBitSet a, OpenBitSet b) {
    long tot = BitUtil.pop_andnot(a.bits, b.bits, 0, Math.min(a.wlen, b.wlen));
    if (a.wlen > b.wlen) {
      tot += BitUtil.pop_array(a.bits, b.wlen, a.wlen-b.wlen);
    }
    return tot;
  }

  public static long xorCount(OpenBitSet a, OpenBitSet b) {
    long tot = BitUtil.pop_xor(a.bits, b.bits, 0, Math.min(a.wlen, b.wlen));
    if (a.wlen < b.wlen) {
      tot += BitUtil.pop_array(b.bits, a.wlen, b.wlen-a.wlen);
    } else if (a.wlen > b.wlen) {
      tot += BitUtil.pop_array(a.bits, b.wlen, a.wlen-b.wlen);
    }
    return tot;
  }



  public int nextSetBit(int index) {
    int i = index>>6;
    if (i>=wlen) return -1;
    int subIndex = index & 0x3f;      // index within the word
    long word = bits[i] >> subIndex;  // skip all the bits to the right of index

    if (word!=0) {
      return (i<<6) + subIndex + BitUtil.ntz(word);
    }

    while(++i < wlen) {
      word = bits[i];
      if (word!=0) return (i<<6) + BitUtil.ntz(word);
    }

    return -1;
  }


  public long nextSetBit(long index) {
    int i = (int)(index>>>6);
    if (i>=wlen) return -1;
    int subIndex = (int)index & 0x3f; // index within the word
    long word = bits[i] >>> subIndex;  // skip all the bits to the right of index

    if (word!=0) {
      return (((long)i)<<6) + (subIndex + BitUtil.ntz(word));
    }

    while(++i < wlen) {
      word = bits[i];
      if (word!=0) return (((long)i)<<6) + BitUtil.ntz(word);
    }

    return -1;
  }



  public int prevSetBit(int index) {
    int i = index >> 6;
    final int subIndex;
    long word;
    if (i >= wlen) {
      i = wlen - 1;
      if (i < 0) return -1;
      subIndex = 63;  // last possible bit
      word = bits[i];
    } else {
      if (i < 0) return -1;
      subIndex = index & 0x3f;  // index within the word
      word = (bits[i] << (63-subIndex));  // skip all the bits to the left of index
    }

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


  public long prevSetBit(long index) {
    int i = (int) (index >> 6);
    final int subIndex;
    long word;
    if (i >= wlen) {
      i = wlen - 1;
      if (i < 0) return -1;
      subIndex = 63;  // last possible bit
      word = bits[i];
    } else {
      if (i < 0) return -1;
      subIndex = (int)index & 0x3f;  // index within the word
      word = (bits[i] << (63-subIndex));  // skip all the bits to the left of index
    }

    if (word != 0) {
      return (((long)i)<<6) + subIndex - Long.numberOfLeadingZeros(word); // See LUCENE-3197
    }

    while (--i >= 0) {
      word = bits[i];
      if (word !=0 ) {
        return (((long)i)<<6) + 63 - Long.numberOfLeadingZeros(word);
      }
    }

    return -1;
  }

  @Override
  public Object clone() {
    try {
      OpenBitSet obs = (OpenBitSet)super.clone();
      obs.bits = obs.bits.clone();  // hopefully an array clone is as fast(er) than arraycopy
      return obs;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

    public void intersect(OpenBitSet other) {
    int newLen= Math.min(this.wlen,other.wlen);
    long[] thisArr = this.bits;
    long[] otherArr = other.bits;
    // testing against zero can be more efficient
    int pos=newLen;
    while(--pos>=0) {
      thisArr[pos] &= otherArr[pos];
    }
    if (this.wlen > newLen) {
      // fill zeros from the new shorter length to the old length
      Arrays.fill(bits,newLen,this.wlen,0);
    }
    this.wlen = newLen;
  }

    public void union(OpenBitSet other) {
    int newLen = Math.max(wlen,other.wlen);
    ensureCapacityWords(newLen);
    assert (numBits = Math.max(other.numBits, numBits)) >= 0;

    long[] thisArr = this.bits;
    long[] otherArr = other.bits;
    int pos=Math.min(wlen,other.wlen);
    while(--pos>=0) {
      thisArr[pos] |= otherArr[pos];
    }
    if (this.wlen < newLen) {
      System.arraycopy(otherArr, this.wlen, thisArr, this.wlen, newLen-this.wlen);
    }
    this.wlen = newLen;
  }


    public void remove(OpenBitSet other) {
    int idx = Math.min(wlen,other.wlen);
    long[] thisArr = this.bits;
    long[] otherArr = other.bits;
    while(--idx>=0) {
      thisArr[idx] &= ~otherArr[idx];
    }
  }

    public void xor(OpenBitSet other) {
    int newLen = Math.max(wlen,other.wlen);
    ensureCapacityWords(newLen);
    assert (numBits = Math.max(other.numBits, numBits)) >= 0;

    long[] thisArr = this.bits;
    long[] otherArr = other.bits;
    int pos=Math.min(wlen,other.wlen);
    while(--pos>=0) {
      thisArr[pos] ^= otherArr[pos];
    }
    if (this.wlen < newLen) {
      System.arraycopy(otherArr, this.wlen, thisArr, this.wlen, newLen-this.wlen);
    }
    this.wlen = newLen;
  }


  // some BitSet compatability methods

  public void and(OpenBitSet other) {
    intersect(other);
  }

  public void or(OpenBitSet other) {
    union(other);
  }

  public void andNot(OpenBitSet other) {
    remove(other);
  }

  public boolean intersects(OpenBitSet other) {
    int pos = Math.min(this.wlen, other.wlen);
    long[] thisArr = this.bits;
    long[] otherArr = other.bits;
    while (--pos>=0) {
      if ((thisArr[pos] & otherArr[pos])!=0) return true;
    }
    return false;
  }




  public void ensureCapacityWords(int numWords) {
    if (bits.length < numWords) {
      bits = ArrayUtil.grow(bits, numWords);
    }
  }


  public void ensureCapacity(long numBits) {
    ensureCapacityWords(bits2words(numBits));
  }


  public void trimTrailingZeros() {
    int idx = wlen-1;
    while (idx>=0 && bits[idx]==0) idx--;
    wlen = idx+1;
  }

    public static int bits2words(long numBits) {
   return (int)(((numBits-1)>>>6)+1);
  }


    @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OpenBitSet)) return false;
    OpenBitSet a;
    OpenBitSet b = (OpenBitSet)o;
    // make a the larger set.
    if (b.wlen > this.wlen) {
      a = b; b=this;
    } else {
      a=this;
    }

    // check for any set bits out of the range of b
    for (int i=a.wlen-1; i>=b.wlen; i--) {
      if (a.bits[i]!=0) return false;
    }

    for (int i=b.wlen-1; i>=0; i--) {
      if (a.bits[i] != b.bits[i]) return false;
    }

    return true;
  }


  @Override
  public int hashCode() {
    // Start with a zero hash and use a mix that results in zero if the input is zero.
    // This effectively truncates trailing zeros without an explicit check.
    long h = 0;
    for (int i = bits.length; --i>=0;) {
      h ^= bits[i];
      h = (h << 1) | (h >>> 63); // rotate left
    }
    // fold leftmost bits into right and add a constant to prevent
    // empty sets from returning 0, which is too common.
    return (int)((h>>32) ^ h) + 0x98761234;
  }

}


