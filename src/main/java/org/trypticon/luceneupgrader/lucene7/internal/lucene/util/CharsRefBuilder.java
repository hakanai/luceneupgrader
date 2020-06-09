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


public class CharsRefBuilder implements Appendable {

  private static final String NULL_STRING = "null";

  private final CharsRef ref;

  public CharsRefBuilder() {
    ref = new CharsRef();
  }

  public char[] chars() {
    return ref.chars;
  }

  public int length() {
    return ref.length;
  }

  public void setLength(int length) {
    this.ref.length = length;
  }

  public char charAt(int offset) {
    return ref.chars[offset];
  }

  public void setCharAt(int offset, char b) {
    ref.chars[offset] = b;
  }

  public void clear() {
    ref.length = 0;
  }

  @Override
  public CharsRefBuilder append(CharSequence csq) {
    if (csq == null) {
      return append(NULL_STRING);
    }
    return append(csq, 0, csq.length());
  }

  @Override
  public CharsRefBuilder append(CharSequence csq, int start, int end) {
    if (csq == null) {
      return append(NULL_STRING);
    }
    grow(ref.length + end - start);
    for (int i = start; i < end; ++i) {
      setCharAt(ref.length++, csq.charAt(i));
    }
    return this;
  }

  @Override
  public CharsRefBuilder append(char c) {
    grow(ref.length + 1);
    setCharAt(ref.length++, c);
    return this;
  }

  public void copyChars(CharsRef other) {
    copyChars(other.chars, other.offset, other.length);
  }

  public void grow(int newLength) {
    ref.chars = ArrayUtil.grow(ref.chars, newLength);
  }

  public void copyUTF8Bytes(byte[] bytes, int offset, int length) {
    grow(length);
    ref.length = UnicodeUtil.UTF8toUTF16(bytes, offset, length, ref.chars);
  }

  public void copyUTF8Bytes(BytesRef bytes) {
    copyUTF8Bytes(bytes.bytes, bytes.offset, bytes.length);
  }

  public void copyChars(char[] otherChars, int otherOffset, int otherLength) {
    grow(otherLength);
    System.arraycopy(otherChars, otherOffset, ref.chars, 0, otherLength);
    ref.length = otherLength;
  }

  public void append(char[] otherChars, int otherOffset, int otherLength) {
    int newLen = ref.length + otherLength;
    grow(newLen);
    System.arraycopy(otherChars, otherOffset, ref.chars, ref.length, otherLength);
    ref.length = newLen;
  }

  public CharsRef get() {
    assert ref.offset == 0 : "Modifying the offset of the returned ref is illegal";
    return ref;
  }

  public CharsRef toCharsRef() {
    return new CharsRef(ArrayUtil.copyOfSubArray(ref.chars, 0, ref.length), 0, ref.length);
  }

  @Override
  public String toString() {
    return get().toString();
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
