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


public class WildcardQuery extends MultiTermQuery {
  private boolean termContainsWildcard;
  private boolean termIsPrefix;
  protected Term term;
    
  public WildcardQuery(Term term) {
    this.term = term;
    String text = term.text();
    this.termContainsWildcard = (text.indexOf('*') != -1)
        || (text.indexOf('?') != -1);
    this.termIsPrefix = termContainsWildcard 
        && (text.indexOf('?') == -1) 
        && (text.indexOf('*') == text.length() - 1);
  }

  @Override
  protected FilteredTermEnum getEnum(IndexReader reader) throws IOException {
    if (termIsPrefix) {
      return new PrefixTermEnum(reader, term.createTerm(term.text()
          .substring(0, term.text().indexOf('*')))); 
    } else if (termContainsWildcard) {
      return new WildcardTermEnum(reader, getTerm());
    } else {
      return new SingleTermEnum(reader, getTerm());
    }
  }
  
  public Term getTerm() {
    return term;
  }
  
  @Override
  public String toString(String field) {
    StringBuilder buffer = new StringBuilder();
    if (!term.field().equals(field)) {
      buffer.append(term.field());
      buffer.append(":");
    }
    buffer.append(term.text());
    buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
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
    WildcardQuery other = (WildcardQuery) obj;
    if (term == null) {
      if (other.term != null)
        return false;
    } else if (!term.equals(other.term))
      return false;
    return true;
  }

}
