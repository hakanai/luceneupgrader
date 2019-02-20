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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;

public abstract class Query implements Cloneable {
  private float boost = 1.0f;                     // query boost factor


  @Deprecated
  public void setBoost(float b) { boost = b; }


  @Deprecated
  public float getBoost() { return boost; }


  public abstract String toString(String field);

  @Override
  public final String toString() {
    return toString("");
  }

  public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    throw new UnsupportedOperationException("Query " + this + " does not implement createWeight");
  }


  public Query rewrite(IndexReader reader) throws IOException {
    if (boost != 1f) {
      Query rewritten = clone();
      rewritten.setBoost(1f);
      return new BoostQuery(rewritten, boost);
    }
    return this;
  }


  @Deprecated
  @Override
  public Query clone() {
    try {
      return (Query)super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Clone not supported: " + e.getMessage());
    }
  }

  @Override
  public int hashCode() {
    return Float.floatToIntBits(boost) ^ getClass().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Query other = (Query) obj;
    return Float.floatToIntBits(boost) == Float.floatToIntBits(other.boost);
  }
}
