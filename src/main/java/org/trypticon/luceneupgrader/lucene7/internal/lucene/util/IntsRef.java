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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util;


public final class IntsRef implements Comparable<IntsRef>, Cloneable {
  public static final int[] EMPTY_INTS = new int[0];

  public int[] ints;
  public int offset;
  public int length;

  public IntsRef() {
    ints = EMPTY_INTS;
  }

  public IntsRef(int capacity) {
    ints = new int[capacity];
  }

  public IntsRef(int[] ints, int offset, int length) {
    this.ints = ints;
    this.offset = offset;
    this.length = length;
    assert isValid();
  }

  @Override
  public IntsRef clone() {
    return new IntsRef(ints, offset, length);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 0;
    final int end = offset + length;
    for(int i = offset; i < end; i++) {
      result = prime * result + ints[i];
    }
    return result;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other instanceof IntsRef) {
      return this.intsEquals((IntsRef) other);
    }
    return false;
  }

  public boolean intsEquals(IntsRef other) {
    return FutureArrays.equals(this.ints, this.offset, this.offset + this.length, 
                               other.ints, other.offset, other.offset + other.length);
  }

  @Override
  public int compareTo(IntsRef other) {
    return FutureArrays.compare(this.ints, this.offset, this.offset + this.length, 
                                other.ints, other.offset, other.offset + other.length);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    final int end = offset + length;
    for(int i=offset;i<end;i++) {
      if (i > offset) {
        sb.append(' ');
      }
      sb.append(Integer.toHexString(ints[i]));
    }
    sb.append(']');
    return sb.toString();
  }
  
  public static IntsRef deepCopyOf(IntsRef other) {
    return new IntsRef(ArrayUtil.copyOfSubArray(other.ints, other.offset, other.offset + other.length), 0, other.length);
  }
  
  public boolean isValid() {
    if (ints == null) {
      throw new IllegalStateException("ints is null");
    }
    if (length < 0) {
      throw new IllegalStateException("length is negative: " + length);
    }
    if (length > ints.length) {
      throw new IllegalStateException("length is out of bounds: " + length + ",ints.length=" + ints.length);
    }
    if (offset < 0) {
      throw new IllegalStateException("offset is negative: " + offset);
    }
    if (offset > ints.length) {
      throw new IllegalStateException("offset out of bounds: " + offset + ",ints.length=" + ints.length);
    }
    if (offset + length < 0) {
      throw new IllegalStateException("offset+length is negative: offset=" + offset + ",length=" + length);
    }
    if (offset + length > ints.length) {
      throw new IllegalStateException("offset+length out of bounds: offset=" + offset + ",length=" + length + ",ints.length=" + ints.length);
    }
    return true;
  }
}
