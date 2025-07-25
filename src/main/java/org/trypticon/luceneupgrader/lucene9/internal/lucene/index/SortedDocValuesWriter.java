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
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ByteBlockPool;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRefHash;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRefHash.DirectBytesStartArray;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.PackedLongValues;

/**
 * Buffers up pending byte[] per doc, deref and sorting via int ord, then flushes when segment
 * flushes.
 */
class SortedDocValuesWriter extends DocValuesWriter<SortedDocValues> {
  final BytesRefHash hash;
  private final PackedLongValues.Builder pending;
  private final DocsWithFieldSet docsWithField;
  private final Counter iwBytesUsed;
  private long bytesUsed; // this currently only tracks differences in 'pending'
  private final FieldInfo fieldInfo;
  private int lastDocID = -1;

  private PackedLongValues finalOrds;
  private int[] finalSortedValues;
  private int[] finalOrdMap;

  public SortedDocValuesWriter(FieldInfo fieldInfo, Counter iwBytesUsed, ByteBlockPool pool) {
    this.fieldInfo = fieldInfo;
    this.iwBytesUsed = iwBytesUsed;
    hash =
        new BytesRefHash(
            pool,
            BytesRefHash.DEFAULT_CAPACITY,
            new DirectBytesStartArray(BytesRefHash.DEFAULT_CAPACITY, iwBytesUsed));
    pending = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT);
    docsWithField = new DocsWithFieldSet();
    bytesUsed = pending.ramBytesUsed() + docsWithField.ramBytesUsed();
    iwBytesUsed.addAndGet(bytesUsed);
  }

  public void addValue(int docID, BytesRef value) {
    if (docID <= lastDocID) {
      throw new IllegalArgumentException(
          "DocValuesField \""
              + fieldInfo.name
              + "\" appears more than once in this document (only one value is allowed per field)");
    }
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

    addOneValue(value);
    docsWithField.add(docID);

    lastDocID = docID;
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

    pending.add(termID);
    updateBytesUsed();
  }

  private void updateBytesUsed() {
    final long newBytesUsed = pending.ramBytesUsed() + docsWithField.ramBytesUsed();
    iwBytesUsed.addAndGet(newBytesUsed - bytesUsed);
    bytesUsed = newBytesUsed;
  }

  private void finish() {
    if (finalSortedValues == null) {
      int valueCount = hash.size();
      updateBytesUsed();
      assert finalOrdMap == null && finalOrds == null;
      finalSortedValues = hash.sort();
      finalOrds = pending.build();
      finalOrdMap = new int[valueCount];
      for (int ord = 0; ord < valueCount; ord++) {
        finalOrdMap[finalSortedValues[ord]] = ord;
      }
    }
  }

  @Override
  SortedDocValues getDocValues() {
    finish();
    return new BufferedSortedDocValues(
        hash, finalOrds, finalSortedValues, finalOrdMap, docsWithField.iterator());
  }

  private static int[] sortDocValues(int maxDoc, Sorter.DocMap sortMap, SortedDocValues oldValues)
      throws IOException {
    int[] ords = new int[maxDoc];
    Arrays.fill(ords, -1);
    int docID;
    while ((docID = oldValues.nextDoc()) != NO_MORE_DOCS) {
      int newDocID = sortMap.oldToNew(docID);
      ords[newDocID] = oldValues.ordValue();
    }
    return ords;
  }

  @Override
  public void flush(SegmentWriteState state, Sorter.DocMap sortMap, DocValuesConsumer dvConsumer)
      throws IOException {
    finish();

    dvConsumer.addSortedField(
        fieldInfo,
        getDocValuesProducer(
            fieldInfo, hash, finalOrds, finalSortedValues, finalOrdMap, docsWithField, sortMap));
  }

  static DocValuesProducer getDocValuesProducer(
      FieldInfo writerFieldInfo,
      BytesRefHash hash,
      PackedLongValues ords,
      int[] sortedValues,
      int[] ordMap,
      DocsWithFieldSet docsWithField,
      Sorter.DocMap sortMap)
      throws IOException {
    final int[] sorted;
    if (sortMap != null) {
      sorted =
          sortDocValues(
              sortMap.size(),
              sortMap,
              new BufferedSortedDocValues(
                  hash, ords, sortedValues, ordMap, docsWithField.iterator()));
    } else {
      sorted = null;
    }
    return new EmptyDocValuesProducer() {
      @Override
      public SortedDocValues getSorted(FieldInfo fieldInfoIn) {
        if (fieldInfoIn != writerFieldInfo) {
          throw new IllegalArgumentException("wrong fieldInfo");
        }
        final SortedDocValues buf =
            new BufferedSortedDocValues(hash, ords, sortedValues, ordMap, docsWithField.iterator());
        if (sorted == null) {
          return buf;
        }
        return new SortingSortedDocValues(buf, sorted);
      }
    };
  }

  static class BufferedSortedDocValues extends SortedDocValues {
    final BytesRefHash hash;
    final BytesRef scratch = new BytesRef();
    final int[] sortedValues;
    final int[] ordMap;
    private int ord;
    final PackedLongValues.Iterator iter;
    final DocIdSetIterator docsWithField;

    public BufferedSortedDocValues(
        BytesRefHash hash,
        PackedLongValues docToOrd,
        int[] sortedValues,
        int[] ordMap,
        DocIdSetIterator docsWithField) {
      this.hash = hash;
      this.sortedValues = sortedValues;
      this.iter = docToOrd.iterator();
      this.ordMap = ordMap;
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
        ord = Math.toIntExact(iter.next());
        ord = ordMap[ord];
      }
      return docID;
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
    public long cost() {
      return docsWithField.cost();
    }

    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public BytesRef lookupOrd(int ord) {
      assert ord >= 0 && ord < sortedValues.length;
      assert sortedValues[ord] >= 0 && sortedValues[ord] < sortedValues.length;
      hash.get(sortedValues[ord], scratch);
      return scratch;
    }

    @Override
    public int getValueCount() {
      return hash.size();
    }
  }

  static class SortingSortedDocValues extends SortedDocValues {

    private final SortedDocValues in;
    private final int[] ords;
    private int docID = -1;

    SortingSortedDocValues(SortedDocValues in, int[] ords) {
      this.in = in;
      this.ords = ords;
      assert ords != null;
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int nextDoc() {
      while (true) {
        docID++;
        if (docID == ords.length) {
          docID = NO_MORE_DOCS;
          break;
        }
        if (ords[docID] != -1) {
          break;
        }
        // skip missing docs
      }

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
      return ords[target] != -1;
    }

    @Override
    public int ordValue() {
      return ords[docID];
    }

    @Override
    public long cost() {
      return in.cost();
    }

    @Override
    public BytesRef lookupOrd(int ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return in.getValueCount();
    }
  }
}
