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
import java.io.Serializable;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;

public abstract class Weight implements Serializable {

  public abstract Explanation explain(IndexReader reader, int doc) throws IOException;

  public abstract Query getQuery();

  public abstract float getValue();

  public abstract void normalize(float norm);

  public abstract Scorer scorer(IndexReader reader, boolean scoreDocsInOrder,
      boolean topScorer) throws IOException;
  
  public abstract float sumOfSquaredWeights() throws IOException;

  public boolean scoresDocsOutOfOrder() { return false; }

}
