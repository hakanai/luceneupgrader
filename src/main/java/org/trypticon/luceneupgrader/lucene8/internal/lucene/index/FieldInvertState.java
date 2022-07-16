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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.TokenStream; // javadocs
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PayloadAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.AttributeSource;

public final class FieldInvertState {
  final int indexCreatedVersionMajor;
  final String name;
  final IndexOptions indexOptions;
  int position;
  int length;
  int numOverlap;
  int offset;
  int maxTermFrequency;
  int uniqueTermCount;
  // we must track these across field instances (multi-valued case)
  int lastStartOffset = 0;
  int lastPosition = 0;
  AttributeSource attributeSource;

  OffsetAttribute offsetAttribute;
  PositionIncrementAttribute posIncrAttribute;
  PayloadAttribute payloadAttribute;
  TermToBytesRefAttribute termAttribute;
  TermFrequencyAttribute termFreqAttribute;

  public FieldInvertState(int indexCreatedVersionMajor, String name, IndexOptions indexOptions) {
    this.indexCreatedVersionMajor = indexCreatedVersionMajor;
    this.name = name;
    this.indexOptions = indexOptions;
  }
  
  public FieldInvertState(int indexCreatedVersionMajor, String name, IndexOptions indexOptions, int position, int length, int numOverlap, int offset, int maxTermFrequency, int uniqueTermCount) {
    this(indexCreatedVersionMajor, name, indexOptions);
    this.position = position;
    this.length = length;
    this.numOverlap = numOverlap;
    this.offset = offset;
    this.maxTermFrequency = maxTermFrequency;
    this.uniqueTermCount = uniqueTermCount;
  }

  void reset() {
    position = -1;
    length = 0;
    numOverlap = 0;
    offset = 0;
    maxTermFrequency = 0;
    uniqueTermCount = 0;
    lastStartOffset = 0;
    lastPosition = 0;
  }
  
  // TODO: better name?
  void setAttributeSource(AttributeSource attributeSource) {
    if (this.attributeSource != attributeSource) {
      this.attributeSource = attributeSource;
      termAttribute = attributeSource.getAttribute(TermToBytesRefAttribute.class);
      termFreqAttribute = attributeSource.addAttribute(TermFrequencyAttribute.class);
      posIncrAttribute = attributeSource.addAttribute(PositionIncrementAttribute.class);
      offsetAttribute = attributeSource.addAttribute(OffsetAttribute.class);
      payloadAttribute = attributeSource.getAttribute(PayloadAttribute.class);
    }
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

  public int getMaxTermFrequency() {
    return maxTermFrequency;
  }
  
  public int getUniqueTermCount() {
    return uniqueTermCount;
  }

  public AttributeSource getAttributeSource() {
    return attributeSource;
  }
  
  public String getName() {
    return name;
  }

  public int getIndexCreatedVersionMajor() {
    return indexCreatedVersionMajor;
  }
  
  public IndexOptions getIndexOptions() {
    return indexOptions;
  }
}
