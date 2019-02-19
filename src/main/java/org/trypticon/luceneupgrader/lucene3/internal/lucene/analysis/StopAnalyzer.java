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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Set;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

public final class StopAnalyzer extends StopwordAnalyzerBase {
  
  public static final Set<?> ENGLISH_STOP_WORDS_SET;
  
  static {
    final List<String> stopWords = Arrays.asList(
      "a", "an", "and", "are", "as", "at", "be", "but", "by",
      "for", "if", "in", "into", "is", "it",
      "no", "not", "of", "on", "or", "such",
      "that", "the", "their", "then", "there", "these",
      "they", "this", "to", "was", "will", "with"
    );
    final CharArraySet stopSet = new CharArraySet(Version.LUCENE_CURRENT, 
        stopWords.size(), false);
    stopSet.addAll(stopWords);  
    ENGLISH_STOP_WORDS_SET = CharArraySet.unmodifiableSet(stopSet); 
  }
  

  public StopAnalyzer(Version matchVersion) {
    this(matchVersion, ENGLISH_STOP_WORDS_SET);
  }


  public StopAnalyzer(Version matchVersion, Set<?> stopWords) {
    super(matchVersion, stopWords);
  }


  public StopAnalyzer(Version matchVersion, File stopwordsFile) throws IOException {
    this(matchVersion, WordlistLoader.getWordSet(IOUtils.getDecodingReader(stopwordsFile,
        IOUtils.CHARSET_UTF_8), matchVersion));
  }


  public StopAnalyzer(Version matchVersion, Reader stopwords) throws IOException {
    this(matchVersion, WordlistLoader.getWordSet(stopwords, matchVersion));
  }


  @Override
  protected TokenStreamComponents createComponents(String fieldName,
      Reader reader) {
    final Tokenizer source = new LowerCaseTokenizer(matchVersion, reader);
    return new TokenStreamComponents(source, new StopFilter(matchVersion,
          source, stopwords));
  }
}

