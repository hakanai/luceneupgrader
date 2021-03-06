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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;

import java.util.Arrays;
import java.util.Comparator;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ByteBlockPool;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRefIterator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IntroSorter;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RamUsageEstimator;

public final class BytesRefArray {
  private final ByteBlockPool pool;
  private int[] offsets = new int[1];
  private int lastElement = 0;
  private int currentOffset = 0;
  private final Counter bytesUsed;
  
  public BytesRefArray(Counter bytesUsed) {
    this.pool = new ByteBlockPool(new ByteBlockPool.DirectTrackingAllocator(
        bytesUsed));
    pool.nextBuffer();
    bytesUsed.addAndGet(RamUsageEstimator.NUM_BYTES_ARRAY_HEADER
        + RamUsageEstimator.NUM_BYTES_INT);
    this.bytesUsed = bytesUsed;
  }
 
  public void clear() {
    lastElement = 0;
    currentOffset = 0;
    Arrays.fill(offsets, 0);
    pool.reset(false, true); // no need to 0 fill the buffers we control the allocator
  }
  
  public int append(BytesRef bytes) {
    if (lastElement >= offsets.length) {
      int oldLen = offsets.length;
      offsets = ArrayUtil.grow(offsets, offsets.length + 1);
      bytesUsed.addAndGet((offsets.length - oldLen)
          * RamUsageEstimator.NUM_BYTES_INT);
    }
    pool.append(bytes);
    offsets[lastElement++] = currentOffset;
    currentOffset += bytes.length;
    return lastElement-1;
  }
  
  public int size() {
    return lastElement;
  }
  
  public BytesRef get(BytesRefBuilder spare, int index) {
    if (lastElement > index) {
      int offset = offsets[index];
      int length = index == lastElement - 1 ? currentOffset - offset
          : offsets[index + 1] - offset;
      spare.grow(length);
      spare.setLength(length);
      pool.readBytes(offset, spare.bytes(), 0, spare.length());
      return spare.get();
    }
    throw new IndexOutOfBoundsException("index " + index
        + " must be less than the size: " + lastElement);
    
  }
  
  private int[] sort(final Comparator<BytesRef> comp) {
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
        return comp.compare(get(scratch1, idx1), get(scratch2, idx2));
      }
      
      @Override
      protected void setPivot(int i) {
        final int index = orderedEntries[i];
        pivot = get(pivotBuilder, index);
      }
      
      @Override
      protected int comparePivot(int j) {
        final int index = orderedEntries[j];
        return comp.compare(pivot, get(scratch2, index));
      }

      private BytesRef pivot;
      private final BytesRefBuilder pivotBuilder = new BytesRefBuilder(),
          scratch1 = new BytesRefBuilder(),
          scratch2 = new BytesRefBuilder();
    }.sort(0, size());
    return orderedEntries;
  }
  
  public BytesRefIterator iterator() {
    return iterator(null);
  }
  
  public BytesRefIterator iterator(final Comparator<BytesRef> comp) {
    final BytesRefBuilder spare = new BytesRefBuilder();
    final int size = size();
    final int[] indices = comp == null ? null : sort(comp);
    return new BytesRefIterator() {
      int pos = 0;
      
      @Override
      public BytesRef next() {
        if (pos < size) {
          return get(spare, indices == null ? pos++ : indices[pos++]);
        }
        return null;
      }
    };
  }
}
