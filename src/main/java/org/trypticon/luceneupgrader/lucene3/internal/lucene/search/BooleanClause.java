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

public class BooleanClause implements java.io.Serializable {
  
  public static enum Occur {

    MUST     { @Override public String toString() { return "+"; } },

    SHOULD   { @Override public String toString() { return "";  } },


    MUST_NOT { @Override public String toString() { return "-"; } };

  }

  private Query query;

  private Occur occur;


  public BooleanClause(Query query, Occur occur) {
    this.query = query;
    this.occur = occur;
    
  }

  public Occur getOccur() {
    return occur;
  }

  public void setOccur(Occur occur) {
    this.occur = occur;

  }

  public Query getQuery() {
    return query;
  }

  public void setQuery(Query query) {
    this.query = query;
  }
  
  public boolean isProhibited() {
    return Occur.MUST_NOT == occur;
  }

  public boolean isRequired() {
    return Occur.MUST == occur;
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
    return query.hashCode() ^ (Occur.MUST == occur?1:0) ^ (Occur.MUST_NOT == occur?2:0);
  }


  @Override
  public String toString() {
    return occur.toString() + query.toString();
  }
}
