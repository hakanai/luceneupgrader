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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.*;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.BooleanQuery.Builder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.AttributeSource;

import java.io.IOException;
import java.util.Objects;

public abstract class MultiTermQuery extends Query {
  protected final String field;
  protected RewriteMethod rewriteMethod = CONSTANT_SCORE_REWRITE;

  public static abstract class RewriteMethod {
    public abstract Query rewrite(IndexReader reader, MultiTermQuery query) throws IOException;
    protected TermsEnum getTermsEnum(MultiTermQuery query, Terms terms, AttributeSource atts) throws IOException {
      return query.getTermsEnum(terms, atts); // allow RewriteMethod subclasses to pull a TermsEnum from the MTQ 
    }
  }


  public static final RewriteMethod CONSTANT_SCORE_REWRITE = new RewriteMethod() {
    @Override
    public Query rewrite(IndexReader reader, MultiTermQuery query) {
      return new MultiTermQueryConstantScoreWrapper<>(query);
    }
  };


  public final static RewriteMethod SCORING_BOOLEAN_REWRITE = ScoringRewrite.SCORING_BOOLEAN_REWRITE;


  public final static RewriteMethod CONSTANT_SCORE_BOOLEAN_REWRITE = ScoringRewrite.CONSTANT_SCORE_BOOLEAN_REWRITE;

  public static final class TopTermsScoringBooleanQueryRewrite extends TopTermsRewrite<BooleanQuery.Builder> {

    public TopTermsScoringBooleanQueryRewrite(int size) {
      super(size);
    }
    
    @Override
    protected int getMaxSize() {
      return BooleanQuery.getMaxClauseCount();
    }
    
    @Override
    protected BooleanQuery.Builder getTopLevelBuilder() {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(true);
      return builder;
    }
    
    @Override
    protected Query build(Builder builder) {
      return builder.build();
    }
    
    @Override
    protected void addClause(BooleanQuery.Builder topLevel, Term term, int docCount, float boost, TermContext states) {
      final TermQuery tq = new TermQuery(term, states);
      topLevel.add(new BoostQuery(tq, boost), BooleanClause.Occur.SHOULD);
    }
  }
  
  public static final class TopTermsBlendedFreqScoringRewrite extends
      TopTermsRewrite<BlendedTermQuery.Builder> {

    public TopTermsBlendedFreqScoringRewrite(int size) {
      super(size);
    }

    @Override
    protected int getMaxSize() {
      return BooleanQuery.getMaxClauseCount();
    }

    @Override
    protected BlendedTermQuery.Builder getTopLevelBuilder() {
      BlendedTermQuery.Builder builder = new BlendedTermQuery.Builder();
      builder.setRewriteMethod(BlendedTermQuery.BOOLEAN_REWRITE);
      return builder;
    }

    @Override
    protected Query build(BlendedTermQuery.Builder builder) {
      return builder.build();
    }

    @Override
    protected void addClause(BlendedTermQuery.Builder topLevel, Term term, int docCount,
        float boost, TermContext states) {
      topLevel.add(term, boost, states);
    }
  }

  public static final class TopTermsBoostOnlyBooleanQueryRewrite extends TopTermsRewrite<BooleanQuery.Builder> {
    
    public TopTermsBoostOnlyBooleanQueryRewrite(int size) {
      super(size);
    }
    
    @Override
    protected int getMaxSize() {
      return BooleanQuery.getMaxClauseCount();
    }
    
    @Override
    protected BooleanQuery.Builder getTopLevelBuilder() {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(true);
      return builder;
    }
    
    @Override
    protected Query build(BooleanQuery.Builder builder) {
      return builder.build();
    }
    
    @Override
    protected void addClause(BooleanQuery.Builder topLevel, Term term, int docFreq, float boost, TermContext states) {
      final Query q = new ConstantScoreQuery(new TermQuery(term, states));
      topLevel.add(new BoostQuery(q, boost), BooleanClause.Occur.SHOULD);
    }
  }

  public MultiTermQuery(final String field) {
    this.field = Objects.requireNonNull(field, "field must not be null");
  }

  public final String getField() { return field; }


  protected abstract TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException;


  protected final TermsEnum getTermsEnum(Terms terms) throws IOException {
    return getTermsEnum(terms, new AttributeSource());
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
    int result = classHash();
    result = prime * result + rewriteMethod.hashCode();
    result = prime * result + field.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }

  private boolean equalsTo(MultiTermQuery other) {
    return rewriteMethod.equals(other.rewriteMethod) && 
           field.equals(other.field);
  }
}
