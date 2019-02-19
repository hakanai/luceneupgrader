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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PrefixCodedTerms;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PrefixCodedTerms.TermIterator;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.BooleanClause.Occur;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRefBuilder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.DocIdSetBuilder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.RamUsageEstimator;

public class TermInSetQuery extends Query implements Accountable {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(TermInSetQuery.class);
  // Same threshold as MultiTermQueryConstantScoreWrapper
  static final int BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD = 16;

  private final String field;
  private final PrefixCodedTerms termData;
  private final int termDataHashCode; // cached hashcode of termData

  public TermInSetQuery(String field, Collection<BytesRef> terms) {
    BytesRef[] sortedTerms = terms.toArray(new BytesRef[terms.size()]);
    // already sorted if we are a SortedSet with natural order
    boolean sorted = terms instanceof SortedSet && ((SortedSet<BytesRef>)terms).comparator() == null;
    if (!sorted) {
      ArrayUtil.timSort(sortedTerms);
    }
    PrefixCodedTerms.Builder builder = new PrefixCodedTerms.Builder();
    BytesRefBuilder previous = null;
    for (BytesRef term : sortedTerms) {
      if (previous == null) {
        previous = new BytesRefBuilder();
      } else if (previous.get().equals(term)) {
        continue; // deduplicate
      }
      builder.add(field, term);
      previous.copyBytes(term);
    }
    this.field = field;
    termData = builder.finish();
    termDataHashCode = termData.hashCode();
  }

  public TermInSetQuery(String field, BytesRef...terms) {
    this(field, Arrays.asList(terms));
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    final int threshold = Math.min(BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD, BooleanQuery.getMaxClauseCount());
    if (termData.size() <= threshold) {
      BooleanQuery.Builder bq = new BooleanQuery.Builder();
      TermIterator iterator = termData.iterator();
      for (BytesRef term = iterator.next(); term != null; term = iterator.next()) {
        bq.add(new TermQuery(new Term(iterator.field(), BytesRef.deepCopyOf(term))), Occur.SHOULD);
      }
      return new ConstantScoreQuery(bq.build());
    }
    return super.rewrite(reader);
  }

  @Override
  public boolean equals(Object other) {
    return sameClassAs(other) &&
        equalsTo(getClass().cast(other));
  }

  private boolean equalsTo(TermInSetQuery other) {
    // no need to check 'field' explicitly since it is encoded in 'termData'
    // termData might be heavy to compare so check the hash code first
    return termDataHashCode == other.termDataHashCode &&
        termData.equals(other.termData);
  }

  @Override
  public int hashCode() {
    return 31 * classHash() + termDataHashCode;
  }

  public PrefixCodedTerms getTermData() {
    return termData;
  }

  @Override
  public String toString(String defaultField) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    TermIterator iterator = termData.iterator();
    for (BytesRef term = iterator.next(); term != null; term = iterator.next()) {
      if (!first) {
        builder.append(' ');
      }
      first = false;
      builder.append(new Term(iterator.field(), term).toString());
    }

