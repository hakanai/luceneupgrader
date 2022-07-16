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

import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Polygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYCircle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYEncodingUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYPolygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYRectangle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;



public class XYPointField extends Field {
  public static final int BYTES = Integer.BYTES;
  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDimensions(2, Integer.BYTES);
    TYPE.freeze();
  }

  public void setLocationValue(float x, float y) {
    final byte[] bytes;
    if (fieldsData == null) {
      bytes = new byte[8];
      fieldsData = new BytesRef(bytes);
    } else {
      bytes = ((BytesRef) fieldsData).bytes;
    }
    int xEncoded = XYEncodingUtils.encode(x);
    int yEncoded = XYEncodingUtils.encode(y);
    NumericUtils.intToSortableBytes(xEncoded, bytes, 0);
    NumericUtils.intToSortableBytes(yEncoded, bytes, Integer.BYTES);
  }

  public XYPointField(String name, float x, float y) {
    super(name, TYPE);
    setLocationValue(x, y);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(getClass().getSimpleName());
    result.append(" <");
    result.append(name);
    result.append(':');

    byte bytes[] = ((BytesRef) fieldsData).bytes;
    result.append(XYEncodingUtils.decode(bytes, 0));
    result.append(',');
    result.append(XYEncodingUtils.decode(bytes, Integer.BYTES));

    result.append('>');
    return result.toString();
  }


  static void checkCompatible(FieldInfo fieldInfo) {
    // point/dv properties could be "unset", if you e.g. used only StoredField with this same name in the segment.
    if (fieldInfo.getPointDimensionCount() != 0 && fieldInfo.getPointDimensionCount() != TYPE.pointDimensionCount()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with numDims=" + fieldInfo.getPointDimensionCount() +
          " but this point type has numDims=" + TYPE.pointDimensionCount() +
                                         ", is the field really a XYPoint?");
    }
    if (fieldInfo.getPointNumBytes() != 0 && fieldInfo.getPointNumBytes() != TYPE.pointNumBytes()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with bytesPerDim=" + fieldInfo.getPointNumBytes() +
                                         " but this point type has bytesPerDim=" + TYPE.pointNumBytes() +
                                         ", is the field really a XYPoint?");
    }
  }

  // static methods for generating queries

  public static Query newBoxQuery(String field, float minX, float maxX, float minY, float maxY) {
    XYRectangle rectangle = new XYRectangle(minX, maxX, minY, maxY);
    return new XYPointInGeometryQuery(field, rectangle);
  }

  public static Query newDistanceQuery(String field, float x, float y, float radius) {
    XYCircle circle = new XYCircle(x, y, radius);
    return new XYPointInGeometryQuery(field, circle);
  }
  
  public static Query newPolygonQuery(String field, XYPolygon... polygons) {
    return newGeometryQuery(field, polygons);
  }

  public static Query newGeometryQuery(String field, XYGeometry... xyGeometries) {
    return new XYPointInGeometryQuery(field, xyGeometries);
  }
}
