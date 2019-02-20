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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;


public final class LengthFilter extends FilteringTokenFilter {

  private final int min;
  private final int max;
  
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);


  public LengthFilter(boolean enablePositionIncrements, TokenStream in, int min, int max) {
    super(enablePositionIncrements, in);
    this.min = min;
    this.max = max;
  }
  

  @Deprecated
  public LengthFilter(TokenStream in, int min, int max) {
    this(false, in, min, max);
  }
  
  @Override
  public boolean accept() throws IOException {
    final int len = termAtt.length();
    return (len >= min && len <= max);
  }
}
