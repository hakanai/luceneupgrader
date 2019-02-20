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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits.MatchNoBits;

public abstract class RandomAccessWeight extends ConstantScoreWeight {

  protected RandomAccessWeight(Query query) {
    super(query);
  }

  protected abstract Bits getMatchingDocs(LeafReaderContext context) throws IOException;

  @Override
  public final Scorer scorer(LeafReaderContext context) throws IOException {
    final Bits matchingDocs = getMatchingDocs(context);
    if (matchingDocs == null || matchingDocs instanceof MatchNoBits) {
      return null;
    }
    final DocIdSetIterator approximation = DocIdSetIterator.all(context.reader().maxDoc());
    final TwoPhaseIterator twoPhase = new TwoPhaseIterator(approximation) {

      @Override
      public boolean matches() throws IOException {
        final int doc = approximation.docID();

        return matchingDocs.get(doc);
      }

      @Override
      public float matchCost() {
        return 10; // TODO: use some cost of matchingDocs
      }
    };

    return new ConstantScoreScorer(this, score(), twoPhase);
  }

}

