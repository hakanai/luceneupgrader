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


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.BooleanClause;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.QueryVisitor;

public final class RamUsageEstimator {

  public static final long ONE_KB = 1024;
  
  public static final long ONE_MB = ONE_KB * ONE_KB;
  
  public static final long ONE_GB = ONE_KB * ONE_MB;

  private RamUsageEstimator() {}

  public final static boolean COMPRESSED_REFS_ENABLED;

  public final static int NUM_BYTES_OBJECT_REF;

  public final static int NUM_BYTES_OBJECT_HEADER;

  public final static int NUM_BYTES_ARRAY_HEADER;
  
  public final static int NUM_BYTES_OBJECT_ALIGNMENT;

  public static final int QUERY_DEFAULT_RAM_BYTES_USED = 1024;

  public static final int UNKNOWN_DEFAULT_RAM_BYTES_USED = 256;

  public static final Map<Class<?>,Integer> primitiveSizes;

  static {
    Map<Class<?>, Integer> primitiveSizesMap = new IdentityHashMap<>();
    primitiveSizesMap.put(boolean.class, 1);
    primitiveSizesMap.put(byte.class, 1);
    primitiveSizesMap.put(char.class, Integer.valueOf(Character.BYTES));
    primitiveSizesMap.put(short.class, Integer.valueOf(Short.BYTES));
    primitiveSizesMap.put(int.class, Integer.valueOf(Integer.BYTES));
    primitiveSizesMap.put(float.class, Integer.valueOf(Float.BYTES));
    primitiveSizesMap.put(double.class, Integer.valueOf(Double.BYTES));
    primitiveSizesMap.put(long.class, Integer.valueOf(Long.BYTES));

    primitiveSizes = Collections.unmodifiableMap(primitiveSizesMap);
  }

  static final long LONG_CACHE_MIN_VALUE, LONG_CACHE_MAX_VALUE;
  static final int LONG_SIZE, STRING_SIZE;
  
  static final boolean JVM_IS_HOTSPOT_64BIT;
  
  static final String MANAGEMENT_FACTORY_CLASS = "java.lang.management.ManagementFactory";
  static final String HOTSPOT_BEAN_CLASS = "com.sun.management.HotSpotDiagnosticMXBean";

