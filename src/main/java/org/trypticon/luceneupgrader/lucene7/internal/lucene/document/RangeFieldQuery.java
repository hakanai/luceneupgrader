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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.ConstantScoreScorer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.ConstantScoreWeight;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.ScorerSupplier;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.DocIdSetBuilder;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FutureArrays;

abstract class RangeFieldQuery extends Query {
  final String field;
  final QueryType queryType;
  final int numDims;
  final byte[] ranges;
  final int bytesPerDim;

  enum QueryType {
    INTERSECTS {

      @Override
      Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue,
          int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, minPackedValue, minOffset, minOffset + bytesPerDim) < 0
            || FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, maxPackedValue, maxOffset, maxOffset + bytesPerDim) > 0) {
          // disjoint
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, maxPackedValue, minOffset, minOffset + bytesPerDim) >= 0
            && FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, minPackedValue, maxOffset, maxOffset + bytesPerDim) <= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, packedValue, minOffset, minOffset + bytesPerDim) >= 0
            && FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, packedValue, maxOffset, maxOffset + bytesPerDim) <= 0;
      }

    },
    WITHIN {

      @Override
      Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue,
          int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, minPackedValue, maxOffset, maxOffset + bytesPerDim) < 0
            || FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, maxPackedValue, minOffset, minOffset + bytesPerDim) > 0) {
          // all ranges have at least one point outside of the query
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, maxPackedValue, maxOffset, maxOffset + bytesPerDim) >= 0
            && FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, minPackedValue, minOffset, minOffset + bytesPerDim) <= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, packedValue, minOffset, minOffset + bytesPerDim) <= 0
            && FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, packedValue, maxOffset, maxOffset + bytesPerDim) >= 0;
      }

    },
    CONTAINS {

      @Override
      Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue,
          int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, maxPackedValue, maxOffset, maxOffset + bytesPerDim) > 0
            || FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, minPackedValue, minOffset, minOffset + bytesPerDim) < 0) {
          // all ranges are either less than the query max or greater than the query min
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, minPackedValue, maxOffset, maxOffset + bytesPerDim) <= 0
            && FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, maxPackedValue, minOffset, minOffset + bytesPerDim) >= 0) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim, int dim) {
        int minOffset = dim * bytesPerDim;
        int maxOffset = minOffset + bytesPerDim * numDims;
        return FutureArrays.compareUnsigned(queryPackedValue, minOffset, minOffset + bytesPerDim, packedValue, minOffset, minOffset + bytesPerDim) >= 0
            && FutureArrays.compareUnsigned(queryPackedValue, maxOffset, maxOffset + bytesPerDim, packedValue, maxOffset, maxOffset + bytesPerDim) <= 0;
      }

    },
    CROSSES {

      @Override
      Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue,
          int numDims, int bytesPerDim, int dim) {
        throw new UnsupportedOperationException();
      }

      @Override
      boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim, int dim) {
        throw new UnsupportedOperationException();
      }

      @Override
      Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue,
          int numDims, int bytesPerDim) {
        Relation intersectRelation = QueryType.INTERSECTS.compare(queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim);
        if (intersectRelation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        }

        Relation withinRelation = QueryType.WITHIN.compare(queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim);
        if (withinRelation == Relation.CELL_INSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        }

        if (intersectRelation == Relation.CELL_INSIDE_QUERY && withinRelation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_INSIDE_QUERY;
        }

        return Relation.CELL_CROSSES_QUERY;
      }

      boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim) {
        return INTERSECTS.matches(queryPackedValue, packedValue, numDims, bytesPerDim)
            && WITHIN.matches(queryPackedValue, packedValue, numDims, bytesPerDim) == false;
      }

    };

    abstract Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue, int numDims, int bytesPerDim, int dim);

    Relation compare(byte[] queryPackedValue, byte[] minPackedValue, byte[] maxPackedValue, int numDims, int bytesPerDim) {
      boolean inside = true;
      for (int dim = 0; dim < numDims; ++dim) {
        Relation relation = compare(queryPackedValue, minPackedValue, maxPackedValue, numDims, bytesPerDim, dim);
        if (relation == Relation.CELL_OUTSIDE_QUERY) {
          return Relation.CELL_OUTSIDE_QUERY;
        } else if (relation != Relation.CELL_INSIDE_QUERY) {
          inside = false;
        }
      }
      return inside ? Relation.CELL_INSIDE_QUERY : Relation.CELL_CROSSES_QUERY;
    }

    abstract boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim, int dim);

    boolean matches(byte[] queryPackedValue, byte[] packedValue, int numDims, int bytesPerDim) {
      for (int dim = 0; dim < numDims; ++dim) {
        if (matches(queryPackedValue, packedValue, numDims, bytesPerDim, dim) == false) {
          return false;
        }
      }
      return true;
    }
  }

  RangeFieldQuery(String field, final byte[] ranges, final int numDims, final QueryType queryType) {
    checkArgs(field, ranges, numDims);
    if (queryType == null) {
      throw new IllegalArgumentException("Query type cannot be null");
    }
    this.field = field;
    this.queryType = queryType;
    this.numDims = numDims;
    this.ranges = ranges;
    this.bytesPerDim = ranges.length / (2*numDims);
  }

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

  private void checkFieldInfo(FieldInfo fieldInfo) {
    if (fieldInfo.getPointDataDimensionCount()/2 != numDims) {
      throw new IllegalArgumentException("field=\"" + field + "\" was indexed with numDims="
          + fieldInfo.getPointDataDimensionCount()/2 + " but this query has numDims=" + numDims);
    }
  }

  @Override
  public final Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
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
          public void visit(int docID, byte[] leaf) throws IOException {
            if (queryType.matches(ranges, leaf, numDims, bytesPerDim)) {
              adder.add(docID);
            }
          }
          @Override
          public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
            return queryType.compare(ranges, minPackedValue, maxPackedValue, numDims, bytesPerDim);
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
            && queryType.compare(ranges, values.getMinPackedValue(), values.getMaxPackedValue(), numDims, bytesPerDim) == Relation.CELL_INSIDE_QUERY) {
          allDocsMatch = true;
        }

        final Weight weight = this;
        if (allDocsMatch) {
          return new ScorerSupplier() {
            @Override
            public Scorer get(long leadCost) {
              return new ConstantScoreScorer(weight, score(), DocIdSetIterator.all(reader.maxDoc()));
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
              return new ConstantScoreScorer(weight, score(), iterator);
            }

            @Override
            public long cost() {
              if (cost == -1) {
                // Computing the cost may be expensive, so only do it if necessary
                cost = values.estimatePointCount(visitor);
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
    return sameClassAs(o) &&
        equalsTo(getClass().cast(o));
  }

  protected boolean equalsTo(RangeFieldQuery other) {
    return Objects.equals(field, other.field) &&
        numDims == other.numDims &&
        Arrays.equals(ranges, other.ranges) &&
        other.queryType == queryType;
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
    for (int d=1; d<numDims; ++d) {
      sb.append(' ');
      sb.append(toString(ranges, d));
    }
    sb.append('>');

    return sb.toString();
  }

  protected abstract String toString(byte[] ranges, int dimension);
}
