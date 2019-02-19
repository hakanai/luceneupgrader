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

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Similarity;

final class TermScorer extends Scorer {
  private final DocsEnum docsEnum;
  private final Similarity.SimScorer docScorer;
  
  TermScorer(Weight weight, DocsEnum td, Similarity.SimScorer docScorer) {
    super(weight);
    this.docScorer = docScorer;
    this.docsEnum = td;
  }

  @Override
  public int docID() {
    return docsEnum.docID();
  }

  @Override
  public int freq() throws IOException {
    return docsEnum.freq();
  }

  @Override
  public int nextDoc() throws IOException {
    return docsEnum.nextDoc();
  }
  
  @Override
  public float score() throws IOException {
    assert docID() != NO_MORE_DOCS;
    return docScorer.score(docsEnum.docID(), docsEnum.freq());  
  }

  @Override
  public int advance(int target) throws IOException {
    return docsEnum.advance(target);
  }
  
  @Override
  public long cost() {
    return docsEnum.cost();
  }

  @Override
  public String toString() { return "scorer(" + weight + ")"; }
}
