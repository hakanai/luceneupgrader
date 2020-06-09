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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.geo;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.checkLatitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.checkLongitude;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MAX_LAT_INCL;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MIN_LAT_INCL;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MAX_LAT_RADIANS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MAX_LON_RADIANS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MIN_LAT_RADIANS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.MIN_LON_RADIANS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.EARTH_MEAN_RADIUS_METERS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.geo.GeoUtils.sloppySin;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.TO_DEGREES;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.asin;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.cos;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.toDegrees;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.toRadians;

public class Rectangle {
  public final double minLat;
  public final double minLon;
  public final double maxLat;
  public final double maxLon;

  public Rectangle(double minLat, double maxLat, double minLon, double maxLon) {
    GeoUtils.checkLatitude(minLat);
    GeoUtils.checkLatitude(maxLat);
    GeoUtils.checkLongitude(minLon);
    GeoUtils.checkLongitude(maxLon);
    this.minLon = minLon;
    this.maxLon = maxLon;
    this.minLat = minLat;
    this.maxLat = maxLat;
    assert maxLat >= minLat;

    // NOTE: cannot assert maxLon >= minLon since this rect could cross the dateline
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append("Rectangle(lat=");
    b.append(minLat);
    b.append(" TO ");
    b.append(maxLat);
    b.append(" lon=");
    b.append(minLon);
    b.append(" TO ");
    b.append(maxLon);
    if (maxLon < minLon) {
      b.append(" [crosses dateline!]");
    }
    b.append(")");

    return b.toString();
  }

  public boolean crossesDateline() {
    return maxLon < minLon;
  }

  public static boolean containsPoint(final double lat, final double lon,
                                      final double minLat, final double maxLat,
                                      final double minLon, final double maxLon) {
    return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
  }

  public static Rectangle fromPointDistance(final double centerLat, final double centerLon, final double radiusMeters) {
    checkLatitude(centerLat);
    checkLongitude(centerLon);
    final double radLat = toRadians(centerLat);
    final double radLon = toRadians(centerLon);
    // LUCENE-7143
    double radDistance = (radiusMeters + 7E-2) / EARTH_MEAN_RADIUS_METERS;
    double minLat = radLat - radDistance;
    double maxLat = radLat + radDistance;
    double minLon;
    double maxLon;

    if (minLat > MIN_LAT_RADIANS && maxLat < MAX_LAT_RADIANS) {
      double deltaLon = asin(sloppySin(radDistance) / cos(radLat));
      minLon = radLon - deltaLon;
      if (minLon < MIN_LON_RADIANS) {
        minLon += 2d * PI;
      }
      maxLon = radLon + deltaLon;
      if (maxLon > MAX_LON_RADIANS) {
        maxLon -= 2d * PI;
      }
    } else {
      // a pole is within the distance
      minLat = max(minLat, MIN_LAT_RADIANS);
      maxLat = min(maxLat, MAX_LAT_RADIANS);
      minLon = MIN_LON_RADIANS;
      maxLon = MAX_LON_RADIANS;
    }

    return new Rectangle(toDegrees(minLat), toDegrees(maxLat), toDegrees(minLon), toDegrees(maxLon));
  }

  public static final double AXISLAT_ERROR = 0.1D / EARTH_MEAN_RADIUS_METERS * TO_DEGREES;

  public static double axisLat(double centerLat, double radiusMeters) {
    // A spherical triangle with:
    // r is the radius of the circle in radians
    // l1 is the latitude of the circle center
    // l2 is the latitude of the point at which the circle intersect's its bbox longitudes
    // We know r is tangent to the bbox meridians at l2, therefore it is a right angle.
    // So from the law of cosines, with the angle of l1 being 90, we have:
    // cos(l1) = cos(r) * cos(l2) + sin(r) * sin(l2) * cos(90)
    // The second part cancels out because cos(90) == 0, so we have:
    // cos(l1) = cos(r) * cos(l2)
    // Solving for l2, we get:
    // l2 = acos( cos(l1) / cos(r) )
    // We ensure r is in the range (0, PI/2) and l1 in the range (0, PI/2]. This means we
    // cannot divide by 0, and we will always get a positive value in the range [0, 1) as
    // the argument to arc cosine, resulting in a range (0, PI/2].
    final double PIO2 = Math.PI / 2D;
    double l1 = toRadians(centerLat);
    double r = (radiusMeters + 7E-2) / EARTH_MEAN_RADIUS_METERS;

    // if we are within radius range of a pole, the lat is the pole itself
    if (Math.abs(l1) + r >= MAX_LAT_RADIANS) {
      return centerLat >= 0 ? MAX_LAT_INCL : MIN_LAT_INCL;
    }

    // adjust l1 as distance from closest pole, to form a right triangle with bbox meridians
    // and ensure it is in the range (0, PI/2]
    l1 = centerLat >= 0 ? PIO2 - l1 : l1 + PIO2;

    double l2 = Math.acos(Math.cos(l1) / Math.cos(r));
    assert !Double.isNaN(l2);

    // now adjust back to range [-pi/2, pi/2], ie latitude in radians
    l2 = centerLat >= 0 ? PIO2 - l2 : l2 - PIO2;

    return toDegrees(l2);
  }

  public static Rectangle fromPolygon(Polygon[] polygons) {
    // compute bounding box
    double minLat = Double.POSITIVE_INFINITY;
    double maxLat = Double.NEGATIVE_INFINITY;
    double minLon = Double.POSITIVE_INFINITY;
    double maxLon = Double.NEGATIVE_INFINITY;

    for (int i = 0;i < polygons.length; i++) {
      minLat = Math.min(polygons[i].minLat, minLat);
      maxLat = Math.max(polygons[i].maxLat, maxLat);
      minLon = Math.min(polygons[i].minLon, minLon);
      maxLon = Math.max(polygons[i].maxLon, maxLon);
    }

    return new Rectangle(minLat, maxLat, minLon, maxLon);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Rectangle rectangle = (Rectangle) o;

    if (Double.compare(rectangle.minLat, minLat) != 0) return false;
    if (Double.compare(rectangle.minLon, minLon) != 0) return false;
    if (Double.compare(rectangle.maxLat, maxLat) != 0) return false;
    return Double.compare(rectangle.maxLon, maxLon) == 0;

  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    temp = Double.doubleToLongBits(minLat);
    result = (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(minLon);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(maxLat);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(maxLon);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }
}
