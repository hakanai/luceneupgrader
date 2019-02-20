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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermDocs;

import java.io.IOException;

public abstract class FieldCacheDocIdSet extends DocIdSet {

  protected final IndexReader reader;
  
  public FieldCacheDocIdSet(IndexReader reader) {
    this.reader = reader;
  }

  protected abstract boolean matchDoc(int doc);

  @Override
  public final boolean isCacheable() {
    return !reader.hasDeletions();
  }

  @Override
  public final DocIdSetIterator iterator() throws IOException {
    if (!reader.hasDeletions()) {
      // Specialization optimization disregard deleted docs
      final int maxDoc = reader.maxDoc();
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
      };
    } else {
      // Stupid consultation of acceptDocs and matchDoc()
      final TermDocs termDocs = reader.termDocs(null);
      return new DocIdSetIterator() {
        private int doc = -1;
        
        @Override
        public int docID() {
          return doc;
        }
      
        @Override
        public int nextDoc() throws IOException {
          do {
            if (!termDocs.next())
              return doc = NO_MORE_DOCS;
          } while (!matchDoc(doc = termDocs.doc()));
          return doc;
        }
      
        @Override
        public int advance(int target) throws IOException {
          if (!termDocs.skipTo(target))
            return doc = NO_MORE_DOCS;
          while (!matchDoc(doc = termDocs.doc())) { 
            if (!termDocs.next())
              return doc = NO_MORE_DOCS;
          }
          return doc;
        }
      };
    }
  }
}
