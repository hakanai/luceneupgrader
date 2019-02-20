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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Explanation;

public abstract class DocValues {
  /*
   * DocValues is distinct from ValueSource because
   * there needs to be an object created at query evaluation time that
   * is not referenced by the query itself because:
   * - Query objects should be MT safe
   * - For caching, Query objects are often used as keys... you don't
   *   want the Query carrying around big objects
   */

  public abstract float floatVal(int doc);
  
  public int intVal(int doc) {
    return (int) floatVal(doc);
  }
  
  public long longVal(int doc) {
    return (long) floatVal(doc);
  }

  public double doubleVal(int doc) {
    return floatVal(doc);
  }
  
  public String strVal(int doc) {
    return Float.toString(floatVal(doc));
  }
  
  public abstract String toString(int doc);
  
  public Explanation explain(int doc) {
    return new Explanation(floatVal(doc), toString(doc));
  }
  
  Object getInnerArray() {
    throw new UnsupportedOperationException("this optional method is for test purposes only");
  }

  // --- some simple statistics on values
  private float minVal = Float.NaN;
  private float maxVal = Float.NaN;
  private float avgVal = Float.NaN;
  private boolean computed=false;
  // compute optional values
  private void compute() {
    if (computed) {
      return;
    }
    float sum = 0;
    int n = 0;
    while (true) {
      float val;
      try {
        val = floatVal(n);
      } catch (ArrayIndexOutOfBoundsException e) {
        break;
      }
      sum += val;
      minVal = Float.isNaN(minVal) ? val : Math.min(minVal, val);
      maxVal = Float.isNaN(maxVal) ? val : Math.max(maxVal, val);
      ++n;
    }

    avgVal = n == 0 ? Float.NaN : sum / n;
    computed = true;
  }

  public float getMinValue() {
    compute();
    return minVal;
  }

  public float getMaxValue() {
    compute();
    return maxVal;
  }

  public float getAverageValue() {
    compute();
    return avgVal;
  }

}
