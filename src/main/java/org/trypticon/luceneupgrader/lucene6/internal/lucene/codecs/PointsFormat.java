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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentWriteState;


public abstract class PointsFormat {

  protected PointsFormat() {
  }

  public abstract PointsWriter fieldsWriter(SegmentWriteState state) throws IOException;


  public abstract PointsReader fieldsReader(SegmentReadState state) throws IOException;

  public static final PointsFormat EMPTY = new PointsFormat() {
      @Override
      public PointsWriter fieldsWriter(SegmentWriteState state) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PointsReader fieldsReader(SegmentReadState state) {
        return new PointsReader() {
          @Override
          public void close() {
          }

          @Override
          public long ramBytesUsed() {
            return 0L;
          }

          @Override
          public void checkIntegrity() {
          }

          @Override
          public void intersect(String fieldName, IntersectVisitor visitor) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public long estimatePointCount(String fieldName, IntersectVisitor visitor) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public byte[] getMinPackedValue(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public byte[] getMaxPackedValue(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public int getNumDimensions(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public int getBytesPerDimension(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public long size(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }

          @Override
          public int getDocCount(String fieldName) {
            throw new IllegalArgumentException("field=\"" + fieldName + "\" was not indexed with points");
          }
        };
      }
    };
}
