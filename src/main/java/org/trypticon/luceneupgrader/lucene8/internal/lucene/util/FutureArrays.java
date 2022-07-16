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

public final class FutureArrays {
  
  private FutureArrays() {} // no instance
  
  // methods in Arrays are defined stupid: they cannot use Objects.checkFromToIndex
  // they throw IAE (vs IOOBE) in the case of fromIndex > toIndex.
  // so this method works just like checkFromToIndex, but with that stupidity added.
  private static void checkFromToIndex(int fromIndex, int toIndex, int length) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex " + fromIndex + " > toIndex " + toIndex);
    }
    if (fromIndex < 0 || toIndex > length) {
      throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + toIndex + ") out-of-bounds for length " + length);
    }
  }

  // byte[]

  public static int mismatch(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++)
      if (a[i+aFromIndex] != b[i+bFromIndex])
        return i;
    return aLen == bLen ? -1 : len;
  }
  
  public static int compareUnsigned(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++) {
      int aByte = a[i+aFromIndex] & 0xFF;
      int bByte = b[i+bFromIndex] & 0xFF;
      int diff = aByte - bByte;
      if (diff != 0) {
        return diff;
      }
    }

    // One is a prefix of the other, or, they are equal:
    return aLen - bLen;
  }
  
  public static boolean equals(byte[] a, int aFromIndex, int aToIndex, byte[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    // lengths differ: cannot be equal
    if (aLen != bLen) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a[i+aFromIndex] != b[i+bFromIndex]) {
        return false;
      }
    }
    return true;
  }

  // char[]

  public static int mismatch(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++)
      if (a[i+aFromIndex] != b[i+bFromIndex])
        return i;
    return aLen == bLen ? -1 : len;
  }
  
  public static int compare(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++) {
      int aInt = a[i+aFromIndex];
      int bInt = b[i+bFromIndex];
      if (aInt > bInt) {
        return 1;
      } else if (aInt < bInt) {
        return -1;
      }
    }

    // One is a prefix of the other, or, they are equal:
    return aLen - bLen;
  }
  
  public static boolean equals(char[] a, int aFromIndex, int aToIndex, char[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    // lengths differ: cannot be equal
    if (aLen != bLen) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a[i+aFromIndex] != b[i+bFromIndex]) {
        return false;
      }
    }
    return true;
  }

  // int[]
  
  public static int compare(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++) {
      int aInt = a[i+aFromIndex];
      int bInt = b[i+bFromIndex];
      if (aInt > bInt) {
        return 1;
      } else if (aInt < bInt) {
        return -1;
      }
    }

    // One is a prefix of the other, or, they are equal:
    return aLen - bLen;
  }
  
  public static boolean equals(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    // lengths differ: cannot be equal
    if (aLen != bLen) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a[i+aFromIndex] != b[i+bFromIndex]) {
        return false;
      }
    }
    return true;
  }
  
  // long[]
  
  public static int compare(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    int len = Math.min(aLen, bLen);
    for (int i = 0; i < len; i++) {
      long aInt = a[i+aFromIndex];
      long bInt = b[i+bFromIndex];
      if (aInt > bInt) {
        return 1;
      } else if (aInt < bInt) {
        return -1;
      }
    }

    // One is a prefix of the other, or, they are equal:
    return aLen - bLen;
  }
  
  public static boolean equals(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
    checkFromToIndex(aFromIndex, aToIndex, a.length);
    checkFromToIndex(bFromIndex, bToIndex, b.length);
    int aLen = aToIndex - aFromIndex;
    int bLen = bToIndex - bFromIndex;
    // lengths differ: cannot be equal
    if (aLen != bLen) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a[i+aFromIndex] != b[i+bFromIndex]) {
        return false;
      }
    }
    return true;
  }
  
}
