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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReader;

public final class BoostQuery extends Query {

  private final Query query;
  private final float boost;

  public BoostQuery(Query query, float boost) {
    this.query = Objects.requireNonNull(query);
    if (Float.isFinite(boost) == false || Float.compare(boost, 0f) < 0) {
      throw new IllegalArgumentException("boost must be a positive float, got " + boost);
    }
    this.boost = boost;
  }

  public Query getQuery() {
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
  
  private boolean equalsTo(BoostQuery other) {
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
    final Query rewritten = query.rewrite(reader);

    if (boost == 1f) {
      return rewritten;
    }

    if (rewritten.getClass() == BoostQuery.class) {
      BoostQuery in = (BoostQuery) rewritten;
      return new BoostQuery(in.query, boost * in.boost);
    }

    if (boost == 0f && rewritten.getClass() != ConstantScoreQuery.class) {
      // so that we pass needScores=false
      return new BoostQuery(new ConstantScoreQuery(rewritten), 0f);
    }

    if (query != rewritten) {
      return new BoostQuery(rewritten, boost);
    }

    return super.rewrite(reader);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
  }

  @Override
  public String toString(String field) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    builder.append(query.toString(field));
    builder.append(")");
    builder.append("^");
    builder.append(boost);
    return builder.toString();
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
    return query.createWeight(searcher, scoreMode, BoostQuery.this.boost * boost);
  }

}
