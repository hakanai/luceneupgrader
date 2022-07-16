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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.TermStates;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.LeafSimScorer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Matches;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.MatchesIterator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.MatchesUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ArrayUtil;

public abstract class SpanWeight extends Weight {

  public enum Postings {
    POSITIONS {
      @Override
      public int getRequiredPostings() {
        return PostingsEnum.POSITIONS;
      }
    },
    PAYLOADS {
      @Override
      public int getRequiredPostings() {
        return PostingsEnum.PAYLOADS;
      }
    },
    OFFSETS {
      @Override
      public int getRequiredPostings() {
        return PostingsEnum.PAYLOADS | PostingsEnum.OFFSETS;
      }
    };

    public abstract int getRequiredPostings();

    public Postings atLeast(Postings postings) {
      if (postings.compareTo(this) > 0)
        return postings;
      return this;
    }
  }

  protected final Similarity similarity;
  protected final Similarity.SimScorer simScorer;
  protected final String field;

  public SpanWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermStates> termStates, float boost) throws IOException {
    super(query);
    this.field = query.getField();
    this.similarity = searcher.getSimilarity();
    this.simScorer = buildSimWeight(query, searcher, termStates, boost);
  }

  private Similarity.SimScorer buildSimWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermStates> termStates, float boost) throws IOException {
    if (termStates == null || termStates.size() == 0 || query.getField() == null)
      return null;
    TermStatistics[] termStats = new TermStatistics[termStates.size()];
    int termUpTo = 0;
    for (Map.Entry<Term, TermStates> entry : termStates.entrySet()) {
      TermStates ts = entry.getValue();
      if (ts.docFreq() > 0) {
        termStats[termUpTo++] = searcher.termStatistics(entry.getKey(), ts.docFreq(), ts.totalTermFreq());
      }
    }
    CollectionStatistics collectionStats = searcher.collectionStatistics(query.getField());
    if (termUpTo > 0) {
      return similarity.scorer(boost, collectionStats, ArrayUtil.copyOfSubArray(termStats, 0, termUpTo));
    } else {
      return null; // no terms at all exist, we won't use similarity
    }
  }

  public abstract void extractTermStates(Map<Term, TermStates> contexts);

  public abstract Spans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException;

  @Override
  public SpanScorer scorer(LeafReaderContext context) throws IOException {
    final Spans spans = getSpans(context, Postings.POSITIONS);
    if (spans == null) {
      return null;
    }
    final LeafSimScorer docScorer = getSimScorer(context);
    return new SpanScorer(this, spans, docScorer);
  }

  public LeafSimScorer getSimScorer(LeafReaderContext context) throws IOException {
    return simScorer == null ? null : new LeafSimScorer(simScorer, context.reader(), field, true);
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    SpanScorer scorer = scorer(context);
    if (scorer != null) {
      int newDoc = scorer.iterator().advance(doc);
      if (newDoc == doc) {
        if (simScorer != null) {
          float freq = scorer.sloppyFreq();
          LeafSimScorer docScorer = new LeafSimScorer(simScorer, context.reader(), field, true);
          Explanation freqExplanation = Explanation.match(freq, "phraseFreq=" + freq);
          Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
          return Explanation.match(scoreExplanation.getValue(),
              "weight("+getQuery()+" in "+doc+") [" + similarity.getClass().getSimpleName() + "], result of:",
              scoreExplanation);
        } else {
          // simScorer won't be set when scoring isn't needed
          return Explanation.match(0f, String.format(Locale.ROOT,
              "match %s in %s without score", getQuery(), doc));
        }
      }
    }

    return Explanation.noMatch("no matching term");
  }

  private static class TermMatch {
    Term term;
    int position;
    int startOffset;
    int endOffset;
  }

  @Override
  public Matches matches(LeafReaderContext context, int doc) throws IOException {
    return MatchesUtils.forField(field, () -> {
      Spans spans = getSpans(context, Postings.OFFSETS);
      if (spans == null || spans.advance(doc) != doc) {
        return null;
      }
      return new MatchesIterator() {

        int innerTermCount = 0;
        TermMatch[] innerTerms = new TermMatch[0];

        SpanCollector termCollector = new SpanCollector() {
          @Override
          public void collectLeaf(PostingsEnum postings, int position, Term term) throws IOException {
            innerTermCount++;
            if (innerTermCount > innerTerms.length) {
              TermMatch[] temp = new TermMatch[innerTermCount];
              System.arraycopy(innerTerms, 0, temp, 0, innerTermCount - 1);
              innerTerms = temp;
              innerTerms[innerTermCount - 1] = new TermMatch();
            }
            innerTerms[innerTermCount - 1].term = term;
            innerTerms[innerTermCount - 1].position = position;
            innerTerms[innerTermCount - 1].startOffset = postings.startOffset();
            innerTerms[innerTermCount - 1].endOffset = postings.endOffset();
          }

          @Override
          public void reset() {
            innerTermCount = 0;
          }
        };

        @Override
        public boolean next() throws IOException {
          innerTermCount = 0;
          return spans.nextStartPosition() != Spans.NO_MORE_POSITIONS;
        }

        @Override
        public int startPosition() {
          return spans.startPosition();
        }

        @Override
        public int endPosition() {
          return spans.endPosition() - 1;
        }

        @Override
        public int startOffset() throws IOException {
          if (innerTermCount == 0) {
            collectInnerTerms();
          }
          return innerTerms[0].startOffset;
        }

        @Override
        public int endOffset() throws IOException {
          if (innerTermCount == 0) {
            collectInnerTerms();
          }
          return innerTerms[innerTermCount - 1].endOffset;
        }

        @Override
        public MatchesIterator getSubMatches() throws IOException {
          if (innerTermCount == 0) {
            collectInnerTerms();
          }
          return new MatchesIterator() {

            int upto = -1;

            @Override
            public boolean next() throws IOException {
              upto++;
              return upto < innerTermCount;
            }

            @Override
            public int startPosition() {
              return innerTerms[upto].position;
            }

            @Override
            public int endPosition() {
              return innerTerms[upto].position;
            }

            @Override
            public int startOffset() throws IOException {
              return innerTerms[upto].startOffset;
            }

            @Override
            public int endOffset() throws IOException {
              return innerTerms[upto].endOffset;
            }

            @Override
            public MatchesIterator getSubMatches() throws IOException {
              return null;
            }

            @Override
            public Query getQuery() {
              return new TermQuery(innerTerms[upto].term);
            }
          };
        }

        @Override
        public Query getQuery() {
          return SpanWeight.this.getQuery();
        }

        void collectInnerTerms() throws IOException {
          termCollector.reset();
          spans.collect(termCollector);
          Arrays.sort(innerTerms, 0, innerTermCount, Comparator.comparing(a -> a.position));
        }
      };
    });
  }
}
