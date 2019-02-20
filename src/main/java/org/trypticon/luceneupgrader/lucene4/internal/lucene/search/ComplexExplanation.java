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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

public class ComplexExplanation extends Explanation {
  private Boolean match;
  
  public ComplexExplanation() {
    super();
  }

  public ComplexExplanation(boolean match, float value, String description) {
    // NOTE: use of "boolean" instead of "Boolean" in params is conscious
    // choice to encourage clients to be specific.
    super(value, description);
    this.match = Boolean.valueOf(match);
  }

  public Boolean getMatch() { return match; }
  public void setMatch(Boolean match) { this.match = match; }
  @Override
  public boolean isMatch() {
    Boolean m = getMatch();
    return (null != m ? m.booleanValue() : super.isMatch());
  }

  @Override
  protected String getSummary() {
    if (null == getMatch())
      return super.getSummary();
    
    return getValue() + " = "
      + (isMatch() ? "(MATCH) " : "(NON-MATCH) ")
      + getDescription();
  }
  
}
