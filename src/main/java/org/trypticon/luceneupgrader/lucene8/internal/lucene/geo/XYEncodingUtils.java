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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.geo;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;


public final class XYEncodingUtils {

  public static final double MIN_VAL_INCL = -Float.MAX_VALUE;
  public static final double MAX_VAL_INCL = Float.MAX_VALUE;

  // No instance:
  private XYEncodingUtils() {
  }

  static float checkVal(float x) {
    if (Float.isFinite(x) == false) {
      throw new IllegalArgumentException("invalid value " + x + "; must be between " + MIN_VAL_INCL + " and " + MAX_VAL_INCL);
    }
    return x;
  }

  public static int encode(float x) {
    return NumericUtils.floatToSortableInt(checkVal(x));
  }

  public static float decode(int encoded) {
    float result = NumericUtils.sortableIntToFloat(encoded);
    assert result >=  MIN_VAL_INCL && result <= MAX_VAL_INCL;
    return result;
  }

  public static float decode(byte[] src, int offset) {
    return decode(NumericUtils.sortableBytesToInt(src, offset));
  }

  static double[] floatArrayToDoubleArray(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    return d;
  }
}
