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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.Serializable;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ArrayUtil;

public class Payload implements Serializable, Cloneable {
  protected byte[] data;
    
  protected int offset;
    
  protected int length;
    
  public Payload() {
    // nothing to do
  }
    
  public Payload(byte[] data) {
    this(data, 0, data.length);
  }

  public Payload(byte[] data, int offset, int length) {
    if (offset < 0 || offset + length > data.length) {
      throw new IllegalArgumentException();
    }
    this.data = data;
    this.offset = offset;
    this.length = length;
  }
    
  public void setData(byte[] data) {
    setData(data, 0, data.length);
  }

  public void setData(byte[] data, int offset, int length) {
    this.data = data;
    this.offset = offset;
    this.length = length;
  }
    
  public byte[] getData() {
    return this.data;
  }
    
  public int getOffset() {
    return this.offset;
  }
    
  public int length() {
    return this.length;
  }
    
  public byte byteAt(int index) {
    if (0 <= index && index < this.length) {
      return this.data[this.offset + index];    
    }
    throw new ArrayIndexOutOfBoundsException(index);
  }
    
  public byte[] toByteArray() {
    byte[] retArray = new byte[this.length];
    System.arraycopy(this.data, this.offset, retArray, 0, this.length);
    return retArray;
  }
    
  public void copyTo(byte[] target, int targetOffset) {
    if (this.length > target.length + targetOffset) {
      throw new ArrayIndexOutOfBoundsException();
    }
    System.arraycopy(this.data, this.offset, target, targetOffset, this.length);
  }

  @Override
  public Object clone() {
    try {
      // Start with a shallow copy of data
      Payload clone = (Payload) super.clone();
      // Only copy the part of data that belongs to this Payload
      if (offset == 0 && length == data.length) {
        // It is the whole thing, so just clone it.
        clone.data = data.clone();
      }
      else {
        // Just get the part
        clone.data = this.toByteArray();
        clone.offset = 0;
      }
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);  // shouldn't happen
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj instanceof Payload) {
      Payload other = (Payload) obj;
      if (length == other.length) {
        for(int i=0;i<length;i++)
          if (data[offset+i] != other.data[other.offset+i])
            return false;
        return true;
      } else
        return false;
    } else
      return false;
  }

  @Override
  public int hashCode() {
    return ArrayUtil.hashCode(data, offset, offset+length);
  }
}
