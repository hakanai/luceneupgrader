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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TaskExecutor;

/**
 * Maintains a {@link IndexReader} {@link TermState} view over {@link IndexReader} instances
 * containing a single term. The {@link TermStates} doesn't track if the given {@link TermState}
 * objects are valid, neither if the {@link TermState} instances refer to the same terms in the
 * associated readers.
 *
 * @lucene.experimental
 */
public final class TermStates {

  private static final TermState EMPTY_TERMSTATE =
      new TermState() {
        @Override
        public void copyFrom(TermState other) {}
      };

  // Important: do NOT keep hard references to index readers
  private final Object topReaderContextIdentity;
  private final TermState[] states;
  private final Term term; // null if stats are to be used
  private int docFreq;
  private long totalTermFreq;

  // public static boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  private TermStates(Term term, IndexReaderContext context) {
    assert context != null && context.isTopLevel;
    topReaderContextIdentity = context.identity;
    docFreq = 0;
    totalTermFreq = 0;
    states = new TermState[context.leaves().size()];
    this.term = term;
  }

  /** Creates an empty {@link TermStates} from a {@link IndexReaderContext} */
  public TermStates(IndexReaderContext context) {
    this(null, context);
  }

  /**
   * Expert: Return whether this {@link TermStates} was built for the given {@link
   * IndexReaderContext}. This is typically used for assertions.
   *
   * @lucene.internal
   */
  public boolean wasBuiltFor(IndexReaderContext context) {
    return topReaderContextIdentity == context.identity;
  }

  /** Creates a {@link TermStates} with an initial {@link TermState}, {@link IndexReader} pair. */
  public TermStates(
      IndexReaderContext context, TermState state, int ord, int docFreq, long totalTermFreq) {
    this(null, context);
    register(state, ord, docFreq, totalTermFreq);
  }

  /**
   * Creates a {@link TermStates} from a top-level {@link IndexReaderContext} and the given {@link
   * Term}. This method will lookup the given term in all context's leaf readers and register each
   * of the readers containing the term in the returned {@link TermStates} using the leaf reader's
   * ordinal.
   *
   * <p>Note: the given context must be a top-level context.
   *
   * @param needsStats if {@code true} then all leaf contexts will be visited up-front to collect
   *     term statistics. Otherwise, the {@link TermState} objects will be built only when requested
   */
  public static TermStates build(IndexSearcher indexSearcher, Term term, boolean needsStats)
      throws IOException {
    IndexReaderContext context = indexSearcher.getTopReaderContext();
    assert context != null;
    final TermStates perReaderTermState = new TermStates(needsStats ? null : term, context);
    if (needsStats) {
      TaskExecutor taskExecutor = indexSearcher.getTaskExecutor();
      // build the term states concurrently
      List<Callable<TermStateInfo>> tasks = new ArrayList<>(context.leaves().size());
      for (LeafReaderContext ctx : context.leaves()) {
        tasks.add(
            () -> {
              TermsEnum termsEnum = loadTermsEnum(ctx, term);
              return termsEnum == null
                  ? null
                  : new TermStateInfo(
                      termsEnum.termState(),
                      ctx.ord,
                      termsEnum.docFreq(),
                      termsEnum.totalTermFreq());
            });
      }
      List<TermStateInfo> resultInfos = taskExecutor.invokeAll(tasks);
      for (TermStateInfo info : resultInfos) {
        if (info != null) {
          perReaderTermState.register(
              info.getState(), info.getOrdinal(), info.getDocFreq(), info.getTotalTermFreq());
        }
      }
    }
    return perReaderTermState;
  }

  private static TermsEnum loadTermsEnum(LeafReaderContext ctx, Term term) throws IOException {
    final Terms terms = Terms.getTerms(ctx.reader(), term.field());
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum;
    }
    return null;
  }

  /** Clears the {@link TermStates} internal state and removes all registered {@link TermState}s */
  public void clear() {
    docFreq = 0;
    totalTermFreq = 0;
    Arrays.fill(states, null);
  }

  /**
   * Registers and associates a {@link TermState} with an leaf ordinal. The leaf ordinal should be
   * derived from a {@link IndexReaderContext}'s leaf ord.
   */
  public void register(
      TermState state, final int ord, final int docFreq, final long totalTermFreq) {
    register(state, ord);
    accumulateStatistics(docFreq, totalTermFreq);
  }

  /**
   * Expert: Registers and associates a {@link TermState} with an leaf ordinal. The leaf ordinal
   * should be derived from a {@link IndexReaderContext}'s leaf ord. On the contrary to {@link
   * #register(TermState, int, int, long)} this method does NOT update term statistics.
   */
  public void register(TermState state, final int ord) {
    assert state != null : "state must not be null";
    assert ord >= 0 && ord < states.length;
    assert states[ord] == null : "state for ord: " + ord + " already registered";
    states[ord] = state;
  }

  /** Expert: Accumulate term statistics. */
  public void accumulateStatistics(final int docFreq, final long totalTermFreq) {
    assert docFreq >= 0;
    assert totalTermFreq >= 0;
    assert docFreq <= totalTermFreq;
    this.docFreq += docFreq;
    this.totalTermFreq += totalTermFreq;
  }

  /**
   * Returns the {@link TermState} for a leaf reader context or <code>null</code> if no {@link
   * TermState} for the context was registered.
   *
   * @param ctx the {@link LeafReaderContext} to get the {@link TermState} for.
   * @return the {@link TermState} for the given readers ord or <code>null</code> if no {@link
   *     TermState} for the reader was registered
   */
  public TermState get(LeafReaderContext ctx) throws IOException {
    assert ctx.ord >= 0 && ctx.ord < states.length;
    if (term == null) return states[ctx.ord];
    if (this.states[ctx.ord] == null) {
      TermsEnum te = loadTermsEnum(ctx, term);
      this.states[ctx.ord] = te == null ? EMPTY_TERMSTATE : te.termState();
    }
    if (this.states[ctx.ord] == EMPTY_TERMSTATE) return null;
    return this.states[ctx.ord];
  }

  /**
   * Returns the accumulated document frequency of all {@link TermState} instances passed to {@link
   * #register(TermState, int, int, long)}.
   *
   * @return the accumulated document frequency of all {@link TermState} instances passed to {@link
   *     #register(TermState, int, int, long)}.
   */
  public int docFreq() {
    if (term != null) {
      throw new IllegalStateException("Cannot call docFreq() when needsStats=false");
    }
    return docFreq;
  }

  /**
   * Returns the accumulated term frequency of all {@link TermState} instances passed to {@link
   * #register(TermState, int, int, long)}.
   *
   * @return the accumulated term frequency of all {@link TermState} instances passed to {@link
   *     #register(TermState, int, int, long)}.
   */
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
    for (TermState termState : states) {
      sb.append("  state=");
      sb.append(termState);
      sb.append('\n');
    }

    return sb.toString();
  }

  /** Wrapper over TermState, ordinal value, term doc frequency and total term frequency */
  private static final class TermStateInfo {
    private final TermState state;
    private final int ordinal;
    private final int docFreq;
    private final long totalTermFreq;

    /** Initialize TermStateInfo */
    public TermStateInfo(TermState state, int ordinal, int docFreq, long totalTermFreq) {
      this.state = state;
      this.ordinal = ordinal;
      this.docFreq = docFreq;
      this.totalTermFreq = totalTermFreq;
    }

    /** Get term state */
    public TermState getState() {
      return state;
    }

    /** Get ordinal value */
    public int getOrdinal() {
      return ordinal;
    }

    /** Get term doc frequency */
    public int getDocFreq() {
      return docFreq;
    }

    /** Get total term frequency */
    public long getTotalTermFreq() {
      return totalTermFreq;
    }
  }
}
