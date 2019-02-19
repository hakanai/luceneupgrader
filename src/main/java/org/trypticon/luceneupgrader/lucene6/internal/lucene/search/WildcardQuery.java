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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;


import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.automaton.Automata;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.automaton.Automaton;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.automaton.Operations;

public class WildcardQuery extends AutomatonQuery {
  public static final char WILDCARD_STRING = '*';

  public static final char WILDCARD_CHAR = '?';

  public static final char WILDCARD_ESCAPE = '\\';
  
  public WildcardQuery(Term term) {
    super(term, toAutomaton(term));
  }
  
  public WildcardQuery(Term term, int maxDeterminizedStates) {
    super(term, toAutomaton(term), maxDeterminizedStates);
  }

  @SuppressWarnings("fallthrough")
  public static Automaton toAutomaton(Term wildcardquery) {
    List<Automaton> automata = new ArrayList<>();
    
    String wildcardText = wildcardquery.text();
    
    for (int i = 0; i < wildcardText.length();) {
      final int c = wildcardText.codePointAt(i);
      int length = Character.charCount(c);
      switch(c) {
        case WILDCARD_STRING: 
          automata.add(Automata.makeAnyString());
          break;
        case WILDCARD_CHAR:
          automata.add(Automata.makeAnyChar());
          break;
        case WILDCARD_ESCAPE:
          // add the next codepoint instead, if it exists
          if (i + length < wildcardText.length()) {
            final int nextChar = wildcardText.codePointAt(i + length);
            length += Character.charCount(nextChar);
            automata.add(Automata.makeChar(nextChar));
            break;
          } // else fallthru, lenient parsing with a trailing \
        default:
          automata.add(Automata.makeChar(c));
      }
      i += length;
    }
    
    return Operations.concatenate(automata);
  }
  
  public Term getTerm() {
    return term;
  }
  
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!getField().equals(field)) {
      buffer.append(getField());
      buffer.append(":");
    }
    buffer.append(term.text());
    return buffer.toString();
  }
}
