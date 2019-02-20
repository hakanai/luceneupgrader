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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed;


import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexOutput;

public final class DirectWriter {
  final int bitsPerValue;
  final long numValues;
  final IndexOutput output;
  
  long count;
  boolean finished;
  
  // for now, just use the existing writer under the hood
  int off;
  final byte[] nextBlocks;
  final long[] nextValues;
  final BulkOperation encoder;
  final int iterations;
  
  DirectWriter(IndexOutput output, long numValues, int bitsPerValue) {
    this.output = output;
    this.numValues = numValues;
    this.bitsPerValue = bitsPerValue;
    encoder = BulkOperation.of(PackedInts.Format.PACKED, bitsPerValue);
    iterations = encoder.computeIterations((int) Math.min(numValues, Integer.MAX_VALUE), PackedInts.DEFAULT_BUFFER_SIZE);
    nextBlocks = new byte[iterations * encoder.byteBlockCount()];
    nextValues = new long[iterations * encoder.byteValueCount()];
  }
  
  public void add(long l) throws IOException {
    assert bitsPerValue == 64 || (l >= 0 && l <= PackedInts.maxValue(bitsPerValue)) : bitsPerValue;
    assert !finished;
    if (count >= numValues) {
      throw new EOFException("Writing past end of stream");
    }
    nextValues[off++] = l;
    if (off == nextValues.length) {
      flush();
    }
    count++;
  }
  
  private void flush() throws IOException {
    encoder.encode(nextValues, 0, nextBlocks, 0, iterations);
    final int blockCount = (int) PackedInts.Format.PACKED.byteCount(PackedInts.VERSION_CURRENT, off, bitsPerValue);
    output.writeBytes(nextBlocks, blockCount);
    Arrays.fill(nextValues, 0L);
    off = 0;
  }

  public void finish() throws IOException {
    if (count != numValues) {
      throw new IllegalStateException("Wrong number of values added, expected: " + numValues + ", got: " + count);
    }
    assert !finished;
    flush();
    // pad for fast io: we actually only need this for certain BPV, but its just 3 bytes...
    for (int i = 0; i < 3; i++) {
      output.writeByte((byte) 0);
    }
    finished = true;
  }
  
  public static DirectWriter getInstance(IndexOutput output, long numValues, int bitsPerValue) {
    if (Arrays.binarySearch(SUPPORTED_BITS_PER_VALUE, bitsPerValue) < 0) {
      throw new IllegalArgumentException("Unsupported bitsPerValue " + bitsPerValue + ". Did you use bitsRequired?");
    }
    return new DirectWriter(output, numValues, bitsPerValue);
  }
  

  private static int roundBits(int bitsRequired) {
    int index = Arrays.binarySearch(SUPPORTED_BITS_PER_VALUE, bitsRequired);
    if (index < 0) {
      return SUPPORTED_BITS_PER_VALUE[-index-1];
    } else {
      return bitsRequired;
    }
  }

  public static int bitsRequired(long maxValue) {
    return roundBits(PackedInts.bitsRequired(maxValue));
  }

  public static int unsignedBitsRequired(long maxValue) {
    return roundBits(PackedInts.unsignedBitsRequired(maxValue));
  }

  final static int SUPPORTED_BITS_PER_VALUE[] = new int[] {
    1, 2, 4, 8, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64
  };
}
