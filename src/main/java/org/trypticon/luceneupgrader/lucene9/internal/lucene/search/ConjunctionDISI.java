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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BitSet;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BitSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.CollectionUtil;

/**
 * A conjunction of DocIdSetIterators. Requires that all of its sub-iterators must be on the same
 * document all the time. This iterates over the doc ids that are present in each given
 * DocIdSetIterator. <br>
 *
 * @lucene.internal
 */
final class ConjunctionDISI extends DocIdSetIterator {

  /**
   * Adds the scorer, possibly splitting up into two phases or collapsing if it is another
   * conjunction
   */
  static void addScorer(
      Scorer scorer,
      List<DocIdSetIterator> allIterators,
      List<TwoPhaseIterator> twoPhaseIterators) {
    TwoPhaseIterator twoPhaseIter = scorer.twoPhaseIterator();
    if (twoPhaseIter != null) {
      addTwoPhaseIterator(twoPhaseIter, allIterators, twoPhaseIterators);
    } else { // no approximation support, use the iterator as-is
      addIterator(scorer.iterator(), allIterators, twoPhaseIterators);
    }
  }

  static void addIterator(
      DocIdSetIterator disi,
      List<DocIdSetIterator> allIterators,
      List<TwoPhaseIterator> twoPhaseIterators) {
    TwoPhaseIterator twoPhase = TwoPhaseIterator.unwrap(disi);
    if (twoPhase != null) {
      addTwoPhaseIterator(twoPhase, allIterators, twoPhaseIterators);
    } else if (disi.getClass()
        == ConjunctionDISI.class) { // Check for exactly this class for collapsing
      ConjunctionDISI conjunction = (ConjunctionDISI) disi;
      // subconjuctions have already split themselves into two phase iterators and others, so we can
      // take those
      // iterators as they are and move them up to this conjunction
      allIterators.add(conjunction.lead1);
      allIterators.add(conjunction.lead2);
      Collections.addAll(allIterators, conjunction.others);
    } else if (disi.getClass() == BitSetConjunctionDISI.class) {
      BitSetConjunctionDISI conjunction = (BitSetConjunctionDISI) disi;
      allIterators.add(conjunction.lead);
      Collections.addAll(allIterators, conjunction.bitSetIterators);
    } else {
      allIterators.add(disi);
    }
  }

  static void addTwoPhaseIterator(
      TwoPhaseIterator twoPhaseIter,
      List<DocIdSetIterator> allIterators,
      List<TwoPhaseIterator> twoPhaseIterators) {
    addIterator(twoPhaseIter.approximation(), allIterators, twoPhaseIterators);
    if (twoPhaseIter.getClass()
        == ConjunctionTwoPhaseIterator.class) { // Check for exactly this class for collapsing
      Collections.addAll(
          twoPhaseIterators, ((ConjunctionTwoPhaseIterator) twoPhaseIter).twoPhaseIterators);
    } else {
      twoPhaseIterators.add(twoPhaseIter);
    }
  }

  static DocIdSetIterator createConjunction(
      List<DocIdSetIterator> allIterators, List<TwoPhaseIterator> twoPhaseIterators) {

    // check that all sub-iterators are on the same doc ID
    int curDoc =
        allIterators.size() > 0
            ? allIterators.get(0).docID()
            : twoPhaseIterators.get(0).approximation.docID();
    long minCost = Long.MAX_VALUE;
    for (DocIdSetIterator allIterator : allIterators) {
      if (allIterator.docID() != curDoc) {
        throwSubIteratorsNotOnSameDocument();
      }
      minCost = Math.min(allIterator.cost(), minCost);
    }
    for (TwoPhaseIterator it : twoPhaseIterators) {
      if (it.approximation().docID() != curDoc) {
        throwSubIteratorsNotOnSameDocument();
      }
    }
    List<BitSetIterator> bitSetIterators = new ArrayList<>();
    List<DocIdSetIterator> iterators = new ArrayList<>();
    for (DocIdSetIterator iterator : allIterators) {
      if (iterator instanceof BitSetIterator && iterator.cost() > minCost) {
        // we put all bitset iterators into bitSetIterators
        // except if they have the minimum cost, since we need
        // them to lead the iteration in that case
        bitSetIterators.add((BitSetIterator) iterator);
      } else {
        iterators.add(iterator);
      }
    }

    DocIdSetIterator disi;
    if (iterators.size() == 1) {
      disi = iterators.get(0);
    } else {
      disi = new ConjunctionDISI(iterators);
    }

    if (bitSetIterators.size() > 0) {
      disi = new BitSetConjunctionDISI(disi, bitSetIterators);
    }

    if (twoPhaseIterators.isEmpty() == false) {
      disi =
          TwoPhaseIterator.asDocIdSetIterator(
              new ConjunctionTwoPhaseIterator(disi, twoPhaseIterators));
    }

    return disi;
  }

