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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.PriorityQueue;

public abstract class TopDocsCollector<T extends ScoreDoc> extends Collector {

  // This is used in case topDocs() is called with illegal parameters, or there
  // simply aren't (enough) results.
  protected static final TopDocs EMPTY_TOPDOCS = new TopDocs(0, new ScoreDoc[0], Float.NaN);
  
  protected PriorityQueue<T> pq;

  protected int totalHits;
  
  protected TopDocsCollector(PriorityQueue<T> pq) {
    this.pq = pq;
  }
  
  protected void populateResults(ScoreDoc[] results, int howMany) {
    for (int i = howMany - 1; i >= 0; i--) { 
      results[i] = pq.pop();
    }
  }

  protected TopDocs newTopDocs(ScoreDoc[] results, int start) {
    return results == null ? EMPTY_TOPDOCS : new TopDocs(totalHits, results);
  }
  
  public int getTotalHits() {
    return totalHits;
  }
  
  protected int topDocsSize() {
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    return totalHits < pq.size() ? totalHits : pq.size();
  }
  
  public TopDocs topDocs() {
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    return topDocs(0, topDocsSize());
  }

  public TopDocs topDocs(int start) {
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    return topDocs(start, topDocsSize());
  }

  public TopDocs topDocs(int start, int howMany) {
    
    // In case pq was populated with sentinel values, there might be less
    // results than pq.size(). Therefore return all results until either
    // pq.size() or totalHits.
    int size = topDocsSize();

    // Don't bother to throw an exception, just return an empty TopDocs in case
    // the parameters are invalid or out of range.
    // TODO: shouldn't we throw IAE if apps give bad params here so they dont
    // have sneaky silent bugs?
    if (start < 0 || start >= size || howMany <= 0) {
      return newTopDocs(null, start);
    }

    // We know that start < pqsize, so just fix howMany. 
    howMany = Math.min(size - start, howMany);
    ScoreDoc[] results = new ScoreDoc[howMany];

    // pq's pop() returns the 'least' element in the queue, therefore need
    // to discard the first ones, until we reach the requested range.
    // Note that this loop will usually not be executed, since the common usage
    // should be that the caller asks for the last howMany results. However it's
    // needed here for completeness.
    for (int i = pq.size() - start - howMany; i > 0; i--) { pq.pop(); }
    
    // Get the requested results from pq.
    populateResults(results, howMany);
    
    return newTopDocs(results, start);
  }

}
