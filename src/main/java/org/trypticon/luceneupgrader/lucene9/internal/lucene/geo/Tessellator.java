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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.geo;

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoEncodingUtils.encodeLatitude;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoEncodingUtils.encodeLongitude;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoUtils.lineCrossesLine;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoUtils.lineOverlapLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.geo.GeoUtils.WindingOrder;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BitUtil;

/**
 * Computes a triangular mesh tessellation for a given polygon.
 *
 * <p>This is inspired by mapbox's earcut algorithm (https://github.com/mapbox/earcut) which is a
 * modification to FIST (https://www.cosy.sbg.ac.at/~held/projects/triang/triang.html) written by
 * Martin Held, and ear clipping
 * (https://www.geometrictools.com/Documentation/TriangulationByEarClipping.pdf) written by David
 * Eberly.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li>Requires valid polygons:
 *       <ul>
 *         <li>No self intersections
 *         <li>Holes may only touch at one vertex
 *         <li>Polygon must have an area (e.g., no "line" boxes)
 *         <li>sensitive to overflow (e.g, subatomic values such as E-200 can cause unexpected
 *             behavior)
 *       </ul>
 * </ul>
 *
 * <p>The code is a modified version of the javascript implementation provided by MapBox under the
 * following license:
 *
 * <p>ISC License
 *
 * <p>Copyright (c) 2016, Mapbox
 *
 * <p>Permission to use, copy, modify, and/or distribute this software for any purpose with or
 * without fee is hereby granted, provided that the above copyright notice and this permission
 * notice appear in all copies.
 *
 * <p>THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH' REGARD TO THIS
 * SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
 * AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 *
 * @lucene.internal
 */
public final class Tessellator {
  // this is a dumb heuristic to control whether we cut over to sorted morton values
  private static final int VERTEX_THRESHOLD = 80;

  /** state of the tessellated split - avoids recursion */
  private enum State {
    INIT,
    CURE,
    SPLIT
  }

  // No Instance:
  private Tessellator() {}

  public static List<Triangle> tessellate(final Polygon polygon, boolean checkSelfIntersections) {
    return tessellate(polygon, checkSelfIntersections, null);
  }

  public static List<Triangle> tessellate(
      final Polygon polygon, boolean checkSelfIntersections, Monitor monitor) {
    // Attempt to establish a doubly-linked list of the provided shell points (should be CCW, but
    // this will correct);
    // then filter instances of intersections.
    Node outerNode =
        createDoublyLinkedList(
            polygon.getPolyLons(),
            polygon.getPolyLats(),
            polygon.getWindingOrder(),
            true,
            0,
            WindingOrder.CW);
    // If an outer node hasn't been detected, the shape is malformed. (must comply with OGC SFA
    // specification)
    if (outerNode == null) {
      throw new IllegalArgumentException("Malformed shape detected in Tessellator!");
    }
    if (outerNode == outerNode.next || outerNode == outerNode.next.next) {
      throw new IllegalArgumentException("at least three non-collinear points required");
    }

    // Determine if the specified list of points contains holes
    if (polygon.numHoles() > 0) {
      // Eliminate the hole triangulation.
      outerNode = eliminateHoles(polygon, outerNode);
    }

    // If the shape crosses VERTEX_THRESHOLD, use z-order curve hashing:
    final boolean mortonOptimized;
    {
      int threshold = VERTEX_THRESHOLD - polygon.numPoints();
      for (int i = 0; threshold >= 0 && i < polygon.numHoles(); ++i) {
        threshold -= polygon.getHole(i).numPoints();
      }

      // Link polygon nodes in Z-Order
      mortonOptimized = threshold < 0;
      if (mortonOptimized == true) {
        sortByMorton(outerNode);
      }
    }
    if (checkSelfIntersections) {
      checkIntersection(outerNode, mortonOptimized);
    }
    // Calculate the tessellation using the doubly LinkedList.
    List<Triangle> result =
        earcutLinkedList(
            polygon, outerNode, new ArrayList<>(), State.INIT, mortonOptimized, monitor, 0);
    if (result.size() == 0) {
      notifyMonitor(Monitor.FAILED, monitor, null, result);
      throw new IllegalArgumentException(
          "Unable to Tessellate shape. Possible malformed shape detected.");
    }
    notifyMonitor(Monitor.COMPLETED, monitor, null, result);

    return result;
  }

  public static List<Triangle> tessellate(final XYPolygon polygon, boolean checkSelfIntersections) {
    return tessellate(polygon, checkSelfIntersections, null);
  }

  public static List<Triangle> tessellate(
      final XYPolygon polygon, boolean checkSelfIntersections, Monitor monitor) {
    // Attempt to establish a doubly-linked list of the provided shell points (should be CCW, but
    // this will correct);
    // then filter instances of intersections.0
    Node outerNode =
        createDoublyLinkedList(
            XYEncodingUtils.floatArrayToDoubleArray(polygon.getPolyX()),
            XYEncodingUtils.floatArrayToDoubleArray(polygon.getPolyY()),
            polygon.getWindingOrder(),
            false,
            0,
            WindingOrder.CW);
    // If an outer node hasn't been detected, the shape is malformed. (must comply with OGC SFA
    // specification)
    if (outerNode == null) {
      throw new IllegalArgumentException("Malformed shape detected in Tessellator!");
    }
    if (outerNode == outerNode.next || outerNode == outerNode.next.next) {
      throw new IllegalArgumentException("at least three non-collinear points required");
    }

    // Determine if the specified list of points contains holes
    if (polygon.numHoles() > 0) {
      // Eliminate the hole triangulation.
      outerNode = eliminateHoles(polygon, outerNode);
    }

    // If the shape crosses VERTEX_THRESHOLD, use z-order curve hashing:
    final boolean mortonOptimized;
    {
      int threshold = VERTEX_THRESHOLD - polygon.numPoints();
      for (int i = 0; threshold >= 0 && i < polygon.numHoles(); ++i) {
        threshold -= polygon.getHole(i).numPoints();
      }

      // Link polygon nodes in Z-Order
      mortonOptimized = threshold < 0;
      if (mortonOptimized == true) {
        sortByMorton(outerNode);
      }
    }
    if (checkSelfIntersections == true) {
      checkIntersection(outerNode, mortonOptimized);
    }
    // Calculate the tessellation using the doubly LinkedList.
    List<Triangle> result =
        earcutLinkedList(
            polygon, outerNode, new ArrayList<>(), State.INIT, mortonOptimized, monitor, 0);
    if (result.size() == 0) {
      notifyMonitor(Monitor.FAILED, monitor, null, result);
      throw new IllegalArgumentException(
          "Unable to Tessellate shape. Possible malformed shape detected.");
    }
    notifyMonitor(Monitor.COMPLETED, monitor, null, result);

    return result;
  }

