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

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;

@Deprecated
public class TermRangeFilter extends MultiTermQueryWrapperFilter<TermRangeQuery> {
    
  public TermRangeFilter(String fieldName, BytesRef lowerTerm, BytesRef upperTerm,
                     boolean includeLower, boolean includeUpper) {
      super(new TermRangeQuery(fieldName, lowerTerm, upperTerm, includeLower, includeUpper));
  }

  public static TermRangeFilter newStringRange(String field, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
    BytesRef lower = lowerTerm == null ? null : new BytesRef(lowerTerm);
    BytesRef upper = upperTerm == null ? null : new BytesRef(upperTerm);
    return new TermRangeFilter(field, lower, upper, includeLower, includeUpper);
  }
  
  public static TermRangeFilter Less(String fieldName, BytesRef upperTerm) {
      return new TermRangeFilter(fieldName, null, upperTerm, false, true);
  }

  public static TermRangeFilter More(String fieldName, BytesRef lowerTerm) {
      return new TermRangeFilter(fieldName, lowerTerm, null, true, false);
  }
  
  public BytesRef getLowerTerm() { return query.getLowerTerm(); }

  public BytesRef getUpperTerm() { return query.getUpperTerm(); }
  
  public boolean includesLower() { return query.includesLower(); }
  
  public boolean includesUpper() { return query.includesUpper(); }
}
