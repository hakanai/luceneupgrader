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

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.IntBinaryOperator;

public final class BytesRefArray implements SortableBytesRefArray {
  private final ByteBlockPool pool;
  private int[] offsets = new int[1];
  private int lastElement = 0;
  private int currentOffset = 0;
  private final Counter bytesUsed;

  public BytesRefArray(Counter bytesUsed) {
    this.pool = new ByteBlockPool(new ByteBlockPool.DirectTrackingAllocator(
        bytesUsed));
    pool.nextBuffer();
    bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER * Integer.BYTES);
    this.bytesUsed = bytesUsed;
  }
 
  @Override
  public void clear() {
    lastElement = 0;
    currentOffset = 0;
    // TODO: it's trappy that this does not return storage held by int[] offsets array!
    Arrays.fill(offsets, 0);
    pool.reset(false, true); // no need to 0 fill the buffers we control the allocator
  }
  
  @Override
  public int append(BytesRef bytes) {
    if (lastElement >= offsets.length) {
      int oldLen = offsets.length;
      offsets = ArrayUtil.grow(offsets, offsets.length + 1);
      bytesUsed.addAndGet((offsets.length - oldLen) * Integer.BYTES);
    }
    pool.append(bytes);
    offsets[lastElement++] = currentOffset;
    currentOffset += bytes.length;
    return lastElement-1;
  }
  
  @Override
  public int size() {
    return lastElement;
  }
  
  public BytesRef get(BytesRefBuilder spare, int index) {
    FutureObjects.checkIndex(index, lastElement);
    int offset = offsets[index];
    int length = index == lastElement - 1 ? currentOffset - offset
        : offsets[index + 1] - offset;
    spare.grow(length);
    spare.setLength(length);
    pool.readBytes(offset, spare.bytes(), 0, spare.length());
    return spare.get();
  }

  private void setBytesRef(BytesRefBuilder spare, BytesRef result, int index) {
    FutureObjects.checkIndex(index, lastElement);
    int offset = offsets[index];
    int length;
    if (index == lastElement - 1) {
      length = currentOffset - offset;
    } else {
      length = offsets[index + 1] - offset;
    }
    pool.setBytesRef(spare, result, offset, length);
  }


  public SortState sort(final Comparator<BytesRef> comp, final IntBinaryOperator tieComparator) {
    final int[] orderedEntries = new int[size()];
    for (int i = 0; i < orderedEntries.length; i++) {
      orderedEntries[i] = i;
    }
    new IntroSorter() {
      @Override
      protected void swap(int i, int j) {
        final int o = orderedEntries[i];
        orderedEntries[i] = orderedEntries[j];
        orderedEntries[j] = o;
      }
      
      @Override
      protected int compare(int i, int j) {
        final int idx1 = orderedEntries[i], idx2 = orderedEntries[j];
        setBytesRef(scratch1, scratchBytes1, idx1);
        setBytesRef(scratch2, scratchBytes2, idx2);
        return compare(idx1, scratchBytes1, idx2, scratchBytes2);
      }
      
      @Override
      protected void setPivot(int i) {
        pivotIndex = orderedEntries[i];
        setBytesRef(pivotBuilder, pivot, pivotIndex);
      }
      
      @Override
      protected int comparePivot(int j) {
        final int index = orderedEntries[j];
        setBytesRef(scratch2, scratchBytes2, index);
        return compare(pivotIndex, pivot, index, scratchBytes2);
      }

      private int compare(int i1, BytesRef b1, int i2, BytesRef b2) {
        int res = comp.compare(b1, b2);
        return res == 0 ? tieComparator.applyAsInt(i1, i2) : res;
      }

      private int pivotIndex;
      private final BytesRef pivot = new BytesRef();
      private final BytesRef scratchBytes1 = new BytesRef();
      private final BytesRef scratchBytes2 = new BytesRef();
      private final BytesRefBuilder pivotBuilder = new BytesRefBuilder();
      private final BytesRefBuilder scratch1 = new BytesRefBuilder();
      private final BytesRefBuilder scratch2 = new BytesRefBuilder();
    }.sort(0, size());
    return new SortState(orderedEntries);
  }
  
  public BytesRefIterator iterator() {
    return iterator((SortState) null);
  }
  
  @Override
  public BytesRefIterator iterator(final Comparator<BytesRef> comp) {
    return iterator(sort(comp, (i, j) -> 0));
  }

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

  public final static class SortState implements Accountable {
    private final int[] indices;

    private SortState(int[] indices) {
      this.indices = indices;
    }

    @Override
    public long ramBytesUsed() {
      return RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + indices.length * Integer.BYTES;
    }
  }

  public interface IndexedBytesRefIterator extends BytesRefIterator {
    int ord();
  }
}
