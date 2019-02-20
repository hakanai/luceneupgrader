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

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermDocs;  // for javadocs

public class FieldCacheTermsFilter extends Filter {
  private String field;
  private String[] terms;

  public FieldCacheTermsFilter(String field, String... terms) {
    this.field = field;
    this.terms = terms;
  }

  public FieldCache getFieldCache() {
    return FieldCache.DEFAULT;
  }

  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
    final FieldCache.StringIndex fcsi = getFieldCache().getStringIndex(reader, field);
    final FixedBitSet bits = new FixedBitSet(fcsi.lookup.length);
    for (int i=0;i<terms.length;i++) {
      int termNumber = fcsi.binarySearchLookup(terms[i]);
      if (termNumber > 0) {
        bits.set(termNumber);
      }
    }
    return new FieldCacheDocIdSet(reader) {
      @Override
      protected final boolean matchDoc(int doc) {
        return bits.get(fcsi.order[doc]);
      }
    };
  }
}
