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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.CompoundFileDirectory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BitUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.MutableBits;

// pkg-private: if this thing is generally useful then it can go back in .util,
// but the serialization must be here underneath the codec.
final class BitVector implements Cloneable, MutableBits {

  private byte[] bits;
  private int size;
  private int count;
  private int version;

  public BitVector(int n) {
    size = n;
    bits = new byte[getNumBytes(size)];
    count = 0;
  }

  BitVector(byte[] bits, int size) {
    this.bits = bits;
    this.size = size;
    count = -1;
  }
  
  private int getNumBytes(int size) {
    int bytesLength = size >>> 3;
    if ((size & 7) != 0) {
      bytesLength++;
    }
    return bytesLength;
  }
  
  @Override
  public BitVector clone() {
    byte[] copyBits = new byte[bits.length];
    System.arraycopy(bits, 0, copyBits, 0, bits.length);
    BitVector clone = new BitVector(copyBits, size);
    clone.count = count;
    return clone;
  }
  
  public final void set(int bit) {
    if (bit >= size) {
      throw new ArrayIndexOutOfBoundsException("bit=" + bit + " size=" + size);
    }
    bits[bit >> 3] |= 1 << (bit & 7);
    count = -1;
  }

  public final boolean getAndSet(int bit) {
    if (bit >= size) {
      throw new ArrayIndexOutOfBoundsException("bit=" + bit + " size=" + size);
    }
    final int pos = bit >> 3;
    final int v = bits[pos];
    final int flag = 1 << (bit & 7);
    if ((flag & v) != 0)
      return true;
    else {
      bits[pos] = (byte) (v | flag);
      if (count != -1) {
        count++;
        assert count <= size;
      }
      return false;
    }
  }

  @Override
  public final void clear(int bit) {
    if (bit >= size) {
      throw new ArrayIndexOutOfBoundsException(bit);
    }
    bits[bit >> 3] &= ~(1 << (bit & 7));
    count = -1;
  }

  public final boolean getAndClear(int bit) {
    if (bit >= size) {
      throw new ArrayIndexOutOfBoundsException(bit);
    }
    final int pos = bit >> 3;
    final int v = bits[pos];
    final int flag = 1 << (bit & 7);
    if ((flag & v) == 0) {
      return false;
    } else {
      bits[pos] &= ~flag;
      if (count != -1) {
        count--;
        assert count >= 0;
      }
      return true;
    }
  }

  @Override
  public final boolean get(int bit) {
    assert bit >= 0 && bit < size: "bit " + bit + " is out of bounds 0.." + (size-1);
    return (bits[bit >> 3] & (1 << (bit & 7))) != 0;
  }

  public final int size() {
    return size;
  }

  @Override
  public int length() {
    return size;
  }

  public final int count() {
    // if the vector has been modified
    if (count == -1) {
      int c = 0;
      int end = bits.length;
      for (int i = 0; i < end; i++) {
        c += BitUtil.bitCount(bits[i]);  // sum bits per byte
      }
      count = c;
    }
    assert count <= size: "count=" + count + " size=" + size;
    return count;
  }

  public final int getRecomputedCount() {
    int c = 0;
    int end = bits.length;
    for (int i = 0; i < end; i++) {
      c += BitUtil.bitCount(bits[i]);  // sum bits per byte
    }
    return c;
  }



  private static String CODEC = "BitVector";

  // Version before version tracking was added:
  public final static int VERSION_PRE = -1;

  // First version:
  public final static int VERSION_START = 0;

  // Changed DGaps to encode gaps between cleared bits, not
  // set:
  public final static int VERSION_DGAPS_CLEARED = 1;
  
  // added checksum
  public final static int VERSION_CHECKSUM = 2;

  // Increment version to change it:
  public final static int VERSION_CURRENT = VERSION_CHECKSUM;

  public int getVersion() {
    return version;
  }

  public final void write(Directory d, String name, IOContext context) throws IOException {
    assert !(d instanceof CompoundFileDirectory);
    IndexOutput output = d.createOutput(name, context);
    try {
      output.writeInt(-2);
      CodecUtil.writeHeader(output, CODEC, VERSION_CURRENT);
      if (isSparse()) { 
        // sparse bit-set more efficiently saved as d-gaps.
        writeClearedDgaps(output);
      } else {
        writeBits(output);
      }
      CodecUtil.writeFooter(output);
      assert verifyCount();
    } finally {
      IOUtils.close(output);
    }
  }

  public void invertAll() {
    if (count != -1) {
      count = size - count;
    }
    if (bits.length > 0) {
      for(int idx=0;idx<bits.length;idx++) {
        bits[idx] = (byte) (~bits[idx]);
      }
      clearUnusedBits();
    }
  }

  private void clearUnusedBits() {
    // Take care not to invert the "unused" bits in the
    // last byte:
    if (bits.length > 0) {
      final int lastNBits = size & 7;
      if (lastNBits != 0) {
        final int mask = (1 << lastNBits)-1;
        bits[bits.length-1] &= mask;
      }
    }
  }

