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
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.VirtualMethod;

/**
 * The abstract base class for queries.
 *
 * <p>Instantiable subclasses are:
 *
 * <ul>
 *   <li>{@link TermQuery}
 *   <li>{@link BooleanQuery}
 *   <li>{@link WildcardQuery}
 *   <li>{@link PhraseQuery}
 *   <li>{@link PrefixQuery}
 *   <li>{@link MultiPhraseQuery}
 *   <li>{@link FuzzyQuery}
 *   <li>{@link RegexpQuery}
 *   <li>{@link TermRangeQuery}
 *   <li>{@link PointRangeQuery}
 *   <li>{@link ConstantScoreQuery}
 *   <li>{@link DisjunctionMaxQuery}
 *   <li>{@link MatchAllDocsQuery}
 * </ul>
 *
 * <p>See also additional queries available in the <a
 * href="{@docRoot}/../queries/overview-summary.html">Queries module</a>.
 */
public abstract class Query {

  private static final VirtualMethod<Query> oldMethod =
      new VirtualMethod<>(Query.class, "rewrite", IndexReader.class);
  private static final VirtualMethod<Query> newMethod =
      new VirtualMethod<>(Query.class, "rewrite", IndexSearcher.class);
  private final boolean isDeprecatedRewriteMethodOverridden =
      AccessController.doPrivileged(
          (PrivilegedAction<Boolean>)
              () ->
                  VirtualMethod.compareImplementationDistance(this.getClass(), oldMethod, newMethod)
                      > 0);

  /**
   * Prints a query to a string, with <code>field</code> assumed to be the default field and
   * omitted.
   */
  public abstract String toString(String field);

  /** Prints a query to a string. */
  @Override
  public final String toString() {
    return toString("");
  }

  /**
   * Expert: Constructs an appropriate Weight implementation for this query.
   *
   * <p>Only implemented by primitive queries, which re-write to themselves.
   *
   * @param scoreMode How the produced scorers will be consumed.
   * @param boost The boost that is propagated by the parent queries.
   */
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    throw new UnsupportedOperationException("Query " + this + " does not implement createWeight");
  }

  /**
   * Expert: called to re-write queries into primitive queries. For example, a PrefixQuery will be
   * rewritten into a BooleanQuery that consists of TermQuerys.
   *
   * <p>Callers are expected to call <code>rewrite</code> multiple times if necessary, until the
   * rewritten query is the same as the original query.
   *
   * @deprecated Use {@link Query#rewrite(IndexSearcher)}
   * @see IndexSearcher#rewrite(Query)
   */
  @Deprecated
  public Query rewrite(IndexReader reader) throws IOException {
    return isDeprecatedRewriteMethodOverridden ? this : rewrite(new IndexSearcher(reader));
  }

  /**
   * Expert: called to re-write queries into primitive queries. For example, a PrefixQuery will be
   * rewritten into a BooleanQuery that consists of TermQuerys.
   *
   * <p>Callers are expected to call <code>rewrite</code> multiple times if necessary, until the
   * rewritten query is the same as the original query.
   *
   * <p>The rewrite process may be able to make use of IndexSearcher's executor and be executed in
   * parallel if the executor is provided.
   *
   * <p>However, if any of the intermediary queries do not satisfy the new API, parallel rewrite is
   * not possible for any subsequent sub-queries. To take advantage of this API, the entire query
   * tree must override this method.
   *
   * @see IndexSearcher#rewrite(Query)
   */
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    return isDeprecatedRewriteMethodOverridden ? rewrite(indexSearcher.getIndexReader()) : this;
  }

  /**
   * Recurse through the query tree, visiting any child queries.
   *
   * @param visitor a QueryVisitor to be called by each query in the tree
   */
  public abstract void visit(QueryVisitor visitor);

  /**
   * Override and implement query instance equivalence properly in a subclass. This is required so
   * that {@link QueryCache} works properly.
   *
   * <p>Typically a query will be equal to another only if it's an instance of the same class and
   * its document-filtering properties are identical to those of the other instance. Utility methods
   * are provided for certain repetitive code.
   *
   * @see #sameClassAs(Object)
   * @see #classHash()
   */
  @Override
  public abstract boolean equals(Object obj);

  /**
   * Override and implement query hash code properly in a subclass. This is required so that {@link
   * QueryCache} works properly.
   *
   * @see #equals(Object)
   */
  @Override
  public abstract int hashCode();

  /**
   * Utility method to check whether <code>other</code> is not null and is exactly of the same class
   * as this object's class.
   *
   * <p>When this method is used in an implementation of {@link #equals(Object)}, consider using
   * {@link #classHash()} in the implementation of {@link #hashCode} to differentiate different
   * class.
   */
  protected final boolean sameClassAs(Object other) {
    return other != null && getClass() == other.getClass();
  }

  private final int CLASS_NAME_HASH = getClass().getName().hashCode();

  /**
   * Provides a constant integer for a given class, derived from the name of the class. The
   * rationale for not using just {@link Class#hashCode()} is that classes may be assigned different
   * hash codes for each execution and we want hashes to be possibly consistent to facilitate
   * debugging.
   */
  protected final int classHash() {
    return CLASS_NAME_HASH;
  }
}
