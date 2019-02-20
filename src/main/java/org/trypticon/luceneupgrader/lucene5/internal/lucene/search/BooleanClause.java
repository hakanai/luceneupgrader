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


import java.util.Objects;

public final class BooleanClause {
  
  public static enum Occur {

    MUST     { @Override public String toString() { return "+"; } },

    FILTER   { @Override public String toString() { return "#"; } },

    SHOULD   { @Override public String toString() { return "";  } },


    MUST_NOT { @Override public String toString() { return "-"; } };

  }

  private Query query;

  private Occur occur;


  public BooleanClause(Query query, Occur occur) {
    this.query = Objects.requireNonNull(query, "Query must not be null");
    this.occur = Objects.requireNonNull(occur, "Occur must not be null");
    
  }

  public Occur getOccur() {
    return occur;
  }

  public Query getQuery() {
    return query;
  }
  
  public boolean isProhibited() {
    return Occur.MUST_NOT == occur;
  }

  public boolean isRequired() {
    return occur == Occur.MUST || occur == Occur.FILTER;
  }

  public boolean isScoring() {
    return occur == Occur.MUST || occur == Occur.SHOULD;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof BooleanClause))
      return false;
    BooleanClause other = (BooleanClause)o;
    return this.query.equals(other.query)
      && this.occur == other.occur;
  }

  @Override
  public int hashCode() {
    return 31 * query.hashCode() + occur.hashCode();
  }


  @Override
  public String toString() {
    return occur.toString() + query.toString();
  }

  @Deprecated
  public void setOccur(Occur occur) {
    this.occur = Objects.requireNonNull(occur, "Occur must not be null"); 
  }

  @Deprecated
  public void setQuery(Query query) {
    this.query = Objects.requireNonNull(query, "Query must not be null");
  }
}
