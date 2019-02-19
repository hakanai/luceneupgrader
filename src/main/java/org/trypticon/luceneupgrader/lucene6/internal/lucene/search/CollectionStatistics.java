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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader; // javadocs
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Terms;       // javadocs


public class CollectionStatistics {
  private final String field;
  private final long maxDoc;
  private final long docCount;
  private final long sumTotalTermFreq;
  private final long sumDocFreq;
  
  public CollectionStatistics(String field, long maxDoc, long docCount, long sumTotalTermFreq, long sumDocFreq) {
    assert maxDoc >= 0;
    assert docCount >= -1 && docCount <= maxDoc; // #docs with field must be <= #docs
    assert sumDocFreq == -1 || sumDocFreq >= docCount; // #postings must be >= #docs with field
    assert sumTotalTermFreq == -1 || sumTotalTermFreq >= sumDocFreq; // #positions must be >= #postings
    this.field = field;
    this.maxDoc = maxDoc;
    this.docCount = docCount;
    this.sumTotalTermFreq = sumTotalTermFreq;
    this.sumDocFreq = sumDocFreq;
  }
  
  public final String field() {
    return field;
  }
  

  public final long maxDoc() {
    return maxDoc;
  }
  

  public final long docCount() {
    return docCount;
  }
  
  public final long sumTotalTermFreq() {
    return sumTotalTermFreq;
  }
  
  public final long sumDocFreq() {
    return sumDocFreq;
  }
}
