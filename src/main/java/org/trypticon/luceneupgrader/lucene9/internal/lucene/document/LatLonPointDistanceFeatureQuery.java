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
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoEncodingUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Rectangle;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.QueryVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScoreMode;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ScorerSupplier;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.DocIdSetBuilder;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.SloppyMath;

final class LatLonPointDistanceFeatureQuery extends Query {

  private final String field;
  private final double originLat;
  private final double originLon;
  private final double pivotDistance;

  LatLonPointDistanceFeatureQuery(
      String field, double originLat, double originLon, double pivotDistance) {
    this.field = Objects.requireNonNull(field);
    GeoUtils.checkLatitude(originLat);
    GeoUtils.checkLongitude(originLon);
    this.originLon = originLon;
    this.originLat = originLat;
    if (pivotDistance <= 0) {
      throw new IllegalArgumentException("pivotDistance must be > 0, got " + pivotDistance);
    }
    this.pivotDistance = pivotDistance;
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(field)) {
      visitor.visitLeaf(this);
    }
  }

  @Override
  public final boolean equals(Object o) {
    return sameClassAs(o) && equalsTo(getClass().cast(o));
  }

  private boolean equalsTo(LatLonPointDistanceFeatureQuery other) {
    return Objects.equals(field, other.field)
        && originLon == other.originLon
        && originLat == other.originLat
        && pivotDistance == other.pivotDistance;
  }

  @Override
  public int hashCode() {
    int h = classHash();
    h = 31 * h + field.hashCode();
    h = 31 * h + Double.hashCode(originLat);
    h = 31 * h + Double.hashCode(originLon);
    h = 31 * h + Double.hashCode(pivotDistance);
    return h;
  }

  @Override
  public String toString(String field) {
    return getClass().getSimpleName()
        + "(field="
        + field
        + ",originLat="
        + originLat
        + ",originLon="
        + originLon
        + ",pivotDistance="
        + pivotDistance
        + ")";
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return new Weight(this) {

      @Override
      public boolean isCacheable(LeafReaderContext ctx) {
        return false;
      }

      @Override
      public Explanation explain(LeafReaderContext context, int doc) throws IOException {
        SortedNumericDocValues multiDocValues = DocValues.getSortedNumeric(context.reader(), field);
        if (multiDocValues.advanceExact(doc) == false) {
          return Explanation.noMatch(
              "Document " + doc + " doesn't have a value for field " + field);
        }
        long encoded = selectValue(multiDocValues);
        int latitudeBits = (int) (encoded >> 32);
        int longitudeBits = (int) (encoded & 0xFFFFFFFF);
        double lat = GeoEncodingUtils.decodeLatitude(latitudeBits);
        double lon = GeoEncodingUtils.decodeLongitude(longitudeBits);
        double distance = SloppyMath.haversinMeters(originLat, originLon, lat, lon);
        float score = (float) (boost * (pivotDistance / (pivotDistance + distance)));
        return Explanation.match(
            score,
            "Distance score, computed as weight * pivotDistance / (pivotDistance + abs(distance)) from:",
            Explanation.match(boost, "weight"),
            Explanation.match(pivotDistance, "pivotDistance"),
            Explanation.match(originLat, "originLat"),
            Explanation.match(originLon, "originLon"),
            Explanation.match(lat, "current lat"),
            Explanation.match(lon, "current lon"),
            Explanation.match(distance, "distance"));
      }

      private long selectValue(SortedNumericDocValues multiDocValues) throws IOException {
        int count = multiDocValues.docValueCount();
        long value = multiDocValues.nextValue();
        if (count == 1) {
          return value;
        }
        // compute exact sort key: avoid any asin() computations
        double distance = getDistanceKeyFromEncoded(value);
        for (int i = 1; i < count; ++i) {
          long nextValue = multiDocValues.nextValue();
          double nextDistance = getDistanceKeyFromEncoded(nextValue);
          if (nextDistance < distance) {
            distance = nextDistance;
            value = nextValue;
          }
        }
        return value;
      }

      private NumericDocValues selectValues(SortedNumericDocValues multiDocValues) {
        final NumericDocValues singleton = DocValues.unwrapSingleton(multiDocValues);
        if (singleton != null) {
          return singleton;
        }
        return new NumericDocValues() {

          long value;

          @Override
          public long longValue() throws IOException {
            return value;
          }

          @Override
          public boolean advanceExact(int target) throws IOException {
            if (multiDocValues.advanceExact(target)) {
              value = selectValue(multiDocValues);
              return true;
            } else {
              return false;
            }
          }

          @Override
          public int docID() {
            return multiDocValues.docID();
          }

          @Override
          public int nextDoc() throws IOException {
            return multiDocValues.nextDoc();
          }

          @Override
          public int advance(int target) throws IOException {
            return multiDocValues.advance(target);
          }

          @Override
          public long cost() {
            return multiDocValues.cost();
          }
        };
      }

      @Override
      public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        PointValues pointValues = context.reader().getPointValues(field);
        if (pointValues == null) {
          // No data on this segment
          return null;
        }
        final SortedNumericDocValues multiDocValues =
            DocValues.getSortedNumeric(context.reader(), field);
        final NumericDocValues docValues = selectValues(multiDocValues);

        final Weight weight = this;
        return new ScorerSupplier() {

          @Override
          public Scorer get(long leadCost) throws IOException {
            return new DistanceScorer(
                weight, context.reader().maxDoc(), leadCost, boost, pointValues, docValues);
          }

          @Override
          public long cost() {
            return docValues.cost();
          }
        };
      }

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        ScorerSupplier scorerSupplier = scorerSupplier(context);
        if (scorerSupplier == null) {
          return null;
        }
        return scorerSupplier.get(Long.MAX_VALUE);
      }
    };
  }

  private double getDistanceFromEncoded(long encoded) {
    return SloppyMath.haversinMeters(getDistanceKeyFromEncoded(encoded));
  }

  private double getDistanceKeyFromEncoded(long encoded) {
    int latitudeBits = (int) (encoded >> 32);
    int longitudeBits = (int) (encoded & 0xFFFFFFFF);
    double lat = GeoEncodingUtils.decodeLatitude(latitudeBits);
    double lon = GeoEncodingUtils.decodeLongitude(longitudeBits);
    return SloppyMath.haversinSortKey(originLat, originLon, lat, lon);
  }

  private class DistanceScorer extends Scorer {

    private final int maxDoc;
    private DocIdSetIterator it;
    private int doc = -1;
    private final long leadCost;
    private final float boost;
    private final PointValues pointValues;
    private final NumericDocValues docValues;
    private double maxDistance = GeoUtils.EARTH_MEAN_RADIUS_METERS * Math.PI;

    protected DistanceScorer(
        Weight weight,
        int maxDoc,
        long leadCost,
        float boost,
        PointValues pointValues,
        NumericDocValues docValues) {
      super(weight);
      this.maxDoc = maxDoc;
      this.leadCost = leadCost;
      this.boost = boost;
      this.pointValues = pointValues;
      this.docValues = docValues;
      // initially use doc values in order to iterate all documents that have
      // a value for this field
      this.it = docValues;
    }

    @Override
    public int docID() {
      return doc;
    }

    private float score(double distance) {
      return (float) (boost * (pivotDistance / (pivotDistance + distance)));
    }

    /**
     * Inverting the score computation is very hard due to all potential rounding errors, so we
     * binary search the maximum distance. The limit is set to 1 meter.
     */
    private double computeMaxDistance(float minScore, double previousMaxDistance) {
      assert score(0) >= minScore;
      if (score(previousMaxDistance) >= minScore) {
        // minScore did not decrease enough to require an update to the max distance
        return previousMaxDistance;
      }
      assert score(previousMaxDistance) < minScore;
      double min = 0, max = previousMaxDistance;
      // invariant: score(min) >= minScore && score(max) < minScore
      while (max - min > 1) {
        double mid = (min + max) / 2;
        float score = score(mid);
        if (score >= minScore) {
          min = mid;
        } else {
          max = mid;
        }
      }
      assert score(min) >= minScore;
      assert min == Double.MAX_VALUE || score(min + 1) < minScore;
      return min;
    }

    @Override
    public float score() throws IOException {
      if (docValues.advanceExact(docID()) == false) {
        return 0;
      }
      return score(getDistanceFromEncoded(docValues.longValue()));
    }

    @Override
    public DocIdSetIterator iterator() {
      // add indirection so that if 'it' is updated then it will
      // be taken into account
      return new DocIdSetIterator() {

        @Override
        public int nextDoc() throws IOException {
          return doc = it.nextDoc();
        }

        @Override
        public int docID() {
          return doc;
        }

        @Override
        public long cost() {
          return it.cost();
        }

        @Override
        public int advance(int target) throws IOException {
          return doc = it.advance(target);
        }
      };
    }

    @Override
    public float getMaxScore(int upTo) {
      return boost;
    }

    private int setMinCompetitiveScoreCounter = 0;

    @Override
    public void setMinCompetitiveScore(float minScore) throws IOException {
      if (minScore > boost) {
        it = DocIdSetIterator.empty();
        return;
      }

      setMinCompetitiveScoreCounter++;
      // We sample the calls to this method as it is expensive to recalculate the iterator.
      if (setMinCompetitiveScoreCounter > 256 && (setMinCompetitiveScoreCounter & 0x1f) != 0x1f) {
        return;
      }

      double previousMaxDistance = maxDistance;
      maxDistance = computeMaxDistance(minScore, maxDistance);
      if (maxDistance == previousMaxDistance) {
        // nothing to update
        return;
      }

      // Ideally we would be doing a distance query but that is too expensive so we approximate
      // with a box query which performs better.
      Rectangle box = Rectangle.fromPointDistance(originLat, originLon, maxDistance);
      final int minLat = GeoEncodingUtils.encodeLatitude(box.minLat);
      final int maxLat = GeoEncodingUtils.encodeLatitude(box.maxLat);
      final int minLon = GeoEncodingUtils.encodeLongitude(box.minLon);
      final int maxLon = GeoEncodingUtils.encodeLongitude(box.maxLon);
      final boolean crossDateLine = box.crossesDateline();

      DocIdSetBuilder result = new DocIdSetBuilder(maxDoc);
      final int doc = docID();
      IntersectVisitor visitor =
          new IntersectVisitor() {

            DocIdSetBuilder.BulkAdder adder;

            @Override
            public void grow(int count) {
              adder = result.grow(count);
            }

            @Override
            public void visit(int docID) {
              if (docID <= doc) {
                // Already visited or skipped
                return;
              }
              adder.add(docID);
            }

            @Override
            public void visit(int docID, byte[] packedValue) {
              if (docID <= doc) {
                // Already visited or skipped
                return;
              }
              int lat = NumericUtils.sortableBytesToInt(packedValue, 0);
              if (lat > maxLat || lat < minLat) {
                // Latitude out of range
                return;
              }
              int lon = NumericUtils.sortableBytesToInt(packedValue, LatLonPoint.BYTES);
              if (crossDateLine) {
                if (lon < minLon && lon > maxLon) {
                  // Longitude out of range
                  return;
                }

              } else {
                if (lon > maxLon || lon < minLon) {
                  // Longitude out of range
                  return;
                }
              }
              adder.add(docID);
            }

            @Override
            public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              int latLowerBound = NumericUtils.sortableBytesToInt(minPackedValue, 0);
              int latUpperBound = NumericUtils.sortableBytesToInt(maxPackedValue, 0);
              if (latLowerBound > maxLat || latUpperBound < minLat) {
                return Relation.CELL_OUTSIDE_QUERY;
              }
              boolean crosses = latLowerBound < minLat || latUpperBound > maxLat;
              int lonLowerBound =
                  NumericUtils.sortableBytesToInt(minPackedValue, LatLonPoint.BYTES);
              int lonUpperBound =
                  NumericUtils.sortableBytesToInt(maxPackedValue, LatLonPoint.BYTES);
              if (crossDateLine) {
                if (lonLowerBound > maxLon && lonUpperBound < minLon) {
                  return Relation.CELL_OUTSIDE_QUERY;
                }
                crosses |= lonLowerBound < maxLon || lonUpperBound > minLon;

              } else {
                if (lonLowerBound > maxLon || lonUpperBound < minLon) {
                  return Relation.CELL_OUTSIDE_QUERY;
                }
                crosses |= lonLowerBound < minLon || lonUpperBound > maxLon;
              }
              if (crosses) {
                return Relation.CELL_CROSSES_QUERY;
              } else {
                return Relation.CELL_INSIDE_QUERY;
              }
            }
          };

      final long currentQueryCost = Math.min(leadCost, it.cost());
      // TODO: what is the right factor compared to the current disi? Is 8 optimal?
      final long threshold = currentQueryCost >>> 3;
      if (PointValues.isEstimatedPointCountGreaterThanOrEqualTo(
          visitor, pointValues.getPointTree(), threshold)) {
        // the new range is not selective enough to be worth materializing
        return;
      }
      pointValues.intersect(visitor);
      it = result.build().iterator();
    }
  }
}
