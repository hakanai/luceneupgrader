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


import java.util.StringTokenizer;




public final class Constants {
  private Constants() {}  // can't construct

  public static final String JVM_VENDOR = System.getProperty("java.vm.vendor");
  public static final String JVM_VERSION = System.getProperty("java.vm.version");
  public static final String JVM_NAME = System.getProperty("java.vm.name");
  public static final String JVM_SPEC_VERSION = System.getProperty("java.specification.version");

  public static final String JAVA_VERSION = System.getProperty("java.version");
 
  public static final String OS_NAME = System.getProperty("os.name");
  public static final boolean LINUX = OS_NAME.startsWith("Linux");
  public static final boolean WINDOWS = OS_NAME.startsWith("Windows");
  public static final boolean SUN_OS = OS_NAME.startsWith("SunOS");
  public static final boolean MAC_OS_X = OS_NAME.startsWith("Mac OS X");
  public static final boolean FREE_BSD = OS_NAME.startsWith("FreeBSD");

  public static final String OS_ARCH = System.getProperty("os.arch");
  public static final String OS_VERSION = System.getProperty("os.version");
  public static final String JAVA_VENDOR = System.getProperty("java.vendor");
  
  private static final int JVM_MAJOR_VERSION;
  private static final int JVM_MINOR_VERSION;
 
  public static final boolean JRE_IS_64BIT;
  
  static {
    final StringTokenizer st = new StringTokenizer(JVM_SPEC_VERSION, ".");
    JVM_MAJOR_VERSION = Integer.parseInt(st.nextToken());
    if (st.hasMoreTokens()) {
      JVM_MINOR_VERSION = Integer.parseInt(st.nextToken());
    } else {
      JVM_MINOR_VERSION = 0;
    }
    boolean is64Bit = false;
    final String x = System.getProperty("sun.arch.data.model");
    if (x != null) {
      is64Bit = x.contains("64");
    } else {
      if (OS_ARCH != null && OS_ARCH.contains("64")) {
        is64Bit = true;
      } else {
        is64Bit = false;
      }
    }
    JRE_IS_64BIT = is64Bit;
  }
  
  public static final boolean JRE_IS_MINIMUM_JAVA8 = JVM_MAJOR_VERSION > 1 || (JVM_MAJOR_VERSION == 1 && JVM_MINOR_VERSION >= 8);
  public static final boolean JRE_IS_MINIMUM_JAVA9 = JVM_MAJOR_VERSION > 1 || (JVM_MAJOR_VERSION == 1 && JVM_MINOR_VERSION >= 9);

  @Deprecated
  public static final String LUCENE_MAIN_VERSION = Version.LATEST.toString();
  
  @Deprecated
  public static final String LUCENE_VERSION = Version.LATEST.toString();
  
}
