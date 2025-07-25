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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.AutomatonProvider;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.Operations;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.RegExp;

/**
 * A fast regular expression query based on the {@link org.apache.lucene.util.automaton} package.
 *
 * <ul>
 *   <li>Comparisons are <a href="http://tusker.org/regex/regex_benchmark.html">fast</a>
 *   <li>The term dictionary is enumerated in an intelligent way, to avoid comparisons. See {@link
 *       AutomatonQuery} for more details.
 * </ul>
 *
 * <p>The supported syntax is documented in the {@link RegExp} class. Note this might be different
 * than other regular expression implementations. For some alternatives with different syntax, look
 * under the sandbox.
 *
 * <p>Note this query can be slow, as it needs to iterate over many terms. In order to prevent
 * extremely slow RegexpQueries, a Regexp term should not start with the expression <code>.*</code>
 *
 * @see RegExp
 * @lucene.experimental
 */
public class RegexpQuery extends AutomatonQuery {

  /** A provider that provides no named automata */
  public static final AutomatonProvider DEFAULT_PROVIDER = name -> null;

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * <p>By default, all regular expression features are enabled.
   *
   * @param term regular expression.
   */
  public RegexpQuery(Term term) {
    this(term, RegExp.ALL);
  }

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * @param term regular expression.
   * @param flags optional RegExp features from {@link RegExp}
   */
  public RegexpQuery(Term term, int flags) {
    this(term, flags, DEFAULT_PROVIDER, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
  }

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * @param term regular expression.
   * @param flags optional RegExp syntax features from {@link RegExp}
   * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
   *     regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
   *     Use {@link Operations#DEFAULT_DETERMINIZE_WORK_LIMIT} as a decent default if you don't
   *     otherwise know what to specify.
   */
  public RegexpQuery(Term term, int flags, int determinizeWorkLimit) {
    this(term, flags, DEFAULT_PROVIDER, determinizeWorkLimit);
  }

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * @param term regular expression.
   * @param syntax_flags optional RegExp syntax features from {@link RegExp} automaton for the
   *     regexp can result in. Set higher to allow more complex queries and lower to prevent memory
   *     exhaustion.
   * @param match_flags boolean 'or' of match behavior options such as case insensitivity
   * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
   *     regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
   *     Use {@link Operations#DEFAULT_DETERMINIZE_WORK_LIMIT} as a decent default if you don't
   *     otherwise know what to specify.
   */
  public RegexpQuery(Term term, int syntax_flags, int match_flags, int determinizeWorkLimit) {
    this(
        term,
        syntax_flags,
        match_flags,
        DEFAULT_PROVIDER,
        determinizeWorkLimit,
        CONSTANT_SCORE_BLENDED_REWRITE);
  }

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * @param term regular expression.
   * @param syntax_flags optional RegExp features from {@link RegExp}
   * @param provider custom AutomatonProvider for named automata
   * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
   *     regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
   *     Use {@link Operations#DEFAULT_DETERMINIZE_WORK_LIMIT} as a decent default if you don't
   *     otherwise know what to specify.
   */
  public RegexpQuery(
      Term term, int syntax_flags, AutomatonProvider provider, int determinizeWorkLimit) {
    this(term, syntax_flags, 0, provider, determinizeWorkLimit, CONSTANT_SCORE_BLENDED_REWRITE);
  }

  /**
   * Constructs a query for terms matching <code>term</code>.
   *
   * @param term regular expression.
   * @param syntax_flags optional RegExp features from {@link RegExp}
   * @param match_flags boolean 'or' of match behavior options such as case insensitivity
   * @param provider custom AutomatonProvider for named automata
   * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
   *     regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
   *     Use {@link Operations#DEFAULT_DETERMINIZE_WORK_LIMIT} as a decent default if you don't
   *     otherwise know what to specify.
   * @param rewriteMethod the rewrite method to use to build the final query
   */
  public RegexpQuery(
      Term term,
      int syntax_flags,
      int match_flags,
      AutomatonProvider provider,
      int determinizeWorkLimit,
      RewriteMethod rewriteMethod) {
    super(
        term,
        new RegExp(term.text(), syntax_flags, match_flags)
            .toAutomaton(provider, determinizeWorkLimit),
        determinizeWorkLimit,
        false,
        rewriteMethod);
  }

  /** Returns the regexp of this query wrapped in a Term. */
  public Term getRegexp() {
    return term;
  }

  /** Prints a user-readable version of this query. */
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
