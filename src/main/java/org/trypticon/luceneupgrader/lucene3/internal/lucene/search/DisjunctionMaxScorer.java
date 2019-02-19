package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;

class DisjunctionMaxScorer extends DisjunctionScorer {
  /* Multiplier applied to non-maximum-scoring subqueries for a document as they are summed into the result. */
  private final float tieBreakerMultiplier;
  private int doc = -1;

  /* Used when scoring currently matching doc. */
  private float scoreSum;
  private float scoreMax;

  public DisjunctionMaxScorer(Weight weight, float tieBreakerMultiplier,
      Similarity similarity, Scorer[] subScorers, int numScorers) {
    super(similarity, weight, subScorers, numScorers);
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }

  @Override
  public int nextDoc() throws IOException {
    if (numScorers == 0) return doc = NO_MORE_DOCS;
    while (subScorers[0].docID() == doc) {
      if (subScorers[0].nextDoc() != NO_MORE_DOCS) {
        heapAdjust(0);
      } else {
        heapRemoveRoot();
        if (numScorers == 0) {
          return doc = NO_MORE_DOCS;
        }
      }
    }
    
    return doc = subScorers[0].docID();
  }

  @Override
  public int docID() {
    return doc;
  }


  @Override
  public float score() throws IOException {
    int doc = subScorers[0].docID();
    scoreSum = scoreMax = subScorers[0].score();
    int size = numScorers;
    scoreAll(1, size, doc);
    scoreAll(2, size, doc);
    return scoreMax + (scoreSum - scoreMax) * tieBreakerMultiplier;
  }

  // Recursively iterate all subScorers that generated last doc computing sum and max
  private void scoreAll(int root, int size, int doc) throws IOException {
    if (root < size && subScorers[root].docID() == doc) {
      float sub = subScorers[root].score();
      scoreSum += sub;
      scoreMax = Math.max(scoreMax, sub);
      scoreAll((root<<1)+1, size, doc);
      scoreAll((root<<1)+2, size, doc);
    }
  }

  @Override
  public float freq() throws IOException {
    int doc = subScorers[0].docID();
    int size = numScorers;
    return 1 + freq(1, size, doc) + freq(2, size, doc);
  }
  
  // Recursively iterate all subScorers that generated last doc computing sum and max
  private int freq(int root, int size, int doc) throws IOException {
    int freq = 0;
    if (root < size && subScorers[root].docID() == doc) {
      freq++;
      freq += freq((root<<1)+1, size, doc);
      freq += freq((root<<1)+2, size, doc);
    }
    return freq;
  }

  @Override
  public int advance(int target) throws IOException {
    if (numScorers == 0) return doc = NO_MORE_DOCS;
    while (subScorers[0].docID() < target) {
      if (subScorers[0].advance(target) != NO_MORE_DOCS) {
        heapAdjust(0);
      } else {
        heapRemoveRoot();
        if (numScorers == 0) {
          return doc = NO_MORE_DOCS;
        }
      }
    }
    return doc = subScorers[0].docID();
  }

  @Override
  public void visitSubScorers(Query parent, BooleanClause.Occur relationship, ScorerVisitor<Query, Query, Scorer> visitor) {
    super.visitSubScorers(parent, relationship, visitor);
    final Query q = weight.getQuery();
    for (int i = 0; i < numScorers; i++) {
      subScorers[i].visitSubScorers(q, BooleanClause.Occur.SHOULD, visitor);
    }
  }

}
