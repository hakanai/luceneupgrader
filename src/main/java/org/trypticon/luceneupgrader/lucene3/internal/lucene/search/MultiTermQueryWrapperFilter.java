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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermDocs;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermEnum;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.FixedBitSet;

public class MultiTermQueryWrapperFilter<Q extends MultiTermQuery> extends Filter {
    
  protected final Q query;

  protected MultiTermQueryWrapperFilter(Q query) {
      this.query = query;
  }
  
  @Override
  public String toString() {
    // query.toString should be ok for the filter, too, if the query boost is 1.0f
    return query.toString();
  }

  @Override
  @SuppressWarnings({"unchecked","rawtypes"})
  public final boolean equals(final Object o) {
    if (o==this) return true;
    if (o==null) return false;
    if (this.getClass().equals(o.getClass())) {
      return this.query.equals( ((MultiTermQueryWrapperFilter)o).query );
    }
    return false;
  }

  @Override
  public final int hashCode() {
    return query.hashCode();
  }
  
  @Deprecated
  public int getTotalNumberOfTerms() {
    return query.getTotalNumberOfTerms();
  }
  
  @Deprecated
  public void clearTotalNumberOfTerms() {
    query.clearTotalNumberOfTerms();
  }
  
  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
    final TermEnum enumerator = query.getEnum(reader);
    try {
      // if current term in enum is null, the enum is empty -> shortcut
      if (enumerator.term() == null)
        return DocIdSet.EMPTY_DOCIDSET;
      // else fill into a FixedBitSet
      final FixedBitSet bitSet = new FixedBitSet(reader.maxDoc());
      final int[] docs = new int[32];
      final int[] freqs = new int[32];
      TermDocs termDocs = reader.termDocs();
      try {
        int termCount = 0;
        do {
          Term term = enumerator.term();
          if (term == null)
            break;
          termCount++;
          termDocs.seek(term);
          while (true) {
            final int count = termDocs.read(docs, freqs);
            if (count != 0) {
              for(int i=0;i<count;i++) {
                bitSet.set(docs[i]);
              }
            } else {
              break;
            }
          }
        } while (enumerator.next());

        query.incTotalNumberOfTerms(termCount);

      } finally {
        termDocs.close();
      }
      return bitSet;
    } finally {
      enumerator.close();
    }
  }

}
