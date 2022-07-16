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

import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYCircle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYEncodingUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYGeometry;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYPolygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYRectangle;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.FieldDoc;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.SortField;


public class XYDocValuesField extends Field {

  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC);
    TYPE.freeze();
  }

  public XYDocValuesField(String name, float x, float y) {
    super(name, TYPE);
    setLocationValue(x, y);
  }
  
  public void setLocationValue(float x, float y) {
    int xEncoded = XYEncodingUtils.encode(x);
    int yEncoded = XYEncodingUtils.encode(y);
    fieldsData = Long.valueOf((((long) xEncoded) << 32) | (yEncoded & 0xFFFFFFFFL));
  }

  static void checkCompatible(FieldInfo fieldInfo) {
    // dv properties could be "unset", if you e.g. used only StoredField with this same name in the segment.
    if (fieldInfo.getDocValuesType() != DocValuesType.NONE && fieldInfo.getDocValuesType() != TYPE.docValuesType()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with docValuesType=" + fieldInfo.getDocValuesType() + 
                                         " but this type has docValuesType=" + TYPE.docValuesType() + 
                                         ", is the field really a XYDocValuesField?");
    }
  }
  
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(getClass().getSimpleName());
    result.append(" <");
    result.append(name);
    result.append(':');

    long currentValue = (Long)fieldsData;
    result.append(XYEncodingUtils.decode((int)(currentValue >> 32)));
    result.append(',');
    result.append(XYEncodingUtils.decode((int)(currentValue & 0xFFFFFFFF)));

    result.append('>');
    return result.toString();
  }

  public static SortField newDistanceSort(String field, float x, float y) {
    return new XYPointSortField(field, x, y);
  }

  public static Query newSlowBoxQuery(String field, float minX, float maxX, float minY, float maxY) {
    XYRectangle rectangle = new XYRectangle(minX, maxX, minY, maxY);
    return new XYDocValuesPointInGeometryQuery(field, rectangle);
  }

  public static Query newSlowDistanceQuery(String field, float x, float y, float radius) {
    XYCircle circle = new XYCircle(x, y, radius);
    return new XYDocValuesPointInGeometryQuery(field, circle);
  }

  public static Query newSlowPolygonQuery(String field, XYPolygon... polygons) {
    return newSlowGeometryQuery(field, polygons);
  }

  public static Query newSlowGeometryQuery(String field, XYGeometry... geometries) {
    return new XYDocValuesPointInGeometryQuery(field, geometries);
  }
}
