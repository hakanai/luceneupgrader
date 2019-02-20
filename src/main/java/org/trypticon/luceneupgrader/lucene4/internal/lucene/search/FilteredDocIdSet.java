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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;

public abstract class FilteredDocIdSet extends DocIdSet {
  private final DocIdSet _innerSet;
  
  public FilteredDocIdSet(DocIdSet innerSet) {
    _innerSet = innerSet;
  }
  
  @Override
  public boolean isCacheable() {
    return _innerSet.isCacheable();
  }

  @Override
  public long ramBytesUsed() {
    return RamUsageEstimator.NUM_BYTES_OBJECT_REF + _innerSet.ramBytesUsed();
  }

  @Override
  public Bits bits() throws IOException {
    final Bits bits = _innerSet.bits();
    return (bits == null) ? null : new Bits() {
      @Override
      public boolean get(int docid) {
        return bits.get(docid) && FilteredDocIdSet.this.match(docid);
      }

      @Override
      public int length() {
        return bits.length();
      }
    };
  }

  protected abstract boolean match(int docid);

  @Override
  public DocIdSetIterator iterator() throws IOException {
    final DocIdSetIterator iterator = _innerSet.iterator();
    if (iterator == null) {
      return null;
    }
    return new FilteredDocIdSetIterator(iterator) {
      @Override
      protected boolean match(int docid) {
        return FilteredDocIdSet.this.match(docid);
      }
    };
  }
}
