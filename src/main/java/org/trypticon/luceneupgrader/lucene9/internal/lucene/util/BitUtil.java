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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util; // from org.apache.solr.util rev 555343

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * A variety of high efficiency bit twiddling routines and encoders for primitives.
 *
 * @lucene.internal
 */
public final class BitUtil {

  private BitUtil() {} // no instance

  /**
   * Native byte order.
   *
   * <p>Warning: This constant is {@link ByteOrder#nativeOrder()} only in production environments,
   * during testing we randomize it. If you need to communicate with native APIs (e.g., Java's
   * Panama API), use {@link ByteOrder#nativeOrder()}.
   */
  public static final ByteOrder NATIVE_BYTE_ORDER = getNativeByteOrder();

  private static ByteOrder getNativeByteOrder() {
    try {
      var prop = System.getProperty("tests.seed");
      if (prop != null) {
        return (prop.hashCode() % 2 == 0) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
      }
    } catch (
        @SuppressWarnings("unused")
        SecurityException se) {
      // fall-through
    }
    return ByteOrder.nativeOrder();
  }

  /**
   * A {@link VarHandle} to read/write little endian {@code short} from/to a byte array. Shape:
   * {@code short vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, short
   * val)}
   */
  public static final VarHandle VH_LE_SHORT =
      MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

