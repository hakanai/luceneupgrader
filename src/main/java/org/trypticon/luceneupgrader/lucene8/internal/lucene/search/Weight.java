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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import java.io.IOException;
import java.util.Set;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Bits;

public abstract class Weight implements SegmentCacheable {

  protected final Query parentQuery;

  protected Weight(Query query) {
    this.parentQuery = query;
  }

  @Deprecated
  public abstract void extractTerms(Set<Term> terms);

  public Matches matches(LeafReaderContext context, int doc) throws IOException {
    ScorerSupplier scorerSupplier = scorerSupplier(context);
    if (scorerSupplier == null) {
      return null;
    }
    Scorer scorer = scorerSupplier.get(1);
    final TwoPhaseIterator twoPhase = scorer.twoPhaseIterator();
    if (twoPhase == null) {
      if (scorer.iterator().advance(doc) != doc) {
        return null;
      }
    }
    else {
      if (twoPhase.approximation().advance(doc) != doc || twoPhase.matches() == false) {
        return null;
      }
    }
    return MatchesUtils.MATCH_WITH_NO_TERMS;
  }

  public abstract Explanation explain(LeafReaderContext context, int doc) throws IOException;

  public final Query getQuery() {
    return parentQuery;
  }

  public abstract Scorer scorer(LeafReaderContext context) throws IOException;

  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
    final Scorer scorer = scorer(context);
    if (scorer == null) {
      return null;
    }
    return new ScorerSupplier() {
      @Override
      public Scorer get(long leadCost) {
        return scorer;
      }

      @Override
      public long cost() {
        return scorer.iterator().cost();
      }
    };
  }

  public BulkScorer bulkScorer(LeafReaderContext context) throws IOException {

    Scorer scorer = scorer(context);
    if (scorer == null) {
      // No docs match
      return null;
    }

    // This impl always scores docs in order, so we can
    // ignore scoreDocsInOrder:
    return new DefaultBulkScorer(scorer);
  }

  protected static class DefaultBulkScorer extends BulkScorer {
    private final Scorer scorer;
    private final DocIdSetIterator iterator;
    private final TwoPhaseIterator twoPhase;

    public DefaultBulkScorer(Scorer scorer) {
      if (scorer == null) {
        throw new NullPointerException();
      }
      this.scorer = scorer;
      this.iterator = scorer.iterator();
      this.twoPhase = scorer.twoPhaseIterator();
    }

    @Override
    public long cost() {
      return iterator.cost();
    }

    @Override
    public int score(LeafCollector collector, Bits acceptDocs, int min, int max) throws IOException {
      collector.setScorer(scorer);
      DocIdSetIterator scorerIterator = twoPhase == null ? iterator : twoPhase.approximation();
      DocIdSetIterator competitiveIterator = collector.competitiveIterator();
      DocIdSetIterator filteredIterator;
      if (competitiveIterator == null) {
        filteredIterator = scorerIterator;
      } else {
        // Wrap CompetitiveIterator and ScorerIterator start with (i.e., calling nextDoc()) the last
        // visited docID because ConjunctionDISI might have advanced to it in the previous
        // scoreRange, but we didn't process due to the range limit of scoreRange.
        if (scorerIterator.docID() != -1) {
          scorerIterator = new StartDISIWrapper(scorerIterator);
        }
        if (competitiveIterator.docID() != -1) {
          competitiveIterator = new StartDISIWrapper(competitiveIterator);
        }
        // filter scorerIterator to keep only competitive docs as defined by collector
        filteredIterator =
            ConjunctionDISI.intersectIterators(Arrays.asList(scorerIterator, competitiveIterator));
      }
      if (filteredIterator.docID() == -1 && min == 0 && max == DocIdSetIterator.NO_MORE_DOCS) {
        scoreAll(collector, filteredIterator, twoPhase, acceptDocs);
        return DocIdSetIterator.NO_MORE_DOCS;
      } else {
        int doc = filteredIterator.docID();
        if (doc < min) {
          doc = filteredIterator.advance(min);
        }
        return scoreRange(collector, filteredIterator, twoPhase, acceptDocs, doc, max);
      }
    }

    static int scoreRange(LeafCollector collector, DocIdSetIterator iterator, TwoPhaseIterator twoPhase,
        Bits acceptDocs, int currentDoc, int end) throws IOException {
      if (twoPhase == null) {
        while (currentDoc < end) {
          if (acceptDocs == null || acceptDocs.get(currentDoc)) {
            collector.collect(currentDoc);
          }
          currentDoc = iterator.nextDoc();
        }
        return currentDoc;
      } else {
        while (currentDoc < end) {
          if ((acceptDocs == null || acceptDocs.get(currentDoc)) && twoPhase.matches()) {
            collector.collect(currentDoc);
          }
          currentDoc = iterator.nextDoc();
        }
        return currentDoc;
      }
    }

    static void scoreAll(LeafCollector collector, DocIdSetIterator iterator, TwoPhaseIterator twoPhase, Bits acceptDocs) throws IOException {
      if (twoPhase == null) {
        for (int doc = iterator.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = iterator.nextDoc()) {
          if (acceptDocs == null || acceptDocs.get(doc)) {
            collector.collect(doc);
          }
        }
      } else {
        // The scorer has an approximation, so run the approximation first, then check acceptDocs, then confirm
        for (int doc = iterator.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = iterator.nextDoc()) {
          if ((acceptDocs == null || acceptDocs.get(doc)) && twoPhase.matches()) {
            collector.collect(doc);
          }
        }
      }
    }
  }

  private static class StartDISIWrapper extends DocIdSetIterator {
    private final DocIdSetIterator in;
    private final int startDocID;
    private int docID = -1;

    StartDISIWrapper(DocIdSetIterator in) {
      this.in = in;
      this.startDocID = in.docID();
    }

    @Override
    public int docID() {
      return docID;
    }

    @Override
    public int nextDoc() throws IOException {
      return advance(docID + 1);
    }

    @Override
    public int advance(int target) throws IOException {
      if (target <= startDocID) {
        return docID = startDocID;
      }
      return docID = in.advance(target);
    }

    @Override
    public long cost() {
      return in.cost();
    }

  }

}
