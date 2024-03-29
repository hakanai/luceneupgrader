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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.BinaryPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.DoublePoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.FloatPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.IntPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.LatLonPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.FutureArrays;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.bkd.BKDConfig;

public abstract class PointValues {

  public static final int MAX_NUM_BYTES = 16;

  public static final int MAX_DIMENSIONS = BKDConfig.MAX_DIMS;

  public static final int MAX_INDEX_DIMENSIONS = BKDConfig.MAX_INDEX_DIMS;

  public static long size(IndexReader reader, String field) throws IOException {
    long size = 0;
    for (LeafReaderContext ctx : reader.leaves()) {
      PointValues values = ctx.reader().getPointValues(field);
      if (values != null) {
        size += values.size();
      }
    }
    return size;
  }

  public static int getDocCount(IndexReader reader, String field) throws IOException {
    int count = 0;
    for (LeafReaderContext ctx : reader.leaves()) {
      PointValues values = ctx.reader().getPointValues(field);
      if (values != null) {
        count += values.getDocCount();
      }
    }
    return count;
  }

  public static byte[] getMinPackedValue(IndexReader reader, String field) throws IOException {
    byte[] minValue = null;
    for (LeafReaderContext ctx : reader.leaves()) {
      PointValues values = ctx.reader().getPointValues(field);
      if (values == null) {
        continue;
      }
      byte[] leafMinValue = values.getMinPackedValue();
      if (leafMinValue == null) {
        continue;
      }
      if (minValue == null) {
        minValue = leafMinValue.clone();
      } else {
        final int numDimensions = values.getNumIndexDimensions();
        final int numBytesPerDimension = values.getBytesPerDimension();
        for (int i = 0; i < numDimensions; ++i) {
          int offset = i * numBytesPerDimension;
          if (FutureArrays.compareUnsigned(leafMinValue, offset, offset + numBytesPerDimension, minValue, offset, offset + numBytesPerDimension) < 0) {
            System.arraycopy(leafMinValue, offset, minValue, offset, numBytesPerDimension);
          }
        }
      }
    }
    return minValue;
  }

  public static byte[] getMaxPackedValue(IndexReader reader, String field) throws IOException {
    byte[] maxValue = null;
    for (LeafReaderContext ctx : reader.leaves()) {
      PointValues values = ctx.reader().getPointValues(field);
      if (values == null) {
        continue;
      }
      byte[] leafMaxValue = values.getMaxPackedValue();
      if (leafMaxValue == null) {
        continue;
      }
      if (maxValue == null) {
        maxValue = leafMaxValue.clone();
      } else {
        final int numDimensions = values.getNumIndexDimensions();
        final int numBytesPerDimension = values.getBytesPerDimension();
        for (int i = 0; i < numDimensions; ++i) {
          int offset = i * numBytesPerDimension;
          if (FutureArrays.compareUnsigned(leafMaxValue, offset, offset + numBytesPerDimension, maxValue, offset, offset + numBytesPerDimension) > 0) {
            System.arraycopy(leafMaxValue, offset, maxValue, offset, numBytesPerDimension);
          }
        }
      }
    }
    return maxValue;
  }

  protected PointValues() {
  }

  public enum Relation {
    CELL_INSIDE_QUERY,
    CELL_OUTSIDE_QUERY,
    CELL_CROSSES_QUERY
  };

  public interface IntersectVisitor {
    void visit(int docID) throws IOException;

    void visit(int docID, byte[] packedValue) throws IOException;

    default void visit(DocIdSetIterator iterator, byte[] packedValue) throws IOException {
      int docID;
      while ((docID = iterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        visit(docID, packedValue);
      }
    }

    Relation compare(byte[] minPackedValue, byte[] maxPackedValue);

    default void grow(int count) {};
  }

  public abstract void intersect(IntersectVisitor visitor) throws IOException;

  public abstract long estimatePointCount(IntersectVisitor visitor);

  public long estimateDocCount(IntersectVisitor visitor) {
    long estimatedPointCount = estimatePointCount(visitor);
    int docCount = getDocCount();
    double size = size();
    if (estimatedPointCount >= size) {
      // math all docs
      return docCount;
    } else if (size == docCount || estimatedPointCount == 0L ) {
      // if the point count estimate is 0 or we have only single values
      // return this estimate
      return  estimatedPointCount;
    } else {
      // in case of multi values estimate the number of docs using the solution provided in
      // https://math.stackexchange.com/questions/1175295/urn-problem-probability-of-drawing-balls-of-k-unique-colors
      // then approximate the solution for points per doc << size() which results in the expression
      // D * (1 - ((N - n) / N)^(N/D))
      // where D is the total number of docs, N the total number of points and n the estimated point count
      long docEstimate = (long) (docCount * (1d - Math.pow((size - estimatedPointCount) / size, size / docCount)));
      return docEstimate == 0L ? 1L : docEstimate;
    }
  }


  public abstract byte[] getMinPackedValue() throws IOException;

  public abstract byte[] getMaxPackedValue() throws IOException;

  public abstract int getNumDimensions() throws IOException;

  public abstract int getNumIndexDimensions() throws IOException;

  public abstract int getBytesPerDimension() throws IOException;

  public abstract long size();

  public abstract int getDocCount();
}
