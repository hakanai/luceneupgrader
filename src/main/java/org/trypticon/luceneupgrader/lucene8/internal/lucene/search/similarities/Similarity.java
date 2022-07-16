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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search.similarities;


import java.util.Collections;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.document.NumericDocValuesField;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.SmallFloat;

public abstract class Similarity {
  
  public Similarity() {}
  
  public abstract long computeNorm(FieldInvertState state);

  public abstract SimScorer scorer(float boost,
      CollectionStatistics collectionStats, TermStatistics... termStats);
  
  public static abstract class SimScorer {

    protected SimScorer() {}

    public abstract float score(float freq, long norm);

    public Explanation explain(Explanation freq, long norm) {
      return Explanation.match(
          score(freq.getValue().floatValue(), norm),
          "score(freq=" + freq.getValue() +"), with freq of:",
          Collections.singleton(freq));
    }

  }
}
