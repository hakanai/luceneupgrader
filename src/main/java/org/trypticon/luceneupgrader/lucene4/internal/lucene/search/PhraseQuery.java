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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity.SimScorer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;

public class PhraseQuery extends Query {
  private String field;
  private ArrayList<Term> terms = new ArrayList<>(4);
  private ArrayList<Integer> positions = new ArrayList<>(4);
  private int maxPosition = 0;
  private int slop = 0;

  public PhraseQuery() {}

  public void setSlop(int s) {
    if (s < 0) {
      throw new IllegalArgumentException("slop value cannot be negative");
    }
    slop = s; 
  }
  public int getSlop() { return slop; }

  public void add(Term term) {
    int position = 0;
    if(positions.size() > 0)
        position = positions.get(positions.size()-1).intValue() + 1;

    add(term, position);
  }

  public void add(Term term, int position) {
    if (terms.size() == 0) {
      field = term.field();
    } else if (!term.field().equals(field)) {
      throw new IllegalArgumentException("All phrase terms must be in the same field: " + term);
    }

    terms.add(term);
    positions.add(Integer.valueOf(position));
    if (position > maxPosition) maxPosition = position;
  }

  public Term[] getTerms() {
    return terms.toArray(new Term[0]);
  }

  public int[] getPositions() {
      int[] result = new int[positions.size()];
      for(int i = 0; i < positions.size(); i++)
          result[i] = positions.get(i).intValue();
      return result;
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (terms.isEmpty()) {
      BooleanQuery bq = new BooleanQuery();
      bq.setBoost(getBoost());
      return bq;
    } else if (terms.size() == 1) {
      TermQuery tq = new TermQuery(terms.get(0));
      tq.setBoost(getBoost());
      return tq;
    } else
      return super.rewrite(reader);
  }

  static class PostingsAndFreq implements Comparable<PostingsAndFreq> {
    final DocsAndPositionsEnum postings;
    final int docFreq;
    final int position;
    final Term[] terms;
    final int nTerms; // for faster comparisons

    public PostingsAndFreq(DocsAndPositionsEnum postings, int docFreq, int position, Term... terms) {
      this.postings = postings;
      this.docFreq = docFreq;
      this.position = position;
      nTerms = terms==null ? 0 : terms.length;
      if (nTerms>0) {
        if (terms.length==1) {
          this.terms = terms;
        } else {
          Term[] terms2 = new Term[terms.length];
          System.arraycopy(terms, 0, terms2, 0, terms.length);
          Arrays.sort(terms2);
          this.terms = terms2;
        }
      } else {
        this.terms = null;
      }
    }

    @Override
    public int compareTo(PostingsAndFreq other) {
      if (docFreq != other.docFreq) {
        return docFreq - other.docFreq;
      }
      if (position != other.position) {
        return position - other.position;
      }
      if (nTerms != other.nTerms) {
        return nTerms - other.nTerms;
      }
      if (nTerms == 0) {
        return 0;
      }
      for (int i=0; i<terms.length; i++) {
        int res = terms[i].compareTo(other.terms[i]);
        if (res!=0) return res;
      }
      return 0;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + docFreq;
      result = prime * result + position;
      for (int i=0; i<nTerms; i++) {
        result = prime * result + terms[i].hashCode(); 
      }
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      PostingsAndFreq other = (PostingsAndFreq) obj;
      if (docFreq != other.docFreq) return false;
      if (position != other.position) return false;
      if (terms == null) return other.terms == null;
      return Arrays.equals(terms, other.terms);
    }
  }

  private class PhraseWeight extends Weight {
    private final Similarity similarity;
    private final Similarity.SimWeight stats;
    private transient TermContext states[];

    public PhraseWeight(IndexSearcher searcher)
      throws IOException {
      this.similarity = searcher.getSimilarity();
      final IndexReaderContext context = searcher.getTopReaderContext();
      states = new TermContext[terms.size()];
      TermStatistics termStats[] = new TermStatistics[terms.size()];
      for (int i = 0; i < terms.size(); i++) {
        final Term term = terms.get(i);
        states[i] = TermContext.build(context, term);
        termStats[i] = searcher.termStatistics(term, states[i]);
      }
      stats = similarity.computeWeight(getBoost(), searcher.collectionStatistics(field), termStats);
    }

    @Override
    public String toString() { return "weight(" + PhraseQuery.this + ")"; }

    @Override
    public Query getQuery() { return PhraseQuery.this; }

    @Override
    public float getValueForNormalization() {
      return stats.getValueForNormalization();
    }

