package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;

public class DisjunctionMaxQuery extends Query implements Iterable<Query> {

  /* The subqueries */
  private ArrayList<Query> disjuncts = new ArrayList<Query>();

  /* Multiple of the non-max disjunct scores added into our final score.  Non-zero values support tie-breaking. */
  private float tieBreakerMultiplier = 0.0f;


  public DisjunctionMaxQuery(float tieBreakerMultiplier) {
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }

  public DisjunctionMaxQuery(Collection<Query> disjuncts, float tieBreakerMultiplier) {
    this.tieBreakerMultiplier = tieBreakerMultiplier;
    add(disjuncts);
  }


  public void add(Query query) {
    disjuncts.add(query);
  }


  public void add(Collection<Query> disjuncts) {
    this.disjuncts.addAll(disjuncts);
  }

  public Iterator<Query> iterator() {
    return disjuncts.iterator();
  }

  protected class DisjunctionMaxWeight extends Weight {
    protected Similarity similarity;

    protected ArrayList<Weight> weights = new ArrayList<Weight>();  // The Weight's for our subqueries, in 1-1 correspondence with disjuncts

    public DisjunctionMaxWeight(Searcher searcher) throws IOException {
      this.similarity = searcher.getSimilarity();
      for (Query disjunctQuery : disjuncts) {
        weights.add(disjunctQuery.createWeight(searcher));
      }
    }

    @Override
    public Query getQuery() { return DisjunctionMaxQuery.this; }

    @Override
    public float getValue() { return getBoost(); }

    @Override
    public float sumOfSquaredWeights() throws IOException {
      float max = 0.0f, sum = 0.0f;
      for (Weight currentWeight : weights) {
        float sub = currentWeight.sumOfSquaredWeights();
        sum += sub;
        max = Math.max(max, sub);
        
      }
      float boost = getBoost();
      return (((sum - max) * tieBreakerMultiplier * tieBreakerMultiplier) + max) * boost * boost;
    }

    @Override
    public void normalize(float norm) {
      norm *= getBoost();  // Incorporate our boost
      for (Weight wt : weights) {
        wt.normalize(norm);
      }
    }

    @Override
    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder,
        boolean topScorer) throws IOException {
      Scorer[] scorers = new Scorer[weights.size()];
      int idx = 0;
      for (Weight w : weights) {
        Scorer subScorer = w.scorer(reader, true, false);
        if (subScorer != null) {
          scorers[idx++] = subScorer;
        }
      }
      if (idx == 0) return null; // all scorers did not have documents
      DisjunctionMaxScorer result = new DisjunctionMaxScorer(this, tieBreakerMultiplier, similarity, scorers, idx);
      return result;
    }

    @Override
    public Explanation explain(IndexReader reader, int doc) throws IOException {
      if (disjuncts.size() == 1) return weights.get(0).explain(reader,doc);
      ComplexExplanation result = new ComplexExplanation();
      float max = 0.0f, sum = 0.0f;
      result.setDescription(tieBreakerMultiplier == 0.0f ? "max of:" : "max plus " + tieBreakerMultiplier + " times others of:");
      for (Weight wt : weights) {
        Explanation e = wt.explain(reader, doc);
        if (e.isMatch()) {
          result.setMatch(Boolean.TRUE);
          result.addDetail(e);
          sum += e.getValue();
          max = Math.max(max, e.getValue());
        }
      }
      result.setValue(max + (sum - max) * tieBreakerMultiplier);
      return result;
    }
    
  }  // end of DisjunctionMaxWeight inner class

  @Override
  public Weight createWeight(Searcher searcher) throws IOException {
    return new DisjunctionMaxWeight(searcher);
  }


  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    int numDisjunctions = disjuncts.size();
    if (numDisjunctions == 1) {
      Query singleton = disjuncts.get(0);
      Query result = singleton.rewrite(reader);
      if (getBoost() != 1.0f) {
        if (result == singleton) result = (Query)result.clone();
        result.setBoost(getBoost() * result.getBoost());
      }
      return result;
    }
    DisjunctionMaxQuery clone = null;
    for (int i = 0 ; i < numDisjunctions; i++) {
      Query clause = disjuncts.get(i);
      Query rewrite = clause.rewrite(reader);
      if (rewrite != clause) {
        if (clone == null) clone = (DisjunctionMaxQuery)this.clone();
        clone.disjuncts.set(i, rewrite);
      }
    }
    if (clone != null) return clone;
    else return this;
  }

  @Override @SuppressWarnings("unchecked")
  public Object clone() {
    DisjunctionMaxQuery clone = (DisjunctionMaxQuery)super.clone();
    clone.disjuncts = (ArrayList<Query>) this.disjuncts.clone();
    return clone;
  }

  // inherit javadoc
  @Override
  public void extractTerms(Set<Term> terms) {
    for (Query query : disjuncts) {
      query.extractTerms(terms);
    }
  }


  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("(");
    int numDisjunctions = disjuncts.size();
    for (int i = 0 ; i < numDisjunctions; i++) {
      Query subquery = disjuncts.get(i);
      if (subquery instanceof BooleanQuery) {   // wrap sub-bools in parens
        buffer.append("(");
        buffer.append(subquery.toString(field));
        buffer.append(")");
      }
      else buffer.append(subquery.toString(field));
      if (i != numDisjunctions-1) buffer.append(" | ");
    }
    buffer.append(")");
    if (tieBreakerMultiplier != 0.0f) {
      buffer.append("~");
      buffer.append(tieBreakerMultiplier);
    }
    if (getBoost() != 1.0) {
      buffer.append("^");
      buffer.append(getBoost());
    }
    return buffer.toString();
  }


  @Override
  public boolean equals(Object o) {
    if (! (o instanceof DisjunctionMaxQuery) ) return false;
    DisjunctionMaxQuery other = (DisjunctionMaxQuery)o;
    return this.getBoost() == other.getBoost()
            && this.tieBreakerMultiplier == other.tieBreakerMultiplier
            && this.disjuncts.equals(other.disjuncts);
  }


  @Override
  public int hashCode() {
    return Float.floatToIntBits(getBoost())
            + Float.floatToIntBits(tieBreakerMultiplier)
            + disjuncts.hashCode();
  }

}
