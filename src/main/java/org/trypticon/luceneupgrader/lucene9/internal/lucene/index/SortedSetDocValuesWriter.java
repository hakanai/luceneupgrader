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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ByteBlockPool.BYTE_BLOCK_SIZE;

import java.io.IOException;
import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedDocValuesWriter.BufferedSortedDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ByteBlockPool;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRefHash;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRefHash.DirectBytesStartArray;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.GrowableWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.PackedLongValues;

/**
 * Buffers up pending byte[]s per doc, deref and sorting via int ord, then flushes when segment
 * flushes.
 */
class SortedSetDocValuesWriter extends DocValuesWriter<SortedSetDocValues> {
  final BytesRefHash hash;
  private final PackedLongValues.Builder pending; // stream of all termIDs
  private PackedLongValues.Builder pendingCounts; // termIDs per doc
  private final DocsWithFieldSet docsWithField;
  private final Counter iwBytesUsed;
  private long bytesUsed; // this only tracks differences in 'pending' and 'pendingCounts'
  private final FieldInfo fieldInfo;
  private int currentDoc = -1;
  private int[] currentValues = new int[8];
  private int currentUpto;
  private int maxCount;

  private PackedLongValues finalOrds;
  private PackedLongValues finalOrdCounts;
  private int[] finalSortedValues;
  private int[] finalOrdMap;

  SortedSetDocValuesWriter(FieldInfo fieldInfo, Counter iwBytesUsed, ByteBlockPool pool) {
    this.fieldInfo = fieldInfo;
    this.iwBytesUsed = iwBytesUsed;
    hash =
        new BytesRefHash(
            pool,
            BytesRefHash.DEFAULT_CAPACITY,
            new DirectBytesStartArray(BytesRefHash.DEFAULT_CAPACITY, iwBytesUsed));
    pending = PackedLongValues.packedBuilder(PackedInts.COMPACT);
    docsWithField = new DocsWithFieldSet();
    bytesUsed =
        pending.ramBytesUsed()
            + docsWithField.ramBytesUsed()
            + RamUsageEstimator.sizeOf(currentValues);
    iwBytesUsed.addAndGet(bytesUsed);
  }

  public void addValue(int docID, BytesRef value) {
    assert docID >= currentDoc;
    if (value == null) {
      throw new IllegalArgumentException(
          "field \"" + fieldInfo.name + "\": null value not allowed");
    }
    if (value.length > (BYTE_BLOCK_SIZE - 2)) {
      throw new IllegalArgumentException(
          "DocValuesField \""
              + fieldInfo.name
              + "\" is too large, must be <= "
              + (BYTE_BLOCK_SIZE - 2));
    }

    if (docID != currentDoc) {
      finishCurrentDoc();
      currentDoc = docID;
    }

    addOneValue(value);
    updateBytesUsed();
  }

