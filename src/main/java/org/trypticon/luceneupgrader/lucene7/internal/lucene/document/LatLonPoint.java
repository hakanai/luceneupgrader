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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.Polygon;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.ConstantScoreQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.MatchNoDocsQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.PointRangeQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.NumericUtils;

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.decodeLatitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.decodeLongitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLatitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLatitudeCeil;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLongitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLongitudeCeil;

// TODO ^^^ that is very sandy and hurts the API, usage, and tests tremendously, because what the user passes
// to the field is not actually what gets indexed. Float would be 1E-5 error vs 1E-7, but it might be
// a better tradeoff? then it would be completely transparent to the user and lucene would be "lossless".
public class LatLonPoint extends Field {
  public static final int BYTES = Integer.BYTES;
  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDimensions(2, Integer.BYTES);
    TYPE.freeze();
  }
  
  public void setLocationValue(double latitude, double longitude) {
    final byte[] bytes;

    if (fieldsData == null) {
      bytes = new byte[8];
      fieldsData = new BytesRef(bytes);
    } else {
      bytes = ((BytesRef) fieldsData).bytes;
    }

    int latitudeEncoded = encodeLatitude(latitude);
    int longitudeEncoded = encodeLongitude(longitude);
    NumericUtils.intToSortableBytes(latitudeEncoded, bytes, 0);
    NumericUtils.intToSortableBytes(longitudeEncoded, bytes, Integer.BYTES);
  }

  public LatLonPoint(String name, double latitude, double longitude) {
    super(name, TYPE);
    setLocationValue(latitude, longitude);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(getClass().getSimpleName());
    result.append(" <");
    result.append(name);
    result.append(':');

    byte bytes[] = ((BytesRef) fieldsData).bytes;
    result.append(decodeLatitude(bytes, 0));
    result.append(',');
    result.append(decodeLongitude(bytes, Integer.BYTES));

    result.append('>');
    return result.toString();
  }
  
  private static byte[] encode(double latitude, double longitude) {
    byte[] bytes = new byte[2 * Integer.BYTES];
    NumericUtils.intToSortableBytes(encodeLatitude(latitude), bytes, 0);
    NumericUtils.intToSortableBytes(encodeLongitude(longitude), bytes, Integer.BYTES);
    return bytes;
  }
  
  private static byte[] encodeCeil(double latitude, double longitude) {
    byte[] bytes = new byte[2 * Integer.BYTES];
    NumericUtils.intToSortableBytes(encodeLatitudeCeil(latitude), bytes, 0);
    NumericUtils.intToSortableBytes(encodeLongitudeCeil(longitude), bytes, Integer.BYTES);
    return bytes;
  }

  static void checkCompatible(FieldInfo fieldInfo) {
    // point/dv properties could be "unset", if you e.g. used only StoredField with this same name in the segment.
    if (fieldInfo.getPointDataDimensionCount() != 0 && fieldInfo.getPointDataDimensionCount() != TYPE.pointDataDimensionCount()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with numDims=" + fieldInfo.getPointDataDimensionCount() +
          " but this point type has numDims=" + TYPE.pointDataDimensionCount() +
                                         ", is the field really a LatLonPoint?");
    }
    if (fieldInfo.getPointNumBytes() != 0 && fieldInfo.getPointNumBytes() != TYPE.pointNumBytes()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with bytesPerDim=" + fieldInfo.getPointNumBytes() + 
                                         " but this point type has bytesPerDim=" + TYPE.pointNumBytes() + 
                                         ", is the field really a LatLonPoint?");
    }
  }

  // static methods for generating queries

  public static Query newBoxQuery(String field, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    // exact double values of lat=90.0D and lon=180.0D must be treated special as they are not represented in the encoding
    // and should not drag in extra bogus junk! TODO: should encodeCeil just throw ArithmeticException to be less trappy here?
    if (minLatitude == 90.0) {
      // range cannot match as 90.0 can never exist
      return new MatchNoDocsQuery("LatLonPoint.newBoxQuery with minLatitude=90.0");
    }
    if (minLongitude == 180.0) {
      if (maxLongitude == 180.0) {
        // range cannot match as 180.0 can never exist
        return new MatchNoDocsQuery("LatLonPoint.newBoxQuery with minLongitude=maxLongitude=180.0");
      } else if (maxLongitude < minLongitude) {
        // encodeCeil() with dateline wrapping!
        minLongitude = -180.0;
      }
    }
    byte[] lower = encodeCeil(minLatitude, minLongitude);
    byte[] upper = encode(maxLatitude, maxLongitude);
    // Crosses date line: we just rewrite into OR of two bboxes, with longitude as an open range:
    if (maxLongitude < minLongitude) {
      // Disable coord here because a multi-valued doc could match both rects and get unfairly boosted:
      BooleanQuery.Builder q = new BooleanQuery.Builder();

      // E.g.: maxLon = -179, minLon = 179
      byte[] leftOpen = lower.clone();
      // leave longitude open
      NumericUtils.intToSortableBytes(Integer.MIN_VALUE, leftOpen, Integer.BYTES);
      Query left = newBoxInternal(field, leftOpen, upper);
      q.add(new BooleanClause(left, BooleanClause.Occur.SHOULD));

      byte[] rightOpen = upper.clone();
      // leave longitude open
      NumericUtils.intToSortableBytes(Integer.MAX_VALUE, rightOpen, Integer.BYTES);
      Query right = newBoxInternal(field, lower, rightOpen);
      q.add(new BooleanClause(right, BooleanClause.Occur.SHOULD));
      return new ConstantScoreQuery(q.build());
    } else {
      return newBoxInternal(field, lower, upper);
    }
  }
  
  private static Query newBoxInternal(String field, byte[] min, byte[] max) {
    return new PointRangeQuery(field, min, max, 2) {
      @Override
      protected String toString(int dimension, byte[] value) {
        if (dimension == 0) {
          return Double.toString(decodeLatitude(value, 0));
        } else if (dimension == 1) {
          return Double.toString(decodeLongitude(value, 0));
        } else {
          throw new AssertionError();
        }
      }
    };
  }
  
  public static Query newDistanceQuery(String field, double latitude, double longitude, double radiusMeters) {
    return new LatLonPointDistanceQuery(field, latitude, longitude, radiusMeters);
  }
  
  public static Query newPolygonQuery(String field, Polygon... polygons) {
    return new LatLonPointInPolygonQuery(field, polygons);
  }
}
