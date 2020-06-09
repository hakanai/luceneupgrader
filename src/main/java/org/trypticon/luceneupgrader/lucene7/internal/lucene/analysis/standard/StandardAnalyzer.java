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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.standard;


import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.CharArraySet;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.LowerCaseFilter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.StopFilter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.StopwordAnalyzerBase;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.WordlistLoader;

public final class StandardAnalyzer extends StopwordAnalyzerBase {

  @Deprecated
  public static final CharArraySet ENGLISH_STOP_WORDS_SET;

  static {
    final List<String> stopWords = Arrays.asList(
      "a", "an", "and", "are", "as", "at", "be", "but", "by",
      "for", "if", "in", "into", "is", "it",
      "no", "not", "of", "on", "or", "such",
      "that", "the", "their", "then", "there", "these",
      "they", "this", "to", "was", "will", "with"
    );
    final CharArraySet stopSet = new CharArraySet(stopWords, false);
    ENGLISH_STOP_WORDS_SET = CharArraySet.unmodifiableSet(stopSet);
  }

  
  public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

  @Deprecated
  public static final CharArraySet STOP_WORDS_SET = ENGLISH_STOP_WORDS_SET;

  public StandardAnalyzer(CharArraySet stopWords) {
    super(stopWords);
  }

  public StandardAnalyzer() {
    this(STOP_WORDS_SET);
  }

  public StandardAnalyzer(Reader stopwords) throws IOException {
    this(loadStopwordSet(stopwords));
  }

  public void setMaxTokenLength(int length) {
    maxTokenLength = length;
  }
    
  public int getMaxTokenLength() {
    return maxTokenLength;
  }

  @Override
  protected TokenStreamComponents createComponents(final String fieldName) {
    final StandardTokenizer src = new StandardTokenizer();
    src.setMaxTokenLength(maxTokenLength);
    TokenStream tok = new LowerCaseFilter(src);
    tok = new StopFilter(tok, stopwords);
    return new TokenStreamComponents(src, tok) {
      @Override
      protected void setReader(final Reader reader) {
        // So that if maxTokenLength was changed, the change takes
        // effect next time tokenStream is called:
        src.setMaxTokenLength(StandardAnalyzer.this.maxTokenLength);
        super.setReader(reader);
      }
    };
  }

  @Override
  protected TokenStream normalize(String fieldName, TokenStream in) {
    return new LowerCaseFilter(in);
  }
}
