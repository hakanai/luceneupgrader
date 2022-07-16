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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.geo;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PointValues;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.GeoUtils.orient;

public interface Component2D {

  double getMinX();

  double getMaxX();

  double getMinY();

  double getMaxY();

  boolean contains(double x, double y);

  PointValues.Relation relate(double minX, double maxX, double minY, double maxY);

  boolean intersectsLine(double minX, double maxX, double minY, double maxY,
                         double aX, double aY, double bX, double bY);

  boolean intersectsTriangle(double minX, double maxX, double minY, double maxY,
                             double aX, double aY, double bX, double bY, double cX, double cY);

  boolean containsLine(double minX, double maxX, double minY, double maxY,
                         double aX, double aY, double bX, double bY);

  boolean containsTriangle(double minX, double maxX, double minY, double maxY,
                           double aX, double aY, double bX, double bY, double cX, double cY);

  enum WithinRelation {
    CANDIDATE,
    NOTWITHIN,
    DISJOINT
  }

  WithinRelation withinPoint(double x, double y);

  WithinRelation withinLine(double minX, double maxX, double minY, double maxY,
                            double aX, double aY, boolean ab, double bX, double bY);

  WithinRelation withinTriangle(double minX, double maxX, double minY, double maxY,
                                double aX, double aY, boolean ab, double bX, double bY, boolean bc, double cX, double cY, boolean ca);


  default boolean intersectsLine(double aX, double aY, double bX, double bY) {
    double minY = StrictMath.min(aY, bY);
    double minX = StrictMath.min(aX, bX);
    double maxY = StrictMath.max(aY, bY);
    double maxX = StrictMath.max(aX, bX);
    return intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY);
  }

  default boolean intersectsTriangle(double aX, double aY, double bX, double bY, double cX, double cY) {
    double minY = StrictMath.min(StrictMath.min(aY, bY), cY);
    double minX = StrictMath.min(StrictMath.min(aX, bX), cX);
    double maxY = StrictMath.max(StrictMath.max(aY, bY), cY);
    double maxX = StrictMath.max(StrictMath.max(aX, bX), cX);
    return intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY);
  }

  default boolean containsLine(double aX, double aY, double bX, double bY) {
    double minY = StrictMath.min(aY, bY);
    double minX = StrictMath.min(aX, bX);
    double maxY = StrictMath.max(aY, bY);
    double maxX = StrictMath.max(aX, bX);
    return containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY);
  }

  default boolean containsTriangle(double aX, double aY, double bX, double bY, double cX, double cY) {
    double minY = StrictMath.min(StrictMath.min(aY, bY), cY);
    double minX = StrictMath.min(StrictMath.min(aX, bX), cX);
    double maxY = StrictMath.max(StrictMath.max(aY, bY), cY);
    double maxX = StrictMath.max(StrictMath.max(aX, bX), cX);
    return containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY);
  }

  default WithinRelation withinLine(double aX, double aY, boolean ab, double bX, double bY) {
    double minY = StrictMath.min(aY, bY);
    double minX = StrictMath.min(aX, bX);
    double maxY = StrictMath.max(aY, bY);
    double maxX = StrictMath.max(aX, bX);
    return withinLine(minX, maxX, minY, maxY, aX, aY, ab, bX, bY);
  }

  default WithinRelation withinTriangle(double aX, double aY, boolean ab, double bX, double bY, boolean bc, double cX, double cY, boolean ca) {
    double minY = StrictMath.min(StrictMath.min(aY, bY), cY);
    double minX = StrictMath.min(StrictMath.min(aX, bX), cX);
    double maxY = StrictMath.max(StrictMath.max(aY, bY), cY);
    double maxX = StrictMath.max(StrictMath.max(aX, bX), cX);
    return withinTriangle(minX, maxX, minY, maxY, aX, aY, ab, bX, bY, bc, cX, cY, ca);
  }

  static  boolean disjoint(double minX1, double maxX1, double minY1, double maxY1, double minX2, double maxX2, double minY2, double maxY2) {
    return (maxY1 < minY2 || minY1 > maxY2 || maxX1 < minX2 || minX1 > maxX2);
  }

  static boolean within(double minX1, double maxX1, double minY1, double maxY1, double minX2, double maxX2, double minY2, double maxY2) {
    return (minY2 <= minY1 && maxY2 >= maxY1 && minX2 <= minX1 && maxX2 >= maxX1);
  }

  static boolean containsPoint(final double x, final double y, final double minX, final double maxX, final double minY, final double maxY) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY;
  }

  static boolean pointInTriangle(double minX, double maxX, double minY, double maxY, double x, double y, double aX, double aY, double bX, double bY, double cX, double cY) {
    // check the bounding box because if the triangle is degenerated, e.g points and lines, we need to filter out
    // coplanar points that are not part of the triangle.
    if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
      int a = orient(x, y, aX, aY, bX, bY);
      int b = orient(x, y, bX, bY, cX, cY);
      if (a == 0 || b == 0 || a < 0 == b < 0) {
        int c = orient(x, y, cX, cY, aX, aY);
        return c == 0 || (c < 0 == (b < 0 || a < 0));
      }
      return false;
    } else {
      return false;
    }
  }

}