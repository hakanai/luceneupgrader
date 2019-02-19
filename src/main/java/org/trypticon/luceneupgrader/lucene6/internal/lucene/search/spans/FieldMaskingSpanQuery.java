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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search.spans;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Query;

import java.io.IOException;
import java.util.Objects;

public final class FieldMaskingSpanQuery extends SpanQuery {
  private final SpanQuery maskedQuery;
  private final String field;
    
  public FieldMaskingSpanQuery(SpanQuery maskedQuery, String maskedField) {
    this.maskedQuery = Objects.requireNonNull(maskedQuery);
    this.field = Objects.requireNonNull(maskedField);
  }

  @Override
  public String getField() {
    return field;
  }

  public SpanQuery getMaskedQuery() {
    return maskedQuery;
  }

  // :NOTE: getBoost and setBoost are not proxied to the maskedQuery
  // ...this is done to be more consistent with things like SpanFirstQuery

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    return maskedQuery.createWeight(searcher, needsScores);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    SpanQuery rewritten = (SpanQuery) maskedQuery.rewrite(reader);
    if (rewritten != maskedQuery) {
      return new FieldMaskingSpanQuery(rewritten, field);
    }

    return super.rewrite(reader);
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("mask(");
    buffer.append(maskedQuery.toString(field));
    buffer.append(")");
    buffer.append(" as ");
    buffer.append(this.field);
    return buffer.toString();
  }
  
  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }
  
  private boolean equalsTo(FieldMaskingSpanQuery other) {
    return getField().equals(other.getField()) && 
           getMaskedQuery().equals(other.getMaskedQuery());
  }

  @Override
  public int hashCode() {
    return classHash() ^ 
           getMaskedQuery().hashCode() ^ 
           getField().hashCode();
  }
}
