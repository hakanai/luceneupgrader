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
import java.text.Collator;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ToStringUtils;

public class TermRangeQuery extends MultiTermQuery {
  private String lowerTerm;
  private String upperTerm;
  private Collator collator;
  private String field;
  private boolean includeLower;
  private boolean includeUpper;


  public TermRangeQuery(String field, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
    this(field, lowerTerm, upperTerm, includeLower, includeUpper, null);
  }


  public TermRangeQuery(String field, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper,
                        Collator collator) {
    this.field = field;
    this.lowerTerm = lowerTerm;
    this.upperTerm = upperTerm;
    this.includeLower = includeLower;
    this.includeUpper = includeUpper;
    this.collator = collator;
  }

  public String getField() { return field; }
  
  public String getLowerTerm() { return lowerTerm; }

  public String getUpperTerm() { return upperTerm; }
  
  public boolean includesLower() { return includeLower; }
  
  public boolean includesUpper() { return includeUpper; }

  public Collator getCollator() { return collator; }
  
  @Override
  protected FilteredTermEnum getEnum(IndexReader reader) throws IOException {
    return new TermRangeTermEnum(reader, field, lowerTerm,
        upperTerm, includeLower, includeUpper, collator);
  }

  @Override
  public String toString(String field) {
      StringBuilder buffer = new StringBuilder();
      if (!getField().equals(field)) {
          buffer.append(getField());
          buffer.append(":");
      }
      buffer.append(includeLower ? '[' : '{');
      buffer.append(lowerTerm != null ? ("*".equals(lowerTerm) ? "\\*" : lowerTerm)  : "*");
      buffer.append(" TO ");
      buffer.append(upperTerm != null ? ("*".equals(upperTerm) ? "\\*" : upperTerm) : "*");
      buffer.append(includeUpper ? ']' : '}');
      buffer.append(ToStringUtils.boost(getBoost()));
      return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((collator == null) ? 0 : collator.hashCode());
    result = prime * result + ((field == null) ? 0 : field.hashCode());
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
    if (collator == null) {
      if (other.collator != null)
        return false;
    } else if (!collator.equals(other.collator))
      return false;
    if (field == null) {
      if (other.field != null)
        return false;
    } else if (!field.equals(other.field))
      return false;
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
