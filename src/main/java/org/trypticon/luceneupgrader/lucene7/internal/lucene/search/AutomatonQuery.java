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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.automaton.Automaton;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.automaton.CompiledAutomaton;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.automaton.Operations;

public class AutomatonQuery extends MultiTermQuery {
  protected final Automaton automaton;
  protected final CompiledAutomaton compiled;
  protected final Term term;
  protected final boolean automatonIsBinary;

  public AutomatonQuery(final Term term, Automaton automaton) {
    this(term, automaton, Operations.DEFAULT_MAX_DETERMINIZED_STATES);
  }

  public AutomatonQuery(final Term term, Automaton automaton, int maxDeterminizedStates) {
    this(term, automaton, maxDeterminizedStates, false);
  }

  public AutomatonQuery(final Term term, Automaton automaton, int maxDeterminizedStates, boolean isBinary) {
    super(term.field());
    this.term = term;
    this.automaton = automaton;
    this.automatonIsBinary = isBinary;
    // TODO: we could take isFinite too, to save a bit of CPU in CompiledAutomaton ctor?:
    this.compiled = new CompiledAutomaton(automaton, null, true, maxDeterminizedStates, isBinary);
  }

  @Override
  protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {
    return compiled.getTermsEnum(terms);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + compiled.hashCode();
    result = prime * result + ((term == null) ? 0 : term.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    AutomatonQuery other = (AutomatonQuery) obj;
    if (!compiled.equals(other.compiled))
      return false;
    if (term == null) {
      if (other.term != null)
        return false;
    } else if (!term.equals(other.term))
      return false;
    return true;
  }

  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!term.field().equals(field)) {
      buffer.append(term.field());
      buffer.append(":");
    }
    buffer.append(getClass().getSimpleName());
    buffer.append(" {");
    buffer.append('\n');
    buffer.append(automaton.toString());
    buffer.append("}");
    return buffer.toString();
  }
  
  public Automaton getAutomaton() {
    return automaton;
  }

  public boolean isAutomatonBinary() {
    return automatonIsBinary;
  }
}
