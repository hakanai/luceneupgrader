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

import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.GeoUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Line;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Polygon;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.geo.Tessellator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;

public final class ShapeField {
  static final int BYTES = Integer.BYTES;

  protected static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDimensions(7, 4, BYTES);
    TYPE.freeze();
  }

  // no instance:
  private ShapeField() {
  }

  public static class Triangle extends Field {

    Triangle(String name, int aXencoded, int aYencoded, int bXencoded, int bYencoded, int cXencoded, int cYencoded) {
      super(name, TYPE);
      setTriangleValue(aXencoded, aYencoded, true, bXencoded, bYencoded, true, cXencoded, cYencoded, true);
    }

    Triangle(String name, Tessellator.Triangle t) {
      super(name, TYPE);
      setTriangleValue(t.getEncodedX(0), t.getEncodedY(0), t.isEdgefromPolygon(0),
          t.getEncodedX(1), t.getEncodedY(1), t.isEdgefromPolygon(1),
          t.getEncodedX(2), t.getEncodedY(2), t.isEdgefromPolygon(2));
    }

    protected void setTriangleValue(int aX, int aY, boolean abFromShape, int bX, int bY, boolean bcFromShape, int cX, int cY, boolean caFromShape) {
      final byte[] bytes;

      if (fieldsData == null) {
        bytes = new byte[7 * BYTES];
        fieldsData = new BytesRef(bytes);
      } else {
        bytes = ((BytesRef) fieldsData).bytes;
      }
      encodeTriangle(bytes, aY, aX, abFromShape, bY, bX, bcFromShape, cY, cX, caFromShape);
    }
  }

  public enum QueryRelation {
    INTERSECTS,
    WITHIN,
    DISJOINT,
    CONTAINS
  }

  private static final int MINY_MINX_MAXY_MAXX_Y_X = 0;
  private static final int MINY_MINX_Y_X_MAXY_MAXX = 1;
  private static final int MAXY_MINX_Y_X_MINY_MAXX = 2;
  private static final int MAXY_MINX_MINY_MAXX_Y_X = 3;
  private static final int Y_MINX_MINY_X_MAXY_MAXX = 4;
  private static final int Y_MINX_MINY_MAXX_MAXY_X = 5;
  private static final int MAXY_MINX_MINY_X_Y_MAXX = 6;
  private static final int MINY_MINX_Y_MAXX_MAXY_X = 7;

  public static void encodeTriangle(byte[] bytes, int aY, int aX, boolean ab, int bY, int bX, boolean bc, int cY, int cX, boolean ca) {
    assert bytes.length == 7 * BYTES;
    // rotate edges and place minX at the beginning
    if (bX < aX || cX < aX) {
      final int tempX = aX;
      final int tempY = aY;
      final boolean tempBool = ab;
      if (bX < cX) {
        aX = bX;
        aY = bY;
        ab = bc;
        bX = cX;
        bY = cY;
        bc = ca;
        cX = tempX;
        cY = tempY;
        ca = tempBool;
      } else {
        aX = cX;
        aY = cY;
        ab = ca;
        cX = bX;
        cY = bY;
        ca = bc;
        bX = tempX;
        bY = tempY;
        bc = tempBool;
      }
    } else if (aX == bX && aX == cX) {
      // degenerated case, all points with same longitude
      // we need to prevent that aX is in the middle (not part of the MBS)
      if (bY < aY || cY < aY) {
        final int tempX = aX;
        final int tempY = aY;
        final boolean tempBool = ab;
        if (bY < cY) {
          aX = bX;
          aY = bY;
          ab = bc;
          bX = cX;
          bY = cY;
          bc = ca;
          cX = tempX;
          cY = tempY;
          ca = tempBool;
        } else {
          aX = cX;
          aY = cY;
          ab = ca;
          cX = bX;
          cY = bY;
          ca = bc;
          bX = tempX;
          bY = tempY;
          bc = tempBool;
        }
      }
    }

    // change orientation if CW
    if (GeoUtils.orient(aX, aY, bX, bY, cX, cY) == -1) {
      // swap b with c
      final int tempX = bX;
      final int tempY = bY;
      final boolean tempBool = ab;
      // aX and aY do not change, ab becomes bc
      ab = bc;
      bX = cX;
      bY = cY;
      // bc does not change, ca becomes ab
      cX = tempX;
      cY = tempY;
      ca = tempBool;
    }

    int minX = aX;
    int minY = StrictMath.min(aY, StrictMath.min(bY, cY));
    int maxX = StrictMath.max(aX, StrictMath.max(bX, cX));
    int maxY = StrictMath.max(aY, StrictMath.max(bY, cY));

    int bits, x, y;
    if (minY == aY) {
      if (maxY == bY && maxX == bX) {
        y = cY;
        x = cX;
        bits = MINY_MINX_MAXY_MAXX_Y_X;
      } else if (maxY == cY && maxX == cX) {
        y = bY;
        x = bX;
        bits = MINY_MINX_Y_X_MAXY_MAXX;
      } else {
        y = bY;
        x = cX;
        bits = MINY_MINX_Y_MAXX_MAXY_X;
      }
    } else if (maxY == aY) {
      if (minY == bY && maxX == bX) {
        y = cY;
        x = cX;
        bits = MAXY_MINX_MINY_MAXX_Y_X;
      } else if (minY == cY && maxX == cX) {
        y = bY;
        x = bX;
        bits = MAXY_MINX_Y_X_MINY_MAXX;
      } else {
        y = cY;
        x = bX;
        bits = MAXY_MINX_MINY_X_Y_MAXX;
      }
    }  else if (maxX == bX && minY == bY) {
      y = aY;
      x = cX;
      bits = Y_MINX_MINY_MAXX_MAXY_X;
    } else if (maxX == cX && maxY == cY) {
      y = aY;
      x = bX;
      bits = Y_MINX_MINY_X_MAXY_MAXX;
    } else {
      throw new IllegalArgumentException("Could not encode the provided triangle");
    }
    bits |= (ab) ? (1 << 3) : 0;
    bits |= (bc) ? (1 << 4) : 0;
    bits |= (ca) ? (1 << 5) : 0;
    NumericUtils.intToSortableBytes(minY, bytes, 0);
    NumericUtils.intToSortableBytes(minX, bytes, BYTES);
    NumericUtils.intToSortableBytes(maxY, bytes, 2 * BYTES);
    NumericUtils.intToSortableBytes(maxX, bytes, 3 * BYTES);
    NumericUtils.intToSortableBytes(y, bytes, 4 * BYTES);
    NumericUtils.intToSortableBytes(x, bytes, 5 * BYTES);
    NumericUtils.intToSortableBytes(bits, bytes, 6 * BYTES);
  }

  public static void decodeTriangle(byte[] t, DecodedTriangle triangle) {
    final int aX, aY, bX, bY, cX, cY;
    final boolean ab, bc, ca;
    int bits = NumericUtils.sortableBytesToInt(t, 6 * BYTES);
    //extract the first three bits
    int tCode = (((1 << 3) - 1) & (bits >> 0));
    switch (tCode) {
      case MINY_MINX_MAXY_MAXX_Y_X:
        aY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      case MINY_MINX_Y_X_MAXY_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case MAXY_MINX_Y_X_MINY_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case MAXY_MINX_MINY_MAXX_Y_X:
        aY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        aX  = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      case Y_MINX_MINY_X_MAXY_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        aX  = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case Y_MINX_MINY_MAXX_MAXY_X:
        aY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        aX  = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      case MAXY_MINX_MINY_X_Y_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        aX  = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case MINY_MINX_Y_MAXX_MAXY_X:
        aY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        aX  = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      default:
        throw new IllegalArgumentException("Could not decode the provided triangle");
    }
    //Points of the decoded triangle must be co-planar or CCW oriented
    assert GeoUtils.orient(aX, aY, bX, bY, cX, cY) >= 0;
    ab = (bits & 1 << 3) == 1 << 3;
    bc = (bits & 1 << 4) == 1 << 4;
    ca = (bits & 1 << 5) == 1 << 5;
    triangle.setValues(aX, aY, ab, bX, bY, bc, cX, cY, ca);
    resolveTriangleType(triangle);
  }

  private static void resolveTriangleType(DecodedTriangle triangle) {
    if (triangle.aX == triangle.bX && triangle.aY == triangle.bY) {
      if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
        triangle.type = DecodedTriangle.TYPE.POINT;
      } else {
        triangle.bX = triangle.cX;
        triangle.bY = triangle.cY;
        triangle.cX = triangle.aX;
        triangle.cY = triangle.aY;
        triangle.type = DecodedTriangle.TYPE.LINE;
      }
    } else if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
      triangle.type = DecodedTriangle.TYPE.LINE;
    } else if (triangle.bX == triangle.cX && triangle.bY == triangle.cY) {
      triangle.cX = triangle.aX;
      triangle.cY = triangle.aY;
      triangle.type = DecodedTriangle.TYPE.LINE;
    } else {
      triangle.type = DecodedTriangle.TYPE.TRIANGLE;
    }
  }

  public static class DecodedTriangle {
    public enum TYPE {
      POINT,
      LINE,
      TRIANGLE
    }
    public int aX;
    public int aY;
    public int bX;
    public int bY;
    public int cX;
    public int cY;
    public boolean ab;
    public boolean bc;
    public boolean ca;
    public TYPE type;

    public DecodedTriangle() {
    }

    private void setValues(int aX, int aY, boolean ab, int bX, int bY, boolean bc, int cX, int cY, boolean ca) {
      this.aX = aX;
      this.aY = aY;
      this.ab = ab;
      this.bX = bX;
      this.bY = bY;
      this.bc = bc;
      this.cX = cX;
      this.cY = cY;
      this.ca = ca;
    }

    @Override
    public int hashCode() {
      return Objects.hash(aX, aY, bX, bY, cX, cY, ab, bc, ca);
    }

    @Override
    public boolean equals(Object o) {
      DecodedTriangle other  = (DecodedTriangle) o;
      return aX == other.aX && bX == other.bX && cX == other.cX
          && aY == other.aY && bY == other.bY && cY == other.cY
          && ab == other.ab && bc == other.bc && ca == other.ca;
    }

    public String toString() {
      String result = aX + ", " + aY + " " +
          bX + ", " + bY + " " +
          cX + ", " + cY + " " + "[" + ab + "," +bc + "," + ca + "]";
      return result;
    }
  }
}
