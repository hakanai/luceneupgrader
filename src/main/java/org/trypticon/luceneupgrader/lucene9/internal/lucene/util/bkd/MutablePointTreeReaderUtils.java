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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.bkd;

import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.MutablePointTree;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil.ByteArrayComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IntroSelector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IntroSorter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RadixSelector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Selector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.StableMSBRadixSorter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.PackedInts;

/**
 * Utility APIs for sorting and partitioning buffered points.
 *
 * @lucene.internal
 */
public final class MutablePointTreeReaderUtils {

  MutablePointTreeReaderUtils() {}

  /** Sort the given {@link MutablePointTree} based on its packed value then doc ID. */
  public static void sort(BKDConfig config, int maxDoc, MutablePointTree reader, int from, int to) {

    boolean sortedByDocID = true;
    int prevDoc = 0;
    for (int i = from; i < to; ++i) {
      int doc = reader.getDocID(i);
      if (doc < prevDoc) {
        sortedByDocID = false;
        break;
      }
      prevDoc = doc;
    }

    // No need to tie break on doc IDs if already sorted by doc ID, since we use a stable sort.
    // This should be a common situation as IndexWriter accumulates data in doc ID order when
    // index sorting is not enabled.
    final int bitsPerDocId = sortedByDocID ? 0 : PackedInts.bitsRequired(maxDoc - 1);
    new StableMSBRadixSorter(config.packedBytesLength + (bitsPerDocId + 7) / 8) {

      @Override
      protected void swap(int i, int j) {
        reader.swap(i, j);
      }

      @Override
      protected void save(int i, int j) {
        reader.save(i, j);
      }

      @Override
      protected void restore(int i, int j) {
        reader.restore(i, j);
      }

      @Override
      protected int byteAt(int i, int k) {
        if (k < config.packedBytesLength) {
          return Byte.toUnsignedInt(reader.getByteAt(i, k));
        } else {
          final int shift = bitsPerDocId - ((k - config.packedBytesLength + 1) << 3);
          return (reader.getDocID(i) >>> Math.max(0, shift)) & 0xff;
        }
      }
    }.sort(from, to);
  }

  /** Sort points on the given dimension. */
  public static void sortByDim(
      BKDConfig config,
      int sortedDim,
      int[] commonPrefixLengths,
      MutablePointTree reader,
      int from,
      int to,
      BytesRef scratch1,
      BytesRef scratch2) {

    final ByteArrayComparator comparator = ArrayUtil.getUnsignedComparator(config.bytesPerDim);
    final int start = sortedDim * config.bytesPerDim;
    // No need for a fancy radix sort here, this is called on the leaves only so
    // there are not many values to sort
    new IntroSorter() {

      final BytesRef pivot = scratch1;
      int pivotDoc = -1;

      @Override
      protected void swap(int i, int j) {
        reader.swap(i, j);
      }

      @Override
      protected void setPivot(int i) {
        reader.getValue(i, pivot);
        pivotDoc = reader.getDocID(i);
      }

      @Override
      protected int comparePivot(int j) {
        reader.getValue(j, scratch2);
        int cmp =
            comparator.compare(
                pivot.bytes, pivot.offset + start, scratch2.bytes, scratch2.offset + start);
        if (cmp == 0) {
          cmp =
              Arrays.compareUnsigned(
                  pivot.bytes,
                  pivot.offset + config.packedIndexBytesLength,
                  pivot.offset + config.packedBytesLength,
                  scratch2.bytes,
                  scratch2.offset + config.packedIndexBytesLength,
                  scratch2.offset + config.packedBytesLength);
          if (cmp == 0) {
            cmp = pivotDoc - reader.getDocID(j);
          }
        }
        return cmp;
      }
    }.sort(from, to);
  }

  /**
   * Partition points around {@code mid}. All values on the left must be less than or equal to it
   * and all values on the right must be greater than or equal to it.
   */
  public static void partition(
      BKDConfig config,
      int maxDoc,
      int splitDim,
      int commonPrefixLen,
      MutablePointTree reader,
      int from,
      int to,
      int mid,
      BytesRef scratch1,
      BytesRef scratch2) {
    final int dimOffset = splitDim * config.bytesPerDim + commonPrefixLen;
    final int dimCmpBytes = config.bytesPerDim - commonPrefixLen;
    final int dataCmpBytes =
        (config.numDims - config.numIndexDims) * config.bytesPerDim + dimCmpBytes;
    final int bitsPerDocId = PackedInts.bitsRequired(maxDoc - 1);
    new RadixSelector(dataCmpBytes + (bitsPerDocId + 7) / 8) {

      @Override
      protected Selector getFallbackSelector(int k) {
        final int dimStart = splitDim * config.bytesPerDim;
        final int dataStart =
            (k < dimCmpBytes)
                ? config.packedIndexBytesLength
                : config.packedIndexBytesLength + k - dimCmpBytes;
        final int dataEnd = config.numDims * config.bytesPerDim;
        final ByteArrayComparator dimComparator =
            ArrayUtil.getUnsignedComparator(config.bytesPerDim);
        return new IntroSelector() {

          final BytesRef pivot = scratch1;
          int pivotDoc;

          @Override
          protected void swap(int i, int j) {
            reader.swap(i, j);
          }

          @Override
          protected void setPivot(int i) {
            reader.getValue(i, pivot);
            pivotDoc = reader.getDocID(i);
          }

          @Override
          protected int comparePivot(int j) {
            if (k < dimCmpBytes) {
              reader.getValue(j, scratch2);
              int cmp =
                  dimComparator.compare(
                      pivot.bytes, pivot.offset + dimStart,
                      scratch2.bytes, scratch2.offset + dimStart);

              if (cmp != 0) {
                return cmp;
              }
            }
            if (k < dataCmpBytes) {
              reader.getValue(j, scratch2);
              int cmp =
                  Arrays.compareUnsigned(
                      pivot.bytes,
                      pivot.offset + dataStart,
                      pivot.offset + dataEnd,
                      scratch2.bytes,
                      scratch2.offset + dataStart,
                      scratch2.offset + dataEnd);
              if (cmp != 0) {
                return cmp;
              }
            }
            return pivotDoc - reader.getDocID(j);
          }
        };
      }

      @Override
      protected void swap(int i, int j) {
        reader.swap(i, j);
      }

      @Override
      protected int byteAt(int i, int k) {
        if (k < dimCmpBytes) {
          return Byte.toUnsignedInt(reader.getByteAt(i, dimOffset + k));
        } else if (k < dataCmpBytes) {
          return Byte.toUnsignedInt(
              reader.getByteAt(i, config.packedIndexBytesLength + k - dimCmpBytes));
        } else {
          final int shift = bitsPerDocId - ((k - dataCmpBytes + 1) << 3);
          return (reader.getDocID(i) >>> Math.max(0, shift)) & 0xff;
        }
      }
    }.select(from, to, mid);
  }
}
