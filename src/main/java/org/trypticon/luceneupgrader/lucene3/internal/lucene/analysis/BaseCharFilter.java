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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ArrayUtil;

import java.util.Arrays;

public abstract class BaseCharFilter extends CharFilter {

  private int offsets[];
  private int diffs[];
  private int size = 0;
  
  public BaseCharFilter(CharStream in) {
    super(in);
  }

  @Override
  protected int correct(int currentOff) {
    if (offsets == null || currentOff < offsets[0]) {
      return currentOff;
    }
    
    int hi = size - 1;
    if(currentOff >= offsets[hi])
      return currentOff + diffs[hi];

    int lo = 0;
    int mid = -1;
    
    while (hi >= lo) {
      mid = (lo + hi) >>> 1;
      if (currentOff < offsets[mid])
        hi = mid - 1;
      else if (currentOff > offsets[mid])
        lo = mid + 1;
      else
        return currentOff + diffs[mid];
    }

    if (currentOff < offsets[mid])
      return mid == 0 ? currentOff : currentOff + diffs[mid-1];
    else
      return currentOff + diffs[mid];
  }
  
  protected int getLastCumulativeDiff() {
    return offsets == null ?
      0 : diffs[size-1];
  }


  protected void addOffCorrectMap(int off, int cumulativeDiff) {
    if (offsets == null) {
      offsets = new int[64];
      diffs = new int[64];
    } else if (size == offsets.length) {
      offsets = ArrayUtil.grow(offsets);
      diffs = ArrayUtil.grow(diffs);
    }
    
    assert (size == 0 || off >= offsets[size])
        : "Offset #" + size + "(" + off + ") is less than the last recorded offset "
          + offsets[size] + "\n" + Arrays.toString(offsets) + "\n" + Arrays.toString(diffs);
    
    if (size == 0 || off != offsets[size - 1]) {
      offsets[size] = off;
      diffs[size++] = cumulativeDiff;
    } else { // Overwrite the diff at the last recorded offset
      diffs[size - 1] = cumulativeDiff;
    }
  }
}
