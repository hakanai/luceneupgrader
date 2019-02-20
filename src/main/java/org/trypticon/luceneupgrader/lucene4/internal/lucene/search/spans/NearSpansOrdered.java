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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.spans;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InPlaceMergeSorter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NearSpansOrdered extends Spans {
  private final int allowedSlop;
  private boolean firstTime = true;
  private boolean more = false;

  private final Spans[] subSpans;

  private boolean inSameDoc = false;

  private int matchDoc = -1;
  private int matchStart = -1;
  private int matchEnd = -1;
  private List<byte[]> matchPayload;

  private final Spans[] subSpansByDoc;
  // Even though the array is probably almost sorted, InPlaceMergeSorter will likely
  // perform better since it has a lower overhead than TimSorter for small arrays
  private final InPlaceMergeSorter sorter = new InPlaceMergeSorter() {
    @Override
    protected void swap(int i, int j) {
      ArrayUtil.swap(subSpansByDoc, i, j);
    }
    @Override
    protected int compare(int i, int j) {
      return subSpansByDoc[i].doc() - subSpansByDoc[j].doc();
    }
  };

  private SpanNearQuery query;
  private boolean collectPayloads = true;
  
  public NearSpansOrdered(SpanNearQuery spanNearQuery, AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts) throws IOException {
    this(spanNearQuery, context, acceptDocs, termContexts, true);
  }

  public NearSpansOrdered(SpanNearQuery spanNearQuery, AtomicReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts, boolean collectPayloads)
  throws IOException {
    if (spanNearQuery.getClauses().length < 2) {
      throw new IllegalArgumentException("Less than 2 clauses: "
                                         + spanNearQuery);
    }
    this.collectPayloads = collectPayloads;
    allowedSlop = spanNearQuery.getSlop();
    SpanQuery[] clauses = spanNearQuery.getClauses();
    subSpans = new Spans[clauses.length];
    matchPayload = new LinkedList<>();
    subSpansByDoc = new Spans[clauses.length];
    for (int i = 0; i < clauses.length; i++) {
      subSpans[i] = clauses[i].getSpans(context, acceptDocs, termContexts);
      subSpansByDoc[i] = subSpans[i]; // used in toSameDoc()
    }
    query = spanNearQuery; // kept for toString() only.
  }

  // inherit javadocs
  @Override
  public int doc() { return matchDoc; }

  // inherit javadocs
  @Override
  public int start() { return matchStart; }

  // inherit javadocs
  @Override
  public int end() { return matchEnd; }
  
  public Spans[] getSubSpans() {
    return subSpans;
  }  

  // TODO: Remove warning after API has been finalized
  // TODO: Would be nice to be able to lazy load payloads
  @Override
  public Collection<byte[]> getPayload() throws IOException {
    return matchPayload;
  }

  // TODO: Remove warning after API has been finalized
  @Override
  public boolean isPayloadAvailable() {
    return matchPayload.isEmpty() == false;
  }

  @Override
  public long cost() {
    long minCost = Long.MAX_VALUE;
    for (int i = 0; i < subSpans.length; i++) {
      minCost = Math.min(minCost, subSpans[i].cost());
    }
    return minCost;
  }

  // inherit javadocs
  @Override
  public boolean next() throws IOException {
    if (firstTime) {
      firstTime = false;
      for (int i = 0; i < subSpans.length; i++) {
        if (! subSpans[i].next()) {
          more = false;
          return false;
        }
      }
      more = true;
    }
    if(collectPayloads) {
      matchPayload.clear();
    }
    return advanceAfterOrdered();
  }

  // inherit javadocs
  @Override
  public boolean skipTo(int target) throws IOException {
    if (firstTime) {
      firstTime = false;
      for (int i = 0; i < subSpans.length; i++) {
        if (! subSpans[i].skipTo(target)) {
          more = false;
          return false;
        }
      }
      more = true;
    } else if (more && (subSpans[0].doc() < target)) {
      if (subSpans[0].skipTo(target)) {
        inSameDoc = false;
      } else {
        more = false;
        return false;
      }
    }
    if(collectPayloads) {
      matchPayload.clear();
    }
    return advanceAfterOrdered();
  }
  

  private boolean advanceAfterOrdered() throws IOException {
    while (more && (inSameDoc || toSameDoc())) {
      if (stretchToOrder() && shrinkToAfterShortestMatch()) {
        return true;
      }
    }
    return false; // no more matches
  }


  private boolean toSameDoc() throws IOException {
    sorter.sort(0, subSpansByDoc.length);
    int firstIndex = 0;
    int maxDoc = subSpansByDoc[subSpansByDoc.length - 1].doc();
    while (subSpansByDoc[firstIndex].doc() != maxDoc) {
      if (! subSpansByDoc[firstIndex].skipTo(maxDoc)) {
        more = false;
        inSameDoc = false;
        return false;
      }
      maxDoc = subSpansByDoc[firstIndex].doc();
      if (++firstIndex == subSpansByDoc.length) {
        firstIndex = 0;
      }
    }
    for (int i = 0; i < subSpansByDoc.length; i++) {
      assert (subSpansByDoc[i].doc() == maxDoc)
             : " NearSpansOrdered.toSameDoc() spans " + subSpansByDoc[0]
                                 + "\n at doc " + subSpansByDoc[i].doc()
                                 + ", but should be at " + maxDoc;
    }
    inSameDoc = true;
    return true;
  }
  

  static final boolean docSpansOrderedNonOverlap(Spans spans1, Spans spans2) {
    assert spans1.doc() == spans2.doc() : "doc1 " + spans1.doc() + " != doc2 " + spans2.doc();
    assert spans1.start() < spans1.end();
    assert spans2.start() < spans2.end();
    return spans1.end() <= spans2.start();
  }


  private static final boolean docSpansOrderedNonOverlap(int start1, int end1, int start2, int end2) {
    assert start1 < end1;
    assert start2 < end2;
    return end1 <= start2;
  }


  private boolean stretchToOrder() throws IOException {
    matchDoc = subSpans[0].doc();
    for (int i = 1; inSameDoc && (i < subSpans.length); i++) {
      while (! docSpansOrderedNonOverlap(subSpans[i-1], subSpans[i])) {
        if (! subSpans[i].next()) {
          inSameDoc = false;
          more = false;
          break;
        } else if (matchDoc != subSpans[i].doc()) {
          inSameDoc = false;
          break;
        }
      }
    }
    return inSameDoc;
  }


  private boolean shrinkToAfterShortestMatch() throws IOException {
    matchStart = subSpans[subSpans.length - 1].start();
    matchEnd = subSpans[subSpans.length - 1].end();
    Set<byte[]> possibleMatchPayloads = new HashSet<>();
    if (subSpans[subSpans.length - 1].isPayloadAvailable()) {
      possibleMatchPayloads.addAll(subSpans[subSpans.length - 1].getPayload());
    }

    Collection<byte[]> possiblePayload = null;
    
    int matchSlop = 0;
    int lastStart = matchStart;
    int lastEnd = matchEnd;
    for (int i = subSpans.length - 2; i >= 0; i--) {
      Spans prevSpans = subSpans[i];
      if (collectPayloads && prevSpans.isPayloadAvailable()) {
        Collection<byte[]> payload = prevSpans.getPayload();
        possiblePayload = new ArrayList<>(payload.size());
        possiblePayload.addAll(payload);
      }
      
      int prevStart = prevSpans.start();
      int prevEnd = prevSpans.end();
      while (true) { // Advance prevSpans until after (lastStart, lastEnd)
        if (! prevSpans.next()) {
          inSameDoc = false;
          more = false;
          break; // Check remaining subSpans for final match.
        } else if (matchDoc != prevSpans.doc()) {
          inSameDoc = false; // The last subSpans is not advanced here.
          break; // Check remaining subSpans for last match in this document.
        } else {
          int ppStart = prevSpans.start();
          int ppEnd = prevSpans.end(); // Cannot avoid invoking .end()
          if (! docSpansOrderedNonOverlap(ppStart, ppEnd, lastStart, lastEnd)) {
            break; // Check remaining subSpans.
          } else { // prevSpans still before (lastStart, lastEnd)
            prevStart = ppStart;
            prevEnd = ppEnd;
            if (collectPayloads && prevSpans.isPayloadAvailable()) {
              Collection<byte[]> payload = prevSpans.getPayload();
              possiblePayload = new ArrayList<>(payload.size());
              possiblePayload.addAll(payload);
            }
          }
        }
      }

      if (collectPayloads && possiblePayload != null) {
        possibleMatchPayloads.addAll(possiblePayload);
      }
      
      assert prevStart <= matchStart;
      if (matchStart > prevEnd) { // Only non overlapping spans add to slop.
        matchSlop += (matchStart - prevEnd);
      }

      /* Do not break on (matchSlop > allowedSlop) here to make sure
       * that subSpans[0] is advanced after the match, if any.
       */
      matchStart = prevStart;
      lastStart = prevStart;
      lastEnd = prevEnd;
    }
    
    boolean match = matchSlop <= allowedSlop;
    
    if(collectPayloads && match && possibleMatchPayloads.size() > 0) {
      matchPayload.addAll(possibleMatchPayloads);
    }

    return match; // ordered and allowed slop
  }

  @Override
  public String toString() {
    return getClass().getName() + "("+query.toString()+")@"+
      (firstTime?"START":(more?(doc()+":"+start()+"-"+end()):"END"));
  }
}

