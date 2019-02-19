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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReaderContext; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;

public abstract class Weight {

  public abstract Explanation explain(AtomicReaderContext context, int doc) throws IOException;

  public abstract Query getQuery();
  
  public abstract float getValueForNormalization() throws IOException;

  public abstract void normalize(float norm, float topLevelBoost);

  public abstract Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException;

  public BulkScorer bulkScorer(AtomicReaderContext context, boolean scoreDocsInOrder, Bits acceptDocs) throws IOException {

    Scorer scorer = scorer(context, acceptDocs);
    if (scorer == null) {
      // No docs match
      return null;
    }

    // This impl always scores docs in order, so we can
    // ignore scoreDocsInOrder:
    return new DefaultBulkScorer(scorer);
  }

  static class DefaultBulkScorer extends BulkScorer {
    private final Scorer scorer;

    public DefaultBulkScorer(Scorer scorer) {
      if (scorer == null) {
        throw new NullPointerException();
      }
      this.scorer = scorer;
    }

    @Override
    public boolean score(Collector collector, int max) throws IOException {
      // TODO: this may be sort of weird, when we are
      // embedded in a BooleanScorer, because we are
      // called for every chunk of 2048 documents.  But,
      // then, scorer is a FakeScorer in that case, so any
      // Collector doing something "interesting" in
      // setScorer will be forced to use BS2 anyways:
      collector.setScorer(scorer);
      if (max == DocIdSetIterator.NO_MORE_DOCS) {
        scoreAll(collector, scorer);
        return false;
      } else {
        int doc = scorer.docID();
        if (doc < 0) {
          doc = scorer.nextDoc();
        }
        return scoreRange(collector, scorer, doc, max);
      }
    }


    static boolean scoreRange(Collector collector, Scorer scorer, int currentDoc, int end) throws IOException {
      while (currentDoc < end) {
        collector.collect(currentDoc);
        currentDoc = scorer.nextDoc();
      }
      return currentDoc != DocIdSetIterator.NO_MORE_DOCS;
    }
    

    static void scoreAll(Collector collector, Scorer scorer) throws IOException {
      int doc;
      while ((doc = scorer.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        collector.collect(doc);
      }
    }
  }

  public boolean scoresDocsOutOfOrder() {
    return false;
  }
}
