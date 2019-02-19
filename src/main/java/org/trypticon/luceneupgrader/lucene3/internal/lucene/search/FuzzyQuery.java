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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ToStringUtils;

import java.io.IOException;

public class FuzzyQuery extends MultiTermQuery {
  
  public final static float defaultMinSimilarity = 0.5f;
  public final static int defaultPrefixLength = 0;
  public final static int defaultMaxExpansions = Integer.MAX_VALUE;
  
  private float minimumSimilarity;
  private int prefixLength;
  private boolean termLongEnough = false;
  
  protected Term term;
  
  public FuzzyQuery(Term term, float minimumSimilarity, int prefixLength,
      int maxExpansions) {
    this.term = term;
    
    if (minimumSimilarity >= 1.0f)
      throw new IllegalArgumentException("minimumSimilarity >= 1");
    else if (minimumSimilarity < 0.0f)
      throw new IllegalArgumentException("minimumSimilarity < 0");
    if (prefixLength < 0)
      throw new IllegalArgumentException("prefixLength < 0");
    if (maxExpansions < 0)
      throw new IllegalArgumentException("maxExpansions < 0");
    
    setRewriteMethod(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(maxExpansions));
    
    if (term.text().length() > 1.0f / (1.0f - minimumSimilarity)) {
      this.termLongEnough = true;
    }
    
    this.minimumSimilarity = minimumSimilarity;
    this.prefixLength = prefixLength;
  }
  
  public FuzzyQuery(Term term, float minimumSimilarity, int prefixLength) {
    this(term, minimumSimilarity, prefixLength, defaultMaxExpansions);
  }
  
  public FuzzyQuery(Term term, float minimumSimilarity) {
    this(term, minimumSimilarity, defaultPrefixLength, defaultMaxExpansions);
  }

  public FuzzyQuery(Term term) {
    this(term, defaultMinSimilarity, defaultPrefixLength, defaultMaxExpansions);
  }
  
  public float getMinSimilarity() {
    return minimumSimilarity;
  }
    
  public int getPrefixLength() {
    return prefixLength;
  }

  @Override
  protected FilteredTermEnum getEnum(IndexReader reader) throws IOException {
    if (!termLongEnough) {  // can only match if it's exact
      return new SingleTermEnum(reader, term);
    }
    return new FuzzyTermEnum(reader, getTerm(), minimumSimilarity, prefixLength);
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
    buffer.append(Float.toString(minimumSimilarity));
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Float.floatToIntBits(minimumSimilarity);
    result = prime * result + prefixLength;
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
    if (Float.floatToIntBits(minimumSimilarity) != Float
        .floatToIntBits(other.minimumSimilarity))
      return false;
    if (prefixLength != other.prefixLength)
      return false;
    if (term == null) {
      if (other.term != null)
        return false;
    } else if (!term.equals(other.term))
      return false;
    return true;
  }


}
