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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

class SegmentTermPositionVector extends SegmentTermVector implements TermPositionVector {
  protected int[][] positions;
  protected TermVectorOffsetInfo[][] offsets;
  public static final int[] EMPTY_TERM_POS = new int[0];
  
  public SegmentTermPositionVector(String field, String terms[], int termFreqs[], int[][] positions, TermVectorOffsetInfo[][] offsets) {
    super(field, terms, termFreqs);
    this.offsets = offsets;
    this.positions = positions;
  }

  public TermVectorOffsetInfo[] getOffsets(int index) {
    TermVectorOffsetInfo[] result = TermVectorOffsetInfo.EMPTY_OFFSET_INFO;
    if(offsets == null)
      return null;
    if (index >=0 && index < offsets.length)
    {
      result = offsets[index];
    }
    return result;
  }
  
  public int[] getTermPositions(int index) {
    int[] result = EMPTY_TERM_POS;
    if(positions == null)
      return null;
    if (index >=0 && index < positions.length)
    {
      result = positions[index];
    }
    
    return result;
  }
}