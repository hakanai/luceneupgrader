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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search.spans;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.TwoPhaseIterator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.Similarity.SimScorer;

public abstract class Spans extends DocIdSetIterator {

  public static final int NO_MORE_POSITIONS = Integer.MAX_VALUE;

  public abstract int nextStartPosition() throws IOException;

  public abstract int startPosition();

  public abstract int endPosition();

  public abstract int width();

  public abstract void collect(SpanCollector collector) throws IOException;

  public abstract float positionsCost();

  public TwoPhaseIterator asTwoPhaseIterator() {
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    Class<? extends Spans> clazz = getClass();
    sb.append(clazz.isAnonymousClass() ? clazz.getName() : clazz.getSimpleName());
    sb.append("(doc=").append(docID());
    sb.append(",start=").append(startPosition());
    sb.append(",end=").append(endPosition());
    sb.append(")");
    return sb.toString();
  }

  protected void doStartCurrentDoc() throws IOException {}

  protected void doCurrentSpans() throws IOException {}

}
