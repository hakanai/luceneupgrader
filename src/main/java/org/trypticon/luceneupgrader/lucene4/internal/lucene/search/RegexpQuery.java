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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton.Automaton;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton.AutomatonProvider;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton.Operations;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton.RegExp;

public class RegexpQuery extends AutomatonQuery {
  private static AutomatonProvider defaultProvider = new AutomatonProvider() {
    @Override
    public Automaton getAutomaton(String name) {
      return null;
    }
  };
  
  public RegexpQuery(Term term) {
    this(term, RegExp.ALL);
  }
  
  public RegexpQuery(Term term, int flags) {
    this(term, flags, defaultProvider,
      Operations.DEFAULT_MAX_DETERMINIZED_STATES);
  }

  public RegexpQuery(Term term, int flags, int maxDeterminizedStates) {
    this(term, flags, defaultProvider, maxDeterminizedStates);
  }

  public RegexpQuery(Term term, int flags, AutomatonProvider provider,
      int maxDeterminizedStates) {
    super(term,
          new RegExp(term.text(), flags).toAutomaton(
                       provider, maxDeterminizedStates), maxDeterminizedStates);
  }
  
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!term.field().equals(field)) {
      buffer.append(term.field());
      buffer.append(":");
    }
    buffer.append('/');
    buffer.append(term.text());
    buffer.append('/');
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }
}
