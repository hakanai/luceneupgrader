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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.IntPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.PointTree;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.SortField.Type;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil.ByteArrayComparator;

/**
 * A range query that can take advantage of the fact that the index is sorted to speed up execution.
 * If the index is sorted on the same field as the query, it performs binary search on the field's
 * numeric doc values to find the documents at the lower and upper ends of the range.
 *
 * <p>This optimized execution strategy is only used if the following conditions hold:
 *
 * <ul>
 *   <li>The index is sorted, and its primary sort is on the same field as the query.
 *   <li>The query field has either {@link SortedNumericDocValues} or {@link NumericDocValues}.
 *   <li>The sort field is of type {@code SortField.Type.LONG} or {@code SortField.Type.INT}.
 *   <li>The segments must have at most one field value per document (otherwise we cannot easily
 *       determine the matching document IDs through a binary search).
 * </ul>
 *
 * If any of these conditions isn't met, the search is delegated to {@code fallbackQuery}.
 *
 * <p>This fallback must be an equivalent range query -- it should produce the same documents and
 * give constant scores. As an example, an {@link IndexSortSortedNumericDocValuesRangeQuery} might
 * be constructed as follows:
 *
 * <pre class="prettyprint">
 *   String field = "field";
 *   long lowerValue = 0, long upperValue = 10;
 *   Query fallbackQuery = LongPoint.newRangeQuery(field, lowerValue, upperValue);
 *   Query rangeQuery = new IndexSortSortedNumericDocValuesRangeQuery(
 *       field, lowerValue, upperValue, fallbackQuery);
 * </pre>
 *
 * @lucene.experimental
 */
public class IndexSortSortedNumericDocValuesRangeQuery extends Query {

  private final String field;
  private final long lowerValue;
  private final long upperValue;
  private final Query fallbackQuery;

  /**
   * Creates a new {@link IndexSortSortedNumericDocValuesRangeQuery}.
   *
   * @param field The field name.
   * @param lowerValue The lower end of the range (inclusive).
   * @param upperValue The upper end of the range (exclusive).
   * @param fallbackQuery A query to fall back to if the optimization cannot be applied.
   */
  public IndexSortSortedNumericDocValuesRangeQuery(
      String field, long lowerValue, long upperValue, Query fallbackQuery) {
    this.field = Objects.requireNonNull(field);
    this.lowerValue = lowerValue;
    this.upperValue = upperValue;
    this.fallbackQuery = fallbackQuery;
  }