  /**
   * A {@link VarHandle} to read/write little endian {@code int} from a byte array. Shape: {@code
   * int vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, int val)}
   */
  public static final VarHandle VH_LE_INT =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  /**
   * A {@link VarHandle} to read/write little endian {@code long} from a byte array. Shape: {@code
   * long vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, long val)}
   */
  public static final VarHandle VH_LE_LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  /**
   * A {@link VarHandle} to read/write little endian {@code float} from a byte array. Shape: {@code
   * float vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, float val)}
   */
  public static final VarHandle VH_LE_FLOAT =
      MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);

  /**
   * A {@link VarHandle} to read/write little endian {@code double} from a byte array. Shape: {@code
   * double vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, double val)}
   */
  public static final VarHandle VH_LE_DOUBLE =
      MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

  /**
   * A {@link VarHandle} to read/write native endian {@code short} from/to a byte array. Shape:
   * {@code short vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, short
   * val)}
   *
   * <p>Warning: This handle uses default order only in production environments, during testing we
   * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use {@link
   * ByteOrder#nativeOrder()}.
   */
  public static final VarHandle VH_NATIVE_SHORT =
      MethodHandles.byteArrayViewVarHandle(short[].class, NATIVE_BYTE_ORDER);

  /**
   * A {@link VarHandle} to read/write native endian {@code int} from a byte array. Shape: {@code
   * int vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, int val)}
   *
   * <p>Warning: This handle uses default order only in production environments, during testing we
   * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use {@link
   * ByteOrder#nativeOrder()}.
   */
  public static final VarHandle VH_NATIVE_INT =
      MethodHandles.byteArrayViewVarHandle(int[].class, NATIVE_BYTE_ORDER);

  /**
   * A {@link VarHandle} to read/write native endian {@code long} from a byte array. Shape: {@code
   * long vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, long val)}
   *
   * <p>Warning: This handle uses default order only in production environments, during testing we
   * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use {@link
   * ByteOrder#nativeOrder()}.
   */
  public static final VarHandle VH_NATIVE_LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, NATIVE_BYTE_ORDER);

  /**
   * A {@link VarHandle} to read/write native endian {@code float} from a byte array. Shape: {@code
   * float vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, float val)}
   *
   * <p>Warning: This handle uses default order only in production environments, during testing we
   * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use {@link
   * ByteOrder#nativeOrder()}.
   */
  public static final VarHandle VH_NATIVE_FLOAT =
      MethodHandles.byteArrayViewVarHandle(float[].class, NATIVE_BYTE_ORDER);

  /**
   * A {@link VarHandle} to read/write native endian {@code double} from a byte array. Shape: {@code
   * double vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, double val)}
   *
   * <p>Warning: This handle uses default order only in production environments, during testing we
   * randomize it. If you need to communicate with native APIs (e.g., Java's Panama API), use {@link
   * ByteOrder#nativeOrder()}.
   */
  public static final VarHandle VH_NATIVE_DOUBLE =
      MethodHandles.byteArrayViewVarHandle(double[].class, NATIVE_BYTE_ORDER);

  /**
   * A {@link VarHandle} to read/write big endian {@code short} from a byte array. Shape: {@code
   * short vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, short val)}
   *
   * @deprecated Better use little endian unless it is needed for backwards compatibility.
   */
  @Deprecated
  public static final VarHandle VH_BE_SHORT =
      MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

  /**
   * A {@link VarHandle} to read/write big endian {@code int} from a byte array. Shape: {@code int
   * vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, int val)}
   *
   * @deprecated Better use little endian unless it is needed for backwards compatibility.
   */
  @Deprecated
  public static final VarHandle VH_BE_INT =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

  /**
   * A {@link VarHandle} to read/write big endian {@code long} from a byte array. Shape: {@code long
   * vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, long val)}
   *
   * @deprecated Better use little endian unless it is needed for backwards compatibility.
   */
  @Deprecated
  public static final VarHandle VH_BE_LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

  /**
   * A {@link VarHandle} to read/write big endian {@code float} from a byte array. Shape: {@code
   * float vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, float val)}
   *
   * @deprecated Better use little endian unless it is needed for backwards compatibility.
   */
  @Deprecated
  public static final VarHandle VH_BE_FLOAT =
      MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);

  /**
   * A {@link VarHandle} to read/write big endian {@code double} from a byte array. Shape: {@code
   * double vh.get(byte[] arr, int ofs)} and {@code void vh.set(byte[] arr, int ofs, double val)}
   *
   * @deprecated Better use little endian unless it is needed for backwards compatibility.
   */
  @Deprecated
  public static final VarHandle VH_BE_DOUBLE =
      MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);

  /**
   * returns the next highest power of two, or the current value if it's already a power of two or
   * zero
   */
  public static int nextHighestPowerOfTwo(int v) {
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v++;
    return v;
  }

  /**
   * returns the next highest power of two, or the current value if it's already a power of two or
   * zero
   */
  public static long nextHighestPowerOfTwo(long v) {
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v |= v >> 32;
    v++;
    return v;
  }

  // magic numbers for bit interleaving
  private static final long MAGIC0 = 0x5555555555555555L;
  private static final long MAGIC1 = 0x3333333333333333L;
  private static final long MAGIC2 = 0x0F0F0F0F0F0F0F0FL;
  private static final long MAGIC3 = 0x00FF00FF00FF00FFL;
  private static final long MAGIC4 = 0x0000FFFF0000FFFFL;
  private static final long MAGIC5 = 0x00000000FFFFFFFFL;
  private static final long MAGIC6 = 0xAAAAAAAAAAAAAAAAL;

  // shift values for bit interleaving
  private static final long SHIFT0 = 1;
  private static final long SHIFT1 = 2;
  private static final long SHIFT2 = 4;
  private static final long SHIFT3 = 8;
  private static final long SHIFT4 = 16;

  /**
   * Interleaves the first 32 bits of each long value
   *
   * <p>Adapted from: http://graphics.stanford.edu/~seander/bithacks.html#InterleaveBMN
   */
  public static long interleave(int even, int odd) {
    long v1 = 0x00000000FFFFFFFFL & even;
    long v2 = 0x00000000FFFFFFFFL & odd;
    v1 = (v1 | (v1 << SHIFT4)) & MAGIC4;
    v1 = (v1 | (v1 << SHIFT3)) & MAGIC3;
    v1 = (v1 | (v1 << SHIFT2)) & MAGIC2;
    v1 = (v1 | (v1 << SHIFT1)) & MAGIC1;
    v1 = (v1 | (v1 << SHIFT0)) & MAGIC0;
    v2 = (v2 | (v2 << SHIFT4)) & MAGIC4;
    v2 = (v2 | (v2 << SHIFT3)) & MAGIC3;
    v2 = (v2 | (v2 << SHIFT2)) & MAGIC2;
    v2 = (v2 | (v2 << SHIFT1)) & MAGIC1;
    v2 = (v2 | (v2 << SHIFT0)) & MAGIC0;

    return (v2 << 1) | v1;
  }

  /** Extract just the even-bits value as a long from the bit-interleaved value */
  public static long deinterleave(long b) {
    b &= MAGIC0;
    b = (b ^ (b >>> SHIFT0)) & MAGIC1;
    b = (b ^ (b >>> SHIFT1)) & MAGIC2;
    b = (b ^ (b >>> SHIFT2)) & MAGIC3;
    b = (b ^ (b >>> SHIFT3)) & MAGIC4;
    b = (b ^ (b >>> SHIFT4)) & MAGIC5;
    return b;
  }

  /** flip flops odd with even bits */
  public static long flipFlop(final long b) {
    return ((b & MAGIC6) >>> 1) | ((b & MAGIC0) << 1);
  }

  /** Same as {@link #zigZagEncode(long)} but on integers. */
  public static int zigZagEncode(int i) {
    return (i >> 31) ^ (i << 1);
  }

  /**
   * <a href="https://developers.google.com/protocol-buffers/docs/encoding#types">Zig-zag</a> encode
   * the provided long. Assuming the input is a signed long whose absolute value can be stored on
   * <code>n</code> bits, the returned value will be an unsigned long that can be stored on <code>
   * n+1</code> bits.
   */
  public static long zigZagEncode(long l) {
    return (l >> 63) ^ (l << 1);
  }

  /** Decode an int previously encoded with {@link #zigZagEncode(int)}. */
  public static int zigZagDecode(int i) {
    return ((i >>> 1) ^ -(i & 1));
  }

  /** Decode a long previously encoded with {@link #zigZagEncode(long)}. */
  public static long zigZagDecode(long l) {
    return ((l >>> 1) ^ -(l & 1));
  }
}
