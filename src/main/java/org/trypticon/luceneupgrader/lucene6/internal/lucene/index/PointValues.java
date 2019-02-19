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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.BinaryPoint;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.DoublePoint;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.FloatPoint;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.IntPoint;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.LongPoint;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.bkd.BKDWriter;


public abstract class PointValues {

  public static final int MAX_NUM_BYTES = 16;

  public static final int MAX_DIMENSIONS = BKDWriter.MAX_DIMS;


  public static long size(IndexReader reader, String field) throws IOException {
    long size = 0;
    for (LeafReaderContext ctx : reader.leaves()) {
      FieldInfo info = ctx.reader().getFieldInfos().fieldInfo(field);
      if (info == null || info.getPointDimensionCount() == 0) {
        continue;
      }
      PointValues values = ctx.reader().getPointValues();
      size += values.size(field);
    }
    return size;
  }


  public static int getDocCount(IndexReader reader, String field) throws IOException {
    int count = 0;
    for (LeafReaderContext ctx : reader.leaves()) {
      FieldInfo info = ctx.reader().getFieldInfos().fieldInfo(field);
      if (info == null || info.getPointDimensionCount() == 0) {
        continue;
      }
      PointValues values = ctx.reader().getPointValues();
      count += values.getDocCount(field);
    }
    return count;
  }


  public static byte[] getMinPackedValue(IndexReader reader, String field) throws IOException {
    byte[] minValue = null;
    for (LeafReaderContext ctx : reader.leaves()) {
      FieldInfo info = ctx.reader().getFieldInfos().fieldInfo(field);
      if (info == null || info.getPointDimensionCount() == 0) {
        continue;
      }
      PointValues values = ctx.reader().getPointValues();
      byte[] leafMinValue = values.getMinPackedValue(field);
      if (leafMinValue == null) {
        continue;
      }
      if (minValue == null) {
        minValue = leafMinValue.clone();
      } else {
        final int numDimensions = values.getNumDimensions(field);
        final int numBytesPerDimension = values.getBytesPerDimension(field);
        for (int i = 0; i < numDimensions; ++i) {
          int offset = i * numBytesPerDimension;
          if (StringHelper.compare(numBytesPerDimension, leafMinValue, offset, minValue, offset) < 0) {
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
      FieldInfo info = ctx.reader().getFieldInfos().fieldInfo(field);
      if (info == null || info.getPointDimensionCount() == 0) {
        continue;
      }
      PointValues values = ctx.reader().getPointValues();
      byte[] leafMaxValue = values.getMaxPackedValue(field);
      if (leafMaxValue == null) {
        continue;
      }
      if (maxValue == null) {
        maxValue = leafMaxValue.clone();
      } else {
        final int numDimensions = values.getNumDimensions(field);
        final int numBytesPerDimension = values.getBytesPerDimension(field);
        for (int i = 0; i < numDimensions; ++i) {
          int offset = i * numBytesPerDimension;
          if (StringHelper.compare(numBytesPerDimension, leafMaxValue, offset, maxValue, offset) > 0) {
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

    Relation compare(byte[] minPackedValue, byte[] maxPackedValue);

    default void grow(int count) {};
  }


  public abstract void intersect(String fieldName, IntersectVisitor visitor) throws IOException;


  public abstract long estimatePointCount(String fieldName, IntersectVisitor visitor);

  public abstract byte[] getMinPackedValue(String fieldName) throws IOException;

  public abstract byte[] getMaxPackedValue(String fieldName) throws IOException;

  public abstract int getNumDimensions(String fieldName) throws IOException;

  public abstract int getBytesPerDimension(String fieldName) throws IOException;

  public abstract long size(String fieldName);

  public abstract int getDocCount(String fieldName);
}
