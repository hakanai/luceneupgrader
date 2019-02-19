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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util;


import java.util.Arrays;

public class BytesRefBuilder {

  private final BytesRef ref;

  public BytesRefBuilder() {
    ref = new BytesRef();
  }

  public byte[] bytes() {
    return ref.bytes;
  }

  public int length() {
    return ref.length;
  }

  public void setLength(int length) {
    this.ref.length = length;
  }
  
  public byte byteAt(int offset) {
    return ref.bytes[offset];
  }

  public void setByteAt(int offset, byte b) {
    ref.bytes[offset] = b;
  }

  public void grow(int capacity) {
    ref.bytes = ArrayUtil.grow(ref.bytes, capacity);
  }

  public void append(byte b) {
    grow(ref.length + 1);
    ref.bytes[ref.length++] = b;
  }

  public void append(byte[] b, int off, int len) {
    grow(ref.length + len);
    System.arraycopy(b, off, ref.bytes, ref.length, len);
    ref.length += len;
  }

  public void append(BytesRef ref) {
    append(ref.bytes, ref.offset, ref.length);
  }

  public void append(BytesRefBuilder builder) {
    append(builder.get());
  }

  public void clear() {
    setLength(0);
  }

  public void copyBytes(byte[] b, int off, int len) {
    clear();
    append(b, off, len);
  }

  public void copyBytes(BytesRef ref) {
    clear();
    append(ref);
  }

  public void copyBytes(BytesRefBuilder builder) {
    clear();
    append(builder);
  }

  public void copyChars(CharSequence text) {
    copyChars(text, 0, text.length());
  }

  public void copyChars(CharSequence text, int off, int len) {
    grow(len * UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR);
    ref.length = UnicodeUtil.UTF16toUTF8(text, off, len, ref.bytes);
  }

  public void copyChars(char[] text, int off, int len) {
    grow(len * UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR);
    ref.length = UnicodeUtil.UTF16toUTF8(text, off, len, ref.bytes);
  }

  public BytesRef get() {
    assert ref.offset == 0 : "Modifying the offset of the returned ref is illegal";
    return ref;
  }

  public BytesRef toBytesRef() {
    return new BytesRef(Arrays.copyOf(ref.bytes, ref.length));
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