  /**
   * Creates a circular doubly linked list using polygon points. The order is governed by the
   * specified winding order
   */
  private static Node createDoublyLinkedList(
      final double[] x,
      final double[] y,
      final WindingOrder polyWindingOrder,
      boolean isGeo,
      int startIndex,
      final WindingOrder windingOrder) {
    Node lastNode = null;
    // Link points into the circular doubly-linked list in the specified winding order
    if (windingOrder == polyWindingOrder) {
      for (int i = 0; i < x.length; ++i) {
        lastNode = insertNode(x, y, startIndex++, i, lastNode, isGeo);
      }
    } else {
      for (int i = x.length - 1; i >= 0; --i) {
        lastNode = insertNode(x, y, startIndex++, i, lastNode, isGeo);
      }
    }
    // if first and last node are the same then remove the end node and set lastNode to the start
    if (lastNode != null && isVertexEquals(lastNode, lastNode.next)) {
      removeNode(lastNode, true);
      lastNode = lastNode.next;
    }

    // Return the last node in the Doubly-Linked List
    return filterPoints(lastNode, null);
  }

  private static Node eliminateHoles(final XYPolygon polygon, Node outerNode) {
    // Define a list to hole a reference to each filtered hole list.
    final List<Node> holeList = new ArrayList<>();
    // keep a reference to the hole
    final Map<Node, XYPolygon> holeListPolygons = new HashMap<>();
    // Iterate through each array of hole vertices.
    XYPolygon[] holes = polygon.getHoles();
    int nodeIndex = polygon.numPoints();
    for (int i = 0; i < polygon.numHoles(); ++i) {
      // create the doubly-linked hole list
      Node list =
          createDoublyLinkedList(
              XYEncodingUtils.floatArrayToDoubleArray(holes[i].getPolyX()),
              XYEncodingUtils.floatArrayToDoubleArray(holes[i].getPolyY()),
              holes[i].getWindingOrder(),
              false,
              nodeIndex,
              WindingOrder.CCW);
      // Determine if the resulting hole polygon was successful.
      if (list != null) {
        // Add the leftmost vertex of the hole.
        Node leftMost = fetchLeftmost(list);
        holeList.add(leftMost);
        holeListPolygons.put(leftMost, holes[i]);
      }
      nodeIndex += holes[i].numPoints();
    }
    return eliminateHoles(holeList, holeListPolygons, outerNode);
  }

  /** Links every hole into the outer loop, producing a single-ring polygon without holes. */
  private static Node eliminateHoles(final Polygon polygon, Node outerNode) {
    // Define a list to hole a reference to each filtered hole list.
    final List<Node> holeList = new ArrayList<>();
    // keep a reference to the hole
    final Map<Node, Polygon> holeListPolygons = new HashMap<>();
    // Iterate through each array of hole vertices.
    Polygon[] holes = polygon.getHoles();
    int nodeIndex = polygon.numPoints();
    for (int i = 0; i < polygon.numHoles(); ++i) {
      // create the doubly-linked hole list
      Node list =
          createDoublyLinkedList(
              holes[i].getPolyLons(),
              holes[i].getPolyLats(),
              holes[i].getWindingOrder(),
              true,
              nodeIndex,
              WindingOrder.CCW);
      if (list == list.next) {
        throw new IllegalArgumentException("Points are all coplanar in hole: " + holes[i]);
      }
      // Add the leftmost vertex of the hole.
      Node leftMost = fetchLeftmost(list);
      holeList.add(leftMost);
      holeListPolygons.put(leftMost, holes[i]);
      nodeIndex += holes[i].numPoints();
    }
    return eliminateHoles(holeList, holeListPolygons, outerNode);
  }

  private static Node eliminateHoles(
      List<Node> holeList, final Map<Node, ?> holeListPolygons, Node outerNode) {
    // Sort the hole vertices by x coordinate
    holeList.sort(
        (Node pNodeA, Node pNodeB) -> {
          double diff = pNodeA.getX() - pNodeB.getX();
          if (diff == 0) {
            diff = pNodeA.getY() - pNodeB.getY();
            if (diff == 0) {
              // same hole node
              double a = Math.min(pNodeA.previous.getY(), pNodeA.next.getY());
              double b = Math.min(pNodeB.previous.getY(), pNodeB.next.getY());
              diff = a - b;
            }
          }
          return diff < 0 ? -1 : diff > 0 ? 1 : 0;
        });

    // Process holes from left to right.
    for (int i = 0; i < holeList.size(); ++i) {
      // Eliminate hole triangles from the result set
      final Node holeNode = holeList.get(i);
      double holeMinX, holeMaxX, holeMinY, holeMaxY;
      Object h = holeListPolygons.get(holeNode);
      if (h instanceof Polygon) {
        Polygon holePoly = (Polygon) h;
        holeMinX = holePoly.minLon;
        holeMaxX = holePoly.maxLon;
        holeMinY = holePoly.minLat;
        holeMaxY = holePoly.maxLat;
      } else {
        XYPolygon holePoly = (XYPolygon) h;
        holeMinX = holePoly.minX;
        holeMaxX = holePoly.maxX;
        holeMinY = holePoly.minY;
        holeMaxY = holePoly.maxY;
      }
      eliminateHole(holeNode, outerNode, holeMinX, holeMaxX, holeMinY, holeMaxY);
      // Filter the new polygon.
      outerNode = filterPoints(outerNode, outerNode.next);
    }
    // Return a pointer to the list.
    return outerNode;
  }

  /** Finds a bridge between vertices that connects a hole with an outer ring, and links it */
  private static void eliminateHole(
      final Node holeNode,
      Node outerNode,
      double holeMinX,
      double holeMaxX,
      double holeMinY,
      double holeMaxY) {

    // Attempt to merge the hole using a common point between if it exists.
    if (maybeMergeHoleWithSharedVertices(
        holeNode, outerNode, holeMinX, holeMaxX, holeMinY, holeMaxY)) {
      return;
    }
    // Attempt to find a logical bridge between the HoleNode and OuterNode.
    outerNode = fetchHoleBridge(holeNode, outerNode);

    // Determine whether a hole bridge could be fetched.
    if (outerNode != null) {
      // compute if the bridge overlaps with a polygon edge.
      boolean fromPolygon =
          isPointInLine(outerNode, outerNode.next, holeNode)
              || isPointInLine(holeNode, holeNode.next, outerNode);
      // Split the resulting polygon.
      Node node = splitPolygon(outerNode, holeNode, fromPolygon);
      // Filter the split nodes.
      filterPoints(node, node.next);
    }
  }

  /**
   * Choose a common vertex between the polygon and the hole if it exists and return true, otherwise
   * return false
   */
  private static boolean maybeMergeHoleWithSharedVertices(
      final Node holeNode,
      Node outerNode,
      double holeMinX,
      double holeMaxX,
      double holeMinY,
      double holeMaxY) {
    // Attempt to find a common point between the HoleNode and OuterNode.
    Node sharedVertex = null;
    Node sharedVertexConnection = null;
    Node next = outerNode;
    do {
      if (Rectangle.containsPoint(
          next.getY(), next.getX(), holeMinY, holeMaxY, holeMinX, holeMaxX)) {
        Node newSharedVertex = getSharedVertex(holeNode, next);
        if (newSharedVertex != null) {
          if (sharedVertex == null) {
            sharedVertex = newSharedVertex;
            sharedVertexConnection = next;
          } else if (newSharedVertex.equals(sharedVertex)) {
            // This can only happen if this vertex has been already used for a bridge. We need to
            // choose the right one.
            sharedVertexConnection =
                getSharedInsideVertex(sharedVertex, sharedVertexConnection, next);
          }
        }
      }
      next = next.next;
    } while (next != outerNode);
    if (sharedVertex != null) {
      // Split the resulting polygon.
      Node node = splitPolygon(sharedVertexConnection, sharedVertex, true);
      // Filter the split nodes.
      filterPoints(node, node.next);
      return true;
    }
    return false;
  }

