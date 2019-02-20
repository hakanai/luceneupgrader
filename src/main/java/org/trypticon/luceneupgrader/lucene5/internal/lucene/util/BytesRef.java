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
import java.util.Comparator;

public final class BytesRef implements Comparable<BytesRef>,Cloneable {
  public static final byte[] EMPTY_BYTES = new byte[0];

  public byte[] bytes;

  public int offset;

  public int length;

  public BytesRef() {
    this(EMPTY_BYTES);
  }


  public BytesRef(byte[] bytes, int offset, int length) {
    this.bytes = bytes;
    this.offset = offset;
    this.length = length;
    assert isValid();
  }

  public BytesRef(byte[] bytes) {
    this(bytes, 0, bytes.length);
  }


  public BytesRef(int capacity) {
    this.bytes = new byte[capacity];
  }

  public BytesRef(CharSequence text) {
    this(new byte[UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR * text.length()]);
    length = UnicodeUtil.UTF16toUTF8(text, 0, text.length(), bytes);
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
  

  @Override
  public int hashCode() {
    return StringHelper.murmurhash3_x86_32(this, StringHelper.GOOD_FAST_HASH_SEED);
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
    final char[] ref = new char[length];
    final int len = UnicodeUtil.UTF8toUTF16(bytes, offset, length, ref);
    return new String(ref, 0, len);
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

  @Override
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

    @Override
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

  @Deprecated
  private final static Comparator<BytesRef> utf8SortedAsUTF16SortOrder = new UTF8SortedAsUTF16Comparator();

  @Deprecated
  public static Comparator<BytesRef> getUTF8SortedAsUTF16Comparator() {
    return utf8SortedAsUTF16SortOrder;
  }

  @Deprecated
  private static class UTF8SortedAsUTF16Comparator implements Comparator<BytesRef> {
    // Only singleton
    private UTF8SortedAsUTF16Comparator() {};

    @Override
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
    copy.bytes = Arrays.copyOfRange(other.bytes, other.offset, other.offset + other.length);
    copy.offset = 0;
    copy.length = other.length;
    return copy;
  }
  

  public boolean isValid() {
    if (bytes == null) {
      throw new IllegalStateException("bytes is null");
    }
    if (length < 0) {
      throw new IllegalStateException("length is negative: " + length);
    }
    if (length > bytes.length) {
      throw new IllegalStateException("length is out of bounds: " + length + ",bytes.length=" + bytes.length);
    }
    if (offset < 0) {
      throw new IllegalStateException("offset is negative: " + offset);
    }
    if (offset > bytes.length) {
      throw new IllegalStateException("offset out of bounds: " + offset + ",bytes.length=" + bytes.length);
    }
    if (offset + length < 0) {
      throw new IllegalStateException("offset+length is negative: offset=" + offset + ",length=" + length);
    }
    if (offset + length > bytes.length) {
      throw new IllegalStateException("offset+length out of bounds: offset=" + offset + ",length=" + length + ",bytes.length=" + bytes.length);
    }
    return true;
  }
}
