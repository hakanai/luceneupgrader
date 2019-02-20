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
import java.util.StringTokenizer;

public abstract class StringHelper {
  public static StringInterner interner = new SimpleStringInterner(1024,8);

    public static String intern(String s) {
    return interner.intern(s);
  }

  public static final int bytesDifference(byte[] bytes1, int len1, byte[] bytes2, int len2) {
    int len = len1 < len2 ? len1 : len2;
    for (int i = 0; i < len; i++)
      if (bytes1[i] != bytes2[i])
        return i;
    return len;
  }
  
  private StringHelper() {
  }
  
  public static Comparator<String> getVersionComparator() {
    return versionComparator;
  }
  
  private static Comparator<String> versionComparator = new Comparator<String>() {
    public int compare(String a, String b) {
      StringTokenizer aTokens = new StringTokenizer(a, ".");
      StringTokenizer bTokens = new StringTokenizer(b, ".");
      
      while (aTokens.hasMoreTokens()) {
        int aToken = Integer.parseInt(aTokens.nextToken());
        if (bTokens.hasMoreTokens()) {
          int bToken = Integer.parseInt(bTokens.nextToken());
          if (aToken != bToken) {
            return aToken < bToken ? -1 : 1;
          }
        } else {
          // a has some extra trailing tokens. if these are all zeroes, thats ok.
          if (aToken != 0) {
            return 1; 
          }
        }
      }
      
      // b has some extra trailing tokens. if these are all zeroes, thats ok.
      while (bTokens.hasMoreTokens()) {
        if (Integer.parseInt(bTokens.nextToken()) != 0)
          return -1;
      }
      
      return 0;
    }
  };
}
