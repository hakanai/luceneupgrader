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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans;

import java.io.IOException;
import java.lang.reflect.Method;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.MultiTermQuery;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.TopTermsRewrite;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.ScoringRewrite;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.BooleanClause.Occur; // javadocs only

public class SpanMultiTermQueryWrapper<Q extends MultiTermQuery> extends SpanQuery {
  protected final Q query;
  private Method getFieldMethod = null, getTermMethod = null;

  @SuppressWarnings({"rawtypes","unchecked"})
  public SpanMultiTermQueryWrapper(Q query) {
    this.query = query;
    
    MultiTermQuery.RewriteMethod method = query.getRewriteMethod();
    if (method instanceof TopTermsRewrite) {
      final int pqsize = ((TopTermsRewrite) method).getSize();
      setRewriteMethod(new TopTermsSpanBooleanQueryRewrite(pqsize));
    } else {
      setRewriteMethod(SCORING_SPAN_QUERY_REWRITE); 
    }
    
    // In Lucene 3.x, MTQ has no fixed field, we need to get it by reflection.
    // If the underlying query does not allow to get a constant field, we throw IAE:
    try {
      getFieldMethod = query.getClass().getMethod("getField");
    } catch (Exception e1) {
      try {
        getTermMethod = query.getClass().getMethod("getTerm");
      } catch (Exception e2) {
        try {
          getTermMethod = query.getClass().getMethod("getPrefix");
        } catch (Exception e3) {
          throw new IllegalArgumentException("SpanMultiTermQueryWrapper can only wrap MultiTermQueries"+
            " that can return a field name using getField() or getTerm()");
        }
      }
    }
  }
  
  public final SpanRewriteMethod getRewriteMethod() {
    final MultiTermQuery.RewriteMethod m = query.getRewriteMethod();
    if (!(m instanceof SpanRewriteMethod))
      throw new UnsupportedOperationException("You can only use SpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
    return (SpanRewriteMethod) m;
  }

  public final void setRewriteMethod(SpanRewriteMethod rewriteMethod) {
    query.setRewriteMethod(rewriteMethod);
  }
  
  @Override
  public Spans getSpans(IndexReader reader) throws IOException {
    throw new UnsupportedOperationException("Query should have been rewritten");
  }

  @Override
  public String getField() {
    try {
      if (getFieldMethod != null) {
        return (String) getFieldMethod.invoke(query);
      } else {
        assert getTermMethod != null;
        return ((Term) getTermMethod.invoke(query)).field();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot invoke getField() or getTerm() on wrapped query.", e);
    }
  }

  @Override
  public String toString(String field) {
    StringBuilder builder = new StringBuilder();
    builder.append("SpanMultiTermQueryWrapper(");
    builder.append(query.toString(field));
    builder.append(")");
    return builder.toString();
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    final Query q = query.rewrite(reader);
    if (!(q instanceof SpanQuery))
      throw new UnsupportedOperationException("You can only use SpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
    return q;
  }
  
  @Override
  public int hashCode() {
    return 31 * query.hashCode();
  }

  @Override
  @SuppressWarnings({"rawtypes","unchecked"})
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final SpanMultiTermQueryWrapper other = (SpanMultiTermQueryWrapper) obj;
    return query.equals(other.query);
  }

  public static abstract class SpanRewriteMethod extends MultiTermQuery.RewriteMethod {
    @Override
    public abstract SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException;
  }

  public final static SpanRewriteMethod SCORING_SPAN_QUERY_REWRITE = new SpanRewriteMethod() {
    private final ScoringRewrite<SpanOrQuery> delegate = new ScoringRewrite<SpanOrQuery>() {
      @Override
      protected SpanOrQuery getTopLevelQuery() {
        return new SpanOrQuery();
      }

      @Override
      protected void addClause(SpanOrQuery topLevel, Term term, float boost) {
        final SpanTermQuery q = new SpanTermQuery(term);
        q.setBoost(boost);
        topLevel.addClause(q);
      }
    };
    
    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      return delegate.rewrite(reader, query);
    }

    // Make sure we are still a singleton even after deserializing
    protected Object readResolve() {
      return SCORING_SPAN_QUERY_REWRITE;
    }
  };
  
  public static final class TopTermsSpanBooleanQueryRewrite extends SpanRewriteMethod  {
    private final TopTermsRewrite<SpanOrQuery> delegate;
  
    public TopTermsSpanBooleanQueryRewrite(int size) {
      delegate = new TopTermsRewrite<SpanOrQuery>(size) {
        @Override
        protected int getMaxSize() {
          return Integer.MAX_VALUE;
        }
    
        @Override
        protected SpanOrQuery getTopLevelQuery() {
          return new SpanOrQuery();
        }

        @Override
        protected void addClause(SpanOrQuery topLevel, Term term, float boost) {
          final SpanTermQuery q = new SpanTermQuery(term);
          q.setBoost(boost);
          topLevel.addClause(q);
        }
      };
    }
    
    public int getSize() {
      return delegate.getSize();
    }

    @Override
    public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
      return delegate.rewrite(reader, query);
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
