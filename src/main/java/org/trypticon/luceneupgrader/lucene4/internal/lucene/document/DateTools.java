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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeQuery; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.PrefixQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.TermRangeQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;        // for javadocs

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTools {
  
  final static TimeZone GMT = TimeZone.getTimeZone("GMT");

  private static final ThreadLocal<Calendar> TL_CAL = new ThreadLocal<Calendar>() {
    @Override
    protected Calendar initialValue() {
      return Calendar.getInstance(GMT, Locale.ROOT);
    }
  };

  //indexed by format length
  private static final ThreadLocal<SimpleDateFormat[]> TL_FORMATS = new ThreadLocal<SimpleDateFormat[]>() {
    @Override
    protected SimpleDateFormat[] initialValue() {
      SimpleDateFormat[] arr = new SimpleDateFormat[Resolution.MILLISECOND.formatLen+1];
      for (Resolution resolution : Resolution.values()) {
        arr[resolution.formatLen] = (SimpleDateFormat)resolution.format.clone();
      }
      return arr;
    }
  };

  // cannot create, the class has static methods only
  private DateTools() {}

  public static String dateToString(Date date, Resolution resolution) {
    return timeToString(date.getTime(), resolution);
  }

  public static String timeToString(long time, Resolution resolution) {
    final Date date = new Date(round(time, resolution));
    return TL_FORMATS.get()[resolution.formatLen].format(date);
  }
  
  public static long stringToTime(String dateString) throws ParseException {
    return stringToDate(dateString).getTime();
  }

  public static Date stringToDate(String dateString) throws ParseException {
    try {
      return TL_FORMATS.get()[dateString.length()].parse(dateString);
    } catch (Exception e) {
      throw new ParseException("Input is not a valid date string: " + dateString, 0);
    }
  }
  
  public static Date round(Date date, Resolution resolution) {
    return new Date(round(date.getTime(), resolution));
  }
  
  @SuppressWarnings("fallthrough")
  public static long round(long time, Resolution resolution) {
    final Calendar calInstance = TL_CAL.get();
    calInstance.setTimeInMillis(time);
    
    switch (resolution) {
      //NOTE: switch statement fall-through is deliberate
      case YEAR:
        calInstance.set(Calendar.MONTH, 0);
      case MONTH:
        calInstance.set(Calendar.DAY_OF_MONTH, 1);
      case DAY:
        calInstance.set(Calendar.HOUR_OF_DAY, 0);
      case HOUR:
        calInstance.set(Calendar.MINUTE, 0);
      case MINUTE:
        calInstance.set(Calendar.SECOND, 0);
      case SECOND:
        calInstance.set(Calendar.MILLISECOND, 0);
      case MILLISECOND:
        // don't cut off anything
        break;
      default:
        throw new IllegalArgumentException("unknown resolution " + resolution);
    }
    return calInstance.getTimeInMillis();
  }

  public static enum Resolution {
    
    YEAR(4),
    MONTH(6),
    DAY(8),
    HOUR(10),
    MINUTE(12),
    SECOND(14),
    MILLISECOND(17);

    final int formatLen;
    final SimpleDateFormat format;//should be cloned before use, since it's not threadsafe

    Resolution(int formatLen) {
      this.formatLen = formatLen;
      // formatLen 10's place:                     11111111
      // formatLen  1's place:            12345678901234567
      this.format = new SimpleDateFormat("yyyyMMddHHmmssSSS".substring(0,formatLen),Locale.ROOT);
      this.format.setTimeZone(GMT);
    }

    @Override
    public String toString() {
      return super.toString().toLowerCase(Locale.ROOT);
    }

  }

}
