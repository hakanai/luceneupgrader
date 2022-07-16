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
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Circle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.GeoUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.LatLonGeometry;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Line;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Point;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Polygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Rectangle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Tessellator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PointValues; // javadoc
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.ConstantScoreQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.GeoEncodingUtils.encodeLatitude;
import static org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.GeoEncodingUtils.encodeLongitude;

public class LatLonShape {

  // no instance:
  private LatLonShape() {
  }

  public static Field[] createIndexableFields(String fieldName, Polygon polygon) {
    // the lionshare of the indexing is done by the tessellator
    List<Tessellator.Triangle> tessellation = Tessellator.tessellate(polygon);
    List<Triangle> fields = new ArrayList<>();
    for (Tessellator.Triangle t : tessellation) {
      fields.add(new Triangle(fieldName, t));
    }
    return fields.toArray(new Field[fields.size()]);
  }

  public static Field[] createIndexableFields(String fieldName, Line line) {
    int numPoints = line.numPoints();
    Field[] fields = new Field[numPoints - 1];
    // create "flat" triangles
    for (int i = 0, j = 1; j < numPoints; ++i, ++j) {
      fields[i] = new Triangle(fieldName,
          encodeLongitude(line.getLon(i)), encodeLatitude(line.getLat(i)),
          encodeLongitude(line.getLon(j)), encodeLatitude(line.getLat(j)),
          encodeLongitude(line.getLon(i)), encodeLatitude(line.getLat(i)));
    }
    return fields;
  }

  public static Field[] createIndexableFields(String fieldName, double lat, double lon) {
    return new Field[] {new Triangle(fieldName,
        encodeLongitude(lon), encodeLatitude(lat),
        encodeLongitude(lon), encodeLatitude(lat),
        encodeLongitude(lon), encodeLatitude(lat))};
  }

  public static Query newBoxQuery(String field, QueryRelation queryRelation, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    if (queryRelation == QueryRelation.CONTAINS && minLongitude > maxLongitude) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.add(newBoxQuery(field, queryRelation, minLatitude, maxLatitude, minLongitude, GeoUtils.MAX_LON_INCL), BooleanClause.Occur.MUST);
      builder.add(newBoxQuery(field, queryRelation, minLatitude, maxLatitude, GeoUtils.MIN_LON_INCL, maxLongitude), BooleanClause.Occur.MUST);
      return builder.build();
    }
    Rectangle rectangle = new Rectangle(minLatitude, maxLatitude, minLongitude, maxLongitude);
    return new LatLonShapeBoundingBoxQuery(field, queryRelation, rectangle);
  }

  public static Query newLineQuery(String field, QueryRelation queryRelation, Line... lines) {
    return newGeometryQuery(field, queryRelation, lines);
  }

  public static Query newPolygonQuery(String field, QueryRelation queryRelation, Polygon... polygons) {
    return newGeometryQuery(field, queryRelation, polygons);
  }

  public static Query newPointQuery(String field, QueryRelation queryRelation, double[]... points) {
    Point[] pointArray = new Point[points.length];
    for (int i =0; i < points.length; i++) {
      pointArray[i] = new Point(points[i][0], points[i][1]);
    }
    return newGeometryQuery(field, queryRelation, pointArray);
  }

  public static Query newDistanceQuery(String field, QueryRelation queryRelation, Circle... circle) {
    return newGeometryQuery(field, queryRelation, circle);
  }

  public static Query newGeometryQuery(String field, QueryRelation queryRelation, LatLonGeometry... latLonGeometries) {
    if  (latLonGeometries.length == 1) {
      LatLonGeometry geometry = latLonGeometries[0];
      if (geometry instanceof Rectangle) {
        Rectangle rect = (Rectangle) geometry;
        return newBoxQuery(field, queryRelation, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon);
      } else {
        return new LatLonShapeQuery(field, queryRelation, latLonGeometries);
      }
    } else {
      if (queryRelation == QueryRelation.CONTAINS) {
        return makeContainsGeometryQuery(field, latLonGeometries);
      } else {
        return new LatLonShapeQuery(field, queryRelation, latLonGeometries);
      }
    }
  }

  private static Query makeContainsGeometryQuery(String field, LatLonGeometry... latLonGeometries) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (LatLonGeometry geometry : latLonGeometries) {
      if (geometry instanceof Rectangle) {
        // this handles rectangles across the dateline
        Rectangle rect = (Rectangle) geometry;
        builder.add(newBoxQuery(field, QueryRelation.CONTAINS, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon), BooleanClause.Occur.MUST);
      } else {
        builder.add(new LatLonShapeQuery(field, QueryRelation.CONTAINS, geometry), BooleanClause.Occur.MUST);
      }
    }
    return new ConstantScoreQuery(builder.build());
  }

}
