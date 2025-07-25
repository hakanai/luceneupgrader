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
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SingleTermsEnum;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.CompiledAutomaton;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton.LevenshteinAutomata;

/**
 * Implements the fuzzy search query. The similarity measurement is based on the Damerau-Levenshtein
 * (optimal string alignment) algorithm, though you can explicitly choose classic Levenshtein by
 * passing <code>false</code> to the <code>transpositions</code> parameter.
 *
 * <p>This query uses {@link MultiTermQuery.TopTermsBlendedFreqScoringRewrite} as default. So terms
 * will be collected and scored according to their edit distance. Only the top terms are used for
 * building the {@link BooleanQuery}. It is not recommended to change the rewrite mode for fuzzy
 * queries.
 *
 * <p>At most, this query will match terms up to {@value
 * org.apache.lucene.util.automaton.LevenshteinAutomata#MAXIMUM_SUPPORTED_DISTANCE} edits. Higher
 * distances (especially with transpositions enabled), are generally not useful and will match a
 * significant amount of the term dictionary. If you really want this, consider using an n-gram
 * indexing technique (such as the SpellChecker in the <a
 * href="{@docRoot}/../suggest/overview-summary.html">suggest module</a>) instead.
 *
 * <p>NOTE: terms of length 1 or 2 will sometimes not match because of how the scaled distance
 * between two terms is computed. For a term to match, the edit distance between the terms must be
 * less than the minimum length term (either the input term, or the candidate term). For example,
 * FuzzyQuery on term "abcd" with maxEdits=2 will not match an indexed term "ab", and FuzzyQuery on
 * term "a" with maxEdits=2 will not match an indexed term "abc".
 */
public class FuzzyQuery extends MultiTermQuery {

  public static final int defaultMaxEdits = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE;
  public static final int defaultPrefixLength = 0;
  public static final int defaultMaxExpansions = 50;
  public static final boolean defaultTranspositions = true;

  /** Creates a default top-terms blended frequency scoring rewrite with the given max expansions */
  public static RewriteMethod defaultRewriteMethod(int maxExpansions) {
    return new MultiTermQuery.TopTermsBlendedFreqScoringRewrite(maxExpansions);
  }

  private final int maxEdits;
  private final int maxExpansions;
  private final boolean transpositions;
  private final int prefixLength;
  private final Term term;

