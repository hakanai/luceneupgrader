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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.document.StringField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;

public final class NormsFieldExistsQuery extends Query {

  private final String field;

  public NormsFieldExistsQuery(String field) {
    this.field = Objects.requireNonNull(field);
  }

  public String getField() {
    return field;
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           field.equals(((NormsFieldExistsQuery) other).field);
  }

  @Override
  public int hashCode() {
    return 31 * classHash() + field.hashCode();
  }

  @Override
  public String toString(String field) {
    return "NormsFieldExistsQuery [field=" + this.field + "]";
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    return new ConstantScoreWeight(this, boost) {
      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        FieldInfos fieldInfos = context.reader().getFieldInfos();
        FieldInfo fieldInfo = fieldInfos.fieldInfo(field);
        if (fieldInfo == null || fieldInfo.hasNorms() == false) {
          return null;
        }
        LeafReader reader = context.reader();
        DocIdSetIterator iterator = reader.getNormValues(field);
        return new ConstantScoreScorer(this, score(), iterator);
      }

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        return true;
      }
    };
  }
}
