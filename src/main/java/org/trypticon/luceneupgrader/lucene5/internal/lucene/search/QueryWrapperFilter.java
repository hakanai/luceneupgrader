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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;

@Deprecated
public class QueryWrapperFilter extends Filter {
  private final Query query;


  public QueryWrapperFilter(Query query) {
    if (query == null)
      throw new NullPointerException("Query may not be null");
    this.query = query;
  }
  
  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    return new BoostQuery(new ConstantScoreQuery(query), 0f);
  }
  
  public final Query getQuery() {
    return query;
  }

  @Override
  public DocIdSet getDocIdSet(final LeafReaderContext context, final Bits acceptDocs) throws IOException {
    // get a private context that is used to rewrite, createWeight and score eventually
    final LeafReaderContext privateContext = context.reader().getContext();
    final Weight weight = new IndexSearcher(privateContext).createNormalizedWeight(query, false);
    
    DocIdSet set = new DocIdSet() {
      @Override
      public DocIdSetIterator iterator() throws IOException {
        Scorer s = weight.scorer(privateContext);
        return s == null ? null : s.iterator();
      }

      @Override
      public long ramBytesUsed() {
        return 0L;
      }
    };
    return BitsFilteredDocIdSet.wrap(set, acceptDocs);
  }

  @Override
  public String toString(String field) {
    return "QueryWrapperFilter(" + query.toString(field) + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o) == false) {
      return false;
    }
    return this.query.equals(((QueryWrapperFilter)o).query);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + query.hashCode();
  }
}
