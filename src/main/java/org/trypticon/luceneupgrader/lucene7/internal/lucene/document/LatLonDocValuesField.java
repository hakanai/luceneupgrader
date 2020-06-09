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

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.decodeLatitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.decodeLongitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLatitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoEncodingUtils.encodeLongitude;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.FieldDoc;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.MatchNoDocsQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortField;

public class LatLonDocValuesField extends Field {

  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC);
    TYPE.freeze();
  }
  
  public LatLonDocValuesField(String name, double latitude, double longitude) {
    super(name, TYPE);
    setLocationValue(latitude, longitude);
  }
  
  public void setLocationValue(double latitude, double longitude) {
    int latitudeEncoded = encodeLatitude(latitude);
    int longitudeEncoded = encodeLongitude(longitude);
    fieldsData = Long.valueOf((((long)latitudeEncoded) << 32) | (longitudeEncoded & 0xFFFFFFFFL));
  }

  static void checkCompatible(FieldInfo fieldInfo) {
    // dv properties could be "unset", if you e.g. used only StoredField with this same name in the segment.
    if (fieldInfo.getDocValuesType() != DocValuesType.NONE && fieldInfo.getDocValuesType() != TYPE.docValuesType()) {
      throw new IllegalArgumentException("field=\"" + fieldInfo.name + "\" was indexed with docValuesType=" + fieldInfo.getDocValuesType() + 
                                         " but this type has docValuesType=" + TYPE.docValuesType() + 
                                         ", is the field really a LatLonDocValuesField?");
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
    result.append(decodeLatitude((int)(currentValue >> 32)));
    result.append(',');
    result.append(decodeLongitude((int)(currentValue & 0xFFFFFFFF)));

    result.append('>');
    return result.toString();
  }

  public static SortField newDistanceSort(String field, double latitude, double longitude) {
    return new LatLonPointSortField(field, latitude, longitude);
  }

  public static Query newSlowBoxQuery(String field, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
    // exact double values of lat=90.0D and lon=180.0D must be treated special as they are not represented in the encoding
    // and should not drag in extra bogus junk! TODO: should encodeCeil just throw ArithmeticException to be less trappy here?
    if (minLatitude == 90.0) {
      // range cannot match as 90.0 can never exist
      return new MatchNoDocsQuery("LatLonDocValuesField.newBoxQuery with minLatitude=90.0");
    }
    if (minLongitude == 180.0) {
      if (maxLongitude == 180.0) {
        // range cannot match as 180.0 can never exist
        return new MatchNoDocsQuery("LatLonDocValuesField.newBoxQuery with minLongitude=maxLongitude=180.0");
      } else if (maxLongitude < minLongitude) {
        // encodeCeil() with dateline wrapping!
        minLongitude = -180.0;
      }
    }
    return new LatLonDocValuesBoxQuery(field, minLatitude, maxLatitude, minLongitude, maxLongitude);
  }

  public static Query newSlowDistanceQuery(String field, double latitude, double longitude, double radiusMeters) {
    return new LatLonDocValuesDistanceQuery(field, latitude, longitude, radiusMeters);
  }
}
