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

package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;

import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRefArray;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRefIterator;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Counter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.RamUsageEstimator;

final class FieldUpdatesBuffer {
  private static final long SELF_SHALLOW_SIZE = RamUsageEstimator.shallowSizeOfInstance(FieldUpdatesBuffer.class);
  private static final long STRING_SHALLOW_SIZE = RamUsageEstimator.shallowSizeOfInstance(String.class);
  private final Counter bytesUsed;
  private int numUpdates = 1;
  // we use a very simple approach and store the update term values without de-duplication
  // which is also not a common case to keep updating the same value more than once...
  // we might pay a higher price in terms of memory in certain cases but will gain
  // on CPU for those. We also save on not needing to sort in order to apply the terms in order
  // since by definition we store them in order.
  private final BytesRefArray termValues;
  private final BytesRefArray byteValues; // this will be null if we are buffering numerics
  private int[] docsUpTo;
  private long[] numericValues; // this will be null if we are buffering binaries
  private FixedBitSet hasValues;
  private long maxNumeric = Long.MIN_VALUE;
  private long minNumeric = Long.MAX_VALUE;
  private String[] fields;
  private final boolean isNumeric;

  private FieldUpdatesBuffer(Counter bytesUsed, DocValuesUpdate initialValue, int docUpTo, boolean isNumeric) {
    this.bytesUsed = bytesUsed;
    this.bytesUsed.addAndGet(SELF_SHALLOW_SIZE);
    termValues = new BytesRefArray(bytesUsed);
    termValues.append(initialValue.term.bytes);
    fields = new String[] {initialValue.term.field};
    bytesUsed.addAndGet(sizeOfString(initialValue.term.field));
    docsUpTo = new int[] {docUpTo};
    if (initialValue.hasValue == false) {
      hasValues = new FixedBitSet(1);
      bytesUsed.addAndGet(hasValues.ramBytesUsed());
    }
    this.isNumeric = isNumeric;
    byteValues = isNumeric ? null : new BytesRefArray(bytesUsed);
  }

  private static long sizeOfString(String string) {
    return STRING_SHALLOW_SIZE + (string.length() * Character.BYTES);
  }

  FieldUpdatesBuffer(Counter bytesUsed, DocValuesUpdate.NumericDocValuesUpdate initialValue, int docUpTo) {
    this(bytesUsed, initialValue, docUpTo, true);
    if (initialValue.hasValue()) {
      numericValues = new long[] {initialValue.getValue()};
      maxNumeric = minNumeric = initialValue.getValue();
    } else {
      numericValues = new long[] {0};
    }
    bytesUsed.addAndGet(Long.BYTES);
  }

  FieldUpdatesBuffer(Counter bytesUsed, DocValuesUpdate.BinaryDocValuesUpdate initialValue, int docUpTo) {
    this(bytesUsed, initialValue, docUpTo, false);
    if (initialValue.hasValue()) {
      byteValues.append(initialValue.getValue());
    }
  }

  long getMaxNumeric() {
    assert isNumeric;
    if (minNumeric == Long.MAX_VALUE && maxNumeric == Long.MIN_VALUE) {
      return 0; // we don't have any value;
    }
    return maxNumeric;
  }

  long getMinNumeric() {
    assert isNumeric;
    if (minNumeric == Long.MAX_VALUE && maxNumeric == Long.MIN_VALUE) {
      return 0; // we don't have any value
    }
    return minNumeric;
  }

  void add(String field, int docUpTo, int ord, boolean hasValue) {
    if (fields[0].equals(field) == false || fields.length != 1 ) {
      if (fields.length <= ord) {
        String[] array = ArrayUtil.grow(fields, ord+1);
        if (fields.length == 1) {
          Arrays.fill(array, 1, ord, fields[0]);
        }
        bytesUsed.addAndGet((array.length - fields.length) * RamUsageEstimator.NUM_BYTES_OBJECT_REF);
        fields = array;
      }
      if (field != fields[0]) { // that's an easy win of not accounting if there is an outlier
        bytesUsed.addAndGet(sizeOfString(field));
      }
      fields[ord] = field;
    }

    if (docsUpTo[0] != docUpTo || docsUpTo.length != 1) {
      if (docsUpTo.length <= ord) {
        int[] array = ArrayUtil.grow(docsUpTo, ord+1);
        if (docsUpTo.length == 1) {
          Arrays.fill(array, 1, ord, docsUpTo[0]);
        }
        bytesUsed.addAndGet((array.length-docsUpTo.length) * Integer.BYTES);
        docsUpTo = array;
      }
      docsUpTo[ord] = docUpTo;
    }

    if (hasValue == false || hasValues != null) {
      if (hasValues == null) {
        hasValues = new FixedBitSet(ord+1);
        hasValues.set(0, ord);
        bytesUsed.addAndGet(hasValues.ramBytesUsed());
      } else if (hasValues.length() <= ord) {
        FixedBitSet fixedBitSet = FixedBitSet.ensureCapacity(hasValues, ArrayUtil.oversize(ord + 1, 1));
        bytesUsed.addAndGet(fixedBitSet.ramBytesUsed()-hasValues.ramBytesUsed());
        hasValues = fixedBitSet;
      }
      if (hasValue) {
        hasValues.set(ord);
      }
    }
  }

