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

import java.text.Collator;

public class TermRangeFilter extends MultiTermQueryWrapperFilter<TermRangeQuery> {
    
  public TermRangeFilter(String fieldName, String lowerTerm, String upperTerm,
                     boolean includeLower, boolean includeUpper) {
      super(new TermRangeQuery(fieldName, lowerTerm, upperTerm, includeLower, includeUpper));
  }

  public TermRangeFilter(String fieldName, String lowerTerm, String upperTerm,
                     boolean includeLower, boolean includeUpper,
                     Collator collator) {
      super(new TermRangeQuery(fieldName, lowerTerm, upperTerm, includeLower, includeUpper, collator));
  }

  public static TermRangeFilter Less(String fieldName, String upperTerm) {
      return new TermRangeFilter(fieldName, null, upperTerm, false, true);
  }

  public static TermRangeFilter More(String fieldName, String lowerTerm) {
      return new TermRangeFilter(fieldName, lowerTerm, null, true, false);
  }

  public String getField() { return query.getField(); }
  
  public String getLowerTerm() { return query.getLowerTerm(); }

  public String getUpperTerm() { return query.getUpperTerm(); }
  
  public boolean includesLower() { return query.includesLower(); }
  
  public boolean includesUpper() { return query.includesUpper(); }

  public Collator getCollator() { return query.getCollator(); }
}