  /** Check if the provided vertex is in the polygon and return it */
  private static Node getSharedVertex(final Node polygon, final Node vertex) {
    Node next = polygon;
    do {
      if (isVertexEquals(next, vertex)) {
        return next;
      }
      next = next.next;
    } while (next != polygon);
    return null;
  }

  /** Choose the vertex that has a smaller angle with the hole vertex */
  static Node getSharedInsideVertex(Node holeVertex, Node candidateA, Node candidateB) {
    assert isVertexEquals(holeVertex, candidateA) && isVertexEquals(holeVertex, candidateB);
    // we are joining candidate.prevNode -> holeVertex.node -> holeVertex.nextNode.
    // A negative area means a convex angle. if both are convex/reflex choose the point of
    // minimum angle
    final double a1 =
        area(
            candidateA.previous.getX(),
            candidateA.previous.getY(),
            holeVertex.getX(),
            holeVertex.getY(),
            holeVertex.next.getX(),
            holeVertex.next.getY());
    final double a2 =
        area(
            candidateB.previous.getX(),
            candidateB.previous.getY(),
            holeVertex.getX(),
            holeVertex.getY(),
            holeVertex.next.getX(),
            holeVertex.next.getY());

    if (a1 < 0 != a2 < 0) {
      // one is convex, the other reflex, get the convex one
      return a1 < a2 ? candidateA : candidateB;
    } else {
      // both are convex / reflex, choose the smallest angle
      final double angle1 = angle(candidateA.previous, candidateA, holeVertex.next);
      final double angle2 = angle(candidateB.previous, candidateB, holeVertex.next);
      return angle1 < angle2 ? candidateA : candidateB;
    }
  }

  private static double angle(Node a, Node b, Node c) {
    final double ax = a.getX() - b.getX();
    final double ay = a.getY() - b.getY();
    final double cx = c.getX() - b.getX();
    final double cy = c.getY() - b.getY();
    final double dotProduct = ax * cx + ay * cy;
    final double aLength = Math.sqrt(ax * ax + ay * ay);
    final double bLength = Math.sqrt(cx * cx + cy * cy);
    return Math.acos(dotProduct / (aLength * bLength));
  }

  /**
   * David Eberly's algorithm for finding a bridge between a hole and outer polygon
   *
   * <p>see: http://www.geometrictools.com/Documentation/TriangulationByEarClipping.pdf
   */
  private static Node fetchHoleBridge(final Node holeNode, final Node outerNode) {
    Node p = outerNode;
    double qx = Double.NEGATIVE_INFINITY;
    final double hx = holeNode.getX();
    final double hy = holeNode.getY();
    Node connection = null;
    // 1. find a segment intersected by a ray from the hole's leftmost point to the left;
    // segment's endpoint with lesser x will be potential connection point
    {
      do {
        if (hy <= p.getY() && hy >= p.next.getY() && p.next.getY() != p.getY()) {
          final double x =
              p.getX() + (hy - p.getY()) * (p.next.getX() - p.getX()) / (p.next.getY() - p.getY());
          if (x <= hx && x > qx) {
            qx = x;
            if (x == hx) {
              if (hy == p.getY()) return p;
              if (hy == p.next.getY()) return p.next;
            }
            connection = p.getX() < p.next.getX() ? p : p.next;
          }
        }
        p = p.next;
      } while (p != outerNode);
    }

    if (connection == null) {
      return null;
    } else if (hx == qx) {
      return connection.previous;
    }

    // 2. look for points inside the triangle of hole point, segment intersection, and endpoint
    // its a valid connection iff there are no points found;
    // otherwise choose the point of the minimum angle with the ray as the connection point
    Node stop = connection;
    final double mx = connection.getX();
    final double my = connection.getY();
    double tanMin = Double.POSITIVE_INFINITY;
    double tan;
    p = connection;
    do {
      if (hx >= p.getX()
          && p.getX() >= mx
          && hx != p.getX()
          && pointInEar(p.getX(), p.getY(), hy < my ? hx : qx, hy, mx, my, hy < my ? qx : hx, hy)) {
        tan = Math.abs(hy - p.getY()) / (hx - p.getX()); // tangential
        if ((tan < tanMin || (tan == tanMin && p.getX() > connection.getX()))
            && isLocallyInside(p, holeNode)) {
          connection = p;
          tanMin = tan;
        }
      }
      p = p.next;
    } while (p != stop);
    return connection;
  }

  /** Finds the left-most hole of a polygon ring. * */
  private static Node fetchLeftmost(final Node start) {
    Node node = start;
    Node leftMost = start;
    do {
      // Determine if the current node possesses a lesser X position.
      if (node.getX() < leftMost.getX()
          || (node.getX() == leftMost.getX() && node.getY() < leftMost.getY())) {
        // Maintain a reference to this Node.
        leftMost = node;
      }
      // Progress the search to the next node in the doubly-linked list.
      node = node.next;
    } while (node != start);

    // Return the node with the smallest X value.
    return leftMost;
  }

  /**
   * Main ear slicing loop which triangulates the vertices of a polygon, provided as a doubly-linked
   * list. *
   */
  private static List<Triangle> earcutLinkedList(
      Object polygon,
      Node currEar,
      final List<Triangle> tessellation,
      State state,
      final boolean mortonOptimized,
      final Monitor monitor,
      int depth) {
    earcut:
    do {
      if (currEar == null || currEar.previous == currEar.next) {
        return tessellation;
      }

      Node stop = currEar;
      Node prevNode;
      Node nextNode;

      // Iteratively slice ears
      do {
        notifyMonitor(state, depth, monitor, currEar, tessellation);
        prevNode = currEar.previous;
        nextNode = currEar.next;
        // Determine whether the current triangle must be cut off.
        final boolean isReflex =
            area(
                    prevNode.getX(),
                    prevNode.getY(),
                    currEar.getX(),
                    currEar.getY(),
                    nextNode.getX(),
                    nextNode.getY())
                >= 0;
        if (isReflex == false && isEar(currEar, mortonOptimized) == true) {
          // Compute if edges belong to the polygon
          boolean abFromPolygon = prevNode.isNextEdgeFromPolygon;
          boolean bcFromPolygon = currEar.isNextEdgeFromPolygon;
          boolean caFromPolygon = isEdgeFromPolygon(prevNode, nextNode, mortonOptimized);
          // Return the triangulated data
          tessellation.add(
              new Triangle(
                  prevNode, abFromPolygon, currEar, bcFromPolygon, nextNode, caFromPolygon));
          // Remove the ear node.
          removeNode(currEar, caFromPolygon);

          // Skipping to the next node leaves fewer slither triangles.
          currEar = nextNode.next;
          stop = nextNode.next;
          continue;
        }
        currEar = nextNode;
        // If the whole polygon has been iterated over and no more ears can be found.
        if (currEar == stop) {
          switch (state) {
            case INIT:
              // try filtering points and slicing again
              currEar = filterPoints(currEar, null);
              state = State.CURE;
              continue earcut;
            case CURE:
              // if this didn't work, try curing all small self-intersections locally
              currEar = cureLocalIntersections(currEar, tessellation, mortonOptimized);
              state = State.SPLIT;
              continue earcut;
            case SPLIT:
              // as a last resort, try splitting the remaining polygon into two
              if (splitEarcut(polygon, currEar, tessellation, mortonOptimized, monitor, depth + 1)
                  == false) {
                // we could not process all points. Tessellation failed
                notifyMonitor(state.name() + "[FAILED]", monitor, currEar, tessellation);
                throw new IllegalArgumentException(
                    "Unable to Tessellate shape. Possible malformed shape detected.");
              }
              break;
          }
          break;
        }
      } while (currEar.previous != currEar.next);
      break;
    } while (true);
    // Return the calculated tessellation
    return tessellation;
  }

