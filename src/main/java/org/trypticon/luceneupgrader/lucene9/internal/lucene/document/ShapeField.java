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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.document;

import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Line;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Polygon;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.Tessellator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.NumericUtils;

/**
 * A base shape utility class used for both LatLon (spherical) and XY (cartesian) shape fields.
 *
 * <p>{@link Polygon}'s and {@link Line}'s are decomposed into a triangular mesh using the {@link
 * Tessellator} utility class. Each {@link Triangle} is encoded by this base class and indexed as a
 * seven dimension multi-value field.
 *
 * <p>Finding all shapes that intersect a range (e.g., bounding box), or target shape, at search
 * time is efficient.
 *
 * <p>This class defines the static methods for encoding the three vertices of a tessellated
 * triangles as a seven dimension point. The coordinates are converted from double precision values
 * into 32 bit integers so they are sortable at index time.
 */
public final class ShapeField {
  /** vertex coordinates are encoded as 4 byte integers */
  static final int BYTES = Integer.BYTES;

  /**
   * tessellated triangles are seven dimensions; the first four are the bounding box index
   * dimensions
   */
  protected static final FieldType TYPE = new FieldType();

  static {
    TYPE.setDimensions(7, 4, BYTES);
    TYPE.freeze();
  }

  // no instance:
  private ShapeField() {}

  /**
   * polygons are decomposed into tessellated triangles using {@link
   * org.apache.lucene.geo.Tessellator} these triangles are encoded and inserted as separate indexed
   * POINT fields
   */
  public static class Triangle extends Field {

    /** constructor for points and lines */
    Triangle(
        String name,
        int aXencoded,
        int aYencoded,
        int bXencoded,
        int bYencoded,
        int cXencoded,
        int cYencoded) {
      super(name, TYPE);
      setTriangleValue(
          aXencoded, aYencoded, true, bXencoded, bYencoded, true, cXencoded, cYencoded, true);
    }

    /** xtor from a given Tessellated Triangle object */
    Triangle(String name, Tessellator.Triangle t) {
      super(name, TYPE);
      setTriangleValue(
          t.getEncodedX(0),
          t.getEncodedY(0),
          t.isEdgefromPolygon(0),
          t.getEncodedX(1),
          t.getEncodedY(1),
          t.isEdgefromPolygon(1),
          t.getEncodedX(2),
          t.getEncodedY(2),
          t.isEdgefromPolygon(2));
    }