    return builder.toString();
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED + termData.ramBytesUsed();
  }

  @Override
  public Collection<Accountable> getChildResources() {
    return Collections.emptyList();
  }

  private static class TermAndState {
    final String field;
    final TermsEnum termsEnum;
    final BytesRef term;
    final TermState state;
    final int docFreq;
    final long totalTermFreq;

    TermAndState(String field, TermsEnum termsEnum) throws IOException {
      this.field = field;
      this.termsEnum = termsEnum;
      this.term = BytesRef.deepCopyOf(termsEnum.term());
      this.state = termsEnum.termState();
      this.docFreq = termsEnum.docFreq();
      this.totalTermFreq = termsEnum.totalTermFreq();
    }
  }

  private static class WeightOrDocIdSet {
    final Weight weight;
    final DocIdSet set;

    WeightOrDocIdSet(Weight weight) {
      this.weight = Objects.requireNonNull(weight);
      this.set = null;
    }

    WeightOrDocIdSet(DocIdSet bitset) {
      this.set = bitset;
      this.weight = null;
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    return new ConstantScoreWeight(this) {

      @Override
      public void extractTerms(Set<Term> terms) {
        // no-op
        // This query is for abuse cases when the number of terms is too high to
        // run efficiently as a BooleanQuery. So likewise we hide its terms in
        // order to protect highlighters
      }

      private WeightOrDocIdSet rewrite(LeafReaderContext context) throws IOException {
        final LeafReader reader = context.reader();

        final Fields fields = reader.fields();
        Terms terms = fields.terms(field);
        if (terms == null) {
          return null;
        }
        TermsEnum termsEnum = terms.iterator();
        PostingsEnum docs = null;
        TermIterator iterator = termData.iterator();

        // We will first try to collect up to 'threshold' terms into 'matchingTerms'
        // if there are two many terms, we will fall back to building the 'builder'
        final int threshold = Math.min(BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD, BooleanQuery.getMaxClauseCount());
        assert termData.size() > threshold : "Query should have been rewritten";
        List<TermAndState> matchingTerms = new ArrayList<>(threshold);
        DocIdSetBuilder builder = null;

        for (BytesRef term = iterator.next(); term != null; term = iterator.next()) {
          assert field.equals(iterator.field());
          if (termsEnum.seekExact(term)) {
            if (matchingTerms == null) {
              docs = termsEnum.postings(docs, PostingsEnum.NONE);
              builder.add(docs);
            } else if (matchingTerms.size() < threshold) {
              matchingTerms.add(new TermAndState(field, termsEnum));
            } else {
              assert matchingTerms.size() == threshold;
              builder = new DocIdSetBuilder(reader.maxDoc(), terms);
              docs = termsEnum.postings(docs, PostingsEnum.NONE);
              builder.add(docs);
              for (TermAndState t : matchingTerms) {
                t.termsEnum.seekExact(t.term, t.state);
                docs = t.termsEnum.postings(docs, PostingsEnum.NONE);
                builder.add(docs);
              }
              matchingTerms = null;
            }
          }
        }
        if (matchingTerms != null) {
          assert builder == null;
          BooleanQuery.Builder bq = new BooleanQuery.Builder();
          for (TermAndState t : matchingTerms) {
            final TermContext termContext = new TermContext(searcher.getTopReaderContext());
            termContext.register(t.state, context.ord, t.docFreq, t.totalTermFreq);
            bq.add(new TermQuery(new Term(t.field, t.term), termContext), Occur.SHOULD);
          }
          Query q = new ConstantScoreQuery(bq.build());
          final Weight weight = searcher.rewrite(q).createWeight(searcher, needsScores);
          weight.normalize(1f, score());
          return new WeightOrDocIdSet(weight);
        } else {
          assert builder != null;
          return new WeightOrDocIdSet(builder.build());
        }
      }

      private Scorer scorer(DocIdSet set) throws IOException {
        if (set == null) {
          return null;
        }
        final DocIdSetIterator disi = set.iterator();
        if (disi == null) {
          return null;
        }
        return new ConstantScoreScorer(this, score(), disi);
      }

      @Override
      public BulkScorer bulkScorer(LeafReaderContext context) throws IOException {
        final WeightOrDocIdSet weightOrBitSet = rewrite(context);
        if (weightOrBitSet == null) {
          return null;
        } else if (weightOrBitSet.weight != null) {
          return weightOrBitSet.weight.bulkScorer(context);
        } else {
          final Scorer scorer = scorer(weightOrBitSet.set);
          if (scorer == null) {
            return null;
          }
          return new DefaultBulkScorer(scorer);
        }
      }

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        final WeightOrDocIdSet weightOrBitSet = rewrite(context);
        if (weightOrBitSet == null) {
          return null;
        } else if (weightOrBitSet.weight != null) {
          return weightOrBitSet.weight.scorer(context);
        } else {
          return scorer(weightOrBitSet.set);
        }
      }
    };
  }
}