  /**
   * Create a new FuzzyQuery that will match terms with an edit distance of at most <code>maxEdits
   * </code> to <code>term</code>. If a <code>prefixLength</code> &gt; 0 is specified, a common
   * prefix of that length is also required.
   *
   * @param term the term to search for
   * @param maxEdits must be {@code >= 0} and {@code <=} {@link
   *     LevenshteinAutomata#MAXIMUM_SUPPORTED_DISTANCE}.
   * @param prefixLength length of common (non-fuzzy) prefix
   * @param maxExpansions the maximum number of terms to match. If this number is greater than
   *     {@link IndexSearcher#getMaxClauseCount} when the query is rewritten, then the
   *     maxClauseCount will be used instead.
   * @param transpositions true if transpositions should be treated as a primitive edit operation.
   *     If this is false, comparisons will implement the classic Levenshtein algorithm.
   * @param rewriteMethod the rewrite method to use to build the final query
   */
  public FuzzyQuery(
      Term term,
      int maxEdits,
      int prefixLength,
      int maxExpansions,
      boolean transpositions,
      RewriteMethod rewriteMethod) {
    super(term.field(), rewriteMethod);

    if (maxEdits < 0 || maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE) {
      throw new IllegalArgumentException(
          "maxEdits must be between 0 and " + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    }
    if (prefixLength < 0) {
      throw new IllegalArgumentException("prefixLength cannot be negative.");
    }
    if (maxExpansions <= 0) {
      throw new IllegalArgumentException("maxExpansions must be positive.");
    }

    this.term = term;
    this.maxEdits = maxEdits;
    this.prefixLength = prefixLength;
    this.transpositions = transpositions;
    this.maxExpansions = maxExpansions;
  }

  /**
   * Calls {@link #FuzzyQuery(Term, int, int, int, boolean,
   * org.apache.lucene.search.MultiTermQuery.RewriteMethod)} FuzzyQuery(term, maxEdits,
   * prefixLength, maxExpansions, defaultRewriteMethod(maxExpansions))
   */
  public FuzzyQuery(
      Term term, int maxEdits, int prefixLength, int maxExpansions, boolean transpositions) {
    this(
        term,
        maxEdits,
        prefixLength,
        maxExpansions,
        transpositions,
        defaultRewriteMethod(maxExpansions));
  }

  /**
   * Calls {@link #FuzzyQuery(Term, int, int, int, boolean) FuzzyQuery(term, maxEdits, prefixLength,
   * defaultMaxExpansions, defaultTranspositions)}.
   */
  public FuzzyQuery(Term term, int maxEdits, int prefixLength) {
    this(term, maxEdits, prefixLength, defaultMaxExpansions, defaultTranspositions);
  }

  /** Calls {@link #FuzzyQuery(Term, int, int) FuzzyQuery(term, maxEdits, defaultPrefixLength)}. */
  public FuzzyQuery(Term term, int maxEdits) {
    this(term, maxEdits, defaultPrefixLength);
  }

  /** Calls {@link #FuzzyQuery(Term, int) FuzzyQuery(term, defaultMaxEdits)}. */
  public FuzzyQuery(Term term) {
    this(term, defaultMaxEdits);
  }

  /**
   * @return the maximum number of edit distances allowed for this query to match.
   */
  public int getMaxEdits() {
    return maxEdits;
  }

  /**
   * Returns the non-fuzzy prefix length. This is the number of characters at the start of a term
   * that must be identical (not fuzzy) to the query term if the query is to match that term.
   */
  public int getPrefixLength() {
    return prefixLength;
  }

  /**
   * Returns true if transpositions should be treated as a primitive edit operation. If this is
   * false, comparisons will implement the classic Levenshtein algorithm.
   */
  public boolean getTranspositions() {
    return transpositions;
  }

  /** Returns the compiled automata used to match terms */
  public CompiledAutomaton getAutomata() {
    return getFuzzyAutomaton(term.text(), maxEdits, prefixLength, transpositions);
  }

  /**
   * Returns the {@link CompiledAutomaton} internally used by {@link FuzzyQuery} to match terms.
   * This is a very low-level method and may no longer exist in case the implementation of
   * fuzzy-matching changes in the future.
   *
   * @lucene.internal
   * @param term the term to search for
   * @param maxEdits must be {@code >= 0} and {@code <=} {@link
   *     LevenshteinAutomata#MAXIMUM_SUPPORTED_DISTANCE}.
   * @param prefixLength length of common (non-fuzzy) prefix
   * @param transpositions true if transpositions should be treated as a primitive edit operation.
   *     If this is false, comparisons will implement the classic Levenshtein algorithm.
   * @return A {@link CompiledAutomaton} that matches terms that satisfy input parameters.
   */
  public static CompiledAutomaton getFuzzyAutomaton(
      String term, int maxEdits, int prefixLength, boolean transpositions) {
    FuzzyAutomatonBuilder builder =
        new FuzzyAutomatonBuilder(term, maxEdits, prefixLength, transpositions);
    return builder.buildMaxEditAutomaton();
  }

  @Override
  public void visit(QueryVisitor visitor) {
    if (visitor.acceptField(field)) {
      visitor.consumeTermsMatching(this, term.field(), () -> getAutomata().runAutomaton);
    }
  }

  @Override
  protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {
    if (maxEdits == 0) { // can only match if it's exact
      return new SingleTermsEnum(terms.iterator(), term.bytes());
    }
    return new FuzzyTermsEnum(terms, atts, getTerm(), maxEdits, prefixLength, transpositions);
  }

  /** Returns the pattern term. */
  public Term getTerm() {
    return term;
  }

  @Override
  public String toString(String field) {
    final StringBuilder buffer = new StringBuilder();
    if (!term.field().equals(field)) {
      buffer.append(term.field());
      buffer.append(":");
    }
    buffer.append(term.text());
    buffer.append('~');
    buffer.append(maxEdits);
    return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + maxEdits;
    result = prime * result + prefixLength;
    result = prime * result + maxExpansions;
    result = prime * result + (transpositions ? 0 : 1);
    result = prime * result + ((term == null) ? 0 : term.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    FuzzyQuery other = (FuzzyQuery) obj;
    return maxEdits == other.maxEdits
        && prefixLength == other.prefixLength
        && maxExpansions == other.maxExpansions
        && transpositions == other.transpositions
        && Objects.equals(term, other.term);
  }

  /**
   * Helper function to convert from "minimumSimilarity" fractions to raw edit distances.
   *
   * @param minimumSimilarity scaled similarity
   * @param termLen length (in unicode codepoints) of the term.
   * @return equivalent number of maxEdits
   */
  public static int floatToEdits(float minimumSimilarity, int termLen) {
    if (minimumSimilarity >= 1f) {
      return (int) Math.min(minimumSimilarity, LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    } else if (minimumSimilarity == 0.0f) {
      return 0; // 0 means exact, not infinite # of edits!
    } else {
      return Math.min(
          (int) ((1D - minimumSimilarity) * termLen),
          LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    }
  }
}