    /** sets the vertices of the triangle as integer encoded values */
    protected void setTriangleValue(
        int aX,
        int aY,
        boolean abFromShape,
        int bX,
        int bY,
        boolean bcFromShape,
        int cX,
        int cY,
        boolean caFromShape) {
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

  /** Query Relation Types * */
  public enum QueryRelation {
    /** used for INTERSECT Queries */
    INTERSECTS,
    /** used for WITHIN Queries */
    WITHIN,
    /** used for DISJOINT Queries */
    DISJOINT,
    /** used for CONTAINS Queries */
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

  /**
   * A triangle is encoded using 6 points and an extra point with encoded information in three bits
   * of how to reconstruct it. Triangles are encoded with CCW orientation and might be rotated to
   * limit the number of possible reconstructions to 2^3. Reconstruction always happens from west to
   * east.
   */
  public static void encodeTriangle(
      byte[] bytes,
      int aY,
      int aX,
      boolean ab,
      int bY,
      int bX,
      boolean bc,
      int cY,
      int cX,
      boolean ca) {
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

    final int minX = aX;
    final int minY = StrictMath.min(aY, StrictMath.min(bY, cY));
    final int maxX = StrictMath.max(aX, StrictMath.max(bX, cX));
    final int maxY = StrictMath.max(aY, StrictMath.max(bY, cY));

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
    } else if (maxX == bX && minY == bY) {
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

  /**
   * Decode a triangle encoded by {@link ShapeField#encodeTriangle(byte[], int, int, boolean, int,
   * int, boolean, int, int, boolean)}.
   */
  public static void decodeTriangle(byte[] t, DecodedTriangle triangle) {
    final int aX, aY, bX, bY, cX, cY;
    final boolean ab, bc, ca;
    int bits = NumericUtils.sortableBytesToInt(t, 6 * BYTES);
    // extract the first three bits
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
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      case Y_MINX_MINY_X_MAXY_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case Y_MINX_MINY_MAXX_MAXY_X:
        aY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      case MAXY_MINX_MINY_X_Y_MAXX:
        aY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        break;
      case MINY_MINX_Y_MAXX_MAXY_X:
        aY = NumericUtils.sortableBytesToInt(t, 0 * BYTES);
        aX = NumericUtils.sortableBytesToInt(t, 1 * BYTES);
        bY = NumericUtils.sortableBytesToInt(t, 4 * BYTES);
        bX = NumericUtils.sortableBytesToInt(t, 3 * BYTES);
        cY = NumericUtils.sortableBytesToInt(t, 2 * BYTES);
        cX = NumericUtils.sortableBytesToInt(t, 5 * BYTES);
        break;
      default:
        throw new IllegalArgumentException("Could not decode the provided triangle");
    }
    // Points of the decoded triangle must be co-planar or CCW oriented
    assert GeoUtils.orient(aX, aY, bX, bY, cX, cY) >= 0;
    ab = (bits & 1 << 3) == 1 << 3;
    bc = (bits & 1 << 4) == 1 << 4;
    ca = (bits & 1 << 5) == 1 << 5;
    triangle.setValues(aX, aY, ab, bX, bY, bc, cX, cY, ca);
    resolveTriangleType(triangle);
  }

  static void resolveTriangleType(DecodedTriangle triangle) {
    if (triangle.aX == triangle.bX && triangle.aY == triangle.bY) {
      if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
        triangle.type = DecodedTriangle.TYPE.POINT;
      } else {
        // a and b are identical, remove ab, and merge bc and ca
        triangle.ab = triangle.bc | triangle.ca;
        triangle.bX = triangle.cX;
        triangle.bY = triangle.cY;
        triangle.cX = triangle.aX;
        triangle.cY = triangle.aY;
        triangle.type = DecodedTriangle.TYPE.LINE;
      }
    } else if (triangle.aX == triangle.cX && triangle.aY == triangle.cY) {
      // a and c are identical, remove ac, and merge ab and bc
      triangle.ab = triangle.ab | triangle.bc;
      triangle.type = DecodedTriangle.TYPE.LINE;
    } else if (triangle.bX == triangle.cX && triangle.bY == triangle.cY) {
      // b and c are identical, remove bc, and merge ab and ca
      triangle.ab = triangle.ab | triangle.ca;
      triangle.cX = triangle.aX;
      triangle.cY = triangle.aY;
      triangle.type = DecodedTriangle.TYPE.LINE;
    } else {
      triangle.type = DecodedTriangle.TYPE.TRIANGLE;
    }
  }

  /**
   * Represents a encoded triangle using {@link ShapeField#decodeTriangle(byte[], DecodedTriangle)}.
   */
  public static class DecodedTriangle {
    /** type of triangle */
    public enum TYPE {
      /** all coordinates are equal */
      POINT,
      /** first and third coordinates are equal */
      LINE,
      /** all coordinates are different */
      TRIANGLE
    }

    /** x coordinate, vertex one */
    public int aX;

    /** y coordinate, vertex one */
    public int aY;

    /** x coordinate, vertex two */
    public int bX;

    /** y coordinate, vertex two */
    public int bY;

    /** x coordinate, vertex three */
    public int cX;

    /** y coordinate, vertex three */
    public int cY;

    /** represent if edge ab belongs to original shape */
    public boolean ab;

    /** represent if edge bc belongs to original shape */
    public boolean bc;

    /** represent if edge ca belongs to original shape */
    public boolean ca;

    /** triangle type */
    public TYPE type;

    /** default xtor */
    public DecodedTriangle() {}

    /** Sets the values of the DecodedTriangle */
    protected void setValues(
        int aX, int aY, boolean ab, int bX, int bY, boolean bc, int cX, int cY, boolean ca) {
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
      DecodedTriangle other = (DecodedTriangle) o;
      return (aX == other.aX && bX == other.bX && cX == other.cX)
          && (aY == other.aY && bY == other.bY && cY == other.cY)
          && (ab == other.ab && bc == other.bc && ca == other.ca);
    }

    /** pretty print the triangle vertices */
    @Override
    public String toString() {
      String result =
          (aX + ", " + aY)
              + " "
              + (bX + ", " + bY)
              + " "
              + (cX + ", " + cY)
              + " "
              + ("[" + ab + "," + bc + "," + ca + "]");
      return result;
    }
  }
}
