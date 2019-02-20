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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.MultiTermQuery.RewriteMethod;

public abstract class ScoringRewrite<Q extends Query> extends TermCollectingRewrite<Q> {


  public final static ScoringRewrite<BooleanQuery> SCORING_BOOLEAN_QUERY_REWRITE = new ScoringRewrite<BooleanQuery>() {
    @Override
    protected BooleanQuery getTopLevelQuery() {
      return new BooleanQuery(true);
    }
    
    @Override
    protected void addClause(BooleanQuery topLevel, Term term, float boost) {
      final TermQuery tq = new TermQuery(term);
      tq.setBoost(boost);
      topLevel.add(tq, BooleanClause.Occur.SHOULD);
    }
    
    // Make sure we are still a singleton even after deserializing
    protected Object readResolve() {
      return SCORING_BOOLEAN_QUERY_REWRITE;
    }    
  };
  

  public final static RewriteMethod CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE = new RewriteMethod() {
    @Override
    public Query rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      final BooleanQuery bq = SCORING_BOOLEAN_QUERY_REWRITE.rewrite(reader, query);
      // TODO: if empty boolean query return NullQuery?
      if (bq.clauses().isEmpty())
        return bq;
      // strip the scores off
      final Query result = new ConstantScoreQuery(bq);
      result.setBoost(query.getBoost());
      return result;
    }

    // Make sure we are still a singleton even after deserializing
    protected Object readResolve() {
      return CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE;
    }
  };

  @Override
  public Q rewrite(final IndexReader reader, final MultiTermQuery query) throws IOException {
    final Q result = getTopLevelQuery();
    final int[] size = new int[1]; // "trick" to be able to make it final
    collectTerms(reader, query, new TermCollector() {
      public boolean collect(Term t, float boost) throws IOException {
        addClause(result, t, query.getBoost() * boost);
        size[0]++;
        return true;
      }
    });
    query.incTotalNumberOfTerms(size[0]);
    return result;
  }
}