  /** Determines whether a polygon node forms a valid ear with adjacent nodes. * */
  private static boolean isEar(final Node ear, final boolean mortonOptimized) {
    if (mortonOptimized == true) {
      return mortonIsEar(ear);
    }

    // make sure there aren't other points inside the potential ear
    Node node = ear.next.next;
    while (node != ear.previous) {
      if (pointInEar(
              node.getX(),
              node.getY(),
              ear.previous.getX(),
              ear.previous.getY(),
              ear.getX(),
              ear.getY(),
              ear.next.getX(),
              ear.next.getY())
          && area(
                  node.previous.getX(),
                  node.previous.getY(),
                  node.getX(),
                  node.getY(),
                  node.next.getX(),
                  node.next.getY())
              >= 0) {
        return false;
      }
      node = node.next;
    }
    return true;
  }

  /**
   * Uses morton code for speed to determine whether or a polygon node forms a valid ear w/ adjacent
   * nodes
   */
  private static boolean mortonIsEar(final Node ear) {
    // triangle bbox (flip the bits so negative encoded values are < positive encoded values)
    int minTX = StrictMath.min(StrictMath.min(ear.previous.x, ear.x), ear.next.x) ^ 0x80000000;
    int minTY = StrictMath.min(StrictMath.min(ear.previous.y, ear.y), ear.next.y) ^ 0x80000000;
    int maxTX = StrictMath.max(StrictMath.max(ear.previous.x, ear.x), ear.next.x) ^ 0x80000000;
    int maxTY = StrictMath.max(StrictMath.max(ear.previous.y, ear.y), ear.next.y) ^ 0x80000000;

    // z-order range for the current triangle bbox;
    long minZ = BitUtil.interleave(minTX, minTY);
    long maxZ = BitUtil.interleave(maxTX, maxTY);

    // now make sure we don't have other points inside the potential ear;

    // look for points inside the triangle in both directions
    Node p = ear.previousZ;
    Node n = ear.nextZ;
    while (p != null
        && Long.compareUnsigned(p.morton, minZ) >= 0
        && n != null
        && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      if (p.idx != ear.previous.idx
          && p.idx != ear.next.idx
          && pointInEar(
              p.getX(),
              p.getY(),
              ear.previous.getX(),
              ear.previous.getY(),
              ear.getX(),
              ear.getY(),
              ear.next.getX(),
              ear.next.getY())
          && area(
                  p.previous.getX(),
                  p.previous.getY(),
                  p.getX(),
                  p.getY(),
                  p.next.getX(),
                  p.next.getY())
              >= 0) return false;
      p = p.previousZ;

      if (n.idx != ear.previous.idx
          && n.idx != ear.next.idx
          && pointInEar(
              n.getX(),
              n.getY(),
              ear.previous.getX(),
              ear.previous.getY(),
              ear.getX(),
              ear.getY(),
              ear.next.getX(),
              ear.next.getY())
          && area(
                  n.previous.getX(),
                  n.previous.getY(),
                  n.getX(),
                  n.getY(),
                  n.next.getX(),
                  n.next.getY())
              >= 0) return false;
      n = n.nextZ;
    }

    // first look for points inside the triangle in decreasing z-order
    while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
      if (p.idx != ear.previous.idx
          && p.idx != ear.next.idx
          && pointInEar(
              p.getX(),
              p.getY(),
              ear.previous.getX(),
              ear.previous.getY(),
              ear.getX(),
              ear.getY(),
              ear.next.getX(),
              ear.next.getY())
          && area(
                  p.previous.getX(),
                  p.previous.getY(),
                  p.getX(),
                  p.getY(),
                  p.next.getX(),
                  p.next.getY())
              >= 0) {
        return false;
      }
      p = p.previousZ;
    }
    // then look for points in increasing z-order
    while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      if (n.idx != ear.previous.idx
          && n.idx != ear.next.idx
          && pointInEar(
              n.getX(),
              n.getY(),
              ear.previous.getX(),
              ear.previous.getY(),
              ear.getX(),
              ear.getY(),
              ear.next.getX(),
              ear.next.getY())
          && area(
                  n.previous.getX(),
                  n.previous.getY(),
                  n.getX(),
                  n.getY(),
                  n.next.getX(),
                  n.next.getY())
              >= 0) {
        return false;
      }
      n = n.nextZ;
    }
    return true;
  }

  /** Iterate through all polygon nodes and remove small local self-intersections * */
  private static Node cureLocalIntersections(
      Node startNode, final List<Triangle> tessellation, final boolean mortonOptimized) {
    Node node = startNode;
    Node nextNode;
    do {
      nextNode = node.next;
      Node a = node.previous;
      Node b = nextNode.next;

      // a self-intersection where edge (v[i-1],v[i]) intersects (v[i+1],v[i+2])
      if (isVertexEquals(a, b) == false
          && linesIntersect(
              a.getX(),
              a.getY(),
              node.getX(),
              node.getY(),
              nextNode.getX(),
              nextNode.getY(),
              b.getX(),
              b.getY())
          && isLocallyInside(a, b)
          && isLocallyInside(b, a)
          // this call is expensive so do it last
          && isIntersectingPolygon(a, a.getX(), a.getY(), b.getX(), b.getY()) == false) {
        // compute edges from polygon
        boolean abFromPolygon =
            (a.next == node)
                ? a.isNextEdgeFromPolygon
                : isEdgeFromPolygon(a, node, mortonOptimized);
        boolean bcFromPolygon =
            (node.next == b)
                ? node.isNextEdgeFromPolygon
                : isEdgeFromPolygon(node, b, mortonOptimized);
        boolean caFromPolygon =
            (b.next == a) ? b.isNextEdgeFromPolygon : isEdgeFromPolygon(a, b, mortonOptimized);
        tessellation.add(new Triangle(a, abFromPolygon, node, bcFromPolygon, b, caFromPolygon));
        // Return the triangulated vertices to the tessellation
        tessellation.add(new Triangle(a, abFromPolygon, node, bcFromPolygon, b, caFromPolygon));

        // remove two nodes involved
        removeNode(node, caFromPolygon);
        removeNode(node.next, caFromPolygon);
        node = startNode = b;
      }
      node = node.next;
    } while (node != startNode);

    return node;
  }

  /**
   * Attempt to split a polygon and independently triangulate each side. Return true if the polygon
   * was splitted *
   */
  private static boolean splitEarcut(
      final Object polygon,
      final Node start,
      final List<Triangle> tessellation,
      final boolean mortonOptimized,
      final Monitor monitor,
      int depth) {
    // Search for a valid diagonal that divides the polygon into two.
    Node searchNode = start;
    do {
      Node nextNode = searchNode.next;
      Node diagonal = nextNode.next;
      while (diagonal != searchNode.previous) {
        if (searchNode.idx != diagonal.idx && isValidDiagonal(searchNode, diagonal)) {
          // Split the polygon into two at the point of the diagonal
          Node splitNode =
              splitPolygon(
                  searchNode, diagonal, isEdgeFromPolygon(searchNode, diagonal, mortonOptimized));
          // Filter the resulting polygon.
          searchNode = filterPoints(searchNode, searchNode.next);
          splitNode = filterPoints(splitNode, splitNode.next);
          // Attempt to earcut both of the resulting polygons
          if (mortonOptimized) {
            sortByMortonWithReset(searchNode);
            sortByMortonWithReset(splitNode);
          }
          notifyMonitorSplit(depth, monitor, searchNode, splitNode);
          earcutLinkedList(
              polygon, searchNode, tessellation, State.INIT, mortonOptimized, monitor, depth);
          earcutLinkedList(
              polygon, splitNode, tessellation, State.INIT, mortonOptimized, monitor, depth);
          notifyMonitorSplitEnd(depth, monitor);
          // Finish the iterative search
          return true;
        }
        diagonal = diagonal.next;
      }
      searchNode = searchNode.next;
    } while (searchNode != start);
    // if there is some area left, we failed
    return signedArea(start, start) == 0;
  }

  /** Computes if edge defined by a and b overlaps with a polygon edge * */
  private static void checkIntersection(Node a, boolean isMorton) {
    Node next = a.next;
    do {
      Node innerNext = next.next;
      if (isMorton) {
        mortonCheckIntersection(next, innerNext);
      } else {
        do {
          checkIntersectionPoint(next, innerNext);
          innerNext = innerNext.next;
        } while (innerNext != next.previous);
      }
      next = next.next;
    } while (next != a.previous);
  }

  /**
   * Uses morton code for speed to determine whether or not and edge defined by a and b overlaps
   * with a polygon edge
   */
  private static void mortonCheckIntersection(final Node a, final Node b) {
    // edge bbox (flip the bits so negative encoded values are < positive encoded values)
    int minTX = StrictMath.min(a.x, a.next.x) ^ 0x80000000;
    int minTY = StrictMath.min(a.y, a.next.y) ^ 0x80000000;
    int maxTX = StrictMath.max(a.x, a.next.x) ^ 0x80000000;
    int maxTY = StrictMath.max(a.y, a.next.y) ^ 0x80000000;

    // z-order range for the current edge;
    long minZ = BitUtil.interleave(minTX, minTY);
    long maxZ = BitUtil.interleave(maxTX, maxTY);

    // now make sure we don't have other points inside the potential ear;

    // look for points inside edge in both directions
    Node p = b.previousZ;
    Node n = b.nextZ;
    while (p != null
        && Long.compareUnsigned(p.morton, minZ) >= 0
        && n != null
        && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      checkIntersectionPoint(p, a);
      p = p.previousZ;
      checkIntersectionPoint(n, a);
      n = n.nextZ;
    }

    // first look for points inside the edge in decreasing z-order
    while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
      checkIntersectionPoint(p, a);
      p = p.previousZ;
    }
    // then look for points in increasing z-order
    while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      checkIntersectionPoint(n, a);
      n = n.nextZ;
    }
  }

  private static void checkIntersectionPoint(final Node a, final Node b) {
    if (a == b) {
      return;
    }

    if (Math.max(a.getY(), a.next.getY()) <= Math.min(b.getY(), b.next.getY())
        || Math.min(a.getY(), a.next.getY()) >= Math.max(b.getY(), b.next.getY())
        || Math.max(a.getX(), a.next.getX()) <= Math.min(b.getX(), b.next.getX())
        || Math.min(a.getX(), a.next.getX()) >= Math.max(b.getX(), b.next.getX())) {
      return;
    }

    if (lineCrossesLine(
        a.getX(),
        a.getY(),
        a.next.getX(),
        a.next.getY(),
        b.getX(),
        b.getY(),
        b.next.getX(),
        b.next.getY())) {
      // Line AB represented as a1x + b1y = c1
      double a1 = a.next.getY() - a.getY();
      double b1 = a.getX() - a.next.getX();
      double c1 = a1 * (a.getX()) + b1 * (a.getY());

      // Line CD represented as a2x + b2y = c2
      double a2 = b.next.getY() - b.getY();
      double b2 = b.getX() - b.next.getX();
      double c2 = a2 * (b.getX()) + b2 * (b.getY());

      double determinant = a1 * b2 - a2 * b1;

      assert determinant != 0;

      double x = (b2 * c1 - b1 * c2) / determinant;
      double y = (a1 * c2 - a2 * c1) / determinant;

      throw new IllegalArgumentException("Polygon self-intersection at lat=" + y + " lon=" + x);
    }
    if (a.isNextEdgeFromPolygon
        && b.isNextEdgeFromPolygon
        && lineOverlapLine(
            a.getX(),
            a.getY(),
            a.next.getX(),
            a.next.getY(),
            b.getX(),
            b.getY(),
            b.next.getX(),
            b.next.getY())) {
      throw new IllegalArgumentException(
          "Polygon ring self-intersection at lat=" + a.getY() + " lon=" + a.getX());
    }
  }

  /** Computes if edge defined by a and b overlaps with a polygon edge * */
  private static boolean isEdgeFromPolygon(final Node a, final Node b, final boolean isMorton) {
    if (isMorton) {
      return isMortonEdgeFromPolygon(a, b);
    }
    Node next = a;
    do {
      if (isPointInLine(next, next.next, a) && isPointInLine(next, next.next, b)) {
        return next.isNextEdgeFromPolygon;
      }
      if (isPointInLine(next, next.previous, a) && isPointInLine(next, next.previous, b)) {
        return next.previous.isNextEdgeFromPolygon;
      }
      next = next.next;
    } while (next != a);
    return false;
  }

  /**
   * Uses morton code for speed to determine whether or not and edge defined by a and b overlaps
   * with a polygon edge
   */
  private static boolean isMortonEdgeFromPolygon(final Node a, final Node b) {
    // edge bbox (flip the bits so negative encoded values are < positive encoded values)
    final int minTX = StrictMath.min(a.x, b.x) ^ 0x80000000;
    final int minTY = StrictMath.min(a.y, b.y) ^ 0x80000000;
    final int maxTX = StrictMath.max(a.x, b.x) ^ 0x80000000;
    final int maxTY = StrictMath.max(a.y, b.y) ^ 0x80000000;

    // z-order range for the current edge;
    final long minZ = BitUtil.interleave(minTX, minTY);
    final long maxZ = BitUtil.interleave(maxTX, maxTY);

    // now make sure we don't have other points inside the potential ear;

    // look for points inside edge in both directions
    Node p = a.previousZ;
    Node n = a.nextZ;
    while (p != null
        && Long.compareUnsigned(p.morton, minZ) >= 0
        && n != null
        && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      if (isPointInLine(p, p.next, a) && isPointInLine(p, p.next, b)) {
        return p.isNextEdgeFromPolygon;
      }
      if (isPointInLine(p, p.previous, a) && isPointInLine(p, p.previous, b)) {
        return p.previous.isNextEdgeFromPolygon;
      }

      p = p.previousZ;

      if (isPointInLine(n, n.next, a) && isPointInLine(n, n.next, b)) {
        return n.isNextEdgeFromPolygon;
      }
      if (isPointInLine(n, n.previous, a) && isPointInLine(n, n.previous, b)) {
        return n.previous.isNextEdgeFromPolygon;
      }

      n = n.nextZ;
    }

    // first look for points inside the edge in decreasing z-order
    while (p != null && Long.compareUnsigned(p.morton, minZ) >= 0) {
      if (isPointInLine(p, p.next, a) && isPointInLine(p, p.next, b)) {
        return p.isNextEdgeFromPolygon;
      }
      if (isPointInLine(p, p.previous, a) && isPointInLine(p, p.previous, b)) {
        return p.previous.isNextEdgeFromPolygon;
      }
      p = p.previousZ;
    }
    // then look for points in increasing z-order
    while (n != null && Long.compareUnsigned(n.morton, maxZ) <= 0) {
      if (isPointInLine(n, n.next, a) && isPointInLine(n, n.next, b)) {
        return n.isNextEdgeFromPolygon;
      }
      if (isPointInLine(n, n.previous, a) && isPointInLine(n, n.previous, b)) {
        return n.previous.isNextEdgeFromPolygon;
      }
      n = n.nextZ;
    }
    return false;
  }

  private static boolean isPointInLine(final Node a, final Node b, final Node point) {
    return isPointInLine(a, b, point.getX(), point.getY());
  }

  /** returns true if the lon, lat point is colinear w/ the provided a and b point */
  private static boolean isPointInLine(
      final Node a, final Node b, final double lon, final double lat) {
    final double dxc = lon - a.getX();
    final double dyc = lat - a.getY();

    final double dxl = b.getX() - a.getX();
    final double dyl = b.getY() - a.getY();

    if (dxc * dyl - dyc * dxl == 0) {
      if (Math.abs(dxl) >= Math.abs(dyl)) {
        return dxl > 0 ? a.getX() <= lon && lon <= b.getX() : b.getX() <= lon && lon <= a.getX();
      } else {
        return dyl > 0 ? a.getY() <= lat && lat <= b.getY() : b.getY() <= lat && lat <= a.getY();
      }
    }
    return false;
  }

  /** Links two polygon vertices using a bridge. * */
  private static Node splitPolygon(final Node a, final Node b, boolean edgeFromPolygon) {
    final Node a2 = new Node(a);
    final Node b2 = new Node(b);
    final Node an = a.next;
    final Node bp = b.previous;

    a.next = b;
    a.isNextEdgeFromPolygon = edgeFromPolygon;
    a.nextZ = b;
    b.previous = a;
    b.previousZ = a;
    a2.next = an;
    a2.nextZ = an;
    an.previous = a2;
    an.previousZ = a2;
    b2.next = a2;
    b2.isNextEdgeFromPolygon = edgeFromPolygon;
    b2.nextZ = a2;
    a2.previous = b2;
    a2.previousZ = b2;
    bp.next = b2;
    bp.nextZ = b2;

    return b2;
  }

  /**
   * Determines whether a diagonal between two polygon nodes lies within a polygon interior. (This
   * determines the validity of the ray.) *
   */
  private static boolean isValidDiagonal(final Node a, final Node b) {
    if (a.next.idx == b.idx
        || a.previous.idx == b.idx
        // check next edges are locally visible
        || isLocallyInside(a.previous, b) == false
        || isLocallyInside(b.next, a) == false
        // check polygons are CCW in both sides
        || isCWPolygon(a, b) == false
        || isCWPolygon(b, a) == false) {
      return false;
    }
    if (isVertexEquals(a, b)) {
      return true;
    }
    return isLocallyInside(a, b)
        && isLocallyInside(b, a)
        && middleInsert(a, a.getX(), a.getY(), b.getX(), b.getY())
        // make sure we don't introduce collinear lines
        && area(a.previous.getX(), a.previous.getY(), a.getX(), a.getY(), b.getX(), b.getY()) != 0
        && area(a.getX(), a.getY(), b.getX(), b.getY(), b.next.getX(), b.next.getY()) != 0
        && area(a.next.getX(), a.next.getY(), a.getX(), a.getY(), b.getX(), b.getY()) != 0
        && area(a.getX(), a.getY(), b.getX(), b.getY(), b.previous.getX(), b.previous.getY()) != 0
        // this call is expensive so do it last
        && isIntersectingPolygon(a, a.getX(), a.getY(), b.getX(), b.getY()) == false;
  }

  /** Determine whether the polygon defined between node start and node end is CW */
  private static boolean isCWPolygon(final Node start, final Node end) {
    // The polygon must be CW
    return signedArea(start, end) < 0;
  }

  /** Determine the signed area between node start and node end */
  private static double signedArea(final Node start, final Node end) {
    Node next = start;
    double windingSum = 0;
    do {
      // compute signed area
      windingSum +=
          area(
              next.getX(), next.getY(), next.next.getX(), next.next.getY(), end.getX(), end.getY());
      next = next.next;
    } while (next.next != end);
    return windingSum;
  }

  private static boolean isLocallyInside(final Node a, final Node b) {
    double area =
        area(
            a.previous.getX(), a.previous.getY(), a.getX(), a.getY(), a.next.getX(), a.next.getY());
    if (area == 0) {
      // parallel
      return false;
    } else if (area < 0) {
      // if a is cw
      return area(a.getX(), a.getY(), b.getX(), b.getY(), a.next.getX(), a.next.getY()) >= 0
          && area(a.getX(), a.getY(), a.previous.getX(), a.previous.getY(), b.getX(), b.getY())
              >= 0;
    } else {
      // ccw
      return area(a.getX(), a.getY(), b.getX(), b.getY(), a.previous.getX(), a.previous.getY()) < 0
          || area(a.getX(), a.getY(), a.next.getX(), a.next.getY(), b.getX(), b.getY()) < 0;
    }
  }

  /** Determine whether the middle point of a polygon diagonal is contained within the polygon */
  private static boolean middleInsert(
      final Node start, final double x0, final double y0, final double x1, final double y1) {
    Node node = start;
    Node nextNode;
    boolean lIsInside = false;
    final double lDx = (x0 + x1) / 2.0f;
    final double lDy = (y0 + y1) / 2.0f;
    do {
      nextNode = node.next;
      if (node.getY() > lDy != nextNode.getY() > lDy
          && lDx
              < (nextNode.getX() - node.getX())
                      * (lDy - node.getY())
                      / (nextNode.getY() - node.getY())
                  + node.getX()) {
        lIsInside = !lIsInside;
      }
      node = node.next;
    } while (node != start);
    return lIsInside;
  }

  /** Determines if the diagonal of a polygon is intersecting with any polygon elements. * */
  private static boolean isIntersectingPolygon(
      final Node start, final double x0, final double y0, final double x1, final double y1) {
    Node node = start;
    Node nextNode;
    do {
      nextNode = node.next;
      if (isVertexEquals(node, x0, y0) == false && isVertexEquals(node, x1, y1) == false) {
        if (linesIntersect(
            node.getX(), node.getY(), nextNode.getX(), nextNode.getY(), x0, y0, x1, y1)) {
          return true;
        }
      }
      node = nextNode;
    } while (node != start);

    return false;
  }

  /** Determines whether two line segments intersect. * */
  public static boolean linesIntersect(
      final double aX0,
      final double aY0,
      final double aX1,
      final double aY1,
      final double bX0,
      final double bY0,
      final double bX1,
      final double bY1) {
    return (area(aX0, aY0, aX1, aY1, bX0, bY0) > 0) != (area(aX0, aY0, aX1, aY1, bX1, bY1) > 0)
        && (area(bX0, bY0, bX1, bY1, aX0, aY0) > 0) != (area(bX0, bY0, bX1, bY1, aX1, aY1) > 0);
  }

  /** Interlinks polygon nodes in Z-Order. It reset the values on the z values* */
  private static void sortByMortonWithReset(Node start) {
    Node next = start;
    do {
      next.previousZ = next.previous;
      next.nextZ = next.next;
      next = next.next;
    } while (next != start);
    sortByMorton(start);
  }

  /** Interlinks polygon nodes in Z-Order. * */
  private static void sortByMorton(Node start) {
    start.previousZ.nextZ = null;
    start.previousZ = null;
    // Sort the generated ring using Z ordering.
    tathamSort(start);
  }

  /**
   * Simon Tatham's doubly-linked list O(n log n) mergesort see:
   * http://www.chiark.greenend.org.uk/~sgtatham/algorithms/listsort.html
   */
  private static void tathamSort(Node list) {
    Node p, q, e, tail;
    int i, numMerges, pSize, qSize;
    int inSize = 1;

    if (list == null) {
      return;
    }

    do {
      p = list;
      list = null;
      tail = null;
      // count number of merges in this pass
      numMerges = 0;

      while (p != null) {
        ++numMerges;
        // step 'insize' places along from p
        q = p;
        for (i = 0, pSize = 0; i < inSize && q != null; ++i, ++pSize, q = q.nextZ) {}
        // if q hasn't fallen off end, we have two lists to merge
        qSize = inSize;

        // now we have two lists; merge
        while (pSize > 0 || (qSize > 0 && q != null)) {
          if (pSize != 0
              && (qSize == 0 || q == null || Long.compareUnsigned(p.morton, q.morton) <= 0)) {
            e = p;
            p = p.nextZ;
            --pSize;
          } else {
            e = q;
            q = q.nextZ;
            --qSize;
          }

          if (tail != null) {
            tail.nextZ = e;
          } else {
            list = e;
          }
          // maintain reverse pointers
          e.previousZ = tail;
          tail = e;
        }
        // now p has stepped 'insize' places along, and q has too
        p = q;
      }

      tail.nextZ = null;
      inSize *= 2;
    } while (numMerges > 1);
  }

  /** Eliminate colinear/duplicate points from the doubly linked list */
  private static Node filterPoints(final Node start, Node end) {
    if (start == null) {
      return start;
    }

    if (end == null) {
      end = start;
    }

    Node node = start;
    Node nextNode;
    Node prevNode;
    boolean continueIteration;

    do {
      continueIteration = false;
      nextNode = node.next;
      prevNode = node.previous;
      // we can filter points when:
      // 1. they are the same
      // 2.- each one starts and ends in each other
      // 3.- they are collinear and both edges have the same value in .isNextEdgeFromPolygon
      // 4.-  they are collinear and second edge returns over the first edge
      if (isVertexEquals(node, nextNode)
          || isVertexEquals(prevNode, nextNode)
          || ((prevNode.isNextEdgeFromPolygon == node.isNextEdgeFromPolygon
                  || isPointInLine(prevNode, node, nextNode.getX(), nextNode.getY()))
              && area(
                      prevNode.getX(),
                      prevNode.getY(),
                      node.getX(),
                      node.getY(),
                      nextNode.getX(),
                      nextNode.getY())
                  == 0)) {
        // Remove the node
        removeNode(node, prevNode.isNextEdgeFromPolygon);
        node = end = prevNode;

        if (node == nextNode) {
          break;
        }
        continueIteration = true;
      } else {
        node = nextNode;
      }
    } while (continueIteration || node != end);
    return end;
  }

  /**
   * Creates a node and optionally links it with a previous node in a circular doubly-linked list
   */
  private static Node insertNode(
      final double[] x,
      final double[] y,
      int index,
      int vertexIndex,
      final Node lastNode,
      boolean isGeo) {
    final Node node = new Node(x, y, index, vertexIndex, isGeo);
    if (lastNode == null) {
      node.previous = node;
      node.previousZ = node;
      node.next = node;
      node.nextZ = node;
    } else {
      node.next = lastNode.next;
      node.nextZ = lastNode.next;
      node.previous = lastNode;
      node.previousZ = lastNode;
      lastNode.next.previous = node;
      lastNode.nextZ.previousZ = node;
      lastNode.next = node;
      lastNode.nextZ = node;
    }
    return node;
  }

  /** Removes a node from the doubly linked list */
  private static void removeNode(Node node, boolean edgeFromPolygon) {
    node.next.previous = node.previous;
    node.previous.next = node.next;
    node.previous.isNextEdgeFromPolygon = edgeFromPolygon;

    if (node.previousZ != null) {
      node.previousZ.nextZ = node.nextZ;
    }
    if (node.nextZ != null) {
      node.nextZ.previousZ = node.previousZ;
    }
  }

  /** Determines if two point vertices are equal. * */
  private static boolean isVertexEquals(final Node a, final Node b) {
    return isVertexEquals(a, b.getX(), b.getY());
  }

  /** Determines if two point vertices are equal. * */
  private static boolean isVertexEquals(final Node a, final double x, final double y) {
    return a.getX() == x && a.getY() == y;
  }

  /** Compute signed area of triangle, negative means convex angle and positive reflex angle. */
  private static double area(
      final double aX,
      final double aY,
      final double bX,
      final double bY,
      final double cX,
      final double cY) {
    return (bY - aY) * (cX - bX) - (bX - aX) * (cY - bY);
  }

  /** Compute whether point is in a candidate ear */
  private static boolean pointInEar(
      final double x,
      final double y,
      final double ax,
      final double ay,
      final double bx,
      final double by,
      final double cx,
      final double cy) {
    return (cx - x) * (ay - y) - (ax - x) * (cy - y) >= 0
        && (ax - x) * (by - y) - (bx - x) * (ay - y) >= 0
        && (bx - x) * (cy - y) - (cx - x) * (by - y) >= 0;
  }

  /**
   * Implementation of this interface will receive calls with internal data at each step of the
   * triangulation algorithm. This is of use for debugging complex cases, as well as gaining insight
   * into the way the algorithm works. Data provided includes a status string containing the current
   * mode, list of points representing the current linked-list of internal nodes used for
   * triangulation, and a list of triangles so far created by the algorithm.
   */
  public interface Monitor {
    String FAILED = "FAILED";
    String COMPLETED = "COMPLETED";

    /** Each loop of the main earclip algorithm will call this with the current state */
    void currentState(String status, List<Point> points, List<Triangle> tessellation);

    /** When a new polygon split is entered for mode=SPLIT, this is called. */
    void startSplit(String status, List<Point> leftPolygon, List<Point> rightPolygon);

    /** When a polygon split is completed, this is called. */
    void endSplit(String status);
  }

  private static List<Point> getPoints(Node start) {
    Node node = start;
    ArrayList<Point> points = new ArrayList<>();
    do {
      points.add(new Point(node.getY(), node.getX()));
      node = node.next;
    } while (node != start);
    return points;
  }

  private static void notifyMonitorSplit(
      int depth, Monitor monitor, Node searchNode, Node diagonalNode) {
    if (monitor != null) {
      if (searchNode == null || diagonalNode == null)
        throw new IllegalStateException("Invalid split provided to monitor");
      monitor.startSplit("SPLIT[" + depth + "]", getPoints(searchNode), getPoints(diagonalNode));
    }
  }

  private static void notifyMonitorSplitEnd(int depth, Monitor monitor) {
    if (monitor != null) {
      monitor.endSplit("SPLIT[" + depth + "]");
    }
  }

  private static void notifyMonitor(
      State state, int depth, Monitor monitor, Node start, List<Triangle> tessellation) {
    if (monitor != null) {
      notifyMonitor(
          state.name() + (depth == 0 ? "" : "[" + depth + "]"), monitor, start, tessellation);
    }
  }

  private static void notifyMonitor(
      String status, Monitor monitor, Node start, List<Triangle> tessellation) {
    if (monitor != null) {
      if (start == null) {
        monitor.currentState(status, null, tessellation);
      } else {
        monitor.currentState(status, getPoints(start), tessellation);
      }
    }
  }

  /** Circular Doubly-linked list used for polygon coordinates */
  static class Node {
    // node index in the linked list
    private final int idx;
    // vertex index in the polygon
    private final int vrtxIdx;
    // reference to the polygon for lat/lon values;
    private final double[] polyX;
    private final double[] polyY;
    // encoded x value
    private final int x;
    // encoded y value
    private final int y;
    // morton code for sorting
    private final long morton;

    // previous node
    Node previous;
    // next node
    Node next;
    // previous z node
    private Node previousZ;
    // next z node
    private Node nextZ;
    // if the edge from this node to the next node is part of the polygon edges
    private boolean isNextEdgeFromPolygon;

    Node(
        final double[] x,
        final double[] y,
        final int index,
        final int vertexIndex,
        final boolean isGeo) {
      this.idx = index;
      this.vrtxIdx = vertexIndex;
      this.polyX = x;
      this.polyY = y;
      // casting to float is safe as original values for non-geo are represented as floats
      this.y =
          isGeo ? encodeLatitude(polyY[vrtxIdx]) : XYEncodingUtils.encode((float) polyY[vrtxIdx]);
      this.x =
          isGeo ? encodeLongitude(polyX[vrtxIdx]) : XYEncodingUtils.encode((float) polyX[vrtxIdx]);
      this.morton = BitUtil.interleave(this.x ^ 0x80000000, this.y ^ 0x80000000);
      this.previous = null;
      this.next = null;
      this.previousZ = null;
      this.nextZ = null;
      this.isNextEdgeFromPolygon = true;
    }

    /** simple deep copy constructor */
    protected Node(Node other) {
      this.idx = other.idx;
      this.vrtxIdx = other.vrtxIdx;
      this.polyX = other.polyX;
      this.polyY = other.polyY;
      this.morton = other.morton;
      this.x = other.x;
      this.y = other.y;
      this.previous = other.previous;
      this.next = other.next;
      this.previousZ = other.previousZ;
      this.nextZ = other.nextZ;
      this.isNextEdgeFromPolygon = other.isNextEdgeFromPolygon;
    }

    /** get the x value */
    public final double getX() {
      return polyX[vrtxIdx];
    }

    /** get the y value */
    public final double getY() {
      return polyY[vrtxIdx];
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (this.previous == null) builder.append("||-");
      else builder.append(this.previous.idx).append(" <- ");
      builder.append(this.idx);
      if (this.next == null) builder.append(" -||");
      else builder.append(" -> ").append(this.next.idx);
      return builder.toString();
    }
  }

  /** Triangle in the tessellated mesh */
  public static final class Triangle {
    Node[] vertex;
    boolean[] edgeFromPolygon;

    private Triangle(
        Node a,
        boolean isABfromPolygon,
        Node b,
        boolean isBCfromPolygon,
        Node c,
        boolean isCAfromPolygon) {
      this.vertex = new Node[] {a, b, c};
      this.edgeFromPolygon = new boolean[] {isABfromPolygon, isBCfromPolygon, isCAfromPolygon};
    }

    /** get quantized x value for the given vertex */
    public int getEncodedX(int vertex) {
      return this.vertex[vertex].x;
    }

    /** get quantized y value for the given vertex */
    public int getEncodedY(int vertex) {
      return this.vertex[vertex].y;
    }

    /** get y value for the given vertex */
    public double getY(int vertex) {
      return this.vertex[vertex].getY();
    }

    /** get x value for the given vertex */
    public double getX(int vertex) {
      return this.vertex[vertex].getX();
    }

    /** get if edge is shared with the polygon for the given edge */
    public boolean isEdgefromPolygon(int startVertex) {
      return edgeFromPolygon[startVertex];
    }

    /** pretty print the triangle vertices */
    @Override
    public String toString() {
      String result =
          vertex[0].x
              + ", "
              + vertex[0].y
              + " ["
              + edgeFromPolygon[0]
              + "] "
              + vertex[1].x
              + ", "
              + vertex[1].y
              + " ["
              + edgeFromPolygon[1]
              + "] "
              + vertex[2].x
              + ", "
              + vertex[2].y
              + " ["
              + edgeFromPolygon[2]
              + "]";
      return result;
    }
  }
}
