package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.standard;

/**
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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.*;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/**
 * Filters {@link org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.standard.UAX29URLEmailTokenizer}
 * with {@link org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.standard.StandardFilter},
 * {@link org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.LowerCaseFilter} and
 * {@link org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.StopFilter}, using a list of
 * English stop words.
 *
 * <a name="version"/>
 * <p>
 *   You must specify the required {@link org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version}
 *   compatibility when creating UAX29URLEmailAnalyzer
 * </p>
 */
public final class UAX29URLEmailAnalyzer extends StopwordAnalyzerBase {

  /** Default maximum allowed token length */
  public static final int DEFAULT_MAX_TOKEN_LENGTH = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

  /** An unmodifiable set containing some common English words that are usually not
  useful for searching. */
  public static final Set<?> STOP_WORDS_SET = StopAnalyzer.ENGLISH_STOP_WORDS_SET;

  /** Builds an analyzer with the given stop words.
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   * @param stopWords stop words */
  public UAX29URLEmailAnalyzer(Version matchVersion, Set<?> stopWords) {
    super(matchVersion, stopWords);
  }

  /** Builds an analyzer with the default stop words ({@link
   * #STOP_WORDS_SET}).
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   */
  public UAX29URLEmailAnalyzer(Version matchVersion) {
    this(matchVersion, STOP_WORDS_SET);
  }

  /** Builds an analyzer with the stop words from the given reader.
   * @see org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.WordlistLoader#getWordSet(java.io.Reader, org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version)
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   * @param stopwords Reader to read stop words from */
  public UAX29URLEmailAnalyzer(Version matchVersion, Reader stopwords) throws IOException {
    this(matchVersion, WordlistLoader.getWordSet(stopwords, matchVersion));
  }

  /**
   * Set maximum allowed token length.  If a token is seen
   * that exceeds this length then it is discarded.  This
   * setting only takes effect the next time tokenStream or
   * reusableTokenStream is called.
   */
  public void setMaxTokenLength(int length) {
    maxTokenLength = length;
  }
    
  /**
   * @see #setMaxTokenLength
   */
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
