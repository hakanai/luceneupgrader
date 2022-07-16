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


import java.io.IOException;
import java.util.Arrays;

public final class TermStates {

  private static final TermState EMPTY_TERMSTATE = new TermState() {
    @Override
    public void copyFrom(TermState other) {

    }
  };

  // Important: do NOT keep hard references to index readers
  private final Object topReaderContextIdentity;
  private final TermState[] states;
  private final Term term;  // null if stats are to be used
  private int docFreq;
  private long totalTermFreq;

  //public static boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  private TermStates(Term term, IndexReaderContext context) {
    assert context != null && context.isTopLevel;
    topReaderContextIdentity = context.identity;
    docFreq = 0;
    totalTermFreq = 0;
    final int len;
    if (context.leaves() == null) {
      len = 1;
    } else {
      len = context.leaves().size();
    }
    states = new TermState[len];
    this.term = term;
  }

  public TermStates(IndexReaderContext context) {
    this(null, context);
  }

  public boolean wasBuiltFor(IndexReaderContext context) {
    return topReaderContextIdentity == context.identity;
  }

  public TermStates(IndexReaderContext context, TermState state, int ord, int docFreq, long totalTermFreq) {
    this(null, context);
    register(state, ord, docFreq, totalTermFreq);
  }

  public static TermStates build(IndexReaderContext context, Term term, boolean needsStats)
      throws IOException {
    assert context != null && context.isTopLevel;
    final TermStates perReaderTermState = new TermStates(needsStats ? null : term, context);
    if (needsStats) {
      for (final LeafReaderContext ctx : context.leaves()) {
        //if (DEBUG) System.out.println("  r=" + leaves[i].reader);
        TermsEnum termsEnum = loadTermsEnum(ctx, term);
        if (termsEnum != null) {
          final TermState termState = termsEnum.termState();
          //if (DEBUG) System.out.println("    found");
          perReaderTermState.register(termState, ctx.ord, termsEnum.docFreq(), termsEnum.totalTermFreq());
        }
      }
    }
    return perReaderTermState;
  }

  private static TermsEnum loadTermsEnum(LeafReaderContext ctx, Term term) throws IOException {
    final Terms terms = ctx.reader().terms(term.field());
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator();
      if (termsEnum.seekExact(term.bytes())) {
        return termsEnum;
      }
    }
    return null;
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
    assert docFreq >= 0;
    assert totalTermFreq >= 0;
    assert docFreq <= totalTermFreq;
    this.docFreq += docFreq;
    this.totalTermFreq += totalTermFreq;
  }

  public TermState get(LeafReaderContext ctx) throws IOException {
    assert ctx.ord >= 0 && ctx.ord < states.length;
    if (term == null)
      return states[ctx.ord];
    if (this.states[ctx.ord] == null) {
      TermsEnum te = loadTermsEnum(ctx, term);
      this.states[ctx.ord] = te == null ? EMPTY_TERMSTATE : te.termState();
    }
    if (this.states[ctx.ord] == EMPTY_TERMSTATE)
      return null;
    return this.states[ctx.ord];
  }

  public int docFreq() {
    if (term != null) {
      throw new IllegalStateException("Cannot call docFreq() when needsStats=false");
    }
    return docFreq;
  }
  
  public long totalTermFreq() {
    if (term != null) {
      throw new IllegalStateException("Cannot call totalTermFreq() when needsStats=false");
    }
    return totalTermFreq;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TermStates\n");
    for(TermState termState : states) {
      sb.append("  state=");
      sb.append(termState);
      sb.append('\n');
    }

    return sb.toString();
  }

}
