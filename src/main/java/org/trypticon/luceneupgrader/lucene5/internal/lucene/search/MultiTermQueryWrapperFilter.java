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

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BitDocIdSet;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;

@Deprecated
public class MultiTermQueryWrapperFilter<Q extends MultiTermQuery> extends Filter {

  protected final Q query;

  protected MultiTermQueryWrapperFilter(Q query) {
      this.query = query;
  }

  @Override
  public String toString(String field) {
    // query.toString should be ok for the filter, too, if the query boost is 1.0f
    return query.toString(field);
  }

  @Override
  @SuppressWarnings({"rawtypes"})
  public final boolean equals(final Object o) {
    if (o==this) return true;
    if (super.equals(o) == false) {
      return false;
    }
    return this.query.equals( ((MultiTermQueryWrapperFilter)o).query );
  }

  @Override
  public final int hashCode() {
    return 31 * super.hashCode() + query.hashCode();
  }

  public final String getField() { return query.getField(); }

  @Override
  public DocIdSet getDocIdSet(LeafReaderContext context, Bits acceptDocs) throws IOException {
    final Terms terms = context.reader().terms(query.field);
    if (terms == null) {
      // field does not exist
      return null;
    }

    final TermsEnum termsEnum = query.getTermsEnum(terms);
    assert termsEnum != null;

    BitDocIdSet.Builder builder = new BitDocIdSet.Builder(context.reader().maxDoc());
    PostingsEnum docs = null;
    while (termsEnum.next() != null) {
      docs = termsEnum.postings(docs, PostingsEnum.NONE);
      builder.or(docs);
    }
    return BitsFilteredDocIdSet.wrap(builder.build(), acceptDocs);
  }
}
