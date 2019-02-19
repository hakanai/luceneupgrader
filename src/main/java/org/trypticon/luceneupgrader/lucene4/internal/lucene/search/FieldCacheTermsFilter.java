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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum; // javadoc @link
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet;

public class FieldCacheTermsFilter extends Filter {
  private String field;
  private BytesRef[] terms;

  public FieldCacheTermsFilter(String field, BytesRef... terms) {
    this.field = field;
    this.terms = terms;
  }

  public FieldCacheTermsFilter(String field, String... terms) {
    this.field = field;
    this.terms = new BytesRef[terms.length];
    for (int i = 0; i < terms.length; i++)
      this.terms[i] = new BytesRef(terms[i]);
  }

  public FieldCache getFieldCache() {
    return FieldCache.DEFAULT;
  }

  @Override
  public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
    final SortedDocValues fcsi = getFieldCache().getTermsIndex(context.reader(), field);
    final FixedBitSet bits = new FixedBitSet(fcsi.getValueCount());
    for (int i=0;i<terms.length;i++) {
      int ord = fcsi.lookupTerm(terms[i]);
      if (ord >= 0) {
        bits.set(ord);
      }
    }
    return new FieldCacheDocIdSet(context.reader().maxDoc(), acceptDocs) {
      @Override
      protected final boolean matchDoc(int doc) {
        int ord = fcsi.getOrd(doc);
        if (ord == -1) {
          // missing
          return false;
        } else {
          return bits.get(ord);
        }
      }
    };
  }
}
