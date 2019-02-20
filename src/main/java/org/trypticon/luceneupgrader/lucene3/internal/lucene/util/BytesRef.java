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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.util.Comparator;
import java.io.UnsupportedEncodingException;

public final class BytesRef implements Comparable<BytesRef>,Cloneable {
  public static final byte[] EMPTY_BYTES = new byte[0];

  public byte[] bytes;

  public int offset;

  public int length;

  public BytesRef() {
    this(EMPTY_BYTES);
  }


  public BytesRef(byte[] bytes, int offset, int length) {
    assert bytes != null;
    assert offset >= 0;
    assert length >= 0;
    assert bytes.length >= offset + length;
    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
  }

  public BytesRef(byte[] bytes) {
    this(bytes, 0, bytes.length);
  }


  public BytesRef(int capacity) {
    this.bytes = new byte[capacity];
  }

  public BytesRef(CharSequence text) {
    this();
    copyChars(text);
  }

  public void copyChars(CharSequence text) {
    assert offset == 0;   // TODO broken if offset != 0
    UnicodeUtil.UTF16toUTF8(text, 0, text.length(), this);
  }

  public void copyChars(char text[], int offset, int length) {
    UnicodeUtil.UTF16toUTF8(text, offset, length, this);
  }

  public boolean bytesEquals(BytesRef other) {
    assert other != null;
    if (length == other.length) {
      int otherUpto = other.offset;
      final byte[] otherBytes = other.bytes;
      final int end = offset + length;
      for(int upto=offset;upto<end;upto++,otherUpto++) {
        if (bytes[upto] != otherBytes[otherUpto]) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public BytesRef clone() {
    return new BytesRef(bytes, offset, length);
  }

  private boolean sliceEquals(BytesRef other, int pos) {
    if (pos < 0 || length - pos < other.length) {
      return false;
    }
    int i = offset + pos;
    int j = other.offset;
    final int k = other.offset + other.length;
    
    while (j < k) {
      if (bytes[i++] != other.bytes[j++]) {
        return false;
      }
    }
    
    return true;
  }
  
  public boolean startsWith(BytesRef other) {
    return sliceEquals(other, 0);
  }

  public boolean endsWith(BytesRef other) {
    return sliceEquals(other, length - other.length);
  }
  

  @Override
  public int hashCode() {
    int hash = 0;
    final int end = offset + length;
    for(int i=offset;i<end;i++) {
      hash = 31 * hash + bytes[i];
    }
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other instanceof BytesRef) {
      return this.bytesEquals((BytesRef) other);
    }
    return false;
  }

  public String utf8ToString() {
    try {
      return new String(bytes, offset, length, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      // should not happen -- UTF8 is presumably supported
      // by all JREs
      throw new RuntimeException(uee);
    }
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
      sb.append(Integer.toHexString(bytes[i]&0xff));
    }
    sb.append(']');
    return sb.toString();
  }

  public void copyBytes(BytesRef other) {
    if (bytes.length - offset < other.length) {
      bytes = new byte[other.length];
      offset = 0;
    }
    System.arraycopy(other.bytes, other.offset, bytes, offset, other.length);
    length = other.length;
  }

  public void append(BytesRef other) {
    int newLen = length + other.length;
    if (bytes.length - offset < newLen) {
      byte[] newBytes = new byte[newLen];
      System.arraycopy(bytes, offset, newBytes, 0, length);
      offset = 0;
      bytes = newBytes;
    }
    System.arraycopy(other.bytes, other.offset, bytes, length+offset, other.length);
    length = newLen;
  }


  public void grow(int newLength) {
    assert offset == 0; // NOTE: senseless if offset != 0
    bytes = ArrayUtil.grow(bytes, newLength);
  }

  public int compareTo(BytesRef other) {
    return utf8SortedAsUnicodeSortOrder.compare(this, other);
  }

  private final static Comparator<BytesRef> utf8SortedAsUnicodeSortOrder = new UTF8SortedAsUnicodeComparator();

  public static Comparator<BytesRef> getUTF8SortedAsUnicodeComparator() {
    return utf8SortedAsUnicodeSortOrder;
  }

  private static class UTF8SortedAsUnicodeComparator implements Comparator<BytesRef> {
    // Only singleton
    private UTF8SortedAsUnicodeComparator() {};

    public int compare(BytesRef a, BytesRef b) {
      final byte[] aBytes = a.bytes;
      int aUpto = a.offset;
      final byte[] bBytes = b.bytes;
      int bUpto = b.offset;
      
      final int aStop = aUpto + Math.min(a.length, b.length);
      while(aUpto < aStop) {
        int aByte = aBytes[aUpto++] & 0xff;
        int bByte = bBytes[bUpto++] & 0xff;

        int diff = aByte - bByte;
        if (diff != 0) {
          return diff;
        }
      }

      // One is a prefix of the other, or, they are equal:
      return a.length - b.length;
    }    
  }

  private final static Comparator<BytesRef> utf8SortedAsUTF16SortOrder = new UTF8SortedAsUTF16Comparator();

  public static Comparator<BytesRef> getUTF8SortedAsUTF16Comparator() {
    return utf8SortedAsUTF16SortOrder;
  }

  private static class UTF8SortedAsUTF16Comparator implements Comparator<BytesRef> {
    // Only singleton
    private UTF8SortedAsUTF16Comparator() {};

    public int compare(BytesRef a, BytesRef b) {

      final byte[] aBytes = a.bytes;
      int aUpto = a.offset;
      final byte[] bBytes = b.bytes;
      int bUpto = b.offset;
      
      final int aStop;
      if (a.length < b.length) {
        aStop = aUpto + a.length;
      } else {
        aStop = aUpto + b.length;
      }

      while(aUpto < aStop) {
        int aByte = aBytes[aUpto++] & 0xff;
        int bByte = bBytes[bUpto++] & 0xff;

        if (aByte != bByte) {

          // See http://icu-project.org/docs/papers/utf16_code_point_order.html#utf-8-in-utf-16-order

          // We know the terms are not equal, but, we may
          // have to carefully fixup the bytes at the
          // difference to match UTF16's sort order:
          
          // NOTE: instead of moving supplementary code points (0xee and 0xef) to the unused 0xfe and 0xff, 
          // we move them to the unused 0xfc and 0xfd [reserved for future 6-byte character sequences]
          // this reserves 0xff for preflex's term reordering (surrogate dance), and if unicode grows such
          // that 6-byte sequences are needed we have much bigger problems anyway.
          if (aByte >= 0xee && bByte >= 0xee) {
            if ((aByte & 0xfe) == 0xee) {
              aByte += 0xe;
            }
            if ((bByte&0xfe) == 0xee) {
              bByte += 0xe;
            }
          }
          return aByte - bByte;
        }
      }

      // One is a prefix of the other, or, they are equal:
      return a.length - b.length;
    }
  }
  
  public static BytesRef deepCopyOf(BytesRef other) {
    BytesRef copy = new BytesRef();
    copy.copyBytes(other);
    return copy;
  }
}
