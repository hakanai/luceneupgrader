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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ByteBlockPool.Allocator;


public final class RecyclingByteBlockAllocator extends ByteBlockPool.Allocator {
  private byte[][] freeByteBlocks;
  private final int maxBufferedBlocks;
  private int freeBlocks = 0;
  private final Counter bytesUsed;
  public static final int DEFAULT_BUFFERED_BLOCKS = 64;

  public RecyclingByteBlockAllocator(int blockSize, int maxBufferedBlocks,
      Counter bytesUsed) {
    super(blockSize);
    freeByteBlocks = new byte[maxBufferedBlocks][];
    this.maxBufferedBlocks = maxBufferedBlocks;
    this.bytesUsed = bytesUsed;
  }

  public RecyclingByteBlockAllocator(int blockSize, int maxBufferedBlocks) {
    this(blockSize, maxBufferedBlocks, Counter.newCounter(false));
  }

  public RecyclingByteBlockAllocator() {
    this(ByteBlockPool.BYTE_BLOCK_SIZE, 64, Counter.newCounter(false));
  }

  @Override
  public byte[] getByteBlock() {
    if (freeBlocks == 0) {
      bytesUsed.addAndGet(blockSize);
      return new byte[blockSize];
    }
    final byte[] b = freeByteBlocks[--freeBlocks];
    freeByteBlocks[freeBlocks] = null;
    return b;
  }

  @Override
  public void recycleByteBlocks(byte[][] blocks, int start, int end) {
    final int numBlocks = Math.min(maxBufferedBlocks - freeBlocks, end - start);
    final int size = freeBlocks + numBlocks;
    if (size >= freeByteBlocks.length) {
      final byte[][] newBlocks = new byte[ArrayUtil.oversize(size,
          RamUsageEstimator.NUM_BYTES_OBJECT_REF)][];
      System.arraycopy(freeByteBlocks, 0, newBlocks, 0, freeBlocks);
      freeByteBlocks = newBlocks;
    }
    final int stop = start + numBlocks;
    for (int i = start; i < stop; i++) {
      freeByteBlocks[freeBlocks++] = blocks[i];
      blocks[i] = null;
    }
    for (int i = stop; i < end; i++) {
      blocks[i] = null;
    }
    bytesUsed.addAndGet(-(end - stop) * blockSize);
    assert bytesUsed.get() >= 0;
  }

  public int numBufferedBlocks() {
    return freeBlocks;
  }

  public long bytesUsed() {
    return bytesUsed.get();
  }

  public int maxBufferedBlocks() {
    return maxBufferedBlocks;
  }

  public int freeBlocks(int num) {
    assert num >= 0 : "free blocks must be >= 0 but was: "+ num;
    final int stop;
    final int count;
    if (num > freeBlocks) {
      stop = 0;
      count = freeBlocks;
    } else {
      stop = freeBlocks - num;
      count = num;
    }
    while (freeBlocks > stop) {
      freeByteBlocks[--freeBlocks] = null;
    }
    bytesUsed.addAndGet(-count*blockSize);
    assert bytesUsed.get() >= 0;
    return count;
  }
}