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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ToStringUtils;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.automaton.Automata;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.automaton.Automaton;

public class TermRangeQuery extends AutomatonQuery {
  private final BytesRef lowerTerm;
  private final BytesRef upperTerm;
  private final boolean includeLower;
  private final boolean includeUpper;

  public TermRangeQuery(String field, BytesRef lowerTerm, BytesRef upperTerm, boolean includeLower, boolean includeUpper) {
    super(new Term(field, lowerTerm), toAutomaton(lowerTerm, upperTerm, includeLower, includeUpper), Integer.MAX_VALUE, true);
    this.lowerTerm = lowerTerm;
    this.upperTerm = upperTerm;
    this.includeLower = includeLower;
    this.includeUpper = includeUpper;
  }

  public static Automaton toAutomaton(BytesRef lowerTerm, BytesRef upperTerm, boolean includeLower, boolean includeUpper) {

    if (lowerTerm == null) {
      // makeBinaryInterval is more picky than we are:
      includeLower = true;
    }

    if (upperTerm == null) {
      // makeBinaryInterval is more picky than we are:
      includeUpper = true;
    }

    return Automata.makeBinaryInterval(lowerTerm, includeLower, upperTerm, includeUpper);
  }

  public static TermRangeQuery newStringRange(String field, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
    BytesRef lower = lowerTerm == null ? null : new BytesRef(lowerTerm);
    BytesRef upper = upperTerm == null ? null : new BytesRef(upperTerm);
    return new TermRangeQuery(field, lower, upper, includeLower, includeUpper);
  }

  public BytesRef getLowerTerm() { return lowerTerm; }

  public BytesRef getUpperTerm() { return upperTerm; }
  
  public boolean includesLower() { return includeLower; }
  
  public boolean includesUpper() { return includeUpper; }
  
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!getField().equals(field)) {
      buffer.append(getField());
      buffer.append(":");
    }
    buffer.append(includeLower ? '[' : '{');
    // TODO: all these toStrings for queries should just output the bytes, it might not be UTF-8!
    buffer.append(lowerTerm != null ? ("*".equals(Term.toString(lowerTerm)) ? "\\*" : Term.toString(lowerTerm))  : "*");
    buffer.append(" TO ");
    buffer.append(upperTerm != null ? ("*".equals(Term.toString(upperTerm)) ? "\\*" : Term.toString(upperTerm)) : "*");
    buffer.append(includeUpper ? ']' : '}');
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (includeLower ? 1231 : 1237);
    result = prime * result + (includeUpper ? 1231 : 1237);
    result = prime * result + ((lowerTerm == null) ? 0 : lowerTerm.hashCode());
    result = prime * result + ((upperTerm == null) ? 0 : upperTerm.hashCode());
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
    TermRangeQuery other = (TermRangeQuery) obj;
    if (includeLower != other.includeLower)
      return false;
    if (includeUpper != other.includeUpper)
      return false;
    if (lowerTerm == null) {
      if (other.lowerTerm != null)
        return false;
    } else if (!lowerTerm.equals(other.lowerTerm))
      return false;
    if (upperTerm == null) {
      if (other.upperTerm != null)
        return false;
    } else if (!upperTerm.equals(other.upperTerm))
      return false;
    return true;
  }
}
