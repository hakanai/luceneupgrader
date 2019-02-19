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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader; // javadoc
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInvertState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.BooleanQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.CollectionStatistics;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.PhraseQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.TermQuery;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.TermStatistics;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.spans.SpanQuery; // javadoc
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.SmallFloat; // javadoc

public abstract class Similarity {
  
  public Similarity() {}
  

  public float coord(int overlap, int maxOverlap) {
    return 1f;
  }
  

  public float queryNorm(float valueForNormalization) {
    return 1f;
  }
  
  public abstract long computeNorm(FieldInvertState state);

  public abstract SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats);

  public abstract SimScorer simScorer(SimWeight weight, AtomicReaderContext context) throws IOException;
  
  public static abstract class SimScorer {
    
    public SimScorer() {}

    public abstract float score(int doc, float freq);

    public abstract float computeSlopFactor(int distance);
    
    public abstract float computePayloadFactor(int doc, int start, int end, BytesRef payload);
    
    public Explanation explain(int doc, Explanation freq) {
      Explanation result = new Explanation(score(doc, freq.getValue()), 
          "score(doc=" + doc + ",freq=" + freq.getValue() +"), with freq of:");
      result.addDetail(freq);
      return result;
    }
  }
  

  public static abstract class SimWeight {
    
    public SimWeight() {}
    
    public abstract float getValueForNormalization();
    
    public abstract void normalize(float queryNorm, float topLevelBoost);
  }
}
