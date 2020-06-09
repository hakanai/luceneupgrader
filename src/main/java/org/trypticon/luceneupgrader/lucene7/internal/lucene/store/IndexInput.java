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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.store;


import java.io.Closeable;
import java.io.IOException;

public abstract class IndexInput extends DataInput implements Cloneable,Closeable {

  private final String resourceDescription;

  protected IndexInput(String resourceDescription) {
    if (resourceDescription == null) {
      throw new IllegalArgumentException("resourceDescription must not be null");
    }
    this.resourceDescription = resourceDescription;
  }

  @Override
  public abstract void close() throws IOException;

  public abstract long getFilePointer();

  public abstract void seek(long pos) throws IOException;

  public abstract long length();

  @Override
  public String toString() {
    return resourceDescription;
  }
  
  @Override
  public IndexInput clone() {
    return (IndexInput) super.clone();
  }
  
  public abstract IndexInput slice(String sliceDescription, long offset, long length) throws IOException;

  protected String getFullSliceDescription(String sliceDescription) {
    if (sliceDescription == null) {
      // Clones pass null sliceDescription:
      return toString();
    } else {
      return toString() + " [slice=" + sliceDescription + "]";
    }
  }

  public RandomAccessInput randomAccessSlice(long offset, long length) throws IOException {
    final IndexInput slice = slice("randomaccess", offset, length);
    if (slice instanceof RandomAccessInput) {
      // slice() already supports random access
      return (RandomAccessInput) slice;
    } else {
      // return default impl
      return new RandomAccessInput() {
        @Override
        public byte readByte(long pos) throws IOException {
          slice.seek(pos);
          return slice.readByte();
        }
        
        @Override
        public short readShort(long pos) throws IOException {
          slice.seek(pos);
          return slice.readShort();
        }
        
        @Override
        public int readInt(long pos) throws IOException {
          slice.seek(pos);
          return slice.readInt();
        }
        
        @Override
        public long readLong(long pos) throws IOException {
          slice.seek(pos);
          return slice.readLong();
        }

        @Override
        public String toString() {
          return "RandomAccessInput(" + IndexInput.this.toString() + ")";
        }
      };
    }
  }
}
