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
import java.io.Serializable;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.queryParser.QueryParser; // for javadoc

public abstract class MultiTermQuery extends Query {
  protected RewriteMethod rewriteMethod = CONSTANT_SCORE_AUTO_REWRITE_DEFAULT;
  transient int numberOfTerms = 0;

  public static abstract class RewriteMethod implements Serializable {
    public abstract Query rewrite(IndexReader reader, MultiTermQuery query) throws IOException;
    
    protected FilteredTermEnum getTermsEnum(IndexReader reader, MultiTermQuery query) throws IOException {
      return query.getEnum(reader); // allow RewriteMethod subclasses to pull a FilteredTermEnum from the MTQ 
    }
  }


  public static final RewriteMethod CONSTANT_SCORE_FILTER_REWRITE = new RewriteMethod() {
    @Override
    public Query rewrite(IndexReader reader, MultiTermQuery query) {
      Query result = new ConstantScoreQuery(new MultiTermQueryWrapperFilter<MultiTermQuery>(query));
      result.setBoost(query.getBoost());
      return result;
    }

    // Make sure we are still a singleton even after deserializing
    protected Object readResolve() {
      return CONSTANT_SCORE_FILTER_REWRITE;
    }
  };


  public final static RewriteMethod SCORING_BOOLEAN_QUERY_REWRITE = ScoringRewrite.SCORING_BOOLEAN_QUERY_REWRITE;
  

  public final static RewriteMethod CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE = ScoringRewrite.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE;

  public static final class TopTermsScoringBooleanQueryRewrite extends TopTermsRewrite<BooleanQuery> {

    public TopTermsScoringBooleanQueryRewrite(int size) {
      super(size);
    }
    
    @Override
    protected int getMaxSize() {
      return BooleanQuery.getMaxClauseCount();
    }
    
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
  }
  
  public static final class TopTermsBoostOnlyBooleanQueryRewrite extends TopTermsRewrite<BooleanQuery> {
    
    public TopTermsBoostOnlyBooleanQueryRewrite(int size) {
      super(size);
    }
    
    @Override
    protected int getMaxSize() {
      return BooleanQuery.getMaxClauseCount();
    }
    
    @Override
    protected BooleanQuery getTopLevelQuery() {
      return new BooleanQuery(true);
    }
    
    @Override
    protected void addClause(BooleanQuery topLevel, Term term, float boost) {
      final Query q = new ConstantScoreQuery(new TermQuery(term));
      q.setBoost(boost);
      topLevel.add(q, BooleanClause.Occur.SHOULD);
    }
  }
    

  public static class ConstantScoreAutoRewrite extends org.trypticon.luceneupgrader.lucene3.internal.lucene.search.ConstantScoreAutoRewrite {}


  public final static RewriteMethod CONSTANT_SCORE_AUTO_REWRITE_DEFAULT = new ConstantScoreAutoRewrite() {
    @Override
    public void setTermCountCutoff(int count) {
      throw new UnsupportedOperationException("Please create a private instance");
    }

    @Override
    public void setDocCountPercent(double percent) {
      throw new UnsupportedOperationException("Please create a private instance");
    }

    // Make sure we are still a singleton even after deserializing
    protected Object readResolve() {
      return CONSTANT_SCORE_AUTO_REWRITE_DEFAULT;
    }
  };

  public MultiTermQuery() {
  }

  protected abstract FilteredTermEnum getEnum(IndexReader reader)
      throws IOException;

  @Deprecated
  public int getTotalNumberOfTerms() {
    return numberOfTerms;
  }
  
  @Deprecated
  public void clearTotalNumberOfTerms() {
    numberOfTerms = 0;
  }
  

  @Deprecated
  protected void incTotalNumberOfTerms(int inc) {
    numberOfTerms += inc;
  }

  @Override
  public final Query rewrite(IndexReader reader) throws IOException {
    return rewriteMethod.rewrite(reader, this);
  }

  public RewriteMethod getRewriteMethod() {
    return rewriteMethod;
  }


  public void setRewriteMethod(RewriteMethod method) {
    rewriteMethod = method;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Float.floatToIntBits(getBoost());
    result = prime * result;
    result += rewriteMethod.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MultiTermQuery other = (MultiTermQuery) obj;
    if (Float.floatToIntBits(getBoost()) != Float.floatToIntBits(other.getBoost()))
      return false;
    if (!rewriteMethod.equals(other.rewriteMethod)) {
      return false;
    }
    return true;
  }
 
}
