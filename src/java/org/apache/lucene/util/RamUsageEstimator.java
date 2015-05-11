package org.apache.lucene.util;

/**
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

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Estimates the size (memory representation) of Java objects.
 * 
 * <p>NOTE: Starting with Lucene 3.6, creating instances of this class
 * is deprecated. If you still do this, please note, that instances of
 * {@code RamUsageEstimator} are not thread-safe!
 * It is also deprecated to enable checking of String intern-ness,
 * the new static method no longer allow to do this. Interned strings
 * will be counted as any other object and count for memory usage.
 * 
 * <p>In Lucene 3.6, custom {@code MemoryModel}s were completely
 * removed. The new implementation is now using Hotspot&trade; internals
 * to get the correct scale factors and offsets for calculating
 * memory usage.
 * 
 *
 *
 *
 * 
 * @lucene.internal
 */
public final class RamUsageEstimator {
  public final static int NUM_BYTES_BOOLEAN = 1;
  public final static int NUM_BYTES_BYTE = 1;
  public final static int NUM_BYTES_CHAR = 2;
  public final static int NUM_BYTES_SHORT = 2;
  public final static int NUM_BYTES_INT = 4;
  public final static int NUM_BYTES_FLOAT = 4;
  public final static int NUM_BYTES_LONG = 8;
  public final static int NUM_BYTES_DOUBLE = 8;

  /** 
   * Number of bytes this jvm uses to represent an object reference. 
   */
  public final static int NUM_BYTES_OBJECT_REF;

  /**
   * Sizes of primitive classes.
   */
  private static final Map<Class<?>,Integer> primitiveSizes;
  static {
    primitiveSizes = new IdentityHashMap<Class<?>,Integer>();
    primitiveSizes.put(boolean.class, NUM_BYTES_BOOLEAN);
    primitiveSizes.put(byte.class, NUM_BYTES_BYTE);
    primitiveSizes.put(char.class, NUM_BYTES_CHAR);
    primitiveSizes.put(short.class, NUM_BYTES_SHORT);
    primitiveSizes.put(int.class, NUM_BYTES_INT);
    primitiveSizes.put(float.class, NUM_BYTES_FLOAT);
    primitiveSizes.put(double.class, NUM_BYTES_DOUBLE);
    primitiveSizes.put(long.class, NUM_BYTES_LONG);
  }


  /**
   * Initialize constants and try to collect information about the JVM internals. 
   */
  static {
    // Initialize empirically measured defaults. We'll modify them to the current
    // JVM settings later on if possible.
    NUM_BYTES_OBJECT_REF = Constants.JRE_IS_64BIT ? 8 : 4;
  }

  // Object with just one field to determine the object header size by getting the offset of the dummy field:
  @SuppressWarnings("unused")
  private static final class DummyOneFieldObject {
    public byte base;
  }

  // Another test object for checking, if the difference in offsets of dummy1 and dummy2 is 8 bytes.
  // Only then we can be sure that those are real, unscaled offsets:
  @SuppressWarnings("unused")
  private static final class DummyTwoLongObject {
    public long dummy1, dummy2;
  }

  /** Creates a new instance of {@code RamUsageEstimator} with intern checking
   * enabled. Don't ever use this method, as intern checking is deprecated,
   * because it is not free of side-effects and strains the garbage collector
   * additionally.
   * @deprecated Don't create instances of this class, instead use the static
   * {@code #sizeOf(Object)} method that has no intern checking, too.
   */
  @Deprecated
  public RamUsageEstimator() {
  }

}
