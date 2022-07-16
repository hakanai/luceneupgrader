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

import java.util.Arrays;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.XYEncodingUtils.checkVal;

public class XYLine extends XYGeometry {
  private final float[] x;
  private final float[] y;

  public final float minX;
  public final float maxX;
  public final float minY;
  public final float maxY;

  public XYLine(float[] x, float[] y) {
    if (x == null) {
      throw new IllegalArgumentException("x must not be null");
    }
    if (y == null) {
      throw new IllegalArgumentException("y must not be null");
    }
    if (x.length != y.length) {
      throw new IllegalArgumentException("x and y must be equal length");
    }
    if (x.length < 2) {
      throw new IllegalArgumentException("at least 2 line points required");
    }

    // compute bounding box
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (int i = 0; i < x.length; ++i) {
      minX = Math.min(checkVal(x[i]), minX);
      minY = Math.min(checkVal(y[i]), minY);
      maxX = Math.max(x[i], maxX);
      maxY = Math.max(y[i], maxY);
    }
    this.x = x.clone();
    this.y = y.clone();

    this.minX = minX;
    this.maxX = maxX;
    this.minY = minY;
    this.maxY = maxY;
  }

  public int numPoints() {
    return x.length;
  }

  public float getX(int vertex) {
    return x[vertex];
  }

  public float getY(int vertex) {
    return y[vertex];
  }

  public float[] getX() {
    return x.clone();
  }

  public float[] getY() {
    return y.clone();
  }

  @Override
  protected Component2D toComponent2D() {
    return Line2D.create(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof XYLine)) return false;
    XYLine line = (XYLine) o;
    return Arrays.equals(x, line.x) && Arrays.equals(y, line.y);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(x);
    result = 31 * result + Arrays.hashCode(y);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("XYLINE(");
    for (int i = 0; i < x.length; i++) {
      sb.append("[")
          .append(x[i])
          .append(", ")
          .append(y[i])
          .append("]");
    }
    sb.append(')');
    return sb.toString();
  }
}
