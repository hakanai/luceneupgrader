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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSet;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;


public class EliasFanoDocIdSet extends DocIdSet {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(EliasFanoDocIdSet.class);

  final EliasFanoEncoder efEncoder;

  public EliasFanoDocIdSet(int numValues, int upperBound) {
    efEncoder = new EliasFanoEncoder(numValues, upperBound);
  }


  public static boolean sufficientlySmallerThanBitSet(long numValues, long upperBound) {
    return EliasFanoEncoder.sufficientlySmallerThanBitSet(numValues, upperBound);
  }


  public void encodeFromDisi(DocIdSetIterator disi) throws IOException {
    while (efEncoder.numEncoded < efEncoder.numValues) {
      int x = disi.nextDoc();
      if (x == DocIdSetIterator.NO_MORE_DOCS) {
        throw new IllegalArgumentException("disi: " + disi.toString()
            + "\nhas " + efEncoder.numEncoded
            + " docs, but at least " + efEncoder.numValues + " are required.");
      }
      efEncoder.encodeNext(x);
    }
  }

  @Override
  public DocIdSetIterator iterator() {
    if (efEncoder.lastEncoded >= DocIdSetIterator.NO_MORE_DOCS) {
      throw new UnsupportedOperationException(
          "Highest encoded value too high for DocIdSetIterator.NO_MORE_DOCS: " + efEncoder.lastEncoded);
    }
    return new DocIdSetIterator() {
      private int curDocId = -1;
      private final EliasFanoDecoder efDecoder = efEncoder.getDecoder();

      @Override
      public int docID() {
        return curDocId;
      }

      private int setCurDocID(long value) {
        curDocId = (value == EliasFanoDecoder.NO_MORE_VALUES)
            ?  NO_MORE_DOCS
                : (int) value;
        return curDocId;
      }

      @Override
      public int nextDoc() {
        return setCurDocID(efDecoder.nextValue());
      }

      @Override
      public int advance(int target) {
        return setCurDocID(efDecoder.advanceToValue(target));
      }

      @Override
      public long cost() {
        return efDecoder.numEncoded();
      }
    };
  }


  @Override
  public boolean isCacheable() {
    return true;
  }

  @Override
  public boolean equals(Object other) {
    return ((other instanceof EliasFanoDocIdSet))
        && efEncoder.equals(((EliasFanoDocIdSet) other).efEncoder);
  }

  @Override
  public int hashCode() {
    return efEncoder.hashCode() ^ getClass().hashCode();
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED + efEncoder.ramBytesUsed();
  }
}