  void addUpdate(Term term, long value, int docUpTo) {
    assert isNumeric;
    final int ord = append(term);
    String field = term.field;
    add(field, docUpTo, ord, true);
    minNumeric = Math.min(minNumeric, value);
    maxNumeric = Math.max(maxNumeric, value);
    if (numericValues[0] != value || numericValues.length != 1) {
      if (numericValues.length <= ord) {
        long[] array = ArrayUtil.grow(numericValues, ord+1);
        if (numericValues.length == 1) {
          Arrays.fill(array, 1, ord, numericValues[0]);
        }
        bytesUsed.addAndGet((array.length-numericValues.length) * Long.BYTES);
        numericValues = array;
      }
      numericValues[ord] = value;
    }
  }

  void addNoValue(Term term, int docUpTo) {
    final int ord = append(term);
    add(term.field, docUpTo, ord, false);
  }

  void addUpdate(Term term, BytesRef value, int docUpTo) {
    assert isNumeric == false;
    final int ord = append(term);
    byteValues.append(value);
    add(term.field, docUpTo, ord, true);
  }

  private int append(Term term) {
    termValues.append(term.bytes);
    return numUpdates++;
  }

  BufferedUpdateIterator iterator() {
    return new BufferedUpdateIterator();
  }

  boolean isNumeric() {
    assert isNumeric || byteValues != null;
    return isNumeric;
  }

  boolean hasSingleValue() {
    // we only do this optimization for numerics so far.
    return isNumeric && numericValues.length == 1;
  }

  long getNumericValue(int idx) {
    if (hasValues != null && hasValues.get(idx) == false) {
      return 0;
    }
    return numericValues[getArrayIndex(numericValues.length, idx)];
  }

  static class BufferedUpdate {

    private BufferedUpdate() {};
    int docUpTo;
    long numericValue;
    BytesRef binaryValue;
    boolean hasValue;
    String termField;
    BytesRef termValue;

    @Override
    public int hashCode() {
      throw new UnsupportedOperationException(
          "this struct should not be use in map or other data-stuctures that use hashCode / equals");
    }

    @Override
    public boolean equals(Object obj) {
      throw new UnsupportedOperationException(
          "this struct should not be use in map or other data-stuctures that use hashCode / equals");
    }
  }

  class BufferedUpdateIterator {
    private final BytesRefIterator termValuesIterator;
    private final BytesRefIterator byteValuesIterator;
    private final BufferedUpdate bufferedUpdate = new BufferedUpdate();
    private final Bits updatesWithValue;
    private int index = 0;

    BufferedUpdateIterator() {
      this.termValuesIterator = termValues.iterator();
      this.byteValuesIterator = isNumeric ? null : byteValues.iterator();
      updatesWithValue = hasValues == null ? new Bits.MatchAllBits(numUpdates) : hasValues;
    }

    BufferedUpdate next() throws IOException {
      BytesRef next = termValuesIterator.next();
      if (next != null) {
        final int idx = index++;
        bufferedUpdate.termValue = next;
        bufferedUpdate.hasValue = updatesWithValue.get(idx);
        bufferedUpdate.termField = fields[getArrayIndex(fields.length, idx)];
        bufferedUpdate.docUpTo = docsUpTo[getArrayIndex(docsUpTo.length, idx)];
        if (bufferedUpdate.hasValue) {
          if (isNumeric) {
            bufferedUpdate.numericValue = numericValues[getArrayIndex(numericValues.length, idx)];
            bufferedUpdate.binaryValue = null;
          } else {
            bufferedUpdate.binaryValue = byteValuesIterator.next();
          }
        } else {
          bufferedUpdate.binaryValue = null;
          bufferedUpdate.numericValue = 0;
        }
        return bufferedUpdate;
      } else {
        return null;
      }
    }
  }

  private static int getArrayIndex(int arrayLength, int index) {
    assert arrayLength == 1 || arrayLength > index : "illegal array index length: " + arrayLength + " index: " + index;
    return Math.min(arrayLength-1, index);
  }
}
