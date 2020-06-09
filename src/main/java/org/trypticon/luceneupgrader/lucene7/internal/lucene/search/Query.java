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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;

public abstract class Query {

  public abstract String toString(String field);

  @Override
  public final String toString() {
    return toString("");
  }

  public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
    throw new UnsupportedOperationException("Query " + this + " does not implement createWeight");
  }

  public Query rewrite(IndexReader reader) throws IOException {
    return this;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  protected final boolean sameClassAs(Object other) {
    return other != null && getClass() == other.getClass();
  }

  private final int CLASS_NAME_HASH = getClass().getName().hashCode();

  protected final int classHash() {
    return CLASS_NAME_HASH;
  }
}
