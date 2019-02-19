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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.spans;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;

public class FieldMaskingSpanQuery extends SpanQuery {
  private SpanQuery maskedQuery;
  private String field;
    
  public FieldMaskingSpanQuery(SpanQuery maskedQuery, String maskedField) {
    this.maskedQuery = maskedQuery;
    this.field = maskedField;
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
  public Spans getSpans(AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts) throws IOException {
    return maskedQuery.getSpans(context, acceptDocs, termContexts);
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    maskedQuery.extractTerms(terms);
  }  

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return maskedQuery.createWeight(searcher);
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    FieldMaskingSpanQuery clone = null;

    SpanQuery rewritten = (SpanQuery) maskedQuery.rewrite(reader);
    if (rewritten != maskedQuery) {
      clone = (FieldMaskingSpanQuery) this.clone();
      clone.maskedQuery = rewritten;
    }

    if (clone != null) {
      return clone;
    } else {
      return this;
    }
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("mask(");
    buffer.append(maskedQuery.toString(field));
    buffer.append(")");
    buffer.append(ToStringUtils.boost(getBoost()));
    buffer.append(" as ");
    buffer.append(this.field);
    return buffer.toString();
  }
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FieldMaskingSpanQuery))
      return false;
    FieldMaskingSpanQuery other = (FieldMaskingSpanQuery) o;
    return (this.getField().equals(other.getField())
            && (this.getBoost() == other.getBoost())
            && this.getMaskedQuery().equals(other.getMaskedQuery()));

  }
  
  @Override
  public int hashCode() {
    return getMaskedQuery().hashCode()
      ^ getField().hashCode()
      ^ Float.floatToRawIntBits(getBoost());
  }
}
