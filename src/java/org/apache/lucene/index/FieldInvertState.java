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
package org.apache.lucene.index;

import org.apache.lucene.util.AttributeSource;

/**
 * This class tracks the number and position / offset parameters of terms
 * being added to the index. The information collected in this class is
 * also used to calculate the normalization factor for a field.
 * 
 * @lucene.experimental
 */
public final class FieldInvertState {
  int position;
  int length;
  int numOverlap;
  int offset;
  int maxTermFrequency;
  int uniqueTermCount;
  float boost;
  AttributeSource attributeSource;

  public FieldInvertState() {
  }

  /**
   * Get total number of terms in this field.
   * @return the length
   */
  public int getLength() {
    return length;
  }

  /**
   * Get the number of terms with <code>positionIncrement == 0</code>.
   * @return the numOverlap
   */
  public int getNumOverlap() {
    return numOverlap;
  }

  /**
   * Get boost value. This is the cumulative product of
   * document boost and field boost for all field instances
   * sharing the same field name.
   * @return the boost
   */
  public float getBoost() {
    return boost;
  }

}