  // finalize currentDoc: this deduplicates the current term ids
  private void finishCurrentDoc() {
    if (currentDoc == -1) {
      return;
    }
    if (currentUpto > 1) {
      Arrays.sort(currentValues, 0, currentUpto);
    }
    int lastValue = -1;
    int count = 0;
    for (int i = 0; i < currentUpto; i++) {
      int termID = currentValues[i];
      // if it's not a duplicate
      if (termID != lastValue) {
        pending.add(termID); // record the term id
        count++;
      }
      lastValue = termID;
    }
    // record the number of unique term ids for this doc
    if (pendingCounts != null) {
      pendingCounts.add(count);
    } else if (count != 1) {
      pendingCounts = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT);
      for (int i = 0; i < docsWithField.cardinality(); ++i) {
        pendingCounts.add(1);
      }
      pendingCounts.add(count);
    }
    maxCount = Math.max(maxCount, count);
    currentUpto = 0;
    docsWithField.add(currentDoc);
  }

  private void addOneValue(BytesRef value) {
    int termID = hash.add(value);
    if (termID < 0) {
      termID = -termID - 1;
    } else {
      // reserve additional space for each unique value:
      // 1. when indexing, when hash is 50% full, rehash() suddenly needs 2*size ints.
      //    TODO: can this same OOM happen in THPF?
      // 2. when flushing, we need 1 int per value (slot in the ordMap).
      iwBytesUsed.addAndGet(2 * Integer.BYTES);
    }

    if (currentUpto == currentValues.length) {
      currentValues = ArrayUtil.grow(currentValues, currentValues.length + 1);
      iwBytesUsed.addAndGet((currentValues.length - currentUpto) * (long) Integer.BYTES);
    }

    currentValues[currentUpto] = termID;
    currentUpto++;
  }

  private void updateBytesUsed() {
    final long newBytesUsed =
        pending.ramBytesUsed()
            + (pendingCounts == null ? 0 : pendingCounts.ramBytesUsed())
            + docsWithField.ramBytesUsed()
            + RamUsageEstimator.sizeOf(currentValues);
    iwBytesUsed.addAndGet(newBytesUsed - bytesUsed);
    bytesUsed = newBytesUsed;
  }

  private void finish() {
    if (finalOrds == null) {
      assert finalOrdCounts == null && finalSortedValues == null && finalOrdMap == null;
      finishCurrentDoc();
      int valueCount = hash.size();
      finalOrds = pending.build();
      finalOrdCounts = pendingCounts == null ? null : pendingCounts.build();
      finalSortedValues = hash.sort();
      finalOrdMap = new int[valueCount];
      for (int ord = 0; ord < finalOrdMap.length; ord++) {
        finalOrdMap[finalSortedValues[ord]] = ord;
      }
    }
  }

  @Override
  SortedSetDocValues getDocValues() {
    finish();
    return getValues(
        finalSortedValues, finalOrdMap, hash, finalOrds, finalOrdCounts, maxCount, docsWithField);
  }

  private SortedSetDocValues getValues(
      int[] sortedValues,
      int[] ordMap,
      BytesRefHash hash,
      PackedLongValues ords,
      PackedLongValues ordCounts,
      int maxCount,
      DocsWithFieldSet docsWithField) {
    if (ordCounts == null) {
      return DocValues.singleton(
          new BufferedSortedDocValues(hash, ords, sortedValues, ordMap, docsWithField.iterator()));
    } else {
      return new BufferedSortedSetDocValues(
          sortedValues, ordMap, hash, ords, ordCounts, maxCount, docsWithField.iterator());
    }
  }

  @Override
  public void flush(SegmentWriteState state, Sorter.DocMap sortMap, DocValuesConsumer dvConsumer)
      throws IOException {
    finish();
    final PackedLongValues ords = finalOrds;
    final PackedLongValues ordCounts = finalOrdCounts;
    final int[] sortedValues = finalSortedValues;
    final int[] ordMap = finalOrdMap;

    if (ordCounts == null) {
      DocValuesProducer singleValueProducer =
          SortedDocValuesWriter.getDocValuesProducer(
              fieldInfo, hash, ords, sortedValues, ordMap, docsWithField, sortMap);
      dvConsumer.addSortedSetField(
          fieldInfo,
          new EmptyDocValuesProducer() {
            @Override
            public SortedSetDocValues getSortedSet(FieldInfo fieldInfo) throws IOException {
              return DocValues.singleton(singleValueProducer.getSorted(fieldInfo));
            }
          });
      return;
    }

    final DocOrds docOrds;
    if (sortMap != null) {
      docOrds =
          new DocOrds(
              state.segmentInfo.maxDoc(),
              sortMap,
              getValues(sortedValues, ordMap, hash, ords, ordCounts, maxCount, docsWithField),
              PackedInts.FASTEST,
              PackedInts.bitsRequired(maxCount));
    } else {
      docOrds = null;
    }
    dvConsumer.addSortedSetField(
        fieldInfo,
        new EmptyDocValuesProducer() {
          @Override
          public SortedSetDocValues getSortedSet(FieldInfo fieldInfoIn) {
            if (fieldInfoIn != fieldInfo) {
              throw new IllegalArgumentException("wrong fieldInfo");
            }
            final SortedSetDocValues buf =
                getValues(sortedValues, ordMap, hash, ords, ordCounts, maxCount, docsWithField);
            if (docOrds == null) {
              return buf;
            } else {
              return new SortingSortedSetDocValues(buf, docOrds);
            }
          }
        });
  }

  private static class BufferedSortedSetDocValues extends SortedSetDocValues {
    final int[] sortedValues;
    final int[] ordMap;
    final BytesRefHash hash;
    final BytesRef scratch = new BytesRef();
    final PackedLongValues.Iterator ordsIter;
    final PackedLongValues.Iterator ordCountsIter;
    final DocIdSetIterator docsWithField;
    final int[] currentDoc;

    private int ordCount;
    private int ordUpto;

    BufferedSortedSetDocValues(
        int[] sortedValues,
        int[] ordMap,
        BytesRefHash hash,
        PackedLongValues ords,
        PackedLongValues ordCounts,
        int maxCount,
        DocIdSetIterator docsWithField) {
      this.currentDoc = new int[maxCount];
      this.sortedValues = sortedValues;
      this.ordMap = ordMap;
      this.hash = hash;
      this.ordsIter = ords.iterator();
      this.ordCountsIter = ordCounts.iterator();
      this.docsWithField = docsWithField;
    }

    @Override
    public int docID() {
      return docsWithField.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      int docID = docsWithField.nextDoc();
      if (docID != NO_MORE_DOCS) {
        ordCount = (int) ordCountsIter.next();
        assert ordCount > 0;
        for (int i = 0; i < ordCount; i++) {
          currentDoc[i] = ordMap[Math.toIntExact(ordsIter.next())];
        }
        Arrays.sort(currentDoc, 0, ordCount);
        ordUpto = 0;
      }
      return docID;
    }

    @Override
    public long nextOrd() {
      if (ordUpto == ordCount) {
        return NO_MORE_ORDS;
      } else {
        return currentDoc[ordUpto++];
      }
    }

    @Override
    public int docValueCount() {
      return ordCount;
    }

    @Override
    public long cost() {
      return docsWithField.cost();
    }

    @Override
    public int advance(int target) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getValueCount() {
      return ordMap.length;
    }

    @Override
    public BytesRef lookupOrd(long ord) {
      assert ord >= 0 && ord < ordMap.length
          : "ord=" + ord + " is out of bounds 0 .. " + (ordMap.length - 1);
      hash.get(sortedValues[Math.toIntExact(ord)], scratch);
      return scratch;
    }
  }

  static class SortingSortedSetDocValues extends SortedSetDocValues {

    private final SortedSetDocValues in;
    private final DocOrds ords;
    private int docID = -1;
    private long ordUpto;
    private long limit;
    private int count;

    SortingSortedSetDocValues(SortedSetDocValues in, DocOrds ords) {
      this.in = in;
      this.ords = ords;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int nextDoc() {
      do {
        docID++;
        if (docID == ords.offsets.length) {
          return docID = NO_MORE_DOCS;
        }
      } while (ords.offsets[docID] <= 0);
      initCount();
      return docID;
    }

    @Override
    public int advance(int target) {
      throw new UnsupportedOperationException("use nextDoc instead");
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      // needed in IndexSorter#StringSorter
      docID = target;
      initCount();
      return ords.offsets[docID] > 0;
    }

    @Override
    public long nextOrd() {
      if (limit == ordUpto) {
        return NO_MORE_ORDS;
      } else {
        return ords.ords.get(ordUpto++);
      }
    }

    @Override
    public int docValueCount() {
      assert docID >= 0;
      return count;
    }

    @Override
    public long cost() {
      return in.cost();
    }

    @Override
    public BytesRef lookupOrd(long ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public long getValueCount() {
      return in.getValueCount();
    }

    private void initCount() {
      assert docID >= 0;
      ordUpto = ords.offsets[docID] - 1;
      count = (int) ords.docValueCounts.get(docID);
      limit = ordUpto + count;
    }
  }

  static final class DocOrds {
    final long[] offsets;
    final PackedLongValues ords;
    final GrowableWriter docValueCounts;

    public static final int START_BITS_PER_VALUE = 2;

    DocOrds(
        int maxDoc,
        Sorter.DocMap sortMap,
        SortedSetDocValues oldValues,
        float acceptableOverheadRatio,
        int bitsPerValue)
        throws IOException {
      offsets = new long[maxDoc];
      PackedLongValues.Builder builder = PackedLongValues.packedBuilder(acceptableOverheadRatio);
      docValueCounts = new GrowableWriter(bitsPerValue, maxDoc, acceptableOverheadRatio);
      long ordOffset = 1;
      int docID;
      while ((docID = oldValues.nextDoc()) != NO_MORE_DOCS) {
        int newDocID = sortMap.oldToNew(docID);
        long startOffset = ordOffset;
        int docValueCount = oldValues.docValueCount();
        ordOffset += docValueCount;
        for (int i = 0; i < docValueCount; i++) {
          builder.add(oldValues.nextOrd());
        }
        docValueCounts.set(newDocID, ordOffset - startOffset);
        if (startOffset != ordOffset) { // do we have any values?
          offsets[newDocID] = startOffset;
        }
      }
      ords = builder.build();
    }
  }
}
