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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util;


public final class LongsRef implements Comparable<LongsRef>, Cloneable {
  public static final long[] EMPTY_LONGS = new long[0];

  public long[] longs;
  public int offset;
  public int length;

  public LongsRef() {
    longs = EMPTY_LONGS;
  }

  public LongsRef(int capacity) {
    longs = new long[capacity];
  }

  public LongsRef(long[] longs, int offset, int length) {
    this.longs = longs;
    this.offset = offset;
    this.length = length;
    assert isValid();
  }

  @Override
  public LongsRef clone() {
    return new LongsRef(longs, offset, length);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 0;
    final long end = offset + length;
    for(int i = offset; i < end; i++) {
      result = prime * result + (int) (longs[i] ^ (longs[i]>>>32));
    }
    return result;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other instanceof LongsRef) {
      return this.longsEquals((LongsRef) other);
    }
    return false;
  }

  public boolean longsEquals(LongsRef other) {
    return FutureArrays.equals(this.longs, this.offset, this.offset + this.length, 
                               other.longs, other.offset, other.offset + other.length);
  }

  @Override
  public int compareTo(LongsRef other) {
    return FutureArrays.compare(this.longs, this.offset, this.offset + this.length, 
                                other.longs, other.offset, other.offset + other.length);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    final long end = offset + length;
    for(int i=offset;i<end;i++) {
      if (i > offset) {
        sb.append(' ');
      }
      sb.append(Long.toHexString(longs[i]));
    }
    sb.append(']');
    return sb.toString();
  }
  
  public static LongsRef deepCopyOf(LongsRef other) {
    return new LongsRef(ArrayUtil.copyOfSubArray(other.longs, other.offset, other.offset + other.length), 0, other.length);
  }
  
  public boolean isValid() {
    if (longs == null) {
      throw new IllegalStateException("longs is null");
    }
    if (length < 0) {
      throw new IllegalStateException("length is negative: " + length);
    }
    if (length > longs.length) {
      throw new IllegalStateException("length is out of bounds: " + length + ",longs.length=" + longs.length);
    }
    if (offset < 0) {
      throw new IllegalStateException("offset is negative: " + offset);
    }
    if (offset > longs.length) {
      throw new IllegalStateException("offset out of bounds: " + offset + ",longs.length=" + longs.length);
    }
    if (offset + length < 0) {
      throw new IllegalStateException("offset+length is negative: offset=" + offset + ",length=" + length);
    }
    if (offset + length > longs.length) {
      throw new IllegalStateException("offset+length out of bounds: offset=" + offset + ",length=" + length + ",longs.length=" + longs.length);
    }
    return true;
  }
}
