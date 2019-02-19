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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.PrefixQuery;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.TermRangeQuery;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeQuery; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.NumericUtils; // for javadocs

import java.util.Date;   // for javadoc
import java.util.Calendar;   // for javadoc

// do not remove in 3.0, needed for reading old indexes!

@Deprecated
public class DateField {
  
  private DateField() {}

  // make date strings long enough to last a millenium
  private static int DATE_LEN = Long.toString(1000L*365*24*60*60*1000,
					       Character.MAX_RADIX).length();

  public static String MIN_DATE_STRING() {
    return timeToString(0);
  }

  public static String MAX_DATE_STRING() {
    char[] buffer = new char[DATE_LEN];
    char c = Character.forDigit(Character.MAX_RADIX-1, Character.MAX_RADIX);
    for (int i = 0 ; i < DATE_LEN; i++)
      buffer[i] = c;
    return new String(buffer);
  }

  public static String dateToString(Date date) {
    return timeToString(date.getTime());
  }
  public static String timeToString(long time) {
    if (time < 0)
      throw new RuntimeException("time '" + time + "' is too early, must be >= 0");

    String s = Long.toString(time, Character.MAX_RADIX);

    if (s.length() > DATE_LEN)
      throw new RuntimeException("time '" + time + "' is too late, length of string " +
          "representation must be <= " + DATE_LEN);

    // Pad with leading zeros
    if (s.length() < DATE_LEN) {
      StringBuilder sb = new StringBuilder(s);
      while (sb.length() < DATE_LEN)
        sb.insert(0, 0);
      s = sb.toString();
    }

    return s;
  }

  public static long stringToTime(String s) {
    return Long.parseLong(s, Character.MAX_RADIX);
  }
  public static Date stringToDate(String s) {
    return new Date(stringToTime(s));
  }
}