  private static void throwSubIteratorsNotOnSameDocument() {
    throw new IllegalArgumentException(
        "Sub-iterators of ConjunctionDISI are not on the same document!");
  }

  final DocIdSetIterator lead1, lead2;
  final DocIdSetIterator[] others;

  private ConjunctionDISI(List<? extends DocIdSetIterator> iterators) {
    assert iterators.size() >= 2;

    // Sort the array the first time to allow the least frequent DocsEnum to
    // lead the matching.
    CollectionUtil.timSort(iterators, (o1, o2) -> Long.compare(o1.cost(), o2.cost()));
    lead1 = iterators.get(0);
    lead2 = iterators.get(1);
    others = iterators.subList(2, iterators.size()).toArray(new DocIdSetIterator[0]);
  }

  private int doNext(int doc) throws IOException {
    advanceHead:
    for (; ; ) {
      assert doc == lead1.docID();

      // find agreement between the two iterators with the lower costs
      // we special case them because they do not need the
      // 'other.docID() < doc' check that the 'others' iterators need
      final int next2 = lead2.advance(doc);
      if (next2 != doc) {
        doc = lead1.advance(next2);
        if (next2 != doc) {
          continue;
        }
      }

      // then find agreement with other iterators
      for (DocIdSetIterator other : others) {
        // other.doc may already be equal to doc if we "continued advanceHead"
        // on the previous iteration and the advance on the lead scorer exactly matched.
        if (other.docID() < doc) {
          final int next = other.advance(doc);

          if (next > doc) {
            // iterator beyond the current doc - advance lead and continue to the new highest doc.
            doc = lead1.advance(next);
            continue advanceHead;
          }
        }
      }

      // success - all iterators are on the same doc
      return doc;
    }
  }

  @Override
  public int advance(int target) throws IOException {
    assert assertItersOnSameDoc()
        : "Sub-iterators of ConjunctionDISI are not one the same document!";
    return doNext(lead1.advance(target));
  }

  @Override
  public int docID() {
    return lead1.docID();
  }

  @Override
  public int nextDoc() throws IOException {
    assert assertItersOnSameDoc()
        : "Sub-iterators of ConjunctionDISI are not on the same document!";
    return doNext(lead1.nextDoc());
  }

  @Override
  public long cost() {
    return lead1.cost(); // overestimate
  }

  // Returns {@code true} if all sub-iterators are on the same doc ID, {@code false} otherwise
  private boolean assertItersOnSameDoc() {
    int curDoc = lead1.docID();
    boolean iteratorsOnTheSameDoc = (lead2.docID() == curDoc);
    for (int i = 0; (i < others.length && iteratorsOnTheSameDoc); i++) {
      iteratorsOnTheSameDoc = iteratorsOnTheSameDoc && (others[i].docID() == curDoc);
    }
    return iteratorsOnTheSameDoc;
  }

  /** Conjunction between a {@link DocIdSetIterator} and one or more {@link BitSetIterator}s. */
  private static class BitSetConjunctionDISI extends DocIdSetIterator {

    private final DocIdSetIterator lead;
    private final BitSetIterator[] bitSetIterators;
    private final BitSet[] bitSets;
    private final int minLength;

