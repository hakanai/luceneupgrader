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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BitDocIdSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.FixedBitSet;

public abstract class DocValuesDocIdSet extends DocIdSet {

  protected final int maxDoc;
  protected final Bits acceptDocs;

  public DocValuesDocIdSet(int maxDoc, Bits acceptDocs) {
    this.maxDoc = maxDoc;
    this.acceptDocs = acceptDocs;
  }

  protected abstract boolean matchDoc(int doc);

  @Override
  public long ramBytesUsed() {
    return 0L;
  }

  @Override
  public final Bits bits() {
    return (acceptDocs == null) ? new Bits() {
      @Override
      public boolean get(int docid) {
        return matchDoc(docid);
      }

      @Override
      public int length() {
        return maxDoc;
      }
    } : new Bits() {
      @Override
      public boolean get(int docid) {
        return acceptDocs.get(docid) && matchDoc(docid);
      }

      @Override
      public int length() {
        return maxDoc;
      }
    };
  }

  @Override
  public final DocIdSetIterator iterator() throws IOException {
    if (acceptDocs == null) {
      // Specialization optimization disregard acceptDocs
      return new DocIdSetIterator() {
        private int doc = -1;
        
        @Override
        public int docID() {
          return doc;
        }
      
        @Override
        public int nextDoc() {
          do {
            doc++;
            if (doc >= maxDoc) {
              return doc = NO_MORE_DOCS;
            }
          } while (!matchDoc(doc));
          return doc;
        }
      
        @Override
        public int advance(int target) {
          for(doc=target; doc<maxDoc; doc++) {
            if (matchDoc(doc)) {
              return doc;
            }
          }
          return doc = NO_MORE_DOCS;
        }

        @Override
        public long cost() {
          return maxDoc;
        }
      };
    } else if (acceptDocs instanceof FixedBitSet) {
      // special case for FixedBitSet: use the iterator and filter it
      // (used e.g. when Filters are chained by FilteredQuery)
      return new FilteredDocIdSetIterator(new BitDocIdSet((FixedBitSet) acceptDocs).iterator()) {
        @Override
        protected boolean match(int doc) {
          return DocValuesDocIdSet.this.matchDoc(doc);
        }
      };
    } else {
      // Stupid consultation of acceptDocs and matchDoc()
      return new DocIdSetIterator() {
        private int doc = -1;
        
        @Override
        public int docID() {
          return doc;
        }
      
        @Override
        public int nextDoc() {
          return advance(doc + 1);
        }
      
        @Override
        public int advance(int target) {
          for(doc=target; doc<maxDoc; doc++) {
            if (acceptDocs.get(doc) && matchDoc(doc)) {
              return doc;
            }
          }
          return doc = NO_MORE_DOCS;
        }

        @Override
        public long cost() {
          return maxDoc;
        }
      };
    }
  }
}
