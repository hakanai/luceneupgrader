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

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYEncodingUtils.encode;

import java.util.ArrayList;
import java.util.List;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.ShapeField.QueryRelation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.ShapeField.Triangle;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Tessellator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYCircle;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYLine;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYPoint;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYPolygon;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.XYRectangle;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues; // javadoc
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.ConstantScoreQuery;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;

/**
 * A cartesian shape utility class for indexing and searching geometries whose vertices are unitless
 * x, y values.
 *
 * <p>This class defines seven static factory methods for common indexing and search operations:
 *
 * <ul>
 *   <li>{@link #createIndexableFields(String, XYPolygon)} for indexing a cartesian polygon.
 *   <li>{@link #createDocValueField(String, XYPolygon)} for indexing a cartesian polygon doc value
 *       field.
 *   <li>{@link #createIndexableFields(String, XYPolygon, boolean)} for indexing a cartesian polygon
 *       with the possibility of checking for self-intersections.
 *   <li>{@link #createIndexableFields(String, XYPolygon, boolean)} for indexing a cartesian polygon
 *       doc value field with the possibility of checking for self-intersections.
 *   <li>{@link #createIndexableFields(String, XYLine)} for indexing a cartesian linestring.
 *   <li>{@link #createDocValueField(String, XYLine)} for indexing a cartesian linestring doc value.
 *   <li>{@link #createIndexableFields(String, float, float)} for indexing a x, y cartesian point.
 *   <li>{@link #createDocValueField(String, float, float)} for indexing a x, y cartesian point doc
 *       value.
 *   <li>{@link #createDocValueField(String, BytesRef)} for indexing a cartesian doc value from
 *       existing encoding.
 *   <li>{@link #newBoxQuery newBoxQuery()} for matching cartesian shapes that have some {@link
 *       QueryRelation} with a bounding box.
 *   <li>{@link #newBoxQuery newLineQuery()} for matching cartesian shapes that have some {@link
 *       QueryRelation} with a linestring.
 *   <li>{@link #newBoxQuery newPolygonQuery()} for matching cartesian shapes that have some {@link
 *       QueryRelation} with a polygon.
 *   <li>{@link #newGeometryQuery newGeometryQuery()} for matching cartesian shapes that have some
 *       {@link QueryRelation} with one or more {@link XYGeometry}.
 *   <li>{@link #createXYShapeDocValues(BytesRef)} for creating the {@link XYShapeDocValues}
 * </ul>
 *
 * <b>WARNING</b>: Like {@link LatLonPoint}, vertex values are indexed with some loss of precision
 * from the original {@code double} values.
 *
 * @see PointValues
 * @see XYDocValuesField
 */
public class XYShape {

  // no instance:
  private XYShape() {}

  /** create indexable fields for cartesian polygon geometry */
  public static Field[] createIndexableFields(String fieldName, XYPolygon polygon) {
    return createIndexableFields(fieldName, polygon, false);
  }

  /** create doc value field for X,Y polygon geometry without creating indexable fields */
  public static XYShapeDocValuesField createDocValueField(String fieldName, XYPolygon polygon) {
    return createDocValueField(fieldName, polygon, false);
  }

  /**
   * create indexable fields for cartesian polygon geometry. If {@code checkSelfIntersections} is
   * set to true, the validity of the provided polygon is checked with a small performance penalty.
   */
  public static Field[] createIndexableFields(
      String fieldName, XYPolygon polygon, boolean checkSelfIntersections) {

    List<Tessellator.Triangle> tessellation =
        Tessellator.tessellate(polygon, checkSelfIntersections);
    Triangle[] fields = new Triangle[tessellation.size()];
    for (int i = 0; i < tessellation.size(); i++) {
      fields[i] = new Triangle(fieldName, tessellation.get(i));
    }
    return fields;
  }

  /** create doc value field for lat lon polygon geometry without creating indexable fields. */
  public static XYShapeDocValuesField createDocValueField(
      String fieldName, XYPolygon polygon, boolean checkSelfIntersections) {
    List<Tessellator.Triangle> tessellation =
        Tessellator.tessellate(polygon, checkSelfIntersections);
    ArrayList<ShapeField.DecodedTriangle> triangles = new ArrayList<>(tessellation.size());
    for (Tessellator.Triangle t : tessellation) {
      ShapeField.DecodedTriangle dt = new ShapeField.DecodedTriangle();
      dt.type = ShapeField.DecodedTriangle.TYPE.TRIANGLE;
      dt.setValues(
          t.getEncodedX(0),
          t.getEncodedY(0),
          t.isEdgefromPolygon(0),
          t.getEncodedX(1),
          t.getEncodedY(1),
          t.isEdgefromPolygon(0),
          t.getEncodedX(2),
          t.getEncodedY(2),
          t.isEdgefromPolygon(2));
      triangles.add(dt);
    }
    return new XYShapeDocValuesField(fieldName, triangles);
  }

  /** create indexable fields for cartesian line geometry */
  public static Field[] createIndexableFields(String fieldName, XYLine line) {
    int numPoints = line.numPoints();
    Field[] fields = new Field[numPoints - 1];
    // create "flat" triangles
    for (int i = 0, j = 1; j < numPoints; ++i, ++j) {
      fields[i] =
          new Triangle(
              fieldName,
              encode(line.getX(i)),
              encode(line.getY(i)),
              encode(line.getX(j)),
              encode(line.getY(j)),
              encode(line.getX(i)),
              encode(line.getY(i)));
    }
    return fields;
  }

