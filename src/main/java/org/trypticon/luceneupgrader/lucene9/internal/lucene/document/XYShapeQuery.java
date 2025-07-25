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

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYEncodingUtils.decode;

import java.util.function.Function;
import java.util.function.Predicate;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.ShapeField.QueryRelation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Component2D;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Geometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYEncodingUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.NumericUtils;

/**
 * Finds all previously indexed cartesian shapes that comply the given {@link QueryRelation} with
 * the specified array of {@link XYGeometry}.
 *
 * <p>The field must be indexed using {@link XYShape#createIndexableFields} added per document.
 *
 * @lucene.internal
 */
final class XYShapeQuery extends SpatialQuery {

  /** Creates a query that matches all indexed shapes to the provided polygons */
  XYShapeQuery(String field, QueryRelation queryRelation, XYGeometry... geometries) {
    super(field, queryRelation, geometries);
  }

  @Override
  protected Component2D createComponent2D(Geometry... geometries) {
    return XYGeometry.create((XYGeometry[]) geometries);
  }

  static SpatialVisitor getSpatialVisitor(Component2D component2D) {
    return new SpatialVisitor() {
      @Override
      protected Relation relate(byte[] minTriangle, byte[] maxTriangle) {

        double minY = XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(minTriangle, 0));
        double minX =
            XYEncodingUtils.decode(NumericUtils.sortableBytesToInt(minTriangle, ShapeField.BYTES));
        double maxY =
            XYEncodingUtils.decode(
                NumericUtils.sortableBytesToInt(maxTriangle, 2 * ShapeField.BYTES));
        double maxX =
            XYEncodingUtils.decode(
                NumericUtils.sortableBytesToInt(maxTriangle, 3 * ShapeField.BYTES));

        // check internal node against query
        return component2D.relate(minX, maxX, minY, maxY);
      }

      @Override
      protected Predicate<byte[]> intersects() {
        final ShapeField.DecodedTriangle scratchTriangle = new ShapeField.DecodedTriangle();
        return triangle -> {
          ShapeField.decodeTriangle(triangle, scratchTriangle);

          switch (scratchTriangle.type) {
            case POINT:
              {
                double y = decode(scratchTriangle.aY);
                double x = decode(scratchTriangle.aX);
                return component2D.contains(x, y);
              }
            case LINE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                return component2D.intersectsLine(aX, aY, bX, bY);
              }
            case TRIANGLE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                double cY = decode(scratchTriangle.cY);
                double cX = decode(scratchTriangle.cX);
                return component2D.intersectsTriangle(aX, aY, bX, bY, cX, cY);
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
                double y = decode(scratchTriangle.aY);
                double x = decode(scratchTriangle.aX);
                return component2D.contains(x, y);
              }
            case LINE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                return component2D.containsLine(aX, aY, bX, bY);
              }
            case TRIANGLE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                double cY = decode(scratchTriangle.cY);
                double cX = decode(scratchTriangle.cX);
                return component2D.containsTriangle(aX, aY, bX, bY, cX, cY);
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
                double y = decode(scratchTriangle.aY);
                double x = decode(scratchTriangle.aX);
                return component2D.withinPoint(x, y);
              }
            case LINE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                return component2D.withinLine(aX, aY, scratchTriangle.ab, bX, bY);
              }
            case TRIANGLE:
              {
                double aY = decode(scratchTriangle.aY);
                double aX = decode(scratchTriangle.aX);
                double bY = decode(scratchTriangle.bY);
                double bX = decode(scratchTriangle.bX);
                double cY = decode(scratchTriangle.cY);
                double cX = decode(scratchTriangle.cX);
                return component2D.withinTriangle(
                    aX,
                    aY,
                    scratchTriangle.ab,
                    bX,
                    bY,
                    scratchTriangle.bc,
                    cX,
                    cY,
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
