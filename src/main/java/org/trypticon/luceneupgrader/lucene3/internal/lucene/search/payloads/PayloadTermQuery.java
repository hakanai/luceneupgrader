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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.payloads;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.TermPositions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Searcher;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Similarity;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.ComplexExplanation;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans.TermSpans;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans.SpanTermQuery;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans.SpanWeight;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.spans.SpanScorer;

import java.io.IOException;


public class PayloadTermQuery extends SpanTermQuery {
  protected PayloadFunction function;
  private boolean includeSpanScore;

  public PayloadTermQuery(Term term, PayloadFunction function) {
    this(term, function, true);
  }

  public PayloadTermQuery(Term term, PayloadFunction function,
      boolean includeSpanScore) {
    super(term);
    this.function = function;
    this.includeSpanScore = includeSpanScore;
  }

  @Override
  public Weight createWeight(Searcher searcher) throws IOException {
    return new PayloadTermWeight(this, searcher);
  }

  protected class PayloadTermWeight extends SpanWeight {

    public PayloadTermWeight(PayloadTermQuery query, Searcher searcher)
        throws IOException {
      super(query, searcher);
    }

    @Override
    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder,
        boolean topScorer) throws IOException {
      return new PayloadTermSpanScorer((TermSpans) query.getSpans(reader),
          this, similarity, reader.norms(query.getField()));
    }
    
    @Override
    public Explanation explain(IndexReader reader, int doc) throws IOException {
      if (includeSpanScore) {
        return super.explain(reader, doc);
      } else {
        // if we don't include the span score, we need to return our scorer's explanation only
        PayloadTermSpanScorer scorer = (PayloadTermSpanScorer) scorer(reader, true, false);
        return scorer.explain(doc);
      }
    }

    protected class PayloadTermSpanScorer extends SpanScorer {
      // TODO: is this the best way to allocate this?
      protected byte[] payload = new byte[256];
      protected TermPositions positions;
      protected float payloadScore;
      protected int payloadsSeen;

      public PayloadTermSpanScorer(TermSpans spans, Weight weight,
          Similarity similarity, byte[] norms) throws IOException {
        super(spans, weight, similarity, norms);
        positions = spans.getPositions();
      }

      @Override
      protected boolean setFreqCurrentDoc() throws IOException {
        if (!more) {
          return false;
        }
        doc = spans.doc();
        freq = 0.0f;
        payloadScore = 0;
        payloadsSeen = 0;
        Similarity similarity1 = getSimilarity();
        while (more && doc == spans.doc()) {
          int matchLength = spans.end() - spans.start();

          freq += similarity1.sloppyFreq(matchLength);
          processPayload(similarity1);

          more = spans.next();// this moves positions to the next match in this
                              // document
        }
        return more || (freq != 0);
      }

      protected void processPayload(Similarity similarity) throws IOException {
        if (positions.isPayloadAvailable()) {
          payload = positions.getPayload(payload, 0);
          payloadScore = function.currentScore(doc, term.field(),
              spans.start(), spans.end(), payloadsSeen, payloadScore,
              similarity.scorePayload(doc, term.field(), spans.start(), spans
                  .end(), payload, 0, positions.getPayloadLength()));
          payloadsSeen++;

        } else {
          // zero out the payload?
        }
      }

      @Override
      public float score() throws IOException {

        return includeSpanScore ? getSpanScore() * getPayloadScore()
            : getPayloadScore();
      }

      protected float getSpanScore() throws IOException {
        return super.score();
      }

      protected float getPayloadScore() {
        return function.docScore(doc, term.field(), payloadsSeen, payloadScore);
      }

      @Override
      protected Explanation explain(final int doc) throws IOException {
        Explanation nonPayloadExpl = super.explain(doc);
        
        // QUESTION: Is there a way to avoid this skipTo call? We need to know
        // whether to load the payload or not
        Explanation payloadBoost = new Explanation();

        float payloadScore = getPayloadScore();
        payloadBoost.setValue(payloadScore);
        // GSI: I suppose we could toString the payload, but I don't think that
        // would be a good idea
        payloadBoost.setDescription("scorePayload(...)");
        
        ComplexExplanation result = new ComplexExplanation();
        if (includeSpanScore) {
          result.addDetail(nonPayloadExpl);
          result.addDetail(payloadBoost);
          result.setValue(nonPayloadExpl.getValue() * payloadScore);
          result.setDescription("btq, product of:");
        } else {
          result.addDetail(payloadBoost);
          result.setValue(payloadScore);
          result.setDescription("btq(includeSpanScore=false), result of:");
        }
        result.setMatch(nonPayloadExpl.getValue() == 0 ? Boolean.FALSE
            : Boolean.TRUE); // LUCENE-1303
        return result;
      }

    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((function == null) ? 0 : function.hashCode());
    result = prime * result + (includeSpanScore ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    PayloadTermQuery other = (PayloadTermQuery) obj;
    if (function == null) {
      if (other.function != null)
        return false;
    } else if (!function.equals(other.function))
      return false;
    if (includeSpanScore != other.includeSpanScore)
      return false;
    return true;
  }

}
