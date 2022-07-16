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

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.ByteRunAutomaton;

public abstract class QueryVisitor {

  public void consumeTerms(Query query, Term... terms) { }

  public void consumeTermsMatching(Query query, String field, Supplier<ByteRunAutomaton> automaton) {
    visitLeaf(query); // default impl for backward compatibility
  }

  public void visitLeaf(Query query) { }

  public boolean acceptField(String field) {
    return true;
  }

  public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
    if (occur == BooleanClause.Occur.MUST_NOT) {
      return EMPTY_VISITOR;
    }
    return this;
  }

  public static QueryVisitor termCollector(Set<Term> termSet) {
    return new QueryVisitor() {
      @Override
      public void consumeTerms(Query query, Term... terms) {
        termSet.addAll(Arrays.asList(terms));
      }
    };
  }

  public static final QueryVisitor EMPTY_VISITOR = new QueryVisitor() {};

}
