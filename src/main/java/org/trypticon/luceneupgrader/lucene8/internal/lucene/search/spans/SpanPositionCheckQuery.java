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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.spans;


import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.TermStates;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.QueryVisitor;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.ScoreMode;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.spans.FilterSpans.AcceptStatus;


public abstract class SpanPositionCheckQuery extends SpanQuery implements Cloneable {
  protected SpanQuery match;

  public SpanPositionCheckQuery(SpanQuery match) {
    this.match = Objects.requireNonNull(match);
  }

  public SpanQuery getMatch() { return match; }

  @Override
  public String getField() { return match.getField(); }

  protected abstract AcceptStatus acceptPosition(Spans spans) throws IOException;

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
    SpanWeight matchWeight = match.createWeight(searcher, scoreMode, boost);
    return new SpanPositionCheckWeight(matchWeight, searcher, scoreMode.needsScores() ? getTermStates(matchWeight) : null, boost);
  }

  public class SpanPositionCheckWeight extends SpanWeight {

    final SpanWeight matchWeight;

    public SpanPositionCheckWeight(SpanWeight matchWeight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost) throws IOException {
      super(SpanPositionCheckQuery.this, searcher, terms, boost);
      this.matchWeight = matchWeight;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      matchWeight.extractTerms(terms);
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return matchWeight.isCacheable(ctx);
    }

    @Override
    public void extractTermStates(Map<Term, TermStates> contexts) {
      matchWeight.extractTermStates(contexts);
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
      Spans matchSpans = matchWeight.getSpans(context, requiredPostings);
      return (matchSpans == null) ? null : new FilterSpans(matchSpans) {
        @Override
        protected AcceptStatus accept(Spans candidate) throws IOException {
          return acceptPosition(candidate);
        }
      };
    }

  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    SpanQuery rewritten = (SpanQuery) match.rewrite(reader);
    if (rewritten != match) {
      try {
        SpanPositionCheckQuery clone = (SpanPositionCheckQuery) this.clone();
        clone.match = rewritten;
        return clone;
      } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
      }
    }

    return super.rewrite(reader);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(getField())) {
      match.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
    }
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           match.equals(((SpanPositionCheckQuery) other).match);
  }

  @Override
  public int hashCode() {
    return classHash() ^ match.hashCode();
  }
}
