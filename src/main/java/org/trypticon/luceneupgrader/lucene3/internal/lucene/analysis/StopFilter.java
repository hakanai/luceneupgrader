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
import java.util.Arrays;
import java.util.Set;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;


public final class StopFilter extends FilteringTokenFilter {

  private final CharArraySet stopWords;
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);


  @Deprecated
  public StopFilter(boolean enablePositionIncrements, TokenStream input, Set<?> stopWords, boolean ignoreCase)
  {
    this(Version.LUCENE_30, enablePositionIncrements, input, stopWords, ignoreCase);
  }
  

  @Deprecated
  public StopFilter(Version matchVersion, TokenStream input, Set<?> stopWords, boolean ignoreCase)
  {
   this(matchVersion, matchVersion.onOrAfter(Version.LUCENE_29), input, stopWords, ignoreCase);
  }
  
  /*
   * convenience ctor to enable deprecated ctors to set posInc explicitly
   */
  private StopFilter(Version matchVersion, boolean enablePositionIncrements, TokenStream input, Set<?> stopWords, boolean ignoreCase){
    super(enablePositionIncrements, input);
    this.stopWords = stopWords instanceof CharArraySet ? (CharArraySet)stopWords : new CharArraySet(matchVersion, stopWords, ignoreCase);  
  }


  @Deprecated
  public StopFilter(boolean enablePositionIncrements, TokenStream in, Set<?> stopWords) {
    this(Version.LUCENE_30, enablePositionIncrements, in, stopWords, false);
  }
  

  public StopFilter(Version matchVersion, TokenStream in, Set<?> stopWords) {
    this(matchVersion, in, stopWords, false);
  }


  @Deprecated
  public static final Set<Object> makeStopSet(String... stopWords) {
    return makeStopSet(Version.LUCENE_30, stopWords, false);
  }


  public static final Set<Object> makeStopSet(Version matchVersion, String... stopWords) {
    return makeStopSet(matchVersion, stopWords, false);
  }
  

  @Deprecated
  public static final Set<Object> makeStopSet(List<?> stopWords) {
    return makeStopSet(Version.LUCENE_30, stopWords, false);
  }


  public static final Set<Object> makeStopSet(Version matchVersion, List<?> stopWords) {
    return makeStopSet(matchVersion, stopWords, false);
  }
    

  @Deprecated
  public static final Set<Object> makeStopSet(String[] stopWords, boolean ignoreCase) {
    return makeStopSet(Version.LUCENE_30, stopWords, ignoreCase);
  }

  public static final Set<Object> makeStopSet(Version matchVersion, String[] stopWords, boolean ignoreCase) {
    CharArraySet stopSet = new CharArraySet(matchVersion, stopWords.length, ignoreCase);
    stopSet.addAll(Arrays.asList(stopWords));
    return stopSet;
  }
  

  @Deprecated
  public static final Set<Object> makeStopSet(List<?> stopWords, boolean ignoreCase){
    return makeStopSet(Version.LUCENE_30, stopWords, ignoreCase);
  }


  public static final Set<Object> makeStopSet(Version matchVersion, List<?> stopWords, boolean ignoreCase){
    CharArraySet stopSet = new CharArraySet(matchVersion, stopWords.size(), ignoreCase);
    stopSet.addAll(stopWords);
    return stopSet;
  }
  

  @Override
  protected boolean accept() throws IOException {
    return !stopWords.contains(termAtt.buffer(), 0, termAtt.length());
  }


  @Deprecated
  public static boolean getEnablePositionIncrementsVersionDefault(Version matchVersion) {
    return matchVersion.onOrAfter(Version.LUCENE_29);
  }
}
