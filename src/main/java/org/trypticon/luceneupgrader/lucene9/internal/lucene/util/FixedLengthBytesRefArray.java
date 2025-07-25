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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util;

import java.util.Comparator;

/**
 * Just like {@link BytesRefArray} except all values have the same length.
 *
 * <p><b>Note: This class is not Thread-Safe!</b>
 *
 * @lucene.internal
 * @lucene.experimental
 */
final class FixedLengthBytesRefArray implements SortableBytesRefArray {
  private final int valueLength;
  private final int valuesPerBlock;

  /** How many values have been appended */
  private int size;

  /** How many blocks are used */
  private int currentBlock = -1;

  private int nextEntry;

  private byte[][] blocks;

  /** Creates a new {@link BytesRefArray} with a counter to track allocated bytes */
  public FixedLengthBytesRefArray(int valueLength) {
    this.valueLength = valueLength;

    // ~32K per page, unless each value is > 32K:
    valuesPerBlock = Math.max(1, 32768 / valueLength);
    nextEntry = valuesPerBlock;
    blocks = new byte[0][];
  }

  /** Clears this {@link BytesRefArray} */
  @Override
  public void clear() {
    size = 0;
    blocks = new byte[0][];
    currentBlock = -1;
    nextEntry = valuesPerBlock;
  }

  /**
   * Appends a copy of the given {@link BytesRef} to this {@link BytesRefArray}.
   *
   * @param bytes the bytes to append
   * @return the index of the appended bytes
   */
  @Override
  public int append(BytesRef bytes) {
    if (bytes.length != valueLength) {
      throw new IllegalArgumentException(
          "value length is " + bytes.length + " but is supposed to always be " + valueLength);
    }
    if (nextEntry == valuesPerBlock) {
      currentBlock++;
      if (currentBlock == blocks.length) {
        int size = ArrayUtil.oversize(currentBlock + 1, RamUsageEstimator.NUM_BYTES_OBJECT_REF);
        byte[][] next = new byte[size][];
        System.arraycopy(blocks, 0, next, 0, blocks.length);
        blocks = next;
      }
      blocks[currentBlock] = new byte[valuesPerBlock * valueLength];
      nextEntry = 0;
    }

    System.arraycopy(
        bytes.bytes, bytes.offset, blocks[currentBlock], nextEntry * valueLength, valueLength);
    nextEntry++;

    return size++;
  }

  /**
   * Returns the current size of this {@link FixedLengthBytesRefArray}
   *
   * @return the current size of this {@link FixedLengthBytesRefArray}
   */
  @Override
  public int size() {
    return size;
  }

  private int[] sort(final Comparator<BytesRef> comp) {
    final int[] orderedEntries = new int[size()];
    for (int i = 0; i < orderedEntries.length; i++) {
      orderedEntries[i] = i;
    }

    new StringSorter(comp) {

      {
        scratchBytes1.length = valueLength;
        scratchBytes2.length = valueLength;
        pivot.length = valueLength;
      }

      @Override
      protected void get(BytesRefBuilder builder, BytesRef result, int i) {
        final int index = orderedEntries[i];
        result.bytes = blocks[index / valuesPerBlock];
        result.offset = (index % valuesPerBlock) * valueLength;
      }

      @Override
      protected void swap(int i, int j) {
        int o = orderedEntries[i];
        orderedEntries[i] = orderedEntries[j];
        orderedEntries[j] = o;
      }
    }.sort(0, size());
    return orderedEntries;
  }

  /**
   * Returns a {@link BytesRefIterator} with point in time semantics. The iterator provides access
   * to all so far appended {@link BytesRef} instances.
   *
   * <p>The iterator will iterate the byte values in the order specified by the comparator.
   *
   * <p>This is a non-destructive operation.
   */
  @Override
  public BytesRefIterator iterator(final Comparator<BytesRef> comp) {
    final BytesRef result = new BytesRef();
    result.length = valueLength;
    final int size = size();
    final int[] indices = sort(comp);
    return new BytesRefIterator() {
      int pos = 0;

      @Override
      public BytesRef next() {
        if (pos < size) {
          int index = indices[pos];
          pos++;
          result.bytes = blocks[index / valuesPerBlock];
          result.offset = (index % valuesPerBlock) * valueLength;
          return result;
        }
        return null;
      }
    };
  }
}
