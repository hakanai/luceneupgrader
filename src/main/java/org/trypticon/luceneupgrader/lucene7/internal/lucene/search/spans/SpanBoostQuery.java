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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search.spans;


import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BoostQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;

public final class SpanBoostQuery extends SpanQuery {

  private final SpanQuery query;
  private final float boost;

  public SpanBoostQuery(SpanQuery query, float boost) {
    this.query = Objects.requireNonNull(query);
    this.boost = boost;
  }

  public SpanQuery getQuery() {
    return query;
  }

  public float getBoost() {
    return boost;
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }
  
  private boolean equalsTo(SpanBoostQuery other) {
    return query.equals(other.query) && 
           Float.floatToIntBits(boost) == Float.floatToIntBits(other.boost);
  }

  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + query.hashCode();
    h = 31 * h + Float.floatToIntBits(boost);
    return h;
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (boost == 1f) {
      return query;
    }

    final SpanQuery rewritten = (SpanQuery) query.rewrite(reader);
    if (query != rewritten) {
      return new SpanBoostQuery(rewritten, boost);
    }

    if (query.getClass() == SpanBoostQuery.class) {
      SpanBoostQuery in = (SpanBoostQuery) query;
      return new SpanBoostQuery(in.query, boost * in.boost);
    }

    return super.rewrite(reader);
  }

  @Override
  public String toString(String field) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    builder.append(query.toString(field));
    builder.append(")^");
    builder.append(boost);
    return builder.toString();
  }

  @Override
  public String getField() {
    return query.getField();
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return query.createWeight(searcher, needsScores, SpanBoostQuery.this.boost * boost);
  }

}
