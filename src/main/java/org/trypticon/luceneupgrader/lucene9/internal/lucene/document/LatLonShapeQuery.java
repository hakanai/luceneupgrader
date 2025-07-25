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

import java.util.function.Function;
import java.util.function.Predicate;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.ShapeField.QueryRelation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Component2D;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoEncodingUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Geometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.LatLonGeometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Line;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.NumericUtils;

/**
 * Finds all previously indexed geo shapes that comply the given {@link QueryRelation} with the
 * specified array of {@link LatLonGeometry}.
 *
 * <p>The field must be indexed using {@link LatLonShape#createIndexableFields} added per document.
 *
 * @lucene.internal
 */
final class LatLonShapeQuery extends SpatialQuery {

  /**
   * Creates a query that matches all indexed shapes to the provided array of {@link LatLonGeometry}
   */
  LatLonShapeQuery(String field, QueryRelation queryRelation, LatLonGeometry... geometries) {
    super(field, queryRelation, validateGeometries(queryRelation, geometries));
  }

  private static LatLonGeometry[] validateGeometries(
      QueryRelation queryRelation, LatLonGeometry... geometries) {
    if (geometries != null && queryRelation == QueryRelation.WITHIN) {
      for (LatLonGeometry geometry : geometries) {
        if (geometry instanceof Line) {
          // TODO: line queries do not support within relations
          throw new IllegalArgumentException(
              "LatLonShapeQuery does not support "
                  + QueryRelation.WITHIN
                  + " queries with line geometries");
        }
      }
    }
    return geometries;
  }

  @Override
  protected Component2D createComponent2D(Geometry... geometries) {
    return LatLonGeometry.create((LatLonGeometry[]) geometries);
  }

  static SpatialVisitor getSpatialVisitor(Component2D component2D) {
    return new SpatialVisitor() {
      @Override
      protected Relation relate(byte[] minTriangle, byte[] maxTriangle) {
        double minLat =
            GeoEncodingUtils.decodeLatitude(NumericUtils.sortableBytesToInt(minTriangle, 0));
        double minLon =
            GeoEncodingUtils.decodeLongitude(
                NumericUtils.sortableBytesToInt(minTriangle, ShapeField.BYTES));
        double maxLat =
            GeoEncodingUtils.decodeLatitude(
                NumericUtils.sortableBytesToInt(maxTriangle, 2 * ShapeField.BYTES));
        double maxLon =
            GeoEncodingUtils.decodeLongitude(
                NumericUtils.sortableBytesToInt(maxTriangle, 3 * ShapeField.BYTES));

        // check internal node against query
        return component2D.relate(minLon, maxLon, minLat, maxLat);
      }

      @Override
      protected Predicate<byte[]> intersects() {
        final ShapeField.DecodedTriangle scratchTriangle = new ShapeField.DecodedTriangle();
        return triangle -> {
          ShapeField.decodeTriangle(triangle, scratchTriangle);

          switch (scratchTriangle.type) {
            case POINT:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                return component2D.contains(alon, alat);
              }
            case LINE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                return component2D.intersectsLine(alon, alat, blon, blat);
              }
            case TRIANGLE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                double clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY);
                double clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX);
                return component2D.intersectsTriangle(alon, alat, blon, blat, clon, clat);
              }
            default:
              throw new IllegalArgumentException(
                  "Unsupported triangle type :[" + scratchTriangle.type + "]");
          }
        };
      }

      @Override
      protected Predicate<byte[]> within() {
        final ShapeField.DecodedTriangle scratchTriangle = new ShapeField.DecodedTriangle();
        return triangle -> {
          ShapeField.decodeTriangle(triangle, scratchTriangle);

          switch (scratchTriangle.type) {
            case POINT:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                return component2D.contains(alon, alat);
              }
            case LINE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                return component2D.containsLine(alon, alat, blon, blat);
              }
            case TRIANGLE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                double clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY);
                double clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX);
                return component2D.containsTriangle(alon, alat, blon, blat, clon, clat);
              }
            default:
              throw new IllegalArgumentException(
                  "Unsupported triangle type :[" + scratchTriangle.type + "]");
          }
        };
      }

      @Override
      protected Function<byte[], Component2D.WithinRelation> contains() {
        final ShapeField.DecodedTriangle scratchTriangle = new ShapeField.DecodedTriangle();
        return triangle -> {
          ShapeField.decodeTriangle(triangle, scratchTriangle);

          switch (scratchTriangle.type) {
            case POINT:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                return component2D.withinPoint(alon, alat);
              }
            case LINE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                return component2D.withinLine(alon, alat, scratchTriangle.ab, blon, blat);
              }
            case TRIANGLE:
              {
                double alat = GeoEncodingUtils.decodeLatitude(scratchTriangle.aY);
                double alon = GeoEncodingUtils.decodeLongitude(scratchTriangle.aX);
                double blat = GeoEncodingUtils.decodeLatitude(scratchTriangle.bY);
                double blon = GeoEncodingUtils.decodeLongitude(scratchTriangle.bX);
                double clat = GeoEncodingUtils.decodeLatitude(scratchTriangle.cY);
                double clon = GeoEncodingUtils.decodeLongitude(scratchTriangle.cX);
                return component2D.withinTriangle(
                    alon,
                    alat,
                    scratchTriangle.ab,
                    blon,
                    blat,
                    scratchTriangle.bc,
                    clon,
                    clat,
                    scratchTriangle.ca);
              }
            default:
              throw new IllegalArgumentException(
                  "Unsupported triangle type :[" + scratchTriangle.type + "]");
          }
        };
      }
    };
  }

  @Override
  protected SpatialVisitor getSpatialVisitor() {
    return getSpatialVisitor(queryComponent2D);
  }
}
