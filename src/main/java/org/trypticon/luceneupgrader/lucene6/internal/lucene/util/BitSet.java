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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator;

public abstract class BitSet implements MutableBits, Accountable {

  public static BitSet of(DocIdSetIterator it, int maxDoc) throws IOException {
    final long cost = it.cost();
    final int threshold = maxDoc >>> 7;
    BitSet set;
    if (cost < threshold) {
      set = new SparseFixedBitSet(maxDoc);
    } else {
      set = new FixedBitSet(maxDoc);
    }
    set.or(it);
    return set;
  }

  public abstract void set(int i);


  public abstract void clear(int startIndex, int endIndex);

  public abstract int cardinality();

  public int approximateCardinality() {
    return cardinality();
  }


  public abstract int prevSetBit(int index);


  public abstract int nextSetBit(int index);

  protected final void assertUnpositioned(DocIdSetIterator iter) {
    if (iter.docID() != -1) {
      throw new IllegalStateException("This operation only works with an unpositioned iterator, got current position = " + iter.docID());
    }
  }

  public void or(DocIdSetIterator iter) throws IOException {
    assertUnpositioned(iter);
    for (int doc = iter.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = iter.nextDoc()) {
      set(doc);
    }
  }

}
