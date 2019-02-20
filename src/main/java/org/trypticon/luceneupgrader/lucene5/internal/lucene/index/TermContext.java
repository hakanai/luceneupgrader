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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;

public final class TermContext {


  public final IndexReaderContext topReaderContext;
  private final TermState[] states;
  private int docFreq;
  private long totalTermFreq;

  //public static boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  public TermContext(IndexReaderContext context) {
    assert context != null && context.isTopLevel;
    topReaderContext = context;
    docFreq = 0;
    totalTermFreq = 0;
    final int len;
    if (context.leaves() == null) {
      len = 1;
    } else {
      len = context.leaves().size();
    }
    states = new TermState[len];
  }
  
  public TermContext(IndexReaderContext context, TermState state, int ord, int docFreq, long totalTermFreq) {
    this(context);
    register(state, ord, docFreq, totalTermFreq);
  }

  public static TermContext build(IndexReaderContext context, Term term)
      throws IOException {
    assert context != null && context.isTopLevel;
    final String field = term.field();
    final BytesRef bytes = term.bytes();
    final TermContext perReaderTermState = new TermContext(context);
    //if (DEBUG) System.out.println("prts.build term=" + term);
    for (final LeafReaderContext ctx : context.leaves()) {
      //if (DEBUG) System.out.println("  r=" + leaves[i].reader);
      final Terms terms = ctx.reader().terms(field);
      if (terms != null) {
        final TermsEnum termsEnum = terms.iterator();
        if (termsEnum.seekExact(bytes)) { 
          final TermState termState = termsEnum.termState();
          //if (DEBUG) System.out.println("    found");
          perReaderTermState.register(termState, ctx.ord, termsEnum.docFreq(), termsEnum.totalTermFreq());
        }
      }
    }
    return perReaderTermState;
  }

  public void clear() {
    docFreq = 0;
    totalTermFreq = 0;
    Arrays.fill(states, null);
  }

  public void register(TermState state, final int ord, final int docFreq, final long totalTermFreq) {
    register(state, ord);
    accumulateStatistics(docFreq, totalTermFreq);
  }

  public void register(TermState state, final int ord) {
    assert state != null : "state must not be null";
    assert ord >= 0 && ord < states.length;
    assert states[ord] == null : "state for ord: " + ord
        + " already registered";
    states[ord] = state;
  }

  public void accumulateStatistics(final int docFreq, final long totalTermFreq) {
    this.docFreq += docFreq;
    if (this.totalTermFreq >= 0 && totalTermFreq >= 0)
      this.totalTermFreq += totalTermFreq;
    else
      this.totalTermFreq = -1;
  }

  public TermState get(int ord) {
    assert ord >= 0 && ord < states.length;
    return states[ord];
  }

  public int docFreq() {
    return docFreq;
  }
  
  public long totalTermFreq() {
    return totalTermFreq;
  }


  public boolean hasOnlyRealTerms() {
    for (TermState termState : states) {
      if (termState != null && termState.isRealTerm() == false) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TermContext\n");
    for(TermState termState : states) {
      sb.append("  state=");
      sb.append(termState.toString());
      sb.append('\n');
    }

    return sb.toString();
  }
}
