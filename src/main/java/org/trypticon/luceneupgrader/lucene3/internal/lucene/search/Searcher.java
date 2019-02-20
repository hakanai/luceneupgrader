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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.FieldSelector;

@Deprecated
public abstract class Searcher implements Searchable {

  public TopFieldDocs search(Query query, Filter filter, int n,
                             Sort sort) throws IOException {
    return search(createNormalizedWeight(query), filter, n, sort);
  }

  public TopFieldDocs search(Query query, int n,
                             Sort sort) throws IOException {
    return search(createNormalizedWeight(query), null, n, sort);
  }

 public void search(Query query, Collector results)
   throws IOException {
   search(createNormalizedWeight(query), null, results);
 }


  public void search(Query query, Filter filter, Collector results)
  throws IOException {
    search(createNormalizedWeight(query), filter, results);
  }


  public TopDocs search(Query query, Filter filter, int n)
    throws IOException {
    return search(createNormalizedWeight(query), filter, n);
  }


  public TopDocs search(Query query, int n)
    throws IOException {
    return search(query, null, n);
  }


  public Explanation explain(Query query, int doc) throws IOException {
    return explain(createNormalizedWeight(query), doc);
  }

  private Similarity similarity = Similarity.getDefault();


  public void setSimilarity(Similarity similarity) {
    this.similarity = similarity;
  }


  public Similarity getSimilarity() {
    return this.similarity;
  }

  public Weight createNormalizedWeight(Query query) throws IOException {
    query = rewrite(query);
    Weight weight = query.createWeight(this);
    float sum = weight.sumOfSquaredWeights();
    // this is a hack for backwards compatibility:
    float norm = query.getSimilarity(this).queryNorm(sum);
    if (Float.isInfinite(norm) || Float.isNaN(norm))
      norm = 1.0f;
    weight.normalize(norm);
    return weight;
  }
  
  @Deprecated
  protected final Weight createWeight(Query query) throws IOException {
    return createNormalizedWeight(query);
  }

  // inherit javadoc
  public int[] docFreqs(Term[] terms) throws IOException {
    int[] result = new int[terms.length];
    for (int i = 0; i < terms.length; i++) {
      result[i] = docFreq(terms[i]);
    }
    return result;
  }

  abstract public void search(Weight weight, Filter filter, Collector results) throws IOException;
  abstract public void close() throws IOException;
  abstract public int docFreq(Term term) throws IOException;
  abstract public int maxDoc() throws IOException;
  abstract public TopDocs search(Weight weight, Filter filter, int n) throws IOException;
  abstract public Document doc(int i) throws CorruptIndexException, IOException;
  abstract public Document doc(int docid, FieldSelector fieldSelector) throws CorruptIndexException, IOException;
  abstract public Query rewrite(Query query) throws IOException;
  abstract public Explanation explain(Weight weight, int doc) throws IOException;
  abstract public TopFieldDocs search(Weight weight, Filter filter, int n, Sort sort) throws IOException;
  /* End patch for GCJ bug #15411. */
}
