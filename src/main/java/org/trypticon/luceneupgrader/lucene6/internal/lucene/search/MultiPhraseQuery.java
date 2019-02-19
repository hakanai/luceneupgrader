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
import java.util.*;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.similarities.Similarity.SimScorer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.PriorityQueue;

public class MultiPhraseQuery extends Query {
  public static class Builder {
    private String field; // becomes non-null on first add() then is unmodified
    private final ArrayList<Term[]> termArrays;
    private final ArrayList<Integer> positions;
    private int slop;

    public Builder() {
      this.field = null;
      this.termArrays = new ArrayList<>();
      this.positions = new ArrayList<>();
      this.slop = 0;
    }

    public Builder(MultiPhraseQuery multiPhraseQuery) {
      this.field = multiPhraseQuery.field;

      int length = multiPhraseQuery.termArrays.length;

      this.termArrays = new ArrayList<>(length);
      this.positions = new ArrayList<>(length);

      for (int i = 0 ; i < length ; ++i) {
        this.termArrays.add(multiPhraseQuery.termArrays[i]);
        this.positions.add(multiPhraseQuery.positions[i]);
      }

      this.slop = multiPhraseQuery.slop;
    }

    public Builder setSlop(int s) {
      if (s < 0) {
        throw new IllegalArgumentException("slop value cannot be negative");
      }
      slop = s;

      return this;
    }

    public Builder add(Term term) { return add(new Term[]{term}); }

    public Builder add(Term[] terms) {
      int position = 0;
      if (positions.size() > 0)
        position = positions.get(positions.size() - 1) + 1;

      return add(terms, position);
    }

    public Builder add(Term[] terms, int position) {
      Objects.requireNonNull(terms, "Term array must not be null");
      if (termArrays.size() == 0)
        field = terms[0].field();

      for (Term term : terms) {
        if (!term.field().equals(field)) {
          throw new IllegalArgumentException(
              "All phrase terms must be in the same field (" + field + "): " + term);
        }
      }

      termArrays.add(terms);
      positions.add(position);

      return this;
    }

    public MultiPhraseQuery build() {
      int[] positionsArray = new int[this.positions.size()];

      for (int i = 0; i < this.positions.size(); ++i) {
        positionsArray[i] = this.positions.get(i);
      }

      Term[][] termArraysArray = termArrays.toArray(new Term[termArrays.size()][]);

      return new MultiPhraseQuery(field, termArraysArray, positionsArray, slop);
    }
  }

  private final String field;
  private final Term[][] termArrays;
  private final int[] positions;
  private final int slop;

  private MultiPhraseQuery(String field, Term[][] termArrays, int[] positions, int slop) {
    // No argument checks here since they are provided by the MultiPhraseQuery.Builder
    this.field = field;
    this.termArrays = termArrays;
    this.positions = positions;
    this.slop = slop;
  }


  public int getSlop() { return slop; }

  public Term[][] getTermArrays() {
    return termArrays;
  }

  public int[] getPositions() {
    return positions;
  }


  private class MultiPhraseWeight extends Weight {
    private final Similarity similarity;
    private final Similarity.SimWeight stats;
    private final Map<Term,TermContext> termContexts = new HashMap<>();
    private final boolean needsScores;

    public MultiPhraseWeight(IndexSearcher searcher, boolean needsScores)
      throws IOException {
      super(MultiPhraseQuery.this);
      this.needsScores = needsScores;
      this.similarity = searcher.getSimilarity(needsScores);
      final IndexReaderContext context = searcher.getTopReaderContext();

      // compute idf
      ArrayList<TermStatistics> allTermStats = new ArrayList<>();
      for(final Term[] terms: termArrays) {
        for (Term term: terms) {
          TermContext termContext = termContexts.get(term);
          if (termContext == null) {
            termContext = TermContext.build(context, term);
            termContexts.put(term, termContext);
          }
          allTermStats.add(searcher.termStatistics(term, termContext));
        }
      }
      stats = similarity.computeWeight(
          searcher.collectionStatistics(field),
          allTermStats.toArray(new TermStatistics[allTermStats.size()]));
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      for (final Term[] arr : termArrays) {
        Collections.addAll(terms, arr);
      }
    }

    @Override
    public float getValueForNormalization() {
      return stats.getValueForNormalization();
    }

    @Override
    public void normalize(float queryNorm, float boost) {
      stats.normalize(queryNorm, boost);
    }

