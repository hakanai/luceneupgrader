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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.util.bkd;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ArrayUtil;

public final class BKDConfig {

  public static final int DEFAULT_MAX_POINTS_IN_LEAF_NODE = 512;

  public static final int MAX_DIMS = 16;

  public static final int MAX_INDEX_DIMS = 8;

  public final int numDims;

  public final int numIndexDims;

  public final int bytesPerDim;

  public final int maxPointsInLeafNode;

  public final int packedBytesLength;

  public final int packedIndexBytesLength;

  public final int bytesPerDoc;

  public BKDConfig(final int numDims, final int numIndexDims, final int bytesPerDim, final int maxPointsInLeafNode) {
    verifyParams(numDims, numIndexDims, bytesPerDim, maxPointsInLeafNode);
    this.numDims = numDims;
    this.numIndexDims = numIndexDims;
    this.bytesPerDim = bytesPerDim;
    this.maxPointsInLeafNode = maxPointsInLeafNode;
    this.packedIndexBytesLength = numIndexDims * bytesPerDim;
    this.packedBytesLength = numDims * bytesPerDim;
    // dimensional values (numDims * bytesPerDim) + docID (int)
    this.bytesPerDoc = this.packedBytesLength + Integer.BYTES;
  }

  private static void verifyParams(final int numDims, final int numIndexDims, final int bytesPerDim, final int maxPointsInLeafNode) {
    // Check inputs are on bounds
    if (numDims < 1 || numDims > MAX_DIMS) {
      throw new IllegalArgumentException("numDims must be 1 .. " + MAX_DIMS + " (got: " + numDims + ")");
    }
    if (numIndexDims < 1 || numIndexDims > MAX_INDEX_DIMS) {
      throw new IllegalArgumentException("numIndexDims must be 1 .. " + MAX_INDEX_DIMS + " (got: " + numIndexDims + ")");
    }
    if (numIndexDims > numDims) {
      throw new IllegalArgumentException("numIndexDims cannot exceed numDims (" + numDims + ") (got: " + numIndexDims + ")");
    }
    if (bytesPerDim <= 0) {
      throw new IllegalArgumentException("bytesPerDim must be > 0; got " + bytesPerDim);
    }
    if (maxPointsInLeafNode <= 0) {
      throw new IllegalArgumentException("maxPointsInLeafNode must be > 0; got " + maxPointsInLeafNode);
    }
    if (maxPointsInLeafNode > ArrayUtil.MAX_ARRAY_LENGTH) {
      throw new IllegalArgumentException("maxPointsInLeafNode must be <= ArrayUtil.MAX_ARRAY_LENGTH (= " + ArrayUtil.MAX_ARRAY_LENGTH + "); got " + maxPointsInLeafNode);
    }
  }
}