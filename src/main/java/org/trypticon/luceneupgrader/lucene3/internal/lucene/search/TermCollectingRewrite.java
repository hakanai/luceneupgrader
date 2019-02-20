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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;

abstract class TermCollectingRewrite<Q extends Query> extends MultiTermQuery.RewriteMethod {
  
  protected abstract Q getTopLevelQuery() throws IOException;
  
  protected abstract void addClause(Q topLevel, Term term, float boost) throws IOException;
  
  protected final void collectTerms(IndexReader reader, MultiTermQuery query, TermCollector collector) throws IOException {
    final FilteredTermEnum enumerator = getTermsEnum(reader, query);
    try {
      do {
        final Term t = enumerator.term();
        if (t == null || !collector.collect(t, enumerator.difference()))
          break;
      } while (enumerator.next());    
    } finally {
      enumerator.close();
    }
  }
  
  protected interface TermCollector {
    boolean collect(Term t, float boost) throws IOException;
  }
}
