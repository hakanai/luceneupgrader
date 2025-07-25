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
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.CompiledAutomaton;

/**
 * A multi-valued version of {@link SortedDocValues}.
 *
 * <p>Per-Document values in a SortedSetDocValues are deduplicated, dereferenced, and sorted into a
 * dictionary of unique values. A pointer to the dictionary value (ordinal) can be retrieved for
 * each document. Ordinals are dense and in increasing sorted order.
 */
public abstract class SortedSetDocValues extends DocValuesIterator {

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  protected SortedSetDocValues() {}

  /**
   * When returned by {@link #nextOrd()} it means there are no more ordinals for the document.
   *
   * @deprecated Will be removed in a future version. Please use {@link #docValueCount()} to know
   *     the number of doc values for the current document up-front.
   */
  @Deprecated public static final long NO_MORE_ORDS = -1;

  /**
   * Returns the next ordinal for the current document. It is illegal to call this method after
   * {@link #advanceExact(int)} returned {@code false}.
   *
   * <p>Note: Returns {@link #NO_MORE_ORDS} when the current document has no more ordinals. This
   * behavior will be removed in a future version. Callers should use {@link #docValueCount()} to
   * determine the number of values for the current document up-front.
   *
   * @return next ordinal for the document, or {@link #NO_MORE_ORDS}. ordinals are dense, start at
   *     0, then increment by 1 for the next value in sorted order.
   */
  public abstract long nextOrd() throws IOException;

  /**
   * Retrieves the number of unique ords for the current document. This must always be greater than
   * zero. It is illegal to call this method after {@link #advanceExact(int)} returned {@code
   * false}.
   */
  public abstract int docValueCount();

  /**
   * Retrieves the value for the specified ordinal. The returned {@link BytesRef} may be re-used
   * across calls to lookupOrd so make sure to {@link BytesRef#deepCopyOf(BytesRef) copy it} if you
   * want to keep it around.
   *
   * @param ord ordinal to lookup
   * @see #nextOrd
   */
  public abstract BytesRef lookupOrd(long ord) throws IOException;

  /**
   * Returns the number of unique values.
   *
   * @return number of unique values in this SortedDocValues. This is also equivalent to one plus
   *     the maximum ordinal.
   */
  public abstract long getValueCount();

  /**
   * If {@code key} exists, returns its ordinal, else returns {@code -insertionPoint-1}, like {@code
   * Arrays.binarySearch}.
   *
   * @param key Key to look up
   */
  public long lookupTerm(BytesRef key) throws IOException {
    long low = 0;
    long high = getValueCount() - 1;

    while (low <= high) {
      long mid = (low + high) >>> 1;
      final BytesRef term = lookupOrd(mid);
      int cmp = term.compareTo(key);

      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return mid; // key found
      }
    }

    return -(low + 1); // key not found.
  }

  /**
   * Returns a {@link TermsEnum} over the values. The enum supports {@link TermsEnum#ord()} and
   * {@link TermsEnum#seekExact(long)}.
   */
  public TermsEnum termsEnum() throws IOException {
    return new SortedSetDocValuesTermsEnum(this);
  }

  /**
   * Returns a {@link TermsEnum} over the values, filtered by a {@link CompiledAutomaton} The enum
   * supports {@link TermsEnum#ord()}.
   */
  public TermsEnum intersect(CompiledAutomaton automaton) throws IOException {
    TermsEnum in = termsEnum();
    switch (automaton.type) {
      case NONE:
        return TermsEnum.EMPTY;
      case ALL:
        return in;
      case SINGLE:
        return new SingleTermsEnum(in, automaton.term);
      case NORMAL:
        return new AutomatonTermsEnum(in, automaton);
      default:
        // unreachable
        throw new RuntimeException("unhandled case");
    }
  }
}
