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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis;


import java.util.Arrays;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.CharTermAttribute;

public class StopFilter extends FilteringTokenFilter {

  private final CharArraySet stopWords;
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  
  public StopFilter(TokenStream in, CharArraySet stopWords) {
    super(in);
    this.stopWords = stopWords;
  }

  public static CharArraySet makeStopSet(String... stopWords) {
    return makeStopSet(stopWords, false);
  }
  
  public static CharArraySet makeStopSet(List<?> stopWords) {
    return makeStopSet(stopWords, false);
  }
    
  public static CharArraySet makeStopSet(String[] stopWords, boolean ignoreCase) {
    CharArraySet stopSet = new CharArraySet(stopWords.length, ignoreCase);
    stopSet.addAll(Arrays.asList(stopWords));
    return stopSet;
  }
  
  public static CharArraySet makeStopSet(List<?> stopWords, boolean ignoreCase){
    CharArraySet stopSet = new CharArraySet(stopWords.size(), ignoreCase);
    stopSet.addAll(stopWords);
    return stopSet;
  }
  
  @Override
  protected boolean accept() {
    return !stopWords.contains(termAtt.buffer(), 0, termAtt.length());
  }

}
