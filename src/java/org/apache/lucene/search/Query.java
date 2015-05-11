package org.apache.lucene.search;

/**
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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

import java.io.IOException;
import java.util.Set;

/** The abstract base class for queries.
    <p>Instantiable subclasses are:
    <ul>
    <li> {@code TermQuery}
    <li> {@code MultiTermQuery}
    <li> {@code BooleanQuery}
    <li> {@code WildcardQuery}
    <li> {@code PhraseQuery}
    <li> {@code PrefixQuery}
    <li> {@code MultiPhraseQuery}
    <li> {@code FuzzyQuery}
    <li> {@code TermRangeQuery}
    <li> {@code NumericRangeQuery}
    <li> {@code org.apache.lucene.search.spans.SpanQuery}
    </ul>
    <p>A parser for queries is contained in:
    <ul>
    <li>{@code org.apache.lucene.queryParser.QueryParser QueryParser}
    </ul>
*/
public abstract class Query implements java.io.Serializable, Cloneable {
  private float boost = 1.0f;                     // query boost factor

  /** Sets the boost for this query clause to <code>b</code>.  Documents
   * matching this clause will (in addition to the normal weightings) have
   * their score multiplied by <code>b</code>.
   */
  public void setBoost(float b) { boost = b; }

  /** Gets the boost for this clause.  Documents matching
   * this clause will (in addition to the normal weightings) have their score
   * multiplied by <code>b</code>.   The boost is 1.0 by default.
   */
  public float getBoost() { return boost; }

  /** Prints a query to a string, with <code>field</code> assumed to be the 
   * default field and omitted.
   * <p>The representation used is one that is supposed to be readable
   * by {@code org.apache.lucene.queryParser.QueryParser QueryParser}. However,
   * there are the following limitations:
   * <ul>
   *  <li>If the query was created by the parser, the printed
   *  representation may not be exactly what was parsed. For example,
   *  characters that need to be escaped will be represented without
   *  the required backslash.</li>
   * <li>Some of the more complicated queries (e.g. span queries)
   *  don't have a representation that can be parsed by QueryParser.</li>
   * </ul>
   */
  public abstract String toString(String field);

  /** Prints a query to a string. */
  @Override
  public String toString() {
    return toString("");
  }

  /**
   * Expert: Constructs an appropriate Weight implementation for this query.
   * 
   * <p>
   * Only implemented by primitive queries, which re-write to themselves.
   */
  public Weight createWeight(Searcher searcher) throws IOException {
    throw new UnsupportedOperationException("Query " + this + " does not implement createWeight");
  }

  /**
   * Expert: Constructs and initializes a Weight for a <b>top-level</b> query.
   * @deprecated never ever use this method in {@code Weight} implementations.
   * Subclasses of {@code Query} should use {@code #createWeight}, instead.
   */
  @Deprecated
  public final Weight weight(Searcher searcher) throws IOException {
    return searcher.createNormalizedWeight(this);
  }
  

  /** Expert: called to re-write queries into primitive queries. For example,
   * a PrefixQuery will be rewritten into a BooleanQuery that consists
   * of TermQuerys.
   */
  public Query rewrite(IndexReader reader) throws IOException {
    return this;
  }


  /**
   * Expert: adds all terms occurring in this query to the terms set. Only
   * works if this query is in its {@code #rewrite rewritten} form.
   * 
   * @throws UnsupportedOperationException if this query is not yet rewritten
   */
  public void extractTerms(Set<Term> terms) {
    // needs to be implemented by query subclasses
    throw new UnsupportedOperationException();
  }


  /** Expert: Returns the Similarity implementation to be used for this query.
   * Subclasses may override this method to specify their own Similarity
   * implementation, perhaps one that delegates through that of the Searcher.
   * By default the Searcher's Similarity implementation is returned.
   * @deprecated Instead of using "runtime" subclassing/delegation, subclass the Weight instead.
   */
  @Deprecated
  public Similarity getSimilarity(Searcher searcher) {
    return searcher.getSimilarity();
  }

  /** Returns a clone of this query. */
  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Clone not supported: " + e.getMessage());
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Float.floatToIntBits(boost);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Query other = (Query) obj;
    if (Float.floatToIntBits(boost) != Float.floatToIntBits(other.boost))
      return false;
    return true;
  }
}
