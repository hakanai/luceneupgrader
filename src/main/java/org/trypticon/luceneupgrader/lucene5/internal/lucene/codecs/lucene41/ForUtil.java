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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene41;
import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedInts.Decoder;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedInts.FormatAndBits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedInts;

import static org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene41.Lucene41PostingsFormat.BLOCK_SIZE;

@Deprecated
final class ForUtil {

  private static final int ALL_VALUES_EQUAL = 0;

  static final int MAX_ENCODED_SIZE = BLOCK_SIZE * 4;

  static final int MAX_DATA_SIZE;
  static {
    int maxDataSize = 0;
    for(int version=PackedInts.VERSION_START;version<=PackedInts.VERSION_CURRENT;version++) {
      for (PackedInts.Format format : PackedInts.Format.values()) {
        for (int bpv = 1; bpv <= 32; ++bpv) {
          if (!format.isSupported(bpv)) {
            continue;
          }
          final PackedInts.Decoder decoder = PackedInts.getDecoder(format, version, bpv);
          final int iterations = computeIterations(decoder);
          maxDataSize = Math.max(maxDataSize, iterations * decoder.byteValueCount());
        }
      }
    }
    MAX_DATA_SIZE = maxDataSize;
  }

  private static int computeIterations(PackedInts.Decoder decoder) {
    return (int) Math.ceil((float) BLOCK_SIZE / decoder.byteValueCount());
  }

  private static int encodedSize(PackedInts.Format format, int packedIntsVersion, int bitsPerValue) {
    final long byteCount = format.byteCount(packedIntsVersion, BLOCK_SIZE, bitsPerValue);
    assert byteCount >= 0 && byteCount <= Integer.MAX_VALUE : byteCount;
    return (int) byteCount;
  }

  private final int[] encodedSizes;
  private final PackedInts.Encoder[] encoders;
  private final PackedInts.Decoder[] decoders;
  private final int[] iterations;

  ForUtil(float acceptableOverheadRatio, DataOutput out) throws IOException {
    out.writeVInt(PackedInts.VERSION_CURRENT);
    encodedSizes = new int[33];
    encoders = new PackedInts.Encoder[33];
    decoders = new PackedInts.Decoder[33];
    iterations = new int[33];

    for (int bpv = 1; bpv <= 32; ++bpv) {
      final FormatAndBits formatAndBits = PackedInts.fastestFormatAndBits(
          BLOCK_SIZE, bpv, acceptableOverheadRatio);
      assert formatAndBits.format.isSupported(formatAndBits.bitsPerValue);
      assert formatAndBits.bitsPerValue <= 32;
      encodedSizes[bpv] = encodedSize(formatAndBits.format, PackedInts.VERSION_CURRENT, formatAndBits.bitsPerValue);
      encoders[bpv] = PackedInts.getEncoder(
          formatAndBits.format, PackedInts.VERSION_CURRENT, formatAndBits.bitsPerValue);
      decoders[bpv] = PackedInts.getDecoder(
          formatAndBits.format, PackedInts.VERSION_CURRENT, formatAndBits.bitsPerValue);
      iterations[bpv] = computeIterations(decoders[bpv]);

      out.writeVInt(formatAndBits.format.getId() << 5 | (formatAndBits.bitsPerValue - 1));
    }
  }

  ForUtil(DataInput in) throws IOException {
    int packedIntsVersion = in.readVInt();
    PackedInts.checkVersion(packedIntsVersion);
    encodedSizes = new int[33];
    encoders = new PackedInts.Encoder[33];
    decoders = new PackedInts.Decoder[33];
    iterations = new int[33];

    for (int bpv = 1; bpv <= 32; ++bpv) {
      final int code = in.readVInt();
      final int formatId = code >>> 5;
      final int bitsPerValue = (code & 31) + 1;

      final PackedInts.Format format = PackedInts.Format.byId(formatId);
      assert format.isSupported(bitsPerValue);
      encodedSizes[bpv] = encodedSize(format, packedIntsVersion, bitsPerValue);
      encoders[bpv] = PackedInts.getEncoder(
          format, packedIntsVersion, bitsPerValue);
      decoders[bpv] = PackedInts.getDecoder(
          format, packedIntsVersion, bitsPerValue);
      iterations[bpv] = computeIterations(decoders[bpv]);
    }
  }

  void writeBlock(int[] data, byte[] encoded, IndexOutput out) throws IOException {
    if (isAllEqual(data)) {
      out.writeByte((byte) ALL_VALUES_EQUAL);
      out.writeVInt(data[0]);
      return;
    }

    final int numBits = bitsRequired(data);
    assert numBits > 0 && numBits <= 32 : numBits;
    final PackedInts.Encoder encoder = encoders[numBits];
    final int iters = iterations[numBits];
    assert iters * encoder.byteValueCount() >= BLOCK_SIZE;
    final int encodedSize = encodedSizes[numBits];
    assert iters * encoder.byteBlockCount() >= encodedSize;

    out.writeByte((byte) numBits);

    encoder.encode(data, 0, encoded, 0, iters);
    out.writeBytes(encoded, encodedSize);
  }

  void readBlock(IndexInput in, byte[] encoded, int[] decoded) throws IOException {
    final int numBits = in.readByte();
    assert numBits <= 32 : numBits;

    if (numBits == ALL_VALUES_EQUAL) {
      final int value = in.readVInt();
      Arrays.fill(decoded, 0, BLOCK_SIZE, value);
      return;
    }

    final int encodedSize = encodedSizes[numBits];
    in.readBytes(encoded, 0, encodedSize);

    final PackedInts.Decoder decoder = decoders[numBits];
    final int iters = iterations[numBits];
    assert iters * decoder.byteValueCount() >= BLOCK_SIZE;

    decoder.decode(encoded, 0, decoded, 0, iters);
  }

  void skipBlock(IndexInput in) throws IOException {
    final int numBits = in.readByte();
    if (numBits == ALL_VALUES_EQUAL) {
      in.readVInt();
      return;
    }
    assert numBits > 0 && numBits <= 32 : numBits;
    final int encodedSize = encodedSizes[numBits];
    in.seek(in.getFilePointer() + encodedSize);
  }

  private static boolean isAllEqual(final int[] data) {
    final int v = data[0];
    for (int i = 1; i < BLOCK_SIZE; ++i) {
      if (data[i] != v) {
        return false;
      }
    }
    return true;
  }

  private static int bitsRequired(final int[] data) {
    long or = 0;
    for (int i = 0; i < BLOCK_SIZE; ++i) {
      assert data[i] >= 0;
      or |= data[i];
    }
    return PackedInts.bitsRequired(or);
  }

}