  /** create doc value field for x, y line geometry without creating indexable fields. */
  public static XYShapeDocValuesField createDocValueField(String fieldName, XYLine line) {
    int numPoints = line.numPoints();
    List<ShapeField.DecodedTriangle> triangles = new ArrayList<>(numPoints - 1);
    // create "flat" triangles
    for (int i = 0, j = 1; j < numPoints; ++i, ++j) {
      ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
      t.type = ShapeField.DecodedTriangle.TYPE.LINE;
      t.setValues(
          encode(line.getX(i)),
          encode(line.getY(i)),
          true,
          encode(line.getX(j)),
          encode(line.getY(j)),
          true,
          encode(line.getX(i)),
          encode(line.getY(i)),
          true);
      triangles.add(t);
    }
    return new XYShapeDocValuesField(fieldName, triangles);
  }

  /** create indexable fields for cartesian point geometry */
  public static Field[] createIndexableFields(String fieldName, float x, float y) {
    return new Field[] {
      new Triangle(fieldName, encode(x), encode(y), encode(x), encode(y), encode(x), encode(y))
    };
  }

  /**
   * create a {@link XYShapeDocValuesField} for cartesian points without creating indexable fields.
   */
  public static XYShapeDocValuesField createDocValueField(String fieldName, float x, float y) {
    List<ShapeField.DecodedTriangle> triangles = new ArrayList<>(1);
    ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
    t.type = ShapeField.DecodedTriangle.TYPE.POINT;
    t.setValues(encode(x), encode(y), true, encode(x), encode(y), true, encode(x), encode(y), true);
    triangles.add(t);
    return new XYShapeDocValuesField(fieldName, triangles);
  }

  /** create a {@link XYShapeDocValuesField} from an existing encoded representation */
  public static XYShapeDocValuesField createDocValueField(String fieldName, BytesRef binaryValue) {
    return new XYShapeDocValuesField(fieldName, binaryValue);
  }

  /** create a {@link XYShapeDocValuesField} from a precomputed tessellation */
  public static XYShapeDocValuesField createDocValueField(
      String fieldName, List<ShapeField.DecodedTriangle> tessellation) {
    return new XYShapeDocValuesField(fieldName, tessellation);
  }

  /** create a query to find all cartesian shapes that intersect a defined bounding box * */
  public static Query newBoxQuery(
      String field, QueryRelation queryRelation, float minX, float maxX, float minY, float maxY) {
    XYRectangle rectangle = new XYRectangle(minX, maxX, minY, maxY);
    return newGeometryQuery(field, queryRelation, rectangle);
  }

  /**
   * create a docvalue query to find all cartesian shapes that intersect a defined bounding box *
   */
  public static Query newSlowDocValuesBoxQuery(
      String field, QueryRelation queryRelation, float minX, float maxX, float minY, float maxY) {
    return new XYShapeDocValuesQuery(field, queryRelation, new XYRectangle(minX, maxX, minY, maxY));
  }

  /**
   * create a query to find all cartesian shapes that intersect a provided linestring (or array of
   * linestrings) *
   */
  public static Query newLineQuery(String field, QueryRelation queryRelation, XYLine... lines) {
    return newGeometryQuery(field, queryRelation, lines);
  }

  /**
   * create a query to find all cartesian shapes that intersect a provided polygon (or array of
   * polygons) *
   */
  public static Query newPolygonQuery(
      String field, QueryRelation queryRelation, XYPolygon... polygons) {
    return newGeometryQuery(field, queryRelation, polygons);
  }

  /**
   * create a query to find all indexed shapes that comply the {@link QueryRelation} with the
   * provided point
   */
  public static Query newPointQuery(String field, QueryRelation queryRelation, float[]... points) {
    XYPoint[] pointArray = new XYPoint[points.length];
    for (int i = 0; i < points.length; i++) {
      pointArray[i] = new XYPoint(points[i][0], points[i][1]);
    }
    return newGeometryQuery(field, queryRelation, pointArray);
  }

  /**
   * create a query to find all cartesian shapes that intersect a provided circle (or arrays of
   * circles) *
   */
  public static Query newDistanceQuery(
      String field, QueryRelation queryRelation, XYCircle... circle) {
    return newGeometryQuery(field, queryRelation, circle);
  }

  /**
   * create a query to find all indexed geo shapes that intersect a provided geometry collection
   * note: Components do not support dateline crossing
   */
  public static Query newGeometryQuery(
      String field, QueryRelation queryRelation, XYGeometry... xyGeometries) {
    if (queryRelation == QueryRelation.CONTAINS && xyGeometries.length > 1) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      for (int i = 0; i < xyGeometries.length; i++) {
        builder.add(
            newGeometryQuery(field, queryRelation, xyGeometries[i]), BooleanClause.Occur.MUST);
      }
      return new ConstantScoreQuery(builder.build());
    }
    return new XYShapeQuery(field, queryRelation, xyGeometries);
  }

  /**
   * Factory method for creating the {@link XYShapeDocValues}
   *
   * @param bytesRef {@link BytesRef}
   * @return {@link XYShapeDocValues}
   */
  public static XYShapeDocValues createXYShapeDocValues(BytesRef bytesRef) {
    return new XYShapeDocValues(bytesRef);
  }
}
