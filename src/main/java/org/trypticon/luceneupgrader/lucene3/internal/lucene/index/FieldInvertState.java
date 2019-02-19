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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;

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

  public FieldInvertState(int position, int length, int numOverlap, int offset, float boost) {
    this.position = position;
    this.length = length;
    this.numOverlap = numOverlap;
    this.offset = offset;
    this.boost = boost;
  }

  void reset(float docBoost) {
    position = 0;
    length = 0;
    numOverlap = 0;
    offset = 0;
    maxTermFrequency = 0;
    uniqueTermCount = 0;
    boost = docBoost;
    attributeSource = null;
  }

  public int getPosition() {
    return position;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }
  
  public int getNumOverlap() {
    return numOverlap;
  }

  public void setNumOverlap(int numOverlap) {
    this.numOverlap = numOverlap;
  }
  
  public int getOffset() {
    return offset;
  }

  public float getBoost() {
    return boost;
  }
  
  public void setBoost(float boost) {
    this.boost = boost;
  }

  public int getMaxTermFrequency() {
    return maxTermFrequency;
  }
  
  public int getUniqueTermCount() {
    return uniqueTermCount;
  }
  
  public AttributeSource getAttributeSource() {
    return attributeSource;
  }
}
