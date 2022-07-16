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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.Automaton;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.AutomatonProvider;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.Operations;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.RegExp;

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
      Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
  }

  public RegexpQuery(Term term, int flags, int determinizeWorkLimit) {
    this(term, flags, defaultProvider, determinizeWorkLimit);
  }

  public RegexpQuery(Term term, int syntax_flags, int match_flags, int determinizeWorkLimit) {
    this(term, syntax_flags, match_flags, defaultProvider, determinizeWorkLimit);
  }
  
  public RegexpQuery(Term term, int syntax_flags, AutomatonProvider provider,
      int determinizeWorkLimit) {
    this(term, syntax_flags, 0, provider, determinizeWorkLimit);
  }
  
  public RegexpQuery(Term term, int syntax_flags, int match_flags, AutomatonProvider provider,
      int determinizeWorkLimit) {
    super(term,
          new RegExp(term.text(), syntax_flags, match_flags).toAutomaton(
                       provider, determinizeWorkLimit), determinizeWorkLimit);
  }

  public Term getRegexp() {
    return term;
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
    return buffer.toString();
  }
}
