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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.search.comparators;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.LeafFieldComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Pruning;

/**
 * Comparator based on {@link Long#compare} for {@code numHits}. This comparator provides a skipping
 * functionality – an iterator that can skip over non-competitive documents.
 */
public class LongComparator extends NumericComparator<Long> {
  private final long[] values;
  protected long topValue;
  protected long bottom;

  public LongComparator(
      int numHits, String field, Long missingValue, boolean reverse, Pruning pruning) {
    super(field, missingValue != null ? missingValue : 0L, reverse, pruning, Long.BYTES);
    values = new long[numHits];
  }

  @Override
  public int compare(int slot1, int slot2) {
    return Long.compare(values[slot1], values[slot2]);
  }

  @Override
  public void setTopValue(Long value) {
    super.setTopValue(value);
    topValue = value;
  }

  @Override
  public Long value(int slot) {
    return Long.valueOf(values[slot]);
  }

  @Override
  public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
    return new LongLeafComparator(context);
  }

  /** Leaf comparator for {@link LongComparator} that provides skipping functionality */
  public class LongLeafComparator extends NumericLeafComparator {

    public LongLeafComparator(LeafReaderContext context) throws IOException {
      super(context);
    }

    private long getValueForDoc(int doc) throws IOException {
      if (docValues.advanceExact(doc)) {
        return docValues.longValue();
      } else {
        return missingValue;
      }
    }

    @Override
    public void setBottom(int slot) throws IOException {
      bottom = values[slot];
      super.setBottom(slot);
    }

    @Override
    public int compareBottom(int doc) throws IOException {
      return Long.compare(bottom, getValueForDoc(doc));
    }

    @Override
    public int compareTop(int doc) throws IOException {
      return Long.compare(topValue, getValueForDoc(doc));
    }

    @Override
    public void copy(int slot, int doc) throws IOException {
      values[slot] = getValueForDoc(doc);
      super.copy(slot, doc);
    }

    @Override
    protected int compareMissingValueWithBottomValue() {
      return Long.compare(missingValue, bottom);
    }

    @Override
    protected int compareMissingValueWithTopValue() {
      return Long.compare(missingValue, topValue);
    }

    @Override
    protected void encodeBottom(byte[] packedValue) {
      LongPoint.encodeDimension(bottom, packedValue, 0);
    }

    @Override
    protected void encodeTop(byte[] packedValue) {
      LongPoint.encodeDimension(topValue, packedValue, 0);
    }
  }
}
