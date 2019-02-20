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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.standard;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;


public final class UAX29URLEmailAnalyzer extends StopwordAnalyzerBase {

  public static final int DEFAULT_MAX_TOKEN_LENGTH = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

  public static final Set<?> STOP_WORDS_SET = StopAnalyzer.ENGLISH_STOP_WORDS_SET;


  public UAX29URLEmailAnalyzer(Version matchVersion, Set<?> stopWords) {
    super(matchVersion, stopWords);
  }


  public UAX29URLEmailAnalyzer(Version matchVersion) {
    this(matchVersion, STOP_WORDS_SET);
  }


  public UAX29URLEmailAnalyzer(Version matchVersion, Reader stopwords) throws IOException {
    this(matchVersion, WordlistLoader.getWordSet(stopwords, matchVersion));
  }


  public void setMaxTokenLength(int length) {
    maxTokenLength = length;
  }
    

  public int getMaxTokenLength() {
    return maxTokenLength;
  }

  @Override
  protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
    final UAX29URLEmailTokenizer src = new UAX29URLEmailTokenizer(matchVersion, reader);
    src.setMaxTokenLength(maxTokenLength);
    TokenStream tok = new StandardFilter(matchVersion, src);
    tok = new LowerCaseFilter(matchVersion, tok);
    tok = new StopFilter(matchVersion, tok, stopwords);
    return new TokenStreamComponents(src, tok) {
      @Override
      protected boolean reset(final Reader reader) throws IOException {
        src.setMaxTokenLength(UAX29URLEmailAnalyzer.this.maxTokenLength);
        return super.reset(reader);
      }
    };
  }
}