  static {    
    if (Constants.JRE_IS_64BIT) {
      // Try to get compressed oops and object alignment (the default seems to be 8 on Hotspot);
      // (this only works on 64 bit, on 32 bits the alignment and reference size is fixed):
      boolean compressedOops = false;
      int objectAlignment = 8;
      boolean isHotspot = false;
      try {
        final Class<?> beanClazz = Class.forName(HOTSPOT_BEAN_CLASS);
        // we use reflection for this, because the management factory is not part
        // of Java 8's compact profile:
        final Object hotSpotBean = Class.forName(MANAGEMENT_FACTORY_CLASS)
          .getMethod("getPlatformMXBean", Class.class)
          .invoke(null, beanClazz);
        if (hotSpotBean != null) {
          isHotspot = true;
          final Method getVMOptionMethod = beanClazz.getMethod("getVMOption", String.class);
          try {
            final Object vmOption = getVMOptionMethod.invoke(hotSpotBean, "UseCompressedOops");
            compressedOops = Boolean.parseBoolean(
                vmOption.getClass().getMethod("getValue").invoke(vmOption).toString()
            );
          } catch (ReflectiveOperationException | RuntimeException e) {
            isHotspot = false;
          }
          try {
            final Object vmOption = getVMOptionMethod.invoke(hotSpotBean, "ObjectAlignmentInBytes");
            objectAlignment = Integer.parseInt(
                vmOption.getClass().getMethod("getValue").invoke(vmOption).toString()
            );
          } catch (ReflectiveOperationException | RuntimeException e) {
            isHotspot = false;
          }
        }
      } catch (ReflectiveOperationException | RuntimeException e) {
        isHotspot = false;
      }
      JVM_IS_HOTSPOT_64BIT = isHotspot;
      COMPRESSED_REFS_ENABLED = compressedOops;
      NUM_BYTES_OBJECT_ALIGNMENT = objectAlignment;
      // reference size is 4, if we have compressed oops:
      NUM_BYTES_OBJECT_REF = COMPRESSED_REFS_ENABLED ? 4 : 8;
      // "best guess" based on reference size:
      NUM_BYTES_OBJECT_HEADER = 8 + NUM_BYTES_OBJECT_REF;
      // array header is NUM_BYTES_OBJECT_HEADER + NUM_BYTES_INT, but aligned (object alignment):
      NUM_BYTES_ARRAY_HEADER = (int) alignObjectSize(NUM_BYTES_OBJECT_HEADER + Integer.BYTES);
    } else {
      JVM_IS_HOTSPOT_64BIT = false;
      COMPRESSED_REFS_ENABLED = false;
      NUM_BYTES_OBJECT_ALIGNMENT = 8;
      NUM_BYTES_OBJECT_REF = 4;
      NUM_BYTES_OBJECT_HEADER = 8;
      // For 32 bit JVMs, no extra alignment of array header:
      NUM_BYTES_ARRAY_HEADER = NUM_BYTES_OBJECT_HEADER + Integer.BYTES;
    }

    // get min/max value of cached Long class instances:
    long longCacheMinValue = 0;
    while (longCacheMinValue > Long.MIN_VALUE
        && Long.valueOf(longCacheMinValue - 1) == Long.valueOf(longCacheMinValue - 1)) {
      longCacheMinValue -= 1;
    }
    long longCacheMaxValue = -1;
    while (longCacheMaxValue < Long.MAX_VALUE
        && Long.valueOf(longCacheMaxValue + 1) == Long.valueOf(longCacheMaxValue + 1)) {
      longCacheMaxValue += 1;
    }
    LONG_CACHE_MIN_VALUE = longCacheMinValue;
    LONG_CACHE_MAX_VALUE = longCacheMaxValue;
    LONG_SIZE = (int) shallowSizeOfInstance(Long.class);
    STRING_SIZE = (int) shallowSizeOfInstance(String.class);
  }

  public static final long HASHTABLE_RAM_BYTES_PER_ENTRY =
      2 * NUM_BYTES_OBJECT_REF // key + value
          * 2; // hash tables need to be oversized to avoid collisions, assume 2x capacity

  public static final long LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY =
      HASHTABLE_RAM_BYTES_PER_ENTRY
          + 2 * NUM_BYTES_OBJECT_REF; // previous & next references

  public static long alignObjectSize(long size) {
    size += (long) NUM_BYTES_OBJECT_ALIGNMENT - 1L;
    return size - (size % NUM_BYTES_OBJECT_ALIGNMENT);
  }

  public static long sizeOf(Long value) {
    if (value >= LONG_CACHE_MIN_VALUE && value <= LONG_CACHE_MAX_VALUE) {
      return 0;
    }
    return LONG_SIZE;
  }

  public static long sizeOf(byte[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + arr.length);
  }
  
  public static long sizeOf(boolean[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + arr.length);
  }
  
  public static long sizeOf(char[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Character.BYTES * arr.length);
  }

  public static long sizeOf(short[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Short.BYTES * arr.length);
  }
  
  public static long sizeOf(int[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Integer.BYTES * arr.length);
  }
  
  public static long sizeOf(float[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Float.BYTES * arr.length);
  }
  
  public static long sizeOf(long[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Long.BYTES * arr.length);
  }
  
