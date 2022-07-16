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


public final class Circle extends LatLonGeometry {
  private final double lat;
  private final double lon;
  private final double radiusMeters;

  public Circle(double lat, double lon, double radiusMeters) {
    GeoUtils.checkLatitude(lat);
    GeoUtils.checkLongitude(lon);
    if (Double.isFinite(radiusMeters) == false || radiusMeters < 0) {
      throw new IllegalArgumentException("radiusMeters: '" + radiusMeters + "' is invalid");
    }
    this.lat = lat;
    this.lon = lon;
    this.radiusMeters = radiusMeters;
  }

  public double getLat() {
    return lat;
  }

  public double getLon() {
    return lon;
  }

  public double getRadius() {
    return radiusMeters;
  }

  @Override
  protected Component2D toComponent2D() {
    return Circle2D.create(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Circle)) return false;
    Circle circle = (Circle) o;
    return lat == circle.lat && lon == circle.lon && radiusMeters == circle.radiusMeters;
  }

  @Override
  public int hashCode() {
    int result = Double.hashCode(lat);
    result = 31 * result + Double.hashCode(lon);
    result = 31 * result + Double.hashCode(radiusMeters);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CIRCLE(");
    sb.append("[" + lat + "," + lon + "]");
    sb.append(" radius = " + radiusMeters + " meters");
    sb.append(')');
    return sb.toString();
  }
}
