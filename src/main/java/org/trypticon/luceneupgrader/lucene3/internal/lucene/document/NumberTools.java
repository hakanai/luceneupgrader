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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.NumericRangeQuery; // for javadocs
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.NumericUtils; // for javadocs

// do not remove this class in 3.0, it may be needed to decode old indexes!

@Deprecated
public class NumberTools {

    private static final int RADIX = 36;

    private static final char NEGATIVE_PREFIX = '-';

    // NB: NEGATIVE_PREFIX must be < POSITIVE_PREFIX
    private static final char POSITIVE_PREFIX = '0';

    //NB: this must be less than
    public static final String MIN_STRING_VALUE = NEGATIVE_PREFIX
            + "0000000000000";

    public static final String MAX_STRING_VALUE = POSITIVE_PREFIX
            + "1y2p0ij32e8e7";

    public static final int STR_SIZE = MIN_STRING_VALUE.length();

    public static String longToString(long l) {

        if (l == Long.MIN_VALUE) {
            // special case, because long is not symmetric around zero
            return MIN_STRING_VALUE;
        }

        StringBuilder buf = new StringBuilder(STR_SIZE);

        if (l < 0) {
            buf.append(NEGATIVE_PREFIX);
            l = Long.MAX_VALUE + l + 1;
        } else {
            buf.append(POSITIVE_PREFIX);
        }
        String num = Long.toString(l, RADIX);

        int padLen = STR_SIZE - num.length() - buf.length();
        while (padLen-- > 0) {
            buf.append('0');
        }
        buf.append(num);

        return buf.toString();
    }

    public static long stringToLong(String str) {
        if (str == null) {
            throw new NullPointerException("string cannot be null");
        }
        if (str.length() != STR_SIZE) {
            throw new NumberFormatException("string is the wrong size");
        }

        if (str.equals(MIN_STRING_VALUE)) {
            return Long.MIN_VALUE;
        }

        char prefix = str.charAt(0);
        long l = Long.parseLong(str.substring(1), RADIX);

        if (prefix == POSITIVE_PREFIX) {
            // nop
        } else if (prefix == NEGATIVE_PREFIX) {
            l = l - Long.MAX_VALUE - 1;
        } else {
            throw new NumberFormatException(
                    "string does not begin with the correct prefix");
        }

        return l;
    }
}