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

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PostingsEnum;

/** A {@link MatchesIterator} over a single term's postings list */
class TermMatchesIterator implements MatchesIterator {

  private int upto;
  private int pos;
  private final PostingsEnum pe;
  private final Query query;

  /** Create a new {@link TermMatchesIterator} for the given term and postings list */
  TermMatchesIterator(Query query, PostingsEnum pe) throws IOException {
    this.pe = pe;
    this.query = query;
    this.upto = pe.freq();
  }

  @Override
  public boolean next() throws IOException {
    if (upto-- > 0) {
      pos = pe.nextPosition();
      return true;
    }
    return false;
  }

  @Override
  public int startPosition() {
    return pos;
  }

  @Override
  public int endPosition() {
    return pos;
  }

  @Override
  public int startOffset() throws IOException {
    return pe.startOffset();
  }

  @Override
  public int endOffset() throws IOException {
    return pe.endOffset();
  }

  @Override
  public MatchesIterator getSubMatches() throws IOException {
    return null;
  }

  @Override
  public Query getQuery() {
    return query;
  }
}
