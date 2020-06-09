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

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.TO_RADIANS;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.cos;
import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath.haversinMeters;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SloppyMath;

public final class GeoUtils {
  public static final double MIN_LON_INCL = -180.0D;

  public static final double MAX_LON_INCL = 180.0D;

  public static final double MIN_LAT_INCL = -90.0D;

  public static final double MAX_LAT_INCL = 90.0D;

  public static final double MIN_LON_RADIANS = TO_RADIANS * MIN_LON_INCL;
  public static final double MIN_LAT_RADIANS = TO_RADIANS * MIN_LAT_INCL;
  public static final double MAX_LON_RADIANS = TO_RADIANS * MAX_LON_INCL;
  public static final double MAX_LAT_RADIANS = TO_RADIANS * MAX_LAT_INCL;

  // WGS84 earth-ellipsoid parameters
  // see http://earth-info.nga.mil/GandG/publications/tr8350.2/wgs84fin.pdf
  public static final double EARTH_MEAN_RADIUS_METERS = 6_371_008.7714;

  // No instance:
  private GeoUtils() {
  }

  public static void checkLatitude(double latitude) {
    if (Double.isNaN(latitude) || latitude < MIN_LAT_INCL || latitude > MAX_LAT_INCL) {
      throw new IllegalArgumentException("invalid latitude " +  latitude + "; must be between " + MIN_LAT_INCL + " and " + MAX_LAT_INCL);
    }
  }

  public static void checkLongitude(double longitude) {
    if (Double.isNaN(longitude) || longitude < MIN_LON_INCL || longitude > MAX_LON_INCL) {
      throw new IllegalArgumentException("invalid longitude " +  longitude + "; must be between " + MIN_LON_INCL + " and " + MAX_LON_INCL);
    }
  }

  // some sloppyish stuff, do we really need this to be done in a sloppy way?
  // unless it is performance sensitive, we should try to remove.
  private static final double PIO2 = Math.PI / 2D;

  // TODO: deprecate/remove this? at least its no longer public.
  public static double sloppySin(double a) {
    return cos(a - PIO2);
  }

  public static double distanceQuerySortKey(double radius) {
    // effectively infinite
    if (radius >= haversinMeters(Double.MAX_VALUE)) {
      return haversinMeters(Double.MAX_VALUE);
    }

    // this is a search through non-negative long space only
    long lo = 0;
    long hi = Double.doubleToRawLongBits(Double.MAX_VALUE);
    while (lo <= hi) {
      long mid = (lo + hi) >>> 1;
      double sortKey = Double.longBitsToDouble(mid);
      double midRadius = haversinMeters(sortKey);
      if (midRadius == radius) {
        return sortKey;
      } else if (midRadius > radius) {
        hi = mid - 1;
      } else {
        lo = mid + 1;
      }
    }

    // not found: this is because a user can supply an arbitrary radius, one that we will never
    // calculate exactly via our haversin method.
    double ceil = Double.longBitsToDouble(lo);
    assert haversinMeters(ceil) > radius;
    return ceil;
  }

  public static PointValues.Relation relate(
      double minLat, double maxLat, double minLon, double maxLon,
      double lat, double lon, double distanceSortKey, double axisLat) {

    if (minLon > maxLon) {
      throw new IllegalArgumentException("Box crosses the dateline");
    }

    if ((lon < minLon || lon > maxLon) && (axisLat + Rectangle.AXISLAT_ERROR < minLat || axisLat - Rectangle.AXISLAT_ERROR > maxLat)) {
      // circle not fully inside / crossing axis
      if (SloppyMath.haversinSortKey(lat, lon, minLat, minLon) > distanceSortKey &&
          SloppyMath.haversinSortKey(lat, lon, minLat, maxLon) > distanceSortKey &&
          SloppyMath.haversinSortKey(lat, lon, maxLat, minLon) > distanceSortKey &&
          SloppyMath.haversinSortKey(lat, lon, maxLat, maxLon) > distanceSortKey) {
        // no points inside
        return Relation.CELL_OUTSIDE_QUERY;
      }
    }

    if (within90LonDegrees(lon, minLon, maxLon) &&
        SloppyMath.haversinSortKey(lat, lon, minLat, minLon) <= distanceSortKey &&
        SloppyMath.haversinSortKey(lat, lon, minLat, maxLon) <= distanceSortKey &&
        SloppyMath.haversinSortKey(lat, lon, maxLat, minLon) <= distanceSortKey &&
        SloppyMath.haversinSortKey(lat, lon, maxLat, maxLon) <= distanceSortKey) {
      // we are fully enclosed, collect everything within this subtree
      return Relation.CELL_INSIDE_QUERY;
    }

    return Relation.CELL_CROSSES_QUERY;
  }

  static boolean within90LonDegrees(double lon, double minLon, double maxLon) {
    if (maxLon <= lon - 180) {
      lon -= 360;
    } else if (minLon >= lon + 180) {
      lon += 360;
    }
    return maxLon - lon < 90 && lon - minLon < 90;
  }

  // see the "Orient2D" method described here:
  // http://www.cs.berkeley.edu/~jrs/meshpapers/robnotes.pdf
  // https://www.cs.cmu.edu/~quake/robust.html
  // Note that this one does not yet have the floating point tricks to be exact!
  public static int orient(double ax, double ay, double bx, double by, double cx, double cy) {
    double v1 = (bx - ax) * (cy - ay);
    double v2 = (cx - ax) * (by - ay);
    if (v1 > v2) {
      return 1;
    } else if (v1 < v2) {
      return -1;
    } else {
      return 0;
    }
  }

  public static Relation lineRelateLine(double a1x, double a1y, double b1x, double b1y, double a2x, double a2y, double b2x, double b2y) {
    // shortcut: either "line" is actually a point
    if ((a1x == b1x && a1y == b1y) || (a2x == b2x && a2y == b2y)) {
      return Relation.CELL_OUTSIDE_QUERY;
    }

    int a = orient(a2x, a2y, b2x, b2y, a1x, a1y) * orient(a2x, a2y, b2x, b2y, b1x, b1y);
    int b = orient(a1x, a1y, b1x, b1y, a2x, a2y) * orient(a1x, a1y, b1x, b1y, b2x, b2y);

    if (a <= 0 && b <= 0) {
      return a == 0 || b == 0 ? Relation.CELL_INSIDE_QUERY : Relation.CELL_CROSSES_QUERY;
    }

    return Relation.CELL_OUTSIDE_QUERY;
  }

  public enum WindingOrder {
    CW(-1), COLINEAR(0), CCW(1);
    private final int sign;
    WindingOrder(int sign) { this.sign = sign; }
    public int sign() {return sign;}
    public static WindingOrder fromSign(final int sign) {
      if (sign == CW.sign) return CW;
      if (sign == COLINEAR.sign) return COLINEAR;
      if (sign == CCW.sign) return CCW;
      throw new IllegalArgumentException("Invalid WindingOrder sign: " + sign);
    }
  }
}
