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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Weight;

public class SpanNearQuery extends SpanQuery implements Cloneable {

  public static class Builder {
    private final boolean ordered;
    private final String field;
    private final List<SpanQuery> clauses = new LinkedList<>();
    private int slop;

    public Builder(String field, boolean ordered) {
      this.field = field;
      this.ordered = ordered;
    }

    public Builder addClause(SpanQuery clause) {
      if (Objects.equals(clause.getField(), field) == false)
        throw new IllegalArgumentException("Cannot add clause " + clause + " to SpanNearQuery for field " + field);
      this.clauses.add(clause);
      return this;
    }

    public Builder addGap(int width) {
      if (!ordered)
        throw new IllegalArgumentException("Gaps can only be added to ordered near queries");
      this.clauses.add(new SpanGapQuery(field, width));
      return this;
    }

    public Builder setSlop(int slop) {
      this.slop = slop;
      return this;
    }

    public SpanNearQuery build() {
      return new SpanNearQuery(clauses.toArray(new SpanQuery[clauses.size()]), slop, ordered);
    }

  }

  public static Builder newOrderedNearQuery(String field) {
    return new Builder(field, true);
  }

  public static Builder newUnorderedNearQuery(String field) {
    return new Builder(field, false);
  }

  protected List<SpanQuery> clauses;
  protected int slop;
  protected boolean inOrder;

  protected String field;

  public SpanNearQuery(SpanQuery[] clausesIn, int slop, boolean inOrder) {
    this.clauses = new ArrayList<>(clausesIn.length);
    for (SpanQuery clause : clausesIn) {
      if (this.field == null) {                               // check field
        this.field = clause.getField();
      } else if (clause.getField() != null && !clause.getField().equals(field)) {
        throw new IllegalArgumentException("Clauses must have same field.");
      }
      this.clauses.add(clause);
    }
    this.slop = slop;
    this.inOrder = inOrder;
  }

  public SpanQuery[] getClauses() {
    return clauses.toArray(new SpanQuery[clauses.size()]);
  }

  public int getSlop() { return slop; }

  public boolean isInOrder() { return inOrder; }

  @Override
  public String getField() { return field; }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("spanNear([");
    Iterator<SpanQuery> i = clauses.iterator();
    while (i.hasNext()) {
      SpanQuery clause = i.next();
      buffer.append(clause.toString(field));
      if (i.hasNext()) {
        buffer.append(", ");
      }
    }
    buffer.append("], ");
    buffer.append(slop);
    buffer.append(", ");
    buffer.append(inOrder);
    buffer.append(")");
    return buffer.toString();
  }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    List<SpanWeight> subWeights = new ArrayList<>();
    for (SpanQuery q : clauses) {
      subWeights.add(q.createWeight(searcher, false, boost));
    }
    return new SpanNearWeight(subWeights, searcher, needsScores ? getTermContexts(subWeights) : null, boost);
  }

  public class SpanNearWeight extends SpanWeight {

    final List<SpanWeight> subWeights;

    public SpanNearWeight(List<SpanWeight> subWeights, IndexSearcher searcher, Map<Term, TermContext> terms, float boost) throws IOException {
      super(SpanNearQuery.this, searcher, terms, boost);
      this.subWeights = subWeights;
    }

    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      for (SpanWeight w : subWeights) {
        w.extractTermContexts(contexts);
      }
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

      Terms terms = context.reader().terms(field);
      if (terms == null) {
        return null; // field does not exist
      }

      ArrayList<Spans> subSpans = new ArrayList<>(clauses.size());
      for (SpanWeight w : subWeights) {
        Spans subSpan = w.getSpans(context, requiredPostings);
        if (subSpan != null) {
          subSpans.add(subSpan);
        } else {
          return null; // all required
        }
      }

      // all NearSpans require at least two subSpans
      return (!inOrder) ? new NearSpansUnordered(slop, subSpans)
          : new NearSpansOrdered(slop, subSpans);
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      for (SpanWeight w : subWeights) {
        w.extractTerms(terms);
      }
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      for (Weight w : subWeights) {
        if (w.isCacheable(ctx) == false)
          return false;
      }
      return true;
    }

  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    boolean actuallyRewritten = false;
    List<SpanQuery> rewrittenClauses = new ArrayList<>();
    for (int i = 0 ; i < clauses.size(); i++) {
      SpanQuery c = clauses.get(i);
      SpanQuery query = (SpanQuery) c.rewrite(reader);
      actuallyRewritten |= query != c;
      rewrittenClauses.add(query);
    }
    if (actuallyRewritten) {
      try {
        SpanNearQuery rewritten = (SpanNearQuery) clone();
        rewritten.clauses = rewrittenClauses;
        return rewritten;
      } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
      }
    }
    return super.rewrite(reader);
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }
  
  private boolean equalsTo(SpanNearQuery other) {
    return inOrder == other.inOrder && 
           slop == other.slop &&
           clauses.equals(other.clauses);
  }

  @Override
  public int hashCode() {
    int result = classHash();
    result ^= clauses.hashCode();
    result += slop;
    int fac = 1 + (inOrder ? 8 : 4);
    return fac * result;
  }

  private static class SpanGapQuery extends SpanQuery {

    private final String field;
    private final int width;

    public SpanGapQuery(String field, int width) {
      this.field = field;
      this.width = width;
    }

    @Override
    public String getField() {
      return field;
    }

    @Override
    public String toString(String field) {
      return "SpanGap(" + field + ":" + width + ")";
    }

    @Override
    public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
      return new SpanGapWeight(searcher, boost);
    }

    private class SpanGapWeight extends SpanWeight {

      SpanGapWeight(IndexSearcher searcher, float boost) throws IOException {
        super(SpanGapQuery.this, searcher, null, boost);
      }

      @Override
      public void extractTermContexts(Map<Term, TermContext> contexts) {

      }

      @Override
      public Spans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
        return new GapSpans(width);
      }

      @Override
      public void extractTerms(Set<Term> terms) {

      }

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        return true;
      }

    }

    @Override
    public boolean equals(Object other) {
      return sameClassAs(other) &&
             equalsTo(getClass().cast(other));
    }
    
    private boolean equalsTo(SpanGapQuery other) {
      return width == other.width &&
             field.equals(other.field);
    }

    @Override
    public int hashCode() {
      int result = classHash();
      result -= 7 * width;
      return result * 15 - field.hashCode();
    }

  }

  static class GapSpans extends Spans {

    int doc = -1;
    int pos = -1;
    final int width;

    GapSpans(int width) {
      this.width = width;
    }

    @Override
    public int nextStartPosition() throws IOException {
      return ++pos;
    }

    public int skipToPosition(int position) throws IOException {
      return pos = position;
    }

    @Override
    public int startPosition() {
      return pos;
    }

    @Override
    public int endPosition() {
      return pos + width;
    }

    @Override
    public int width() {
      return width;
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {

    }

    @Override
    public int docID() {
      return doc;
    }

    @Override
    public int nextDoc() throws IOException {
      pos = -1;
      return ++doc;
    }

    @Override
    public int advance(int target) throws IOException {
      pos = -1;
      return doc = target;
    }

    @Override
    public long cost() {
      return 0;
    }

    @Override
    public float positionsCost() {
      return 0;
    }
  }

}
