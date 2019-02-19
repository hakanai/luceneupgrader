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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search.spans;


import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.ReaderUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ToStringUtils;

public class SpanTermQuery extends SpanQuery {

  protected final Term term;
  protected final TermContext termContext;

  public SpanTermQuery(Term term) {
    this.term = Objects.requireNonNull(term);
    this.termContext = null;
  }

  public SpanTermQuery(Term term, TermContext context) {
    this.term = Objects.requireNonNull(term);
    this.termContext = context;
  }

  public Term getTerm() { return term; }

  @Override
  public String getField() { return term.field(); }

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    final TermContext context;
    final IndexReaderContext topContext = searcher.getTopReaderContext();
    if (termContext == null || termContext.topReaderContext != topContext) {
      context = TermContext.build(topContext, term);
    }
    else {
      context = termContext;
    }
    return new SpanTermWeight(context, searcher, needsScores ? Collections.singletonMap(term, context) : null);
  }

  public class SpanTermWeight extends SpanWeight {

    final TermContext termContext;

    public SpanTermWeight(TermContext termContext, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
      super(SpanTermQuery.this, searcher, terms);
      this.termContext = termContext;
      assert termContext != null : "TermContext must not be null";
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      terms.add(term);
    }

    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      contexts.put(term, termContext);
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

      assert termContext.topReaderContext == ReaderUtil.getTopLevelContext(context) : "The top-reader used to create Weight (" + termContext.topReaderContext + ") is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);

      final TermState state = termContext.get(context.ord);
      if (state == null) { // term is not present in that reader
        assert context.reader().docFreq(term) == 0 : "no termstate found but term exists in reader term=" + term;
        return null;
      }

      final Terms terms = context.reader().terms(term.field());
      if (terms == null)
        return null;
      if (terms.hasPositions() == false)
        throw new IllegalStateException("field \"" + term.field() + "\" was indexed without position data; cannot run SpanTermQuery (term=" + term.text() + ")");

      final TermsEnum termsEnum = terms.iterator();
      termsEnum.seekExact(term.bytes(), state);

      final PostingsEnum postings = termsEnum.postings(null, requiredPostings.getRequiredPostings());
      float positionsCost = termPositionsCost(termsEnum) * PHRASE_TO_SPAN_TERM_POSITIONS_COST;
      return new TermSpans(getSimScorer(context), postings, term, positionsCost);
    }
  }


  private static final float PHRASE_TO_SPAN_TERM_POSITIONS_COST = 4.0f;

  private static final int TERM_POSNS_SEEK_OPS_PER_DOC = 128;

  private static final int TERM_OPS_PER_POS = 7;


  static float termPositionsCost(TermsEnum termsEnum) throws IOException {
    int docFreq = termsEnum.docFreq();
    assert docFreq > 0;
    long totalTermFreq = termsEnum.totalTermFreq(); // -1 when not available
    float expOccurrencesInMatchingDoc = (totalTermFreq < docFreq) ? 1 : (totalTermFreq / (float) docFreq);
    return TERM_POSNS_SEEK_OPS_PER_DOC + expOccurrencesInMatchingDoc * TERM_OPS_PER_POS;
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (term.field().equals(field))
      buffer.append(term.text());
    else
      buffer.append(term.toString());
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + term.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (! super.equals(obj)) {
      return false;
    }
    SpanTermQuery other = (SpanTermQuery) obj;
    return term.equals(other.term);
  }

}
