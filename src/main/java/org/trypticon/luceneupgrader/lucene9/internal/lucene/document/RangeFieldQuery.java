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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.document;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ConstantScoreScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ConstantScoreWeight;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.QueryVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScoreMode;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScorerSupplier;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil.ByteArrayComparator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.DocIdSetBuilder;

/**
 * Query class for searching {@code RangeField} types by a defined {@link Relation}.
 *
 * @lucene.internal
 */
public abstract class RangeFieldQuery extends Query {
  /** field name */
  final String field;

  /**
   * query relation intersects: {@code CELL_CROSSES_QUERY}, contains: {@code CELL_CONTAINS_QUERY},
   * within: {@code CELL_WITHIN_QUERY}
   */
  final QueryType queryType;

  /** number of dimensions - max 4 */
  final int numDims;

  /** ranges encoded as a sortable byte array */
  final byte[] ranges;

  /** number of bytes per dimension */
  final int bytesPerDim;

  /** ByteArrayComparator selected by bytesPerDim */
  final ByteArrayComparator comparator;

  /**
   * Used by {@code RangeFieldQuery} to check how each internal or leaf node relates to the query.
   */
  public enum QueryType {
    /** Use this for intersects queries. */
    INTERSECTS {

      @Override
      Relation compare(
          byte[] queryPackedValue,
          byte[] minPackedValue,
          byte[] maxPackedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, minOffset) < 0
            || comparator.compare(queryPackedValue, minOffset, maxPackedValue, maxOffset) > 0) {
          // disjoint
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, minOffset) >= 0
            && comparator.compare(queryPackedValue, minOffset, minPackedValue, maxOffset) <= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(
          byte[] queryPackedValue,
          byte[] packedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return comparator.compare(queryPackedValue, maxOffset, packedValue, minOffset) >= 0
            && comparator.compare(queryPackedValue, minOffset, packedValue, maxOffset) <= 0;
      }
    },
    /** Use this for within queries. */
    WITHIN {

      @Override
      Relation compare(
          byte[] queryPackedValue,
          byte[] minPackedValue,
          byte[] maxPackedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, maxOffset) < 0
            || comparator.compare(queryPackedValue, minOffset, maxPackedValue, minOffset) > 0) {
          // all ranges have at least one point outside of the query
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, maxOffset) >= 0
            && comparator.compare(queryPackedValue, minOffset, minPackedValue, minOffset) <= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(
          byte[] queryPackedValue,
          byte[] packedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return comparator.compare(queryPackedValue, minOffset, packedValue, minOffset) <= 0
            && comparator.compare(queryPackedValue, maxOffset, packedValue, maxOffset) >= 0;
      }
    },
    /** Use this for contains */
    CONTAINS {

      @Override
      Relation compare(
          byte[] queryPackedValue,
          byte[] minPackedValue,
          byte[] maxPackedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (comparator.compare(queryPackedValue, maxOffset, maxPackedValue, maxOffset) > 0
            || comparator.compare(queryPackedValue, minOffset, minPackedValue, minOffset) < 0) {
          // all ranges are either less than the query max or greater than the query min
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (comparator.compare(queryPackedValue, maxOffset, minPackedValue, maxOffset) <= 0
            && comparator.compare(queryPackedValue, minOffset, maxPackedValue, minOffset) >= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(
          byte[] queryPackedValue,
          byte[] packedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return comparator.compare(queryPackedValue, minOffset, packedValue, minOffset) >= 0
            && comparator.compare(queryPackedValue, maxOffset, packedValue, maxOffset) <= 0;
      }
    },
    /** Use this for crosses queries */
    CROSSES {

      @Override
      Relation compare(
          byte[] queryPackedValue,
          byte[] minPackedValue,
          byte[] maxPackedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        throw new UnsupportedOperationException();
      }

      @Override
      boolean matches(
          byte[] queryPackedValue,
          byte[] packedValue,
          int numDims,
          int bytesPerDim,
          int dim,
          ByteArrayComparator comparator) {
        throw new UnsupportedOperationException();
      }

      @Override
      Relation compare(
          byte[] queryPackedValue,
          byte[] minPackedValue,
          byte[] maxPackedValue,
          int numDims,
          int bytesPerDim,
          ByteArrayComparator comparator) {
        Relation intersectRelation =
            QueryType.INTERSECTS.compare(
                queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator);
        if (intersectRelation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        }

        Relation withinRelation =
            QueryType.WITHIN.compare(
                queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator);
        if (withinRelation == Relation.CELL_INSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (intersectRelation == Relation.CELL_INSIDE_QUERY
            && withinRelation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      public boolean matches(
          byte[] queryPackedValue,
          byte[] packedValue,
          int numDims,
          int bytesPerDim,
          ByteArrayComparator comparator) {
        return INTERSECTS.matches(queryPackedValue, packedValue, numDims, bytesPerDim, comparator)
            && WITHIN.matches(queryPackedValue, packedValue, numDims, bytesPerDim, comparator)
                == false;
      }
    };

    abstract Relation compare(
        byte[] queryPackedValue,
        byte[] minPackedValue,
        byte[] maxPackedValue,
        int numDims,
        int bytesPerDim,
        int dim,
        ByteArrayComparator comparator);

    Relation compare(
        byte[] queryPackedValue,
        byte[] minPackedValue,
        byte[] maxPackedValue,
        int numDims,
        int bytesPerDim,
        ByteArrayComparator comparator) {
      boolean inside = true;
      for (int dim = 0; dim < numDims; ++dim) {
        Relation relation =
            compare(
                queryPackedValue,
                minPackedValue,
                maxPackedValue,
                numDims,
                bytesPerDim,
                dim,
                comparator);
        if (relation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        } else if (relation != Relation.CELL_INSIDE_QUERY) {
          inside = false;
        }
      }
      return inside ? Relation.CELL_INSIDE_QUERY : Relation.CELL_CROSSES_QUERY;
    }

    abstract boolean matches(
        byte[] queryPackedValue,
        byte[] packedValue,
        int numDims,
        int bytesPerDim,
        int dim,
        ByteArrayComparator comparator);

    /**
     * Compares every dim for 2 encoded ranges and returns true if all dims match. Matching
     * implementation is based on the QueryType.
     */
    public boolean matches(
        byte[] queryPackedValue,
        byte[] packedValue,
        int numDims,
        int bytesPerDim,
        ByteArrayComparator comparator) {
      for (int dim = 0; dim < numDims; ++dim) {
        if (matches(queryPackedValue, packedValue, numDims, bytesPerDim, dim, comparator)
            == false) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Create a query for searching indexed ranges that match the provided relation.
   *
   * @param field field name. must not be null.
   * @param ranges encoded range values; this is done by the {@code RangeField} implementation
   * @param queryType the query relation
   */
  protected RangeFieldQuery(
      String field, final byte[] ranges, final int numDims, final QueryType queryType) {
    checkArgs(field, ranges, numDims);
    if (queryType == null) {
      throw new IllegalArgumentException("Query type cannot be null");
    }
    this.field = field;
    this.queryType = queryType;
    this.numDims = numDims;
    this.ranges = ranges;
    this.bytesPerDim = ranges.length / (2 * numDims);
    this.comparator = ArrayUtil.getUnsignedComparator(bytesPerDim);
  }

  /** check input arguments */
  private static void checkArgs(String field, final byte[] ranges, final int numDims) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    if (numDims > 4) {
      throw new IllegalArgumentException("dimension size cannot be greater than 4");
    }
    if (ranges == null || ranges.length == 0) {
      throw new IllegalArgumentException("encoded ranges cannot be null or empty");
    }
  }

  /** Check indexed field info against the provided query data. */
  private void checkFieldInfo(FieldInfo fieldInfo) {
    if (fieldInfo.getPointDimensionCount() / 2 != numDims) {
      throw new IllegalArgumentException(
          "field=\""
              + field
              + "\" was indexed with numDims="
              + fieldInfo.getPointDimensionCount() / 2
              + " but this query has numDims="
              + numDims);
    }
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(field)) {
      visitor.visitLeaf(this);
    }
  }

  @Override
  public final Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return new ConstantScoreWeight(this, boost) {

      private IntersectVisitor getIntersectVisitor(DocIdSetBuilder result) {
        return new IntersectVisitor() {
          DocIdSetBuilder.BulkAdder adder;

          @Override
          public void grow(int count) {
            adder = result.grow(count);
          }

          @Override
          public void visit(int docID) throws IOException {
            adder.add(docID);
          }

          @Override
          public void visit(DocIdSetIterator iterator) throws IOException {
            adder.add(iterator);
          }

          @Override
          public void visit(int docID, byte[] leaf) throws IOException {
            if (queryType.matches(ranges, leaf, numDims, bytesPerDim, comparator)) {
              visit(docID);
            }
          }

          @Override
          public void visit(DocIdSetIterator iterator, byte[] leaf) throws IOException {
            if (queryType.matches(ranges, leaf, numDims, bytesPerDim, comparator)) {
              adder.add(iterator);
            }
          }

          @Override
          public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
            return queryType.compare(
                ranges, minPackedValue, maxPackedValue, numDims, bytesPerDim, comparator);
          }
        };
      }

      @Override
      public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        LeafReader reader = context.reader();
        PointValues values = reader.getPointValues(field);
        if (values == null) {
          // no docs in this segment indexed any ranges
          return null;
        }
        FieldInfo fieldInfo = reader.getFieldInfos().fieldInfo(field);
        if (fieldInfo == null) {
          // no docs in this segment indexed this field
          return null;
        }
        checkFieldInfo(fieldInfo);
        boolean allDocsMatch = false;
        if (values.getDocCount() == reader.maxDoc()
            && queryType.compare(
                    ranges,
                    values.getMinPackedValue(),
                    values.getMaxPackedValue(),
                    numDims,
                    bytesPerDim,
                    comparator)
                == Relation.CELL_INSIDE_QUERY) {
          allDocsMatch = true;
        }

        final Weight weight = this;
        if (allDocsMatch) {
          return new ScorerSupplier() {
            @Override
            public Scorer get(long leadCost) {
              return new ConstantScoreScorer(
                  weight, score(), scoreMode, DocIdSetIterator.all(reader.maxDoc()));
            }

            @Override
            public long cost() {
              return reader.maxDoc();
            }
          };
        } else {
          return new ScorerSupplier() {

            final DocIdSetBuilder result = new DocIdSetBuilder(reader.maxDoc(), values, field);
            final IntersectVisitor visitor = getIntersectVisitor(result);
            long cost = -1;

            @Override
            public Scorer get(long leadCost) throws IOException {
              values.intersect(visitor);
              DocIdSetIterator iterator = result.build().iterator();
              return new ConstantScoreScorer(weight, score(), scoreMode, iterator);
            }

            @Override
            public long cost() {
              if (cost == -1) {
                // Computing the cost may be expensive, so only do it if necessary
                cost = values.estimateDocCount(visitor);
                assert cost >= 0;
              }
              return cost;
            }
          };
        }
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
        return true;
      }
    };
  }

  @Override
  public int hashCode() {
    int hash = classHash();
    hash = 31 * hash + field.hashCode();
    hash = 31 * hash + numDims;
    hash = 31 * hash + queryType.hashCode();
    hash = 31 * hash + Arrays.hashCode(ranges);

    return hash;
  }

  @Override
  public final boolean equals(Object o) {
    return sameClassAs(o) && equalsTo(getClass().cast(o));
  }

  /** Check equality of two RangeFieldQuery objects */
  protected boolean equalsTo(RangeFieldQuery other) {
    return Objects.equals(field, other.field)
        && numDims == other.numDims
        && Arrays.equals(ranges, other.ranges)
        && other.queryType == queryType;
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    if (this.field.equals(field) == false) {
      sb.append(this.field);
      sb.append(':');
    }
    sb.append("<ranges:");
    sb.append(toString(ranges, 0));
    for (int d = 1; d < numDims; ++d) {
      sb.append(' ');
      sb.append(toString(ranges, d));
    }
    sb.append('>');

    return sb.toString();
  }

  /**
   * Returns a string of a single value in a human-readable format for debugging. This is used by
   * {@link #toString()}.
   *
   * @param dimension dimension of the particular value
   * @param ranges encoded ranges, never null
   * @return human readable value for debugging
   */
  protected abstract String toString(byte[] ranges, int dimension);
}
