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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.util.fst;

import java.io.IOException;

class BitTableUtil {

  static boolean isBitSet(int bitIndex, FST.BytesReader reader) throws IOException {
    assert bitIndex >= 0 : "bitIndex=" + bitIndex;
    reader.skipBytes(bitIndex >> 3);
    return (readByte(reader) & (1L << (bitIndex & (Byte.SIZE - 1)))) != 0;
  }


  static int countBits(int bitTableBytes, FST.BytesReader reader) throws IOException {
    assert bitTableBytes >= 0 : "bitTableBytes=" + bitTableBytes;
    int bitCount = 0;
    for (int i = bitTableBytes >> 3; i > 0; i--) {
      // Count the bits set for all plain longs.
      bitCount += Long.bitCount(read8Bytes(reader));
    }
    int numRemainingBytes;
    if ((numRemainingBytes = bitTableBytes & (Long.BYTES - 1)) != 0) {
      bitCount += Long.bitCount(readUpTo8Bytes(numRemainingBytes, reader));
    }
    return bitCount;
  }

  static int countBitsUpTo(int bitIndex, FST.BytesReader reader) throws IOException {
    assert bitIndex >= 0 : "bitIndex=" + bitIndex;
    int bitCount = 0;
    for (int i = bitIndex >> 6; i > 0; i--) {
      // Count the bits set for all plain longs.
      bitCount += Long.bitCount(read8Bytes(reader));
    }
    int remainingBits;
    if ((remainingBits = bitIndex & (Long.SIZE - 1)) != 0) {
      int numRemainingBytes = (remainingBits + (Byte.SIZE - 1)) >> 3;
      // Prepare a mask with 1s on the right up to bitIndex exclusive.
      long mask = (1L << bitIndex) - 1L; // Shifts are mod 64.
      // Count the bits set only within the mask part, so up to bitIndex exclusive.
      bitCount += Long.bitCount(readUpTo8Bytes(numRemainingBytes, reader) & mask);
    }
    return bitCount;
  }

  static int nextBitSet(int bitIndex, int bitTableBytes, FST.BytesReader reader) throws IOException {
    assert bitIndex >= -1 && bitIndex < bitTableBytes * Byte.SIZE : "bitIndex=" + bitIndex + " bitTableBytes=" + bitTableBytes;
    int byteIndex = bitIndex / Byte.SIZE;
    int mask = -1 << ((bitIndex + 1) & (Byte.SIZE - 1));
    int i;
    if (mask == -1 && bitIndex != -1) {
      reader.skipBytes(byteIndex + 1);
      i = 0;
    } else {
      reader.skipBytes(byteIndex);
      i = (reader.readByte() & 0xFF) & mask;
    }
    while (i == 0) {
      if (++byteIndex == bitTableBytes) {
        return -1;
      }
      i = reader.readByte() & 0xFF;
    }
    return Integer.numberOfTrailingZeros(i) + (byteIndex << 3);
  }

  static int previousBitSet(int bitIndex, FST.BytesReader reader) throws IOException {
    assert bitIndex >= 0 : "bitIndex=" + bitIndex;
    int byteIndex = bitIndex >> 3;
    reader.skipBytes(byteIndex);
    int mask = (1 << (bitIndex & (Byte.SIZE - 1))) - 1;
    int i = (reader.readByte() & 0xFF) & mask;
    while (i == 0) {
      if (byteIndex-- == 0) {
        return -1;
      }
      reader.skipBytes(-2); // FST.BytesReader implementations support negative skip.
      i = reader.readByte() & 0xFF;
    }
    return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(i) + (byteIndex << 3);
  }

  private static long readByte(FST.BytesReader reader) throws IOException {
    return reader.readByte() & 0xFFL;
  }

  private static long readUpTo8Bytes(int numBytes, FST.BytesReader reader) throws IOException {
    assert numBytes > 0 && numBytes <= 8 : "numBytes=" + numBytes;
    long l = readByte(reader);
    int shift = 0;
    while (--numBytes != 0) {
      l |= readByte(reader) << (shift += 8);
    }
    return l;
  }

  private static long read8Bytes(FST.BytesReader reader) throws IOException {
    return readByte(reader)
        | readByte(reader) << 8
        | readByte(reader) << 16
        | readByte(reader) << 24
        | readByte(reader) << 32
        | readByte(reader) << 40
        | readByte(reader) << 48
        | readByte(reader) << 56;
  }
}