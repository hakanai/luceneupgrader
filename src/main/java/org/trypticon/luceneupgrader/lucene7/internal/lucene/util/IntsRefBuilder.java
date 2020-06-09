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


public class IntsRefBuilder {

  private final IntsRef ref;

  public IntsRefBuilder() {
    ref = new IntsRef();
  }

  public int[] ints() {
    return ref.ints;
  }

  public int length() {
    return ref.length;
  }

  public void setLength(int length) {
    this.ref.length = length;
  }

  public void clear() {
    setLength(0);
  }

  public int intAt(int offset) {
    return ref.ints[offset];
  }

  public void setIntAt(int offset, int b) {
    ref.ints[offset] = b;
  }

  public void append(int i) {
    grow(ref.length + 1);
    ref.ints[ref.length++] = i;
  }

  public void grow(int newLength) {
    ref.ints = ArrayUtil.grow(ref.ints, newLength);
  }

  public void copyInts(int[] otherInts, int otherOffset, int otherLength) {
    grow(otherLength);
    System.arraycopy(otherInts, otherOffset, ref.ints, 0, otherLength);
    ref.length = otherLength;
  }

  public void copyInts(IntsRef ints) {
    copyInts(ints.ints, ints.offset, ints.length);
  }

  public void copyUTF8Bytes(BytesRef bytes) {
    grow(bytes.length);
    ref.length = UnicodeUtil.UTF8toUTF32(bytes, ref.ints);
  }

  public IntsRef get() {
    assert ref.offset == 0 : "Modifying the offset of the returned ref is illegal";
    return ref;
  }

  public IntsRef toIntsRef() {
    return IntsRef.deepCopyOf(get());
  }

  @Override
  public boolean equals(Object obj) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