    @Override
    public Scorer scorer(LeafReaderContext context) throws IOException {
      assert termArrays.length != 0;
      final LeafReader reader = context.reader();

      PhraseQuery.PostingsAndFreq[] postingsFreqs = new PhraseQuery.PostingsAndFreq[termArrays.length];

      final Terms fieldTerms = reader.terms(field);
      if (fieldTerms == null) {
        return null;
      }

      // TODO: move this check to createWeight to happen earlier to the user?
      if (fieldTerms.hasPositions() == false) {
        throw new IllegalStateException("field \"" + field + "\" was indexed without position data;" +
            " cannot run MultiPhraseQuery (phrase=" + getQuery() + ")");
      }

      // Reuse single TermsEnum below:
      final TermsEnum termsEnum = fieldTerms.iterator();
      float totalMatchCost = 0;

      for (int pos=0; pos<postingsFreqs.length; pos++) {
        Term[] terms = termArrays[pos];
        List<PostingsEnum> postings = new ArrayList<>();

        for (Term term : terms) {
          TermState termState = termContexts.get(term).get(context.ord);
          if (termState != null) {
            termsEnum.seekExact(term.bytes(), termState);
            postings.add(termsEnum.postings(null, PostingsEnum.POSITIONS));
            totalMatchCost += PhraseQuery.termPositionsCost(termsEnum);
          }
        }

        if (postings.isEmpty()) {
          return null;
        }

        final PostingsEnum postingsEnum;
        if (postings.size() == 1) {
          postingsEnum = postings.get(0);
        } else {
          postingsEnum = new UnionPostingsEnum(postings);
        }

        postingsFreqs[pos] = new PhraseQuery.PostingsAndFreq(postingsEnum, positions[pos], terms);
      }

      // sort by increasing docFreq order
      if (slop == 0) {
        ArrayUtil.timSort(postingsFreqs);
      }

      if (slop == 0) {
        return new ExactPhraseScorer(this, postingsFreqs,
                                      similarity.simScorer(stats, context),
                                      needsScores, totalMatchCost);
      } else {
        return new SloppyPhraseScorer(this, postingsFreqs, slop,
                                        similarity.simScorer(stats, context),
                                        needsScores, totalMatchCost);
      }
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
      Scorer scorer = scorer(context);
      if (scorer != null) {
        int newDoc = scorer.iterator().advance(doc);
        if (newDoc == doc) {
          float freq = slop == 0 ? scorer.freq() : ((SloppyPhraseScorer)scorer).sloppyFreq();
          SimScorer docScorer = similarity.simScorer(stats, context);
          Explanation freqExplanation = Explanation.match(freq, "phraseFreq=" + freq);
          Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
          return Explanation.match(
              scoreExplanation.getValue(),
              "weight("+getQuery()+" in "+doc+") [" + similarity.getClass().getSimpleName() + "], result of:",
              scoreExplanation);
        }
      }

      return Explanation.noMatch("no matching term");
    }
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (termArrays.length == 0) {
      return new MatchNoDocsQuery("empty MultiPhraseQuery");
    } else if (termArrays.length == 1) {                 // optimize one-term case
      Term[] terms = termArrays[0];
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setDisableCoord(true);
      for (Term term : terms) {
        builder.add(new TermQuery(term), BooleanClause.Occur.SHOULD);
      }
      return builder.build();
    } else {
      return super.rewrite(reader);
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    return new MultiPhraseWeight(searcher, needsScores);
  }

  @Override
  public final String toString(String f) {
    StringBuilder buffer = new StringBuilder();
    if (field == null || !field.equals(f)) {
      buffer.append(field);
      buffer.append(":");
    }

    buffer.append("\"");
    int lastPos = -1;

    for (int i = 0 ; i < termArrays.length ; ++i) {
      Term[] terms = termArrays[i];
      int position = positions[i];
      if (i != 0) {
        buffer.append(" ");
        for (int j=1; j<(position-lastPos); j++) {
          buffer.append("? ");
        }
      }
      if (terms.length > 1) {
        buffer.append("(");
        for (int j = 0; j < terms.length; j++) {
          buffer.append(terms[j].text());
          if (j < terms.length-1)
            buffer.append(" ");
        }
        buffer.append(")");
      } else {
        buffer.append(terms[0].text());
      }
      lastPos = position;
    }
    buffer.append("\"");

    if (slop != 0) {
      buffer.append("~");
      buffer.append(slop);
    }

    return buffer.toString();
  }


  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
           equalsTo(getClass().cast(other));
  }