    @Override
    public void normalize(float queryNorm, float topLevelBoost) {
      stats.normalize(queryNorm, topLevelBoost);
    }

    @Override
    public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
      assert !terms.isEmpty();
      final AtomicReader reader = context.reader();
      final Bits liveDocs = acceptDocs;
      PostingsAndFreq[] postingsFreqs = new PostingsAndFreq[terms.size()];

      final Terms fieldTerms = reader.terms(field);
      if (fieldTerms == null) {
        return null;
      }

      // Reuse single TermsEnum below:
      final TermsEnum te = fieldTerms.iterator(null);
      
      for (int i = 0; i < terms.size(); i++) {
        final Term t = terms.get(i);
        final TermState state = states[i].get(context.ord);
        if (state == null) { /* term doesnt exist in this segment */
          assert termNotInReader(reader, t): "no termstate found but term exists in reader";
          return null;
        }
        te.seekExact(t.bytes(), state);
        DocsAndPositionsEnum postingsEnum = te.docsAndPositions(liveDocs, null, DocsEnum.FLAG_NONE);

        // PhraseQuery on a field that did not index
        // positions.
        if (postingsEnum == null) {
          assert te.seekExact(t.bytes()) : "termstate found but no term exists in reader";
          // term does exist, but has no positions
          throw new IllegalStateException("field \"" + t.field() + "\" was indexed without position data; cannot run PhraseQuery (term=" + t.text() + ")");
        }
        postingsFreqs[i] = new PostingsAndFreq(postingsEnum, te.docFreq(), positions.get(i).intValue(), t);
      }

      // sort by increasing docFreq order
      if (slop == 0) {
        ArrayUtil.timSort(postingsFreqs);
      }

      if (slop == 0) {  // optimize exact case
        return new ExactPhraseScorer(this, postingsFreqs, similarity.simScorer(stats, context));
      } else {
        return new SloppyPhraseScorer(this, postingsFreqs, slop, similarity.simScorer(stats, context));
      }
    }
    
    // only called from assert
    private boolean termNotInReader(AtomicReader reader, Term term) throws IOException {
      return reader.docFreq(term) == 0;
    }

    @Override
    public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
      Scorer scorer = scorer(context, context.reader().getLiveDocs());
      if (scorer != null) {
        int newDoc = scorer.advance(doc);
        if (newDoc == doc) {
          float freq = slop == 0 ? scorer.freq() : ((SloppyPhraseScorer)scorer).sloppyFreq();
          SimScorer docScorer = similarity.simScorer(stats, context);
          ComplexExplanation result = new ComplexExplanation();
          result.setDescription("weight("+getQuery()+" in "+doc+") [" + similarity.getClass().getSimpleName() + "], result of:");
          Explanation scoreExplanation = docScorer.explain(doc, new Explanation(freq, "phraseFreq=" + freq));
          result.addDetail(scoreExplanation);
          result.setValue(scoreExplanation.getValue());
          result.setMatch(true);
          return result;
        }
      }
      
      return new ComplexExplanation(false, 0.0f, "no matching term");
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new PhraseWeight(searcher);
  }

  @Override
  public void extractTerms(Set<Term> queryTerms) {
    queryTerms.addAll(terms);
  }

  @Override
  public String toString(String f) {
    StringBuilder buffer = new StringBuilder();
    if (field != null && !field.equals(f)) {
      buffer.append(field);
      buffer.append(":");
    }

    buffer.append("\"");
    String[] pieces = new String[maxPosition + 1];
    for (int i = 0; i < terms.size(); i++) {
      int pos = positions.get(i).intValue();
      String s = pieces[pos];
      if (s == null) {
        s = (terms.get(i)).text();
      } else {
        s = s + "|" + (terms.get(i)).text();
      }
      pieces[pos] = s;
    }
    for (int i = 0; i < pieces.length; i++) {
      if (i > 0) {
        buffer.append(' ');
      }
      String s = pieces[i];
      if (s == null) {
        buffer.append('?');
      } else {
        buffer.append(s);
      }
    }
    buffer.append("\"");

    if (slop != 0) {
      buffer.append("~");
      buffer.append(slop);
    }

    buffer.append(ToStringUtils.boost(getBoost()));

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PhraseQuery))
      return false;
    PhraseQuery other = (PhraseQuery)o;
    return (this.getBoost() == other.getBoost())
      && (this.slop == other.slop)
      &&  this.terms.equals(other.terms)
      && this.positions.equals(other.positions);
  }

  @Override
  public int hashCode() {
    return Float.floatToIntBits(getBoost())
      ^ slop
      ^ terms.hashCode()
      ^ positions.hashCode();
  }

}
