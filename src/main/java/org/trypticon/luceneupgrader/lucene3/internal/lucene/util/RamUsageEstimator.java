package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

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
  public final static int NUM_BYTES_CHAR = 2;
  public final static int NUM_BYTES_INT = 4;
  public final static int NUM_BYTES_LONG = 8;

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
   */
  private RamUsageEstimator() {
  }

}
