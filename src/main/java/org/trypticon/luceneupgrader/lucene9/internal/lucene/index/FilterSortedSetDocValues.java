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
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.CompiledAutomaton;

/** Delegates all methods to a wrapped {@link SortedSetDocValues}. */
public class FilterSortedSetDocValues extends SortedSetDocValues {

  /** Wrapped values */
  protected final SortedSetDocValues in;

  /** Initializes delegate */
  public FilterSortedSetDocValues(SortedSetDocValues in) {
    Objects.requireNonNull(in);
    this.in = in;
  }

  @Override
  public boolean advanceExact(int target) throws IOException {
    return in.advanceExact(target);
  }

  @Override
  public long nextOrd() throws IOException {
    return in.nextOrd();
  }

  @Override
  public int docValueCount() {
    return in.docValueCount();
  }

  @Override
  public BytesRef lookupOrd(long ord) throws IOException {
    return in.lookupOrd(ord);
  }

  @Override
  public long getValueCount() {
    return in.getValueCount();
  }

  @Override
  public long lookupTerm(BytesRef key) throws IOException {
    return in.lookupTerm(key);
  }

  @Override
  public TermsEnum termsEnum() throws IOException {
    return in.termsEnum();
  }

  @Override
  public TermsEnum intersect(CompiledAutomaton automaton) throws IOException {
    return in.intersect(automaton);
  }

  @Override
  public int docID() {
    return in.docID();
  }

  @Override
  public int nextDoc() throws IOException {
    return in.nextDoc();
  }

  @Override
  public int advance(int target) throws IOException {
    return in.advance(target);
  }

  @Override
  public long cost() {
    return in.cost();
  }
}