  public Query getFallbackQuery() {
    return fallbackQuery;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IndexSortSortedNumericDocValuesRangeQuery that = (IndexSortSortedNumericDocValuesRangeQuery) o;
    return lowerValue == that.lowerValue
        && upperValue == that.upperValue
        && Objects.equals(field, that.field)
        && Objects.equals(fallbackQuery, that.fallbackQuery);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, lowerValue, upperValue, fallbackQuery);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(field)) {
      visitor.visitLeaf(this);
      fallbackQuery.visit(visitor);
    }
  }

  @Override
  public String toString(String field) {
    StringBuilder b = new StringBuilder();
    if (this.field.equals(field) == false) {
      b.append(this.field).append(":");
    }
    return b.append("[")
        .append(lowerValue)
        .append(" TO ")
        .append(upperValue)
        .append("]")
        .toString();
  }

  @Override
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    if (lowerValue == Long.MIN_VALUE && upperValue == Long.MAX_VALUE) {
      return new FieldExistsQuery(field);
    }

    Query rewrittenFallback = fallbackQuery.rewrite(indexSearcher);
    if (rewrittenFallback.getClass() == MatchAllDocsQuery.class) {
      return new MatchAllDocsQuery();
    }
    if (rewrittenFallback == fallbackQuery) {
      return this;
    } else {
      return new IndexSortSortedNumericDocValuesRangeQuery(
          field, lowerValue, upperValue, rewrittenFallback);
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    Weight fallbackWeight = fallbackQuery.createWeight(searcher, scoreMode, boost);

    return new ConstantScoreWeight(this, boost) {

      @Override
      public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        final Weight weight = this;
        IteratorAndCount itAndCount = getDocIdSetIteratorOrNull(context);
        if (itAndCount != null) {
          DocIdSetIterator disi = itAndCount.it;
          return new ScorerSupplier() {
            @Override
            public Scorer get(long leadCost) throws IOException {
              return new ConstantScoreScorer(weight, score(), scoreMode, disi);
            }

            @Override
            public long cost() {
              return disi.cost();
            }
          };
        }
        return fallbackWeight.scorerSupplier(context);
      }

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        ScorerSupplier scorerSupplier = scorerSupplier(context);
        if (scorerSupplier == null) {
          return null;
        }
        return scorerSupplier.get(Long.MAX_VALUE);
      }

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        // Both queries should always return the same values, so we can just check
        // if the fallback query is cacheable.
        return fallbackWeight.isCacheable(ctx);
      }

      @Override
      public int count(LeafReaderContext context) throws IOException {
        if (context.reader().hasDeletions() == false) {
          IteratorAndCount itAndCount = getDocIdSetIteratorOrNull(context);
          if (itAndCount != null && itAndCount.count != -1) {
            return itAndCount.count;
          }
        }
        return fallbackWeight.count(context);
      }
    };
  }

  private static class ValueAndDoc {
    byte[] value;
    int docID;
    boolean done;
  }

  /**
   * Move to the minimum leaf node that has at least one value that is greater than (or equal to if
   * {@code allowEqual}) {@code value}, and return the next greater value on this block. Upon
   * returning, the {@code pointTree} must be on the leaf node where the value was found.
   */
  private static ValueAndDoc findNextValue(
      PointTree pointTree,
      byte[] value,
      boolean allowEqual,
      ByteArrayComparator comparator,
      boolean lastDoc)
      throws IOException {
    int cmp = comparator.compare(pointTree.getMaxPackedValue(), 0, value, 0);
    if (cmp < 0 || (cmp == 0 && allowEqual == false)) {
      return null;
    }
    if (pointTree.moveToChild() == false) {
      ValueAndDoc vd = new ValueAndDoc();
      pointTree.visitDocValues(
          new IntersectVisitor() {

            @Override
            public void visit(int docID, byte[] packedValue) throws IOException {
              if (vd.value == null) {
                int cmp = comparator.compare(packedValue, 0, value, 0);
                if (cmp > 0 || (cmp == 0 && allowEqual)) {
                  vd.value = packedValue.clone();
                  vd.docID = docID;
                }
              } else if (lastDoc && vd.done == false) {
                int cmp = comparator.compare(packedValue, 0, vd.value, 0);
                assert cmp >= 0;
                if (cmp > 0) {
                  vd.done = true;
                } else {
                  vd.docID = docID;
                }
              }
            }

            @Override
            public void visit(int docID) throws IOException {
              throw new UnsupportedOperationException();
            }

            @Override
            public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              return Relation.CELL_CROSSES_QUERY;
            }
          });
      if (vd.value != null) {
        return vd;
      } else {
        return null;
      }
    }

    // Recurse
    do {
      ValueAndDoc vd = findNextValue(pointTree, value, allowEqual, comparator, lastDoc);
      if (vd != null) {
        return vd;
      }
    } while (pointTree.moveToSibling());

    boolean moved = pointTree.moveToParent();
    assert moved;
    return null;
  }

  /**
   * Find the next value that is greater than (or equal to if {@code allowEqual}) and return either
   * its first doc ID or last doc ID depending on {@code lastDoc}. This method returns -1 if there
   * is no greater value in the dataset.
   */
  private static int nextDoc(
      PointTree pointTree,
      byte[] value,
      boolean allowEqual,
      ByteArrayComparator comparator,
      boolean lastDoc)
      throws IOException {
    ValueAndDoc vd = findNextValue(pointTree, value, allowEqual, comparator, lastDoc);
    if (vd == null) {
      return -1;
    }
    if (lastDoc == false || vd.done) {
      return vd.docID;
    }

    // We found the next value, now we need the last doc ID.
    int doc = lastDoc(pointTree, vd.value, comparator);
    if (doc == -1) {
      // vd.docID was actually the last doc ID
      return vd.docID;
    } else {
      return doc;
    }
  }

  /**
   * Compute the last doc ID that matches the given value and is stored on a leaf node that compares
   * greater than the current leaf node that the provided {@link PointTree} is positioned on. This
   * returns -1 if no other leaf node contains the provided {@code value}.
   */
  private static int lastDoc(PointTree pointTree, byte[] value, ByteArrayComparator comparator)
      throws IOException {
    // Create a stack of nodes that may contain value that we'll use to search for the last leaf
    // node that contains `value`.
    // While the logic looks a bit complicated due to the fact that the PointTree API doesn't allow
    // moving back to previous siblings, this effectively performs a binary search.
    Deque<PointTree> stack = new ArrayDeque<>();

    outer:
    while (true) {

      // Move to the next node
      while (pointTree.moveToSibling() == false) {
        if (pointTree.moveToParent() == false) {
          // No next node
          break outer;
        }
      }

      int cmp = comparator.compare(pointTree.getMinPackedValue(), 0, value, 0);
      if (cmp > 0) {
        // This node doesn't have `value`, so next nodes can't either
        break;
      }

      stack.push(pointTree.clone());
    }

    while (stack.isEmpty() == false) {
      PointTree next = stack.pop();
      if (next.moveToChild() == false) {
        int[] lastDoc = {-1};
        next.visitDocValues(
            new IntersectVisitor() {

              @Override
              public void visit(int docID) throws IOException {
                throw new UnsupportedOperationException();
              }

              @Override
              public void visit(int docID, byte[] packedValue) throws IOException {
                int cmp = comparator.compare(value, 0, packedValue, 0);
                if (cmp == 0) {
                  lastDoc[0] = docID;
                }
              }

              @Override
              public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
                return Relation.CELL_CROSSES_QUERY;
              }
            });
        if (lastDoc[0] != -1) {
          return lastDoc[0];
        }
      } else {
        do {
          int cmp = comparator.compare(next.getMinPackedValue(), 0, value, 0);
          if (cmp > 0) {
            // This node doesn't have `value`, so next nodes can't either
            break;
          }
          stack.push(next.clone());
        } while (next.moveToSibling());
      }
    }

    return -1;
  }

  private boolean matchNone(PointValues points, byte[] queryLowerPoint, byte[] queryUpperPoint)
      throws IOException {
    assert points.getNumDimensions() == 1;
    final ByteArrayComparator comparator =
        ArrayUtil.getUnsignedComparator(points.getBytesPerDimension());
    return comparator.compare(points.getMinPackedValue(), 0, queryUpperPoint, 0) > 0
        || comparator.compare(points.getMaxPackedValue(), 0, queryLowerPoint, 0) < 0;
  }

  private boolean matchAll(PointValues points, byte[] queryLowerPoint, byte[] queryUpperPoint)
      throws IOException {
    assert points.getNumDimensions() == 1;
    final ByteArrayComparator comparator =
        ArrayUtil.getUnsignedComparator(points.getBytesPerDimension());
    return comparator.compare(points.getMinPackedValue(), 0, queryLowerPoint, 0) >= 0
        && comparator.compare(points.getMaxPackedValue(), 0, queryUpperPoint, 0) <= 0;
  }

  private IteratorAndCount getDocIdSetIteratorOrNullFromBkd(
      LeafReaderContext context, DocIdSetIterator delegate) throws IOException {
    Sort indexSort = context.reader().getMetaData().getSort();
    if (indexSort == null
        || indexSort.getSort().length == 0
        || indexSort.getSort()[0].getField().equals(field) == false) {
      return null;
    }

    final boolean reverse = indexSort.getSort()[0].getReverse();

    PointValues points = context.reader().getPointValues(field);
    if (points == null) {
      return null;
    }

    if (points.getNumDimensions() != 1) {
      return null;
    }

    if (points.getBytesPerDimension() != Long.BYTES
        && points.getBytesPerDimension() != Integer.BYTES) {
      return null;
    }

    if (points.size() != points.getDocCount()) {
      return null;
    }

    assert lowerValue <= upperValue;
    byte[] queryLowerPoint;
    byte[] queryUpperPoint;
    if (points.getBytesPerDimension() == Integer.BYTES) {
      queryLowerPoint = IntPoint.pack((int) lowerValue).bytes;
      queryUpperPoint = IntPoint.pack((int) upperValue).bytes;
    } else {
      queryLowerPoint = LongPoint.pack(lowerValue).bytes;
      queryUpperPoint = LongPoint.pack(upperValue).bytes;
    }
    if (matchNone(points, queryLowerPoint, queryUpperPoint)) {
      return IteratorAndCount.empty();
    }
    if (matchAll(points, queryLowerPoint, queryUpperPoint)) {
      int maxDoc = context.reader().maxDoc();
      if (points.getDocCount() == maxDoc) {
        return IteratorAndCount.all(maxDoc);
      } else {
        return IteratorAndCount.sparseRange(0, maxDoc, delegate);
      }
    }

    int minDocId, maxDocId;
    final ByteArrayComparator comparator =
        ArrayUtil.getUnsignedComparator(points.getBytesPerDimension());

    if (reverse) {
      minDocId = nextDoc(points.getPointTree(), queryUpperPoint, false, comparator, true) + 1;
    } else {
      minDocId = nextDoc(points.getPointTree(), queryLowerPoint, true, comparator, false);
      if (minDocId == -1) {
        // No matches
        return IteratorAndCount.empty();
      }
    }

    if (reverse) {
      maxDocId = nextDoc(points.getPointTree(), queryLowerPoint, true, comparator, true) + 1;
      if (maxDocId == 0) {
        // No matches
        return IteratorAndCount.empty();
      }
    } else {
      maxDocId = nextDoc(points.getPointTree(), queryUpperPoint, false, comparator, false);
      if (maxDocId == -1) {
        maxDocId = context.reader().maxDoc();
      }
    }

    if (minDocId == maxDocId) {
      return IteratorAndCount.empty();
    }

    if ((points.getDocCount() == context.reader().maxDoc())) {
      return IteratorAndCount.denseRange(minDocId, maxDocId);
    } else {
      return IteratorAndCount.sparseRange(minDocId, maxDocId, delegate);
    }
  }

  private IteratorAndCount getDocIdSetIteratorOrNull(LeafReaderContext context) throws IOException {
    if (lowerValue > upperValue) {
      return IteratorAndCount.empty();
    }

    SortedNumericDocValues sortedNumericValues =
        DocValues.getSortedNumeric(context.reader(), field);
    NumericDocValues numericValues = DocValues.unwrapSingleton(sortedNumericValues);
    if (numericValues != null) {
      IteratorAndCount itAndCount = getDocIdSetIteratorOrNullFromBkd(context, numericValues);
      if (itAndCount != null) {
        return itAndCount;
      }
      Sort indexSort = context.reader().getMetaData().getSort();
      if (indexSort != null
          && indexSort.getSort().length > 0
          && indexSort.getSort()[0].getField().equals(field)) {

        final SortField sortField = indexSort.getSort()[0];
        final SortField.Type sortFieldType = getSortFieldType(sortField);
        // The index sort optimization is only supported for Type.INT and Type.LONG
        if (sortFieldType == Type.INT || sortFieldType == Type.LONG) {
          return getDocIdSetIterator(sortField, sortFieldType, context, numericValues);
        }
      }
    }
    return null;
  }

  /**
   * Computes the document IDs that lie within the range [lowerValue, upperValue] by performing
   * binary search on the field's doc values.
   *
   * <p>Because doc values only allow forward iteration, we need to reload the field comparator
   * every time the binary search accesses an earlier element.
   *
   * <p>We must also account for missing values when performing the binary search. For this reason,
   * we load the {@link FieldComparator} instead of checking the docvalues directly. The returned
   * {@link DocIdSetIterator} makes sure to wrap the original docvalues to skip over documents with
   * no value.
   */
  private IteratorAndCount getDocIdSetIterator(
      SortField sortField,
      SortField.Type sortFieldType,
      LeafReaderContext context,
      DocIdSetIterator delegate)
      throws IOException {
    long lower = sortField.getReverse() ? upperValue : lowerValue;
    long upper = sortField.getReverse() ? lowerValue : upperValue;
    int maxDoc = context.reader().maxDoc();

    // Perform a binary search to find the first document with value >= lower.
    ValueComparator comparator = loadComparator(sortField, sortFieldType, lower, context);
    int low = 0;
    int high = maxDoc - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (comparator.compare(mid) <= 0) {
        high = mid - 1;
        comparator = loadComparator(sortField, sortFieldType, lower, context);
      } else {
        low = mid + 1;
      }
    }
    int firstDocIdInclusive = high + 1;

    // Perform a binary search to find the first document with value > upper.
    // Since we know that upper >= lower, we can initialize the lower bound
    // of the binary search to the result of the previous search.
    comparator = loadComparator(sortField, sortFieldType, upper, context);
    low = firstDocIdInclusive;
    high = maxDoc - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (comparator.compare(mid) < 0) {
        high = mid - 1;
        comparator = loadComparator(sortField, sortFieldType, upper, context);
      } else {
        low = mid + 1;
      }
    }

    int lastDocIdExclusive = high + 1;

    if (firstDocIdInclusive == lastDocIdExclusive) {
      return IteratorAndCount.empty();
    }

    Object missingValue = sortField.getMissingValue();
    LeafReader reader = context.reader();
    PointValues pointValues = reader.getPointValues(field);
    final long missingLongValue = missingValue == null ? 0L : ((Number) missingValue).longValue();
    // all documents have docValues or missing value falls outside the range
    if ((pointValues != null && pointValues.getDocCount() == reader.maxDoc())
        || (missingLongValue < lowerValue || missingLongValue > upperValue)) {
      return IteratorAndCount.denseRange(firstDocIdInclusive, lastDocIdExclusive);
    } else {
      return IteratorAndCount.sparseRange(firstDocIdInclusive, lastDocIdExclusive, delegate);
    }
  }

  /** Compares the given document's value with a stored reference value. */
  private interface ValueComparator {
    int compare(int docID) throws IOException;
  }

  private static ValueComparator loadComparator(
      SortField sortField, SortField.Type type, long topValue, LeafReaderContext context)
      throws IOException {
    @SuppressWarnings("unchecked")
    FieldComparator<Number> fieldComparator =
        (FieldComparator<Number>) sortField.getComparator(1, Pruning.NONE);
    if (type == Type.INT) {
      fieldComparator.setTopValue((int) topValue);
    } else {
      // Since we support only Type.INT and Type.LONG, assuming LONG for all other cases
      fieldComparator.setTopValue(topValue);
    }

    LeafFieldComparator leafFieldComparator = fieldComparator.getLeafComparator(context);
    int direction = sortField.getReverse() ? -1 : 1;

    return doc -> {
      int value = leafFieldComparator.compareTop(doc);
      return direction * value;
    };
  }

  private static SortField.Type getSortFieldType(SortField sortField) {
    // We expect the sortField to be SortedNumericSortField
    if (sortField instanceof SortedNumericSortField) {
      return ((SortedNumericSortField) sortField).getNumericType();
    } else {
      return sortField.getType();
    }
  }

  /**
   * Provides a {@code DocIdSetIterator} along with an accurate count of documents provided by the
   * iterator (or {@code -1} if an accurate count is unknown).
   */
  private static class IteratorAndCount {
    private final DocIdSetIterator it;
    private final int count;

    IteratorAndCount(DocIdSetIterator it, int count) {
      this.it = it;
      this.count = count;
    }

    static IteratorAndCount empty() {
      return new IteratorAndCount(DocIdSetIterator.empty(), 0);
    }

    static IteratorAndCount all(int maxDoc) {
      return new IteratorAndCount(DocIdSetIterator.all(maxDoc), maxDoc);
    }

    static IteratorAndCount denseRange(int minDoc, int maxDoc) {
      return new IteratorAndCount(DocIdSetIterator.range(minDoc, maxDoc), maxDoc - minDoc);
    }

    static IteratorAndCount sparseRange(int minDoc, int maxDoc, DocIdSetIterator delegate) {
      return new IteratorAndCount(new BoundedDocIdSetIterator(minDoc, maxDoc, delegate), -1);
    }
  }

  /**
   * A doc ID set iterator that wraps a delegate iterator and only returns doc IDs in the range
   * [firstDocInclusive, lastDoc).
   */
  private static class BoundedDocIdSetIterator extends DocIdSetIterator {
    private final int firstDoc;
    private final int lastDoc;
    private final DocIdSetIterator delegate;

    private int docID = -1;

    BoundedDocIdSetIterator(int firstDoc, int lastDoc, DocIdSetIterator delegate) {
      assert delegate != null;
      this.firstDoc = firstDoc;
      this.lastDoc = lastDoc;
      this.delegate = delegate;
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
      if (target < firstDoc) {
        target = firstDoc;
      }

      int result = delegate.advance(target);
      if (result < lastDoc) {
        docID = result;
      } else {
        docID = NO_MORE_DOCS;
      }
      return docID;
    }

    @Override
    public long cost() {
      return Math.min(delegate.cost(), lastDoc - firstDoc);
    }
  }
}
