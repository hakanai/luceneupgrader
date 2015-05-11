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

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;

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
  /**
   * JVM diagnostic features.
   */
  public enum JvmFeature {
    OBJECT_REFERENCE_SIZE("Object reference size estimated using array index scale"),
    ARRAY_HEADER_SIZE("Array header size estimated using array based offset"),
    FIELD_OFFSETS("Shallow instance size based on field offsets"),
    OBJECT_ALIGNMENT("Object alignment retrieved from HotSpotDiagnostic MX bean");

    public final String description;

    JvmFeature(String description) {
      this.description = description;
    }
    
    @Override
    public String toString() {
      return super.name() + " (" + description + ")";
    }
  }

  /** JVM info string for debugging and reports. */
  public final static String JVM_INFO_STRING;

  /** One kilobyte bytes. */
  public static final long ONE_KB = 1024;
  
  /** One megabyte bytes. */
  public static final long ONE_MB = ONE_KB * ONE_KB;

  /** One gigabyte bytes.*/
  public static final long ONE_GB = ONE_KB * ONE_MB;

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
   * Number of bytes to represent an object header (no fields, no alignments).
   */
  public final static int NUM_BYTES_OBJECT_HEADER;

  /**
   * Number of bytes to represent an array header (no content, but with alignments).
   */
  public final static int NUM_BYTES_ARRAY_HEADER;
  
  /**
   * A constant specifying the object alignment boundary inside the JVM. Objects will
   * always take a full multiple of this constant, possibly wasting some space. 
   */
  public final static int NUM_BYTES_OBJECT_ALIGNMENT;

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
   * A handle to <code>sun.misc.Unsafe</code>.
   */
  private final static Object theUnsafe;

  /**
   * All the supported "internal" JVM features detected at clinit. 
   */
  private final static EnumSet<JvmFeature> supportedFeatures;

  /**
   * Initialize constants and try to collect information about the JVM internals. 
   */
  static {
    // Initialize empirically measured defaults. We'll modify them to the current
    // JVM settings later on if possible.
    int referenceSize = Constants.JRE_IS_64BIT ? 8 : 4;

    supportedFeatures = EnumSet.noneOf(JvmFeature.class);

    Class<?> unsafeClass = null;
    Object tempTheUnsafe = null;
    try {
      unsafeClass = Class.forName("sun.misc.Unsafe");
      final Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      tempTheUnsafe = unsafeField.get(null);
    } catch (Exception e) {
      // Ignore.
    }
    theUnsafe = tempTheUnsafe;

    // get object reference size by getting scale factor of Object[] arrays:
    try {
      final Method arrayIndexScaleM = unsafeClass.getMethod("arrayIndexScale", Class.class);
      referenceSize = ((Number) arrayIndexScaleM.invoke(theUnsafe, Object[].class)).intValue();
      supportedFeatures.add(JvmFeature.OBJECT_REFERENCE_SIZE);
    } catch (Exception e) {
      // ignore.
    }

    // "best guess" based on reference size. We will attempt to modify
    // these to exact values if there is supported infrastructure.
    int objectHeader = Constants.JRE_IS_64BIT ? (8 + referenceSize) : 8;
    // The following is objectHeader + NUM_BYTES_INT, but aligned (object alignment)
    // so on 64 bit JVMs it'll be align(16 + 4, @8) = 24.
    int arrayHeader =  Constants.JRE_IS_64BIT ? (8 + 2 * referenceSize) : 12;

    // get the object header size:
    // - first try out if the field offsets are not scaled (see warning in Unsafe docs)
    // - get the object header size by getting the field offset of the first field of a dummy object
    // If the scaling is byte-wise and unsafe is available, enable dynamic size measurement for
    // estimateRamUsage().
    Method tempObjectFieldOffsetMethod = null;
    try {
      final Method objectFieldOffsetM = unsafeClass.getMethod("objectFieldOffset", Field.class);
      final Field dummy1Field = DummyTwoLongObject.class.getDeclaredField("dummy1");
      final int ofs1 = ((Number) objectFieldOffsetM.invoke(theUnsafe, dummy1Field)).intValue();
      final Field dummy2Field = DummyTwoLongObject.class.getDeclaredField("dummy2");
      final int ofs2 = ((Number) objectFieldOffsetM.invoke(theUnsafe, dummy2Field)).intValue();
      if (Math.abs(ofs2 - ofs1) == NUM_BYTES_LONG) {
        final Field baseField = DummyOneFieldObject.class.getDeclaredField("base");
        objectHeader = ((Number) objectFieldOffsetM.invoke(theUnsafe, baseField)).intValue();
        supportedFeatures.add(JvmFeature.FIELD_OFFSETS);
      }
    } catch (Exception e) {
      // Ignore.
    }

    // Get the array header size by retrieving the array base offset
    // (offset of the first element of an array).
    try {
      final Method arrayBaseOffsetM = unsafeClass.getMethod("arrayBaseOffset", Class.class);
      // we calculate that only for byte[] arrays, it's actually the same for all types:
      arrayHeader = ((Number) arrayBaseOffsetM.invoke(theUnsafe, byte[].class)).intValue();
      supportedFeatures.add(JvmFeature.ARRAY_HEADER_SIZE);
    } catch (Exception e) {
      // Ignore.
    }

    NUM_BYTES_OBJECT_REF = referenceSize;
    NUM_BYTES_OBJECT_HEADER = objectHeader;
    NUM_BYTES_ARRAY_HEADER = arrayHeader;
    
    // Try to get the object alignment (the default seems to be 8 on Hotspot, 
    // regardless of the architecture). Retrieval only works with Java 6.
    int objectAlignment = 8;
    try {
      final Class<?> beanClazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
      final Object hotSpotBean = ManagementFactory.newPlatformMXBeanProxy(
        ManagementFactory.getPlatformMBeanServer(),
        "com.sun.management:type=HotSpotDiagnostic",
        beanClazz
      );
      final Method getVMOptionMethod = beanClazz.getMethod("getVMOption", String.class);
      final Object vmOption = getVMOptionMethod.invoke(hotSpotBean, "ObjectAlignmentInBytes");
      objectAlignment = Integer.parseInt(
          vmOption.getClass().getMethod("getValue").invoke(vmOption).toString()
      );
      supportedFeatures.add(JvmFeature.OBJECT_ALIGNMENT);
    } catch (Exception e) {
      // Ignore.
    }

    NUM_BYTES_OBJECT_ALIGNMENT = objectAlignment;

    JVM_INFO_STRING = "[JVM: " +
        Constants.JVM_NAME + ", " + Constants.JVM_VERSION + ", " + Constants.JVM_VENDOR + ", " + 
        Constants.JAVA_VENDOR + ", " + Constants.JAVA_VERSION + "]";
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