  public void setAll() {
    Arrays.fill(bits, (byte) 0xff);
    clearUnusedBits();
    count = size;
  }
     
  private void writeBits(IndexOutput output) throws IOException {
    output.writeInt(size());        // write size
    output.writeInt(count());       // write count
    output.writeBytes(bits, bits.length);
  }
  
  private void writeClearedDgaps(IndexOutput output) throws IOException {
    output.writeInt(-1);            // mark using d-gaps                         
    output.writeInt(size());        // write size
    output.writeInt(count());       // write count
    int last=0;
    int numCleared = size()-count();
    for (int i=0; i<bits.length && numCleared>0; i++) {
      if (bits[i] != (byte) 0xff) {
        output.writeVInt(i-last);
        output.writeByte(bits[i]);
        last = i;
        numCleared -= (8-BitUtil.bitCount(bits[i]));
        assert numCleared >= 0 || (i == (bits.length-1) && numCleared == -(8-(size&7)));
      }
    }
  }

  private boolean isSparse() {

    final int clearedCount = size() - count();
    if (clearedCount == 0) {
      return true;
    }

    final int avgGapLength = bits.length / clearedCount;

    // expected number of bytes for vInt encoding of each gap
    final int expectedDGapBytes;
    if (avgGapLength <= (1<< 7)) {
      expectedDGapBytes = 1;
    } else if (avgGapLength <= (1<<14)) {
      expectedDGapBytes = 2;
    } else if (avgGapLength <= (1<<21)) {
      expectedDGapBytes = 3;
    } else if (avgGapLength <= (1<<28)) {
      expectedDGapBytes = 4;
    } else {
      expectedDGapBytes = 5;
    }

    // +1 because we write the byte itself that contains the
    // set bit
    final int bytesPerSetBit = expectedDGapBytes + 1;
    
    // note: adding 32 because we start with ((int) -1) to indicate d-gaps format.
    final long expectedBits = 32 + 8 * bytesPerSetBit * clearedCount;

    // note: factor is for read/write of byte-arrays being faster than vints.  
    final long factor = 10;  
    return factor * expectedBits < size();
  }

  public BitVector(Directory d, String name, IOContext context) throws IOException {
    ChecksumIndexInput input = d.openChecksumInput(name, context);

    try {
      final int firstInt = input.readInt();

      if (firstInt == -2) {
        // New format, with full header & version:
        version = CodecUtil.checkHeader(input, CODEC, VERSION_START, VERSION_CURRENT);
        size = input.readInt();
      } else {
        version = VERSION_PRE;
        size = firstInt;
      }
      if (size == -1) {
        if (version >= VERSION_DGAPS_CLEARED) {
          readClearedDgaps(input);
        } else {
          readSetDgaps(input);
        }
      } else {
        readBits(input);
      }

      if (version < VERSION_DGAPS_CLEARED) {
        invertAll();
      }

      if (version >= VERSION_CHECKSUM) {
        CodecUtil.checkFooter(input);
      } else if (version >= VERSION_DGAPS_CLEARED) {
        CodecUtil.checkEOF(input);
      } // otherwise, before this we cannot even check that we read the entire file due to bugs in those versions!!!!
      assert verifyCount();
    } finally {
      input.close();
    }
  }

  // asserts only
  private boolean verifyCount() {
    assert count != -1;
    final int countSav = count;
    count = -1;
    assert countSav == count(): "saved count was " + countSav + " but recomputed count is " + count;
    return true;
  }

  private void readBits(IndexInput input) throws IOException {
    count = input.readInt();        // read count
    bits = new byte[getNumBytes(size)];     // allocate bits
    input.readBytes(bits, 0, bits.length);
  }

  private void readSetDgaps(IndexInput input) throws IOException {
    size = input.readInt();       // (re)read size
    count = input.readInt();        // read count
    bits = new byte[getNumBytes(size)];     // allocate bits
    int last=0;
    int n = count();
    while (n>0) {
      last += input.readVInt();
      bits[last] = input.readByte();
      n -= BitUtil.bitCount(bits[last]);
      assert n >= 0;
    }          
  }

  private void readClearedDgaps(IndexInput input) throws IOException {
    size = input.readInt();       // (re)read size
    count = input.readInt();        // read count
    bits = new byte[getNumBytes(size)];     // allocate bits
    Arrays.fill(bits, (byte) 0xff);
    clearUnusedBits();
    int last=0;
    int numCleared = size()-count();
    while (numCleared>0) {
      last += input.readVInt();
      bits[last] = input.readByte();
      numCleared -= 8-BitUtil.bitCount(bits[last]);
      assert numCleared >= 0 || (last == (bits.length-1) && numCleared == -(8-(size&7)));
    }
  }
}
