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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSet;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSetIterator;

public class BitDocIdSet extends DocIdSet {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(BitDocIdSet.class);

  private final BitSet set;
  private final long cost;

  public BitDocIdSet(BitSet set, long cost) {
    this.set = set;
    this.cost = cost;
  }

  public BitDocIdSet(BitSet set) {
    this(set, set.approximateCardinality());
  }

  @Override
  public DocIdSetIterator iterator() {
    return new BitSetIterator(set, cost);
  }

  @Override
  public BitSet bits() {
    return set;
  }

  @Override
  public boolean isCacheable() {
    return true;
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED + set.ramBytesUsed();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(set=" + set + ",cost=" + cost + ")";
  }

  public static final class Builder {

    private final int maxDoc;
    private final int threshold;
    private SparseFixedBitSet sparseSet;
    private FixedBitSet denseSet;

    // we cache an upper bound of the cost of this builder so that we don't have
    // to re-compute approximateCardinality on the sparse set every time 
    private long costUpperBound;

    public Builder(int maxDoc, boolean full) {
      this.maxDoc = maxDoc;
      threshold = maxDoc >>> 10;
      if (full) {
        denseSet = new FixedBitSet(maxDoc);
        denseSet.set(0, maxDoc);
      }
    }

    public Builder(int maxDoc) {
      this(maxDoc, false);
    }

    // pkg-private for testing
    boolean dense() {
      return denseSet != null;
    }

    public boolean isDefinitelyEmpty() {
      return sparseSet == null && denseSet == null;
    }

    public void or(DocIdSetIterator it) throws IOException {
      if (denseSet != null) {
        // already upgraded
        denseSet.or(it);
        return;
      }

      final long itCost = it.cost();
      costUpperBound += itCost;
      if (costUpperBound >= threshold) {
        costUpperBound = (sparseSet == null ? 0 : sparseSet.approximateCardinality()) + itCost;

        if (costUpperBound >= threshold) {
          // upgrade
          denseSet = new FixedBitSet(maxDoc);
          denseSet.or(it);
          if (sparseSet != null) {
            denseSet.or(new BitSetIterator(sparseSet, 0L));
          }
          return;
        }
      }

      // we are still sparse
      if (sparseSet == null) {
        sparseSet = new SparseFixedBitSet(maxDoc);
      }
      sparseSet.or(it);
    }

    @Deprecated
    public void and(DocIdSetIterator it) throws IOException {
      if (denseSet != null) {
        denseSet.and(it);
      } else if (sparseSet != null) {
        sparseSet.and(it);
      }
    }

    @Deprecated
    public void andNot(DocIdSetIterator it) throws IOException {
      if (denseSet != null) {
        denseSet.andNot(it);
      } else if (sparseSet != null) {
        sparseSet.andNot(it);
      }
    }

    public BitDocIdSet build() {
      final BitDocIdSet result;
      if (denseSet != null) {
        result = new BitDocIdSet(denseSet);
      } else if (sparseSet != null) {
        result = new BitDocIdSet(sparseSet);
      } else {
        result = null;
      }
      denseSet = null;
      sparseSet = null;
      costUpperBound = 0;
      return result;
    }

  }

}
