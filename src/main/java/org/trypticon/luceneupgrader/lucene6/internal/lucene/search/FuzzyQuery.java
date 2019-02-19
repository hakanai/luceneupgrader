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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SingleTermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.automaton.LevenshteinAutomata;

public class FuzzyQuery extends MultiTermQuery {
  
  public final static int defaultMaxEdits = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE;
  public final static int defaultPrefixLength = 0;
  public final static int defaultMaxExpansions = 50;
  public final static boolean defaultTranspositions = true;
  
  private final int maxEdits;
  private final int maxExpansions;
  private final boolean transpositions;
  private final int prefixLength;
  private final Term term;
  
  public FuzzyQuery(Term term, int maxEdits, int prefixLength, int maxExpansions, boolean transpositions) {
    super(term.field());
    
    if (maxEdits < 0 || maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE) {
      throw new IllegalArgumentException("maxEdits must be between 0 and " + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
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
    setRewriteMethod(new MultiTermQuery.TopTermsBlendedFreqScoringRewrite(maxExpansions));
  }
  
  public FuzzyQuery(Term term, int maxEdits, int prefixLength) {
    this(term, maxEdits, prefixLength, defaultMaxExpansions, defaultTranspositions);
  }
  
  public FuzzyQuery(Term term, int maxEdits) {
    this(term, maxEdits, defaultPrefixLength);
  }

  public FuzzyQuery(Term term) {
    this(term, defaultMaxEdits);
  }
  
  public int getMaxEdits() {
    return maxEdits;
  }
    
  public int getPrefixLength() {
    return prefixLength;
  }
  
  public boolean getTranspositions() {
    return transpositions;
  }

  @Override
  protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {
    if (maxEdits == 0 || prefixLength >= term.text().length()) {  // can only match if it's exact
      return new SingleTermsEnum(terms.iterator(), term.bytes());
    }
    return new FuzzyTermsEnum(terms, atts, getTerm(), maxEdits, prefixLength, transpositions);
  }
  
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
    buffer.append(Integer.toString(maxEdits));
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
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    FuzzyQuery other = (FuzzyQuery) obj;
    if (maxEdits != other.maxEdits)
      return false;
    if (prefixLength != other.prefixLength)
      return false;
    if (maxExpansions != other.maxExpansions)
      return false;
    if (transpositions != other.transpositions)
      return false;
    if (term == null) {
      if (other.term != null)
        return false;
    } else if (!term.equals(other.term))
      return false;
    return true;
  }
  
  @Deprecated
  public final static float defaultMinSimilarity = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE;

  @Deprecated
  public static int floatToEdits(float minimumSimilarity, int termLen) {
    if (minimumSimilarity >= 1f) {
      return (int) Math.min(minimumSimilarity, LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    } else if (minimumSimilarity == 0.0f) {
      return 0; // 0 means exact, not infinite # of edits!
    } else {
      return Math.min((int) ((1D-minimumSimilarity) * termLen), 
        LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    }
  }
}
