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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.BooleanClause.Occur;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.MultiTermQuery;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.ScoringRewrite;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.TopTermsRewrite;

public class SpanMultiTermQueryWrapper<Q extends MultiTermQuery> extends SpanQuery {

  protected final Q query;
  private SpanRewriteMethod rewriteMethod;

  @SuppressWarnings({"rawtypes","unchecked"})
  public SpanMultiTermQueryWrapper(Q query) {
    this.query = Objects.requireNonNull(query);
    this.rewriteMethod = selectRewriteMethod(query);
  }

  private static SpanRewriteMethod selectRewriteMethod(MultiTermQuery query) {
    MultiTermQuery.RewriteMethod method = query.getRewriteMethod();
    if (method instanceof TopTermsRewrite) {
      final int pqsize = ((TopTermsRewrite) method).getSize();
      return new TopTermsSpanBooleanQueryRewrite(pqsize);
    } else {
      return SCORING_SPAN_QUERY_REWRITE;
    }
  }

  public final SpanRewriteMethod getRewriteMethod() {
    return rewriteMethod;
  }

  public final void setRewriteMethod(SpanRewriteMethod rewriteMethod) {
    this.rewriteMethod = rewriteMethod;
  }

  @Override
  public String getField() {
    return query.getField();
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    throw new IllegalArgumentException("Rewrite first!");
  }

  public Query getWrappedQuery() {
    return query;
  }

  @Override
  public String toString(String field) {
    StringBuilder builder = new StringBuilder();
    builder.append("SpanMultiTermQueryWrapper(");
    // NOTE: query.toString must be placed in a temp local to avoid compile errors on Java 8u20
    // see https://bugs.openjdk.java.net/browse/JDK-8056984?page=com.atlassian.streams.streams-jira-plugin:activity-stream-issue-tab
    String queryStr = query.toString(field);
    builder.append(queryStr);
    builder.append(")");
    return builder.toString();
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    return rewriteMethod.rewrite(reader, query);
  }
  
  @Override
  public int hashCode() {
    return classHash() * 31 + query.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           query.equals(((SpanMultiTermQueryWrapper<?>) other).query);
  }

  public static abstract class SpanRewriteMethod extends MultiTermQuery.RewriteMethod {
    @Override
    public abstract SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException;
  }

  public final static SpanRewriteMethod SCORING_SPAN_QUERY_REWRITE = new SpanRewriteMethod() {
    private final ScoringRewrite<List<SpanQuery>> delegate = new ScoringRewrite<List<SpanQuery>>() {
      @Override
      protected List<SpanQuery> getTopLevelBuilder() {
        return new ArrayList<SpanQuery>();
      }

      protected Query build(List<SpanQuery> builder) {
        return new SpanOrQuery(builder.toArray(new SpanQuery[builder.size()]));
      }

      @Override
      protected void checkMaxClauseCount(int count) {
        // we accept all terms as SpanOrQuery has no limits
      }
    
      @Override
      protected void addClause(List<SpanQuery> topLevel, Term term, int docCount, float boost, TermContext states) {
        final SpanTermQuery q = new SpanTermQuery(term, states);
        topLevel.add(q);
      }
    };
    
    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      return (SpanQuery) delegate.rewrite(reader, query);
    }
  };
  
  public static final class TopTermsSpanBooleanQueryRewrite extends SpanRewriteMethod  {
    private final TopTermsRewrite<List<SpanQuery>> delegate;
  
    public TopTermsSpanBooleanQueryRewrite(int size) {
      delegate = new TopTermsRewrite<List<SpanQuery>>(size) {
        @Override
        protected int getMaxSize() {
          return Integer.MAX_VALUE;
        }
    
        @Override
        protected List<SpanQuery> getTopLevelBuilder() {
          return new ArrayList<SpanQuery>();
        }

        @Override
        protected Query build(List<SpanQuery> builder) {
          return new SpanOrQuery(builder.toArray(new SpanQuery[builder.size()]));
        }

        @Override
        protected void addClause(List<SpanQuery> topLevel, Term term, int docFreq, float boost, TermContext states) {
          final SpanTermQuery q = new SpanTermQuery(term, states);
          topLevel.add(q);
        }
      };
    }
    
    public int getSize() {
      return delegate.getSize();
    }

    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      return (SpanQuery) delegate.rewrite(reader, query);
    }
  
    @Override
    public int hashCode() {
      return 31 * delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final TopTermsSpanBooleanQueryRewrite other = (TopTermsSpanBooleanQueryRewrite) obj;
      return delegate.equals(other.delegate);
    }
    
  }
  
}