  private boolean equalsTo(MultiPhraseQuery other) {
    return this.slop == other.slop && 
           termArraysEquals(this.termArrays, other.termArrays) && /* terms equal implies field equal */ 
           Arrays.equals(this.positions, other.positions);

  }

  @Override
  public int hashCode() {
    return classHash()
      ^ slop
      ^ termArraysHashCode() // terms equal implies field equal
      ^ Arrays.hashCode(positions);
  }

  // Breakout calculation of the termArrays hashcode
  private int termArraysHashCode() {
    int hashCode = 1;
    for (final Term[] termArray: termArrays) {
      hashCode = 31 * hashCode
          + (termArray == null ? 0 : Arrays.hashCode(termArray));
    }
    return hashCode;
  }

  // Breakout calculation of the termArrays equals
  private boolean termArraysEquals(Term[][] termArrays1, Term[][] termArrays2) {
    if (termArrays1.length != termArrays2.length) {
      return false;
    }

    for (int i = 0 ; i < termArrays1.length ; ++i) {
      Term[] termArray1 = termArrays1[i];
      Term[] termArray2 = termArrays2[i];
      if (!(termArray1 == null ? termArray2 == null : Arrays.equals(termArray1,
          termArray2))) {
        return false;
      }
    }
    return true;
  }

  static class UnionPostingsEnum extends PostingsEnum {
    final DocsQueue docsQueue;
    final long cost;

    final PositionsQueue posQueue = new PositionsQueue();
    int posQueueDoc = -2;
    final PostingsEnum[] subs;

    UnionPostingsEnum(Collection<PostingsEnum> subs) {
      docsQueue = new DocsQueue(subs.size());
      long cost = 0;
      for (PostingsEnum sub : subs) {
        docsQueue.add(sub);
        cost += sub.cost();
      }
      this.cost = cost;
      this.subs = subs.toArray(new PostingsEnum[subs.size()]);
    }

    @Override
    public int freq() throws IOException {
      int doc = docID();
      if (doc != posQueueDoc) {
        posQueue.clear();
        for (PostingsEnum sub : subs) {
          if (sub.docID() == doc) {
            int freq = sub.freq();
            for (int i = 0; i < freq; i++) {
              posQueue.add(sub.nextPosition());
            }
          }
        }
        posQueue.sort();
        posQueueDoc = doc;
      }
      return posQueue.size();
    }

    @Override
    public int nextPosition() throws IOException {
      return posQueue.next();
    }

    @Override
    public int docID() {
      return docsQueue.top().docID();
    }

    @Override
    public int nextDoc() throws IOException {
      PostingsEnum top = docsQueue.top();
      int doc = top.docID();

      do {
        top.nextDoc();
        top = docsQueue.updateTop();
      } while (top.docID() == doc);

      return top.docID();
    }

    @Override
    public int advance(int target) throws IOException {
      PostingsEnum top = docsQueue.top();

      do {
        top.advance(target);
        top = docsQueue.updateTop();
      } while (top.docID() < target);

      return top.docID();
    }

    @Override
    public long cost() {
      return cost;
    }

    @Override
    public int startOffset() throws IOException {
      return -1; // offsets are unsupported
    }

    @Override
    public int endOffset() throws IOException {
      return -1; // offsets are unsupported
    }

    @Override
    public BytesRef getPayload() throws IOException {
      return null; // payloads are unsupported
    }

    static class DocsQueue extends PriorityQueue<PostingsEnum> {
      DocsQueue(int size) {
        super(size);
      }

      @Override
      public final boolean lessThan(PostingsEnum a, PostingsEnum b) {
        return a.docID() < b.docID();
      }
    }

    static class PositionsQueue {
      private int arraySize = 16;
      private int index = 0;
      private int size = 0;
      private int[] array = new int[arraySize];

      void add(int i) {
        if (size == arraySize)
          growArray();

        array[size++] = i;
      }

      int next() {
        return array[index++];
      }

      void sort() {
        Arrays.sort(array, index, size);
      }

      void clear() {
        index = 0;
        size = 0;
      }

      int size() {
        return size;
      }

      private void growArray() {
        int[] newArray = new int[arraySize * 2];
        System.arraycopy(array, 0, newArray, 0, arraySize);
        array = newArray;
        arraySize *= 2;
      }
    }
  }
}