    BitSetConjunctionDISI(DocIdSetIterator lead, Collection<BitSetIterator> bitSetIterators) {
      this.lead = lead;
      assert bitSetIterators.size() > 0;

      this.bitSetIterators = bitSetIterators.toArray(new BitSetIterator[0]);
      // Put the least costly iterators first so that we exit as soon as possible
      ArrayUtil.timSort(this.bitSetIterators, (a, b) -> Long.compare(a.cost(), b.cost()));
      this.bitSets = new BitSet[this.bitSetIterators.length];
      int minLen = Integer.MAX_VALUE;
      for (int i = 0; i < this.bitSetIterators.length; ++i) {
        BitSet bitSet = this.bitSetIterators[i].getBitSet();
        this.bitSets[i] = bitSet;
        minLen = Math.min(minLen, bitSet.length());
      }
      this.minLength = minLen;
    }

    @Override
    public int docID() {
      return lead.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      assert assertItersOnSameDoc()
          : "Sub-iterators of ConjunctionDISI are not on the same document!";
      return doNext(lead.nextDoc());
    }

    @Override
    public int advance(int target) throws IOException {
      assert assertItersOnSameDoc()
          : "Sub-iterators of ConjunctionDISI are not on the same document!";
      return doNext(lead.advance(target));
    }

    private int doNext(int doc) throws IOException {
      advanceLead:
      for (; ; doc = lead.nextDoc()) {
        if (doc >= minLength) {
          if (doc != NO_MORE_DOCS) {
            lead.advance(NO_MORE_DOCS);
          }
          return NO_MORE_DOCS;
        }
        for (BitSet bitSet : bitSets) {
          if (bitSet.get(doc) == false) {
            continue advanceLead;
          }
        }
        for (BitSetIterator iterator : bitSetIterators) {
          iterator.setDocId(doc);
        }
        return doc;
      }
    }

    @Override
    public long cost() {
      return lead.cost();
    }

    // Returns {@code true} if all sub-iterators are on the same doc ID, {@code false} otherwise
    private boolean assertItersOnSameDoc() {
      int curDoc = lead.docID();
      boolean iteratorsOnTheSameDoc = true;
      for (int i = 0; (i < bitSetIterators.length && iteratorsOnTheSameDoc); i++) {
        iteratorsOnTheSameDoc = iteratorsOnTheSameDoc && (bitSetIterators[i].docID() == curDoc);
      }
      return iteratorsOnTheSameDoc;
    }
  }

  /** {@link TwoPhaseIterator} implementing a conjunction. */
  private static final class ConjunctionTwoPhaseIterator extends TwoPhaseIterator {

    private final TwoPhaseIterator[] twoPhaseIterators;
    private final float matchCost;

    private ConjunctionTwoPhaseIterator(
        DocIdSetIterator approximation, List<? extends TwoPhaseIterator> twoPhaseIterators) {
      super(approximation);
      assert twoPhaseIterators.size() > 0;

      CollectionUtil.timSort(
          twoPhaseIterators, (o1, o2) -> Float.compare(o1.matchCost(), o2.matchCost()));

      this.twoPhaseIterators =
          twoPhaseIterators.toArray(new TwoPhaseIterator[twoPhaseIterators.size()]);

      // Compute the matchCost as the total matchCost of the sub iterators.
      // TODO: This could be too high because the matching is done cheapest first: give the lower
      // matchCosts a higher weight.
      float totalMatchCost = 0;
      for (TwoPhaseIterator tpi : twoPhaseIterators) {
        totalMatchCost += tpi.matchCost();
      }
      matchCost = totalMatchCost;
    }

    @Override
    public boolean matches() throws IOException {
      for (TwoPhaseIterator twoPhaseIterator : twoPhaseIterators) { // match cheapest first
        if (twoPhaseIterator.matches() == false) {
          return false;
        }
      }
      return true;
    }

    @Override
    public float matchCost() {
      return matchCost;
    }
  }
}
