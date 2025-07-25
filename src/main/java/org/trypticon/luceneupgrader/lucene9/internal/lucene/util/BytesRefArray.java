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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * A simple append only random-access {@link BytesRef} array that stores full copies of the appended
 * bytes in a {@link ByteBlockPool}.
 *
 * <p><b>Note: This class is not Thread-Safe!</b>
 *
 * @lucene.internal
 * @lucene.experimental
 */
public final class BytesRefArray implements SortableBytesRefArray {
  private final ByteBlockPool pool;
  private int[] offsets = new int[1];
  private int lastElement = 0;
  private int currentOffset = 0;
  private final Counter bytesUsed;

  /** Creates a new {@link BytesRefArray} with a counter to track allocated bytes */
  public BytesRefArray(Counter bytesUsed) {
    this.pool = new ByteBlockPool(new ByteBlockPool.DirectTrackingAllocator(bytesUsed));
    pool.nextBuffer();
    bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER * (long) Integer.BYTES);
    this.bytesUsed = bytesUsed;
  }

  /** Clears this {@link BytesRefArray} */
  @Override
  public void clear() {
    lastElement = 0;
    currentOffset = 0;
    // TODO: it's trappy that this does not return storage held by int[] offsets array!
    Arrays.fill(offsets, 0);
    pool.reset(false, true); // no need to 0 fill the buffers we control the allocator
  }

  /**
   * Appends a copy of the given {@link BytesRef} to this {@link BytesRefArray}.
   *
   * @param bytes the bytes to append
   * @return the index of the appended bytes
   */
  @Override
  public int append(BytesRef bytes) {
    if (lastElement >= offsets.length) {
      int oldLen = offsets.length;
      offsets = ArrayUtil.grow(offsets, offsets.length + 1);
      bytesUsed.addAndGet((offsets.length - oldLen) * (long) Integer.BYTES);
    }
    pool.append(bytes);
    offsets[lastElement++] = currentOffset;
    currentOffset += bytes.length;
    return lastElement - 1;
  }

  /**
   * Returns the current size of this {@link BytesRefArray}
   *
   * @return the current size of this {@link BytesRefArray}
   */
  @Override
  public int size() {
    return lastElement;
  }

  /**
   * Returns the <i>n'th</i> element of this {@link BytesRefArray}
   *
   * @param spare a spare {@link BytesRef} instance
   * @param index the elements index to retrieve
   * @return the <i>n'th</i> element of this {@link BytesRefArray}
   */
  public BytesRef get(BytesRefBuilder spare, int index) {
    Objects.checkIndex(index, lastElement);
    int offset = offsets[index];
    int length = index == lastElement - 1 ? currentOffset - offset : offsets[index + 1] - offset;
    spare.growNoCopy(length);
    spare.setLength(length);
    pool.readBytes(offset, spare.bytes(), 0, spare.length());
    return spare.get();
  }

  /**
   * Used only by sort below, to set a {@link BytesRef} with the specified slice, avoiding copying
   * bytes in the common case when the slice is contained in a single block in the byte block pool.
   */
  private void setBytesRef(BytesRefBuilder spare, BytesRef result, int index) {
    Objects.checkIndex(index, lastElement);
    int offset = offsets[index];
    int length;
    if (index == lastElement - 1) {
      length = currentOffset - offset;
    } else {
      length = offsets[index + 1] - offset;
    }
    pool.setBytesRef(spare, result, offset, length);
  }

  /**
   * Returns a {@link SortState} representing the order of elements in this array. This is a
   * non-destructive operation.
   *
   * @param comp The comparator to compare {@link BytesRef}s. A radix sort optimization is available
   *     if the comparator implements {@link BytesRefComparator}
   * @param stable If the sort needs to be stable
   * @return A {@link SortState} that could be used in {@link BytesRefArray#iterator(SortState)}
   */
  public SortState sort(final Comparator<BytesRef> comp, boolean stable) {
    final int[] orderedEntries = new int[size()];
    for (int i = 0; i < orderedEntries.length; i++) {
      orderedEntries[i] = i;
    }
    StringSorter sorter;
    if (stable) {
      sorter =
          new StableStringSorter(comp) {

            private final int[] tmp = new int[size()];

            @Override
            protected void get(BytesRefBuilder builder, BytesRef result, int i) {
              BytesRefArray.this.setBytesRef(builder, result, orderedEntries[i]);
            }

            @Override
            protected void save(int i, int j) {
              tmp[j] = orderedEntries[i];
            }

            @Override
            protected void restore(int i, int j) {
              System.arraycopy(tmp, i, orderedEntries, i, j - i);
            }

            @Override
            protected void swap(int i, int j) {
              int o = orderedEntries[i];
              orderedEntries[i] = orderedEntries[j];
              orderedEntries[j] = o;
            }
          };
    } else {
      sorter =
          new StringSorter(comp) {
            @Override
            protected void get(BytesRefBuilder builder, BytesRef result, int i) {
              BytesRefArray.this.setBytesRef(builder, result, orderedEntries[i]);
            }

            @Override
            protected void swap(int i, int j) {
              int o = orderedEntries[i];
              orderedEntries[i] = orderedEntries[j];
              orderedEntries[j] = o;
            }
          };
    }

    sorter.sort(0, size());
    return new SortState(orderedEntries);
  }

  /** sugar for {@link #iterator(Comparator)} with a <code>null</code> comparator */
  public BytesRefIterator iterator() {
    return iterator((SortState) null);
  }

  /**
   * Returns a {@link BytesRefIterator} with point in time semantics. The iterator provides access
   * to all so far appended {@link BytesRef} instances.
   *
   * <p>If a non <code>null</code> {@link Comparator} is provided the iterator will iterate the byte
   * values in the order specified by the comparator. Otherwise the order is the same as the values
   * were appended.
   *
   * <p>This is a non-destructive operation.
   */
  @Override
  public BytesRefIterator iterator(final Comparator<BytesRef> comp) {
    return iterator(sort(comp, false));
  }

  /**
   * Returns an {@link IndexedBytesRefIterator} with point in time semantics. The iterator provides
   * access to all so far appended {@link BytesRef} instances. If a non-null sortState is specified
   * then the iterator will iterate the byte values in the order of the sortState; otherwise, the
   * order is the same as the values were appended.
   */
  public IndexedBytesRefIterator iterator(final SortState sortState) {
    final int size = size();
    final int[] indices = sortState == null ? null : sortState.indices;
    assert indices == null || indices.length == size : indices.length + " != " + size;
    final BytesRefBuilder spare = new BytesRefBuilder();
    final BytesRef result = new BytesRef();

    return new IndexedBytesRefIterator() {
      int pos = -1;
      int ord = 0;

      @Override
      public BytesRef next() {
        ++pos;
        if (pos < size) {
          ord = indices == null ? pos : indices[pos];
          setBytesRef(spare, result, ord);
          return result;
        }
        return null;
      }

      @Override
      public int ord() {
        return ord;
      }
    };
  }

  /** Used to iterate the elements of an array in a given order. */
  public static final class SortState implements Accountable {
    private final int[] indices;

    private SortState(int[] indices) {
      this.indices = indices;
    }

    @Override
    public long ramBytesUsed() {
      return RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + indices.length * Integer.BYTES;
    }
  }

  /**
   * An extension of {@link BytesRefIterator} that allows retrieving the index of the current
   * element
   */
  public interface IndexedBytesRefIterator extends BytesRefIterator {
    /**
     * Returns the ordinal position of the element that was returned in the latest call of {@link
     * #next()}. Do not call this method if {@link #next()} is not called yet or the last call
     * returned a null value.
     */
    int ord();
  }
}
