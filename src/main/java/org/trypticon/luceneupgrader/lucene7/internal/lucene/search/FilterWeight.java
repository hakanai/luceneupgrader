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
import java.util.Set;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.Term;

public abstract class FilterWeight extends Weight {

  final protected Weight in;

  protected FilterWeight(Weight weight) {
    this(weight.getQuery(), weight);
  }

  protected FilterWeight(Query query, Weight weight) {
    super(query);
    this.in = weight;
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    return in.isCacheable(ctx);
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    in.extractTerms(terms);
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    return in.explain(context, doc);
  }

  @Override
  public Scorer scorer(LeafReaderContext context) throws IOException {
    return in.scorer(context);
  }

  @Override
  public Matches matches(LeafReaderContext context, int doc) throws IOException {
    return in.matches(context, doc);
  }
}