  public static long sizeOf(double[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) Double.BYTES * arr.length);
  }

  public static long sizeOf(String[] arr) {
    long size = shallowSizeOf(arr);
    for (String s : arr) {
      if (s == null) {
        continue;
      }
      size += sizeOf(s);
    }
    return size;
  }

  public static final int MAX_DEPTH = 1;

  public static long sizeOfMap(Map<?, ?> map) {
    return sizeOfMap(map, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED);
  }

  public static long sizeOfMap(Map<?, ?> map, long defSize) {
    return sizeOfMap(map, 0, defSize);
  }

  private static long sizeOfMap(Map<?, ?> map, int depth, long defSize) {
    if (map == null) {
      return 0;
    }
    long size = shallowSizeOf(map);
    if (depth > MAX_DEPTH) {
      return size;
    }
    long sizeOfEntry = -1;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (sizeOfEntry == -1) {
        sizeOfEntry = shallowSizeOf(entry);
      }
      size += sizeOfEntry;
      size += sizeOfObject(entry.getKey(), depth, defSize);
      size += sizeOfObject(entry.getValue(), depth, defSize);
    }
    return alignObjectSize(size);
  }

  public static long sizeOfCollection(Collection<?> collection) {
    return sizeOfCollection(collection, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED);
  }

  public static long sizeOfCollection(Collection<?> collection, long defSize) {
    return sizeOfCollection(collection, 0, defSize);
  }

  private static long sizeOfCollection(Collection<?> collection, int depth, long defSize) {
    if (collection == null) {
      return 0;
    }
    long size = shallowSizeOf(collection);
    if (depth > MAX_DEPTH) {
      return size;
    }
    // assume array-backed collection and add per-object references
    size += NUM_BYTES_ARRAY_HEADER + collection.size() * NUM_BYTES_OBJECT_REF;
    for (Object o : collection) {
      size += sizeOfObject(o, depth, defSize);
    }
    return alignObjectSize(size);
  }

  private static final class RamUsageQueryVisitor extends QueryVisitor {
    long total;
    long defSize;
    Query root;

    RamUsageQueryVisitor(Query root, long defSize) {
      this.root = root;
      this.defSize = defSize;
      if (defSize > 0) {
        total = defSize;
      } else {
        total = shallowSizeOf(root);
      }
    }

    @Override
    public void consumeTerms(Query query, Term... terms) {
      if (query != root) {
        if (defSize > 0) {
          total += defSize;
        } else {
          total += shallowSizeOf(query);
        }
      }
      if (terms != null) {
        total += sizeOf(terms);
      }
    }

    @Override
    public void visitLeaf(Query query) {
      if (query == root) {
        return;
      }
      if (query instanceof Accountable) {
        total += ((Accountable)query).ramBytesUsed();
      } else {
        if (defSize > 0) {
          total += defSize;
        } else {
          total += shallowSizeOf(query);
        }
      }
    }

    @Override
    public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
      return this;
    }
  }

  public static long sizeOf(Query q) {
    return sizeOf(q, QUERY_DEFAULT_RAM_BYTES_USED);
  }

  public static long sizeOf(Query q, long defSize) {
    if (q instanceof Accountable) {
      return ((Accountable)q).ramBytesUsed();
    } else {
      RamUsageQueryVisitor visitor = new RamUsageQueryVisitor(q, defSize);
      q.visit(visitor);
      return alignObjectSize(visitor.total);
    }
  }

  public static long sizeOfObject(Object o) {
    return sizeOfObject(o, 0, UNKNOWN_DEFAULT_RAM_BYTES_USED);
  }

  public static long sizeOfObject(Object o, long defSize) {
    return sizeOfObject(o, 0, defSize);
  }

  private static long sizeOfObject(Object o, int depth, long defSize) {
    if (o == null) {
      return 0;
    }
    long size;
    if (o instanceof Accountable) {
      size = ((Accountable)o).ramBytesUsed();
    } else if (o instanceof String) {
      size = sizeOf((String)o);
    } else if (o instanceof boolean[]) {
      size = sizeOf((boolean[])o);
    } else if (o instanceof byte[]) {
      size = sizeOf((byte[])o);
    } else if (o instanceof char[]) {
      size = sizeOf((char[])o);
    } else if (o instanceof double[]) {
      size = sizeOf((double[])o);
    } else if (o instanceof float[]) {
      size = sizeOf((float[])o);
    } else if (o instanceof int[]) {
      size = sizeOf((int[])o);
    } else if (o instanceof Long) {
      size = sizeOf((Long)o);
    } else if (o instanceof long[]) {
      size = sizeOf((long[])o);
    } else if (o instanceof short[]) {
      size = sizeOf((short[])o);
    } else if (o instanceof String[]) {
      size = sizeOf((String[]) o);
    } else if (o instanceof Query) {
      size = sizeOf((Query)o, defSize);
    } else if (o instanceof Map) {
      size = sizeOfMap((Map) o, ++depth, defSize);
    } else if (o instanceof Collection) {
      size = sizeOfCollection((Collection)o, ++depth, defSize);
    } else {
      if (defSize > 0) {
        size = defSize;
      } else {
        size = shallowSizeOf(o);
      }
    }
    return size;
  }

  public static long sizeOf(Accountable accountable) {
    return accountable.ramBytesUsed();
  }

  public static long sizeOf(String s) {
    if (s == null) {
      return 0;
    }
    // may not be true in Java 9+ and CompactStrings - but we have no way to determine this

    // char[] + hashCode
    long size = STRING_SIZE + (long)NUM_BYTES_ARRAY_HEADER + (long)Character.BYTES * s.length();
    return alignObjectSize(size);
  }

  // Use this method instead of #shallowSizeOf(Object) to avoid costly reflection
  public static long shallowSizeOf(Object[] arr) {
    return alignObjectSize((long) NUM_BYTES_ARRAY_HEADER + (long) NUM_BYTES_OBJECT_REF * arr.length);
  }

  public static long shallowSizeOf(Object obj) {
    if (obj == null) return 0;
    final Class<?> clz = obj.getClass();
    if (clz.isArray()) {
      return shallowSizeOfArray(obj);
    } else {
      return shallowSizeOfInstance(clz);
    }
  }

  public static long shallowSizeOfInstance(Class<?> clazz) {
    if (clazz.isArray())
      throw new IllegalArgumentException("This method does not work with array classes.");
    if (clazz.isPrimitive())
      return primitiveSizes.get(clazz);
    
    long size = NUM_BYTES_OBJECT_HEADER;

    // Walk type hierarchy
    for (;clazz != null; clazz = clazz.getSuperclass()) {
      final Class<?> target = clazz;
      final Field[] fields = AccessController.doPrivileged(new PrivilegedAction<Field[]>() {
        @Override
        public Field[] run() {
          return target.getDeclaredFields();
        }
      });
      for (Field f : fields) {
        if (!Modifier.isStatic(f.getModifiers())) {
          size = adjustForField(size, f);
        }
      }
    }
    return alignObjectSize(size);    
  }

  private static long shallowSizeOfArray(Object array) {
    long size = NUM_BYTES_ARRAY_HEADER;
    final int len = Array.getLength(array);
    if (len > 0) {
      Class<?> arrayElementClazz = array.getClass().getComponentType();
      if (arrayElementClazz.isPrimitive()) {
        size += (long) len * primitiveSizes.get(arrayElementClazz);
      } else {
        size += (long) NUM_BYTES_OBJECT_REF * len;
      }
    }
    return alignObjectSize(size);
  }

  static long adjustForField(long sizeSoFar, final Field f) {
    final Class<?> type = f.getType();
    final int fsize = type.isPrimitive() ? primitiveSizes.get(type) : NUM_BYTES_OBJECT_REF;
    // TODO: No alignments based on field type/ subclass fields alignments?
    return sizeSoFar + fsize;
  }

  public static String humanReadableUnits(long bytes) {
    return humanReadableUnits(bytes, 
        new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT)));
  }

  public static String humanReadableUnits(long bytes, DecimalFormat df) {
    if (bytes / ONE_GB > 0) {
      return df.format((float) bytes / ONE_GB) + " GB";
    } else if (bytes / ONE_MB > 0) {
      return df.format((float) bytes / ONE_MB) + " MB";
    } else if (bytes / ONE_KB > 0) {
      return df.format((float) bytes / ONE_KB) + " KB";
    } else {
      return bytes + " bytes";
    }
  }

  public static long sizeOf(Accountable[] accountables) {
    long size = shallowSizeOf(accountables);
    for (Accountable accountable : accountables) {
      if (accountable != null) {
        size += accountable.ramBytesUsed();
      }
    }
    return size;
  }
}
