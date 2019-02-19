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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;


public class FilteredQuery extends Query {

  private final Query query;
  private final Filter filter;
  private final FilterStrategy strategy;

  public FilteredQuery(Query query, Filter filter) {
    this(query, filter, RANDOM_ACCESS_FILTER_STRATEGY);
  }
  
  public FilteredQuery(Query query, Filter filter, FilterStrategy strategy) {
    if (query == null || filter == null)
      throw new IllegalArgumentException("Query and filter cannot be null.");
    if (strategy == null)
      throw new IllegalArgumentException("FilterStrategy can not be null");
    this.strategy = strategy;
    this.query = query;
    this.filter = filter;
  }
  
  @Override
  public Weight createWeight(final IndexSearcher searcher) throws IOException {
    final Weight weight = query.createWeight (searcher);
    return new Weight() {
      
      @Override
      public boolean scoresDocsOutOfOrder() {
        return true;
      }

      @Override
      public float getValueForNormalization() throws IOException { 
        return weight.getValueForNormalization() * getBoost() * getBoost(); // boost sub-weight
      }

      @Override
      public void normalize(float norm, float topLevelBoost) { 
        weight.normalize(norm, topLevelBoost * getBoost()); // incorporate boost
      }

      @Override
      public Explanation explain(AtomicReaderContext ir, int i) throws IOException {
        Explanation inner = weight.explain (ir, i);
        Filter f = FilteredQuery.this.filter;
        DocIdSet docIdSet = f.getDocIdSet(ir, ir.reader().getLiveDocs());
        DocIdSetIterator docIdSetIterator = docIdSet == null ? DocIdSetIterator.empty() : docIdSet.iterator();
        if (docIdSetIterator == null) {
          docIdSetIterator = DocIdSetIterator.empty();
        }
        if (docIdSetIterator.advance(i) == i) {
          return inner;
        } else {
          Explanation result = new Explanation
            (0.0f, "failure to match filter: " + f.toString());
          result.addDetail(inner);
          return result;
        }
      }

      // return this query
      @Override
      public Query getQuery() {
        return FilteredQuery.this;
      }

      // return a filtering scorer
      @Override
      public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        assert filter != null;

        DocIdSet filterDocIdSet = filter.getDocIdSet(context, acceptDocs);
        if (filterDocIdSet == null) {
          // this means the filter does not accept any documents.
          return null;
        }

        return strategy.filteredScorer(context, weight, filterDocIdSet);
      }

      // return a filtering top scorer
      @Override
      public BulkScorer bulkScorer(AtomicReaderContext context, boolean scoreDocsInOrder, Bits acceptDocs) throws IOException {
        assert filter != null;

        DocIdSet filterDocIdSet = filter.getDocIdSet(context, acceptDocs);
        if (filterDocIdSet == null) {
          // this means the filter does not accept any documents.
          return null;
        }

        return strategy.filteredBulkScorer(context, weight, scoreDocsInOrder, filterDocIdSet);
      }
    };
  }
  
  private static final class QueryFirstScorer extends Scorer {
    private final Scorer scorer;
    private int scorerDoc = -1;
    private final Bits filterBits;

    protected QueryFirstScorer(Weight weight, Bits filterBits, Scorer other) {
      super(weight);
      this.scorer = other;
      this.filterBits = filterBits;
    }

    @Override
    public int nextDoc() throws IOException {
      int doc;
      for(;;) {
        doc = scorer.nextDoc();
        if (doc == Scorer.NO_MORE_DOCS || filterBits.get(doc)) {
          return scorerDoc = doc;
        }
      } 
    }
    
    @Override
    public int advance(int target) throws IOException {
      int doc = scorer.advance(target);
      if (doc != Scorer.NO_MORE_DOCS && !filterBits.get(doc)) {
        return scorerDoc = nextDoc();
      } else {
        return scorerDoc = doc;
      }
    }

    @Override
    public int docID() {
      return scorerDoc;
    }
    
    @Override
    public float score() throws IOException {
      return scorer.score();
    }
    
    @Override
    public int freq() throws IOException { return scorer.freq(); }
    
    @Override
    public Collection<ChildScorer> getChildren() {
      return Collections.singleton(new ChildScorer(scorer, "FILTERED"));
    }

    @Override
    public long cost() {
      return scorer.cost();
    }
  }

  private static class QueryFirstBulkScorer extends BulkScorer {

    private final Scorer scorer;
    private final Bits filterBits;

    public QueryFirstBulkScorer(Scorer scorer, Bits filterBits) {
      this.scorer = scorer;
      this.filterBits = filterBits;
    }

    @Override
    public boolean score(Collector collector, int maxDoc) throws IOException {
      // the normalization trick already applies the boost of this query,
      // so we can use the wrapped scorer directly:
      collector.setScorer(scorer);
      if (scorer.docID() == -1) {
        scorer.nextDoc();
      }
      while (true) {
        final int scorerDoc = scorer.docID();
        if (scorerDoc < maxDoc) {
          if (filterBits.get(scorerDoc)) {
            collector.collect(scorerDoc);
          }
          scorer.nextDoc();
        } else {
          break;
        }
      }

      return scorer.docID() != Scorer.NO_MORE_DOCS;
    }
  }
  
  private static class LeapFrogScorer extends Scorer {
    private final DocIdSetIterator secondary;
    private final DocIdSetIterator primary;
    private final Scorer scorer;
    protected int primaryDoc = -1;
    protected int secondaryDoc = -1;

    protected LeapFrogScorer(Weight weight, DocIdSetIterator primary, DocIdSetIterator secondary, Scorer scorer) {
      super(weight);
      this.primary = primary;
      this.secondary = secondary;
      this.scorer = scorer;
    }

    private final int advanceToNextCommonDoc() throws IOException {
      for (;;) {
        if (secondaryDoc < primaryDoc) {
          secondaryDoc = secondary.advance(primaryDoc);
        } else if (secondaryDoc == primaryDoc) {
          return primaryDoc;
        } else {
          primaryDoc = primary.advance(secondaryDoc);
        }
      }
    }

    @Override
    public final int nextDoc() throws IOException {
      primaryDoc = primaryNext();
      return advanceToNextCommonDoc();
    }
    
    protected int primaryNext() throws IOException {
      return primary.nextDoc();
    }
    
    @Override
    public final int advance(int target) throws IOException {
      if (target > primaryDoc) {
        primaryDoc = primary.advance(target);
      }
      return advanceToNextCommonDoc();
    }

    @Override
    public final int docID() {
      return secondaryDoc;
    }
    
    @Override
    public final float score() throws IOException {
      return scorer.score();
    }
    
    @Override
    public final int freq() throws IOException {
      return scorer.freq();
    }
    
    @Override
    public final Collection<ChildScorer> getChildren() {
      return Collections.singleton(new ChildScorer(scorer, "FILTERED"));
    }

    @Override
    public long cost() {
      return Math.min(primary.cost(), secondary.cost());
    }
  }
  
  // TODO once we have way to figure out if we use RA or LeapFrog we can remove this scorer
  private static final class PrimaryAdvancedLeapFrogScorer extends LeapFrogScorer {
    private final int firstFilteredDoc;

    protected PrimaryAdvancedLeapFrogScorer(Weight weight, int firstFilteredDoc, DocIdSetIterator filterIter, Scorer other) {
      super(weight, filterIter, other, other);
      this.firstFilteredDoc = firstFilteredDoc;
      this.primaryDoc = firstFilteredDoc; // initialize to prevent and advance call to move it further
    }

    @Override
    protected int primaryNext() throws IOException {
      if (secondaryDoc != -1) {
        return super.primaryNext();
      } else {
        return firstFilteredDoc;
      }
    }
  }
  

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    final Query queryRewritten = query.rewrite(reader);
    
    if (queryRewritten != query) {
      // rewrite to a new FilteredQuery wrapping the rewritten query
      final Query rewritten = new FilteredQuery(queryRewritten, filter, strategy);
      rewritten.setBoost(this.getBoost());
      return rewritten;
    } else {
      // nothing to rewrite, we are done!
      return this;
    }
  }

  public final Query getQuery() {
    return query;
  }

  public final Filter getFilter() {
    return filter;
  }
  
  public FilterStrategy getFilterStrategy() {
    return this.strategy;
  }

  // inherit javadoc
  @Override
  public void extractTerms(Set<Term> terms) {
    getQuery().extractTerms(terms);
  }

  @Override
  public String toString (String s) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("filtered(");
    buffer.append(query.toString(s));
    buffer.append(")->");
    buffer.append(filter);
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (!super.equals(o))
      return false;
    assert o instanceof FilteredQuery;
    final FilteredQuery fq = (FilteredQuery) o;
    return fq.query.equals(this.query) && fq.filter.equals(this.filter) && fq.strategy.equals(this.strategy);
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    hash = hash * 31 + strategy.hashCode();
    hash = hash * 31 + query.hashCode();
    hash = hash * 31 + filter.hashCode();
    return hash;
  }
  
  public static final FilterStrategy RANDOM_ACCESS_FILTER_STRATEGY = new RandomAccessFilterStrategy();
  
  public static final FilterStrategy LEAP_FROG_FILTER_FIRST_STRATEGY = new LeapFrogFilterStrategy(false);
  
  public static final FilterStrategy LEAP_FROG_QUERY_FIRST_STRATEGY = new LeapFrogFilterStrategy(true);
  
  public static final FilterStrategy QUERY_FIRST_FILTER_STRATEGY = new QueryFirstFilterStrategy();
  
  public static abstract class FilterStrategy {
    
    public abstract Scorer filteredScorer(AtomicReaderContext context,
        Weight weight, DocIdSet docIdSet) throws IOException;

    public BulkScorer filteredBulkScorer(AtomicReaderContext context,
        Weight weight, boolean scoreDocsInOrder, DocIdSet docIdSet) throws IOException {
      Scorer scorer = filteredScorer(context, weight, docIdSet);
      if (scorer == null) {
        return null;
      }
      // This impl always scores docs in order, so we can
      // ignore scoreDocsInOrder:
      return new Weight.DefaultBulkScorer(scorer);
    }
  }
  
  public static class RandomAccessFilterStrategy extends FilterStrategy {

    @Override
    public Scorer filteredScorer(AtomicReaderContext context, Weight weight, DocIdSet docIdSet) throws IOException {
      final DocIdSetIterator filterIter = docIdSet.iterator();
      if (filterIter == null) {
        // this means the filter does not accept any documents.
        return null;
      }  

      final int firstFilterDoc = filterIter.nextDoc();
      if (firstFilterDoc == DocIdSetIterator.NO_MORE_DOCS) {
        return null;
      }
      
      final Bits filterAcceptDocs = docIdSet.bits();
      // force if RA is requested
      final boolean useRandomAccess = filterAcceptDocs != null && useRandomAccess(filterAcceptDocs, firstFilterDoc);
      if (useRandomAccess) {
        // if we are using random access, we return the inner scorer, just with other acceptDocs
        return weight.scorer(context, filterAcceptDocs);
      } else {
        assert firstFilterDoc > -1;
        // we are gonna advance() this scorer, so we set inorder=true/toplevel=false
        // we pass null as acceptDocs, as our filter has already respected acceptDocs, no need to do twice
        final Scorer scorer = weight.scorer(context, null);
        // TODO once we have way to figure out if we use RA or LeapFrog we can remove this scorer
        return (scorer == null) ? null : new PrimaryAdvancedLeapFrogScorer(weight, firstFilterDoc, filterIter, scorer);
      }
    }
    
    protected boolean useRandomAccess(Bits bits, int firstFilterDoc) {
      //TODO once we have a cost API on filters and scorers we should rethink this heuristic
      return firstFilterDoc < 100;
    }
  }
  
  private static final class LeapFrogFilterStrategy extends FilterStrategy {
    
    private final boolean scorerFirst;
    
    private LeapFrogFilterStrategy(boolean scorerFirst) {
      this.scorerFirst = scorerFirst;
    }

    @Override
    public Scorer filteredScorer(AtomicReaderContext context,
        Weight weight, DocIdSet docIdSet) throws IOException {
      final DocIdSetIterator filterIter = docIdSet.iterator();
      if (filterIter == null) {
        // this means the filter does not accept any documents.
        return null;
      }
      // we pass null as acceptDocs, as our filter has already respected acceptDocs, no need to do twice
      final Scorer scorer = weight.scorer(context, null);
      if (scorer == null) {
        return null;
      }

      if (scorerFirst) {
        return new LeapFrogScorer(weight, scorer, filterIter, scorer);  
      } else {
        return new LeapFrogScorer(weight, filterIter, scorer, scorer);  
      }
    }
  }
  
  private static final class QueryFirstFilterStrategy extends FilterStrategy {
    @Override
    public Scorer filteredScorer(final AtomicReaderContext context,
        Weight weight,
        DocIdSet docIdSet) throws IOException {
      Bits filterAcceptDocs = docIdSet.bits();
      if (filterAcceptDocs == null) {
        // Filter does not provide random-access Bits; we
        // must fallback to leapfrog:
        return LEAP_FROG_QUERY_FIRST_STRATEGY.filteredScorer(context, weight, docIdSet);
      }
      final Scorer scorer = weight.scorer(context, null);
      return scorer == null ? null : new QueryFirstScorer(weight,
          filterAcceptDocs, scorer);
    }

    @Override
    public BulkScorer filteredBulkScorer(final AtomicReaderContext context,
        Weight weight,
        boolean scoreDocsInOrder, // ignored (we always top-score in order)
        DocIdSet docIdSet) throws IOException {
      Bits filterAcceptDocs = docIdSet.bits();
      if (filterAcceptDocs == null) {
        // Filter does not provide random-access Bits; we
        // must fallback to leapfrog:
        return LEAP_FROG_QUERY_FIRST_STRATEGY.filteredBulkScorer(context, weight, scoreDocsInOrder, docIdSet);
      }
      final Scorer scorer = weight.scorer(context, null);
      return scorer == null ? null : new QueryFirstBulkScorer(scorer, filterAcceptDocs);
    }
  }
  
}
