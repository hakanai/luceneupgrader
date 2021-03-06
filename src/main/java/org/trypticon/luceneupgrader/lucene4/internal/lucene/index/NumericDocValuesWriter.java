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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedLongValues;

class NumericDocValuesWriter extends DocValuesWriter {

  private final static long MISSING = 0L;

  private PackedLongValues.Builder pending;
  private final Counter iwBytesUsed;
  private long bytesUsed;
  private FixedBitSet docsWithField;
  private final FieldInfo fieldInfo;

  public NumericDocValuesWriter(FieldInfo fieldInfo, Counter iwBytesUsed, boolean trackDocsWithField) {
    pending = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT);
    docsWithField = trackDocsWithField ? new FixedBitSet(64) : null;
    bytesUsed = pending.ramBytesUsed() + docsWithFieldBytesUsed();
    this.fieldInfo = fieldInfo;
    this.iwBytesUsed = iwBytesUsed;
    iwBytesUsed.addAndGet(bytesUsed);
  }

  public void addValue(int docID, long value) {
    if (docID < pending.size()) {
      throw new IllegalArgumentException("DocValuesField \"" + fieldInfo.name + "\" appears more than once in this document (only one value is allowed per field)");
    }

    // Fill in any holes:
    for (int i = (int)pending.size(); i < docID; ++i) {
      pending.add(MISSING);
    }

    pending.add(value);
    if (docsWithField != null) {
      docsWithField = FixedBitSet.ensureCapacity(docsWithField, docID);
      docsWithField.set(docID);
    }

    updateBytesUsed();
  }
  
  private long docsWithFieldBytesUsed() {
    // size of the long[] + some overhead
    return docsWithField == null ? 0 : RamUsageEstimator.sizeOf(docsWithField.getBits()) + 64;
  }

  private void updateBytesUsed() {
    final long newBytesUsed = pending.ramBytesUsed() + docsWithFieldBytesUsed();
    iwBytesUsed.addAndGet(newBytesUsed - bytesUsed);
    bytesUsed = newBytesUsed;
  }

  @Override
  public void finish(int maxDoc) {
  }

  @Override
  public void flush(SegmentWriteState state, DocValuesConsumer dvConsumer) throws IOException {

    final int maxDoc = state.segmentInfo.getDocCount();
    final PackedLongValues values = pending.build();

    dvConsumer.addNumericField(fieldInfo,
                               new Iterable<Number>() {
                                 @Override
                                 public Iterator<Number> iterator() {
                                   return new NumericIterator(maxDoc, values, docsWithField);
                                 }
                               });
  }

  // iterates over the values we have in ram
  private static class NumericIterator implements Iterator<Number> {
    final PackedLongValues.Iterator iter;
    final FixedBitSet docsWithField;
    final int size;
    final int maxDoc;
    int upto;
    
    NumericIterator(int maxDoc, PackedLongValues values, FixedBitSet docsWithFields) {
      this.maxDoc = maxDoc;
      this.iter = values.iterator();
      this.size = (int) values.size();
      this.docsWithField = docsWithFields;
    }
    
    @Override
    public boolean hasNext() {
      return upto < maxDoc;
    }

    @Override
    public Number next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Long value;
      if (upto < size) {
        long v = iter.next();
        if (docsWithField == null || docsWithField.get(upto)) {
          value = v;
        } else {
          value = null;
        }
      } else {
        value = docsWithField != null ? null : MISSING;
      }
      upto++;
      return value;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
