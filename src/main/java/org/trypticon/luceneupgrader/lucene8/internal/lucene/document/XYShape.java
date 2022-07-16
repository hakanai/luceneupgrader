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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.document;

import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.ShapeField.QueryRelation; // javadoc
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.ShapeField.Triangle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Tessellator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYCircle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYRectangle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PointValues; // javadoc
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYLine;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYPolygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.ConstantScoreQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYEncodingUtils.encode;

public class XYShape {

  // no instance:
  private XYShape() {
  }

  public static Field[] createIndexableFields(String fieldName, XYPolygon polygon) {

    List<Tessellator.Triangle> tessellation = Tessellator.tessellate(polygon);
    List<Triangle> fields = new ArrayList<>(tessellation.size());
    for (Tessellator.Triangle t : tessellation) {
      fields.add(new Triangle(fieldName, t));
    }
    return fields.toArray(new Field[fields.size()]);
  }

  public static Field[] createIndexableFields(String fieldName, XYLine line) {
    int numPoints = line.numPoints();
    Field[] fields = new Field[numPoints - 1];
    // create "flat" triangles
    for (int i = 0, j = 1; j < numPoints; ++i, ++j) {
      fields[i] = new Triangle(fieldName,
          encode(line.getX(i)), encode(line.getY(i)),
          encode(line.getX(j)), encode(line.getY(j)),
          encode(line.getX(i)), encode(line.getY(i)));
    }
    return fields;
  }

  public static Field[] createIndexableFields(String fieldName, float x, float y) {
    return new Field[] {new Triangle(fieldName,
        encode(x), encode(y), encode(x), encode(y), encode(x), encode(y))};
  }

  public static Query newBoxQuery(String field, QueryRelation queryRelation, float minX, float maxX, float minY, float maxY) {
    XYRectangle rectangle = new XYRectangle(minX, maxX, minY, maxY);
    return newGeometryQuery(field, queryRelation, rectangle);
  }

  public static Query newLineQuery(String field, QueryRelation queryRelation, XYLine... lines) {
    return newGeometryQuery(field, queryRelation, lines);
  }

  public static Query newPolygonQuery(String field, QueryRelation queryRelation, XYPolygon... polygons) {
    return newGeometryQuery(field, queryRelation, polygons);
  }

  public static Query newPointQuery(String field, QueryRelation queryRelation, float[]... points) {
    XYPoint[] pointArray = new XYPoint[points.length];
    for (int i =0; i < points.length; i++) {
      pointArray[i] = new XYPoint(points[i][0], points[i][1]);
    }
    return newGeometryQuery(field, queryRelation, pointArray);
  }

  public static Query newDistanceQuery(String field, QueryRelation queryRelation, XYCircle... circle) {
    return newGeometryQuery(field, queryRelation, circle);
  }

  public static Query newGeometryQuery(String field, QueryRelation queryRelation, XYGeometry... xyGeometries) {
    if (queryRelation == QueryRelation.CONTAINS && xyGeometries.length > 1) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      for (int i = 0; i < xyGeometries.length; i++) {
        builder.add(newGeometryQuery(field, queryRelation, xyGeometries[i]), BooleanClause.Occur.MUST);
      }
      return new ConstantScoreQuery(builder.build());
    }
    return new XYShapeQuery(field, queryRelation, xyGeometries);
  }
}
