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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;

public abstract class StopwordAnalyzerBase extends Analyzer {

  protected final CharArraySet stopwords;

  public CharArraySet getStopwordSet() {
    return stopwords;
  }

  protected StopwordAnalyzerBase(final CharArraySet stopwords) {
    // analyzers should use char array set for stopwords!
    this.stopwords = stopwords == null ? CharArraySet.EMPTY_SET : CharArraySet
        .unmodifiableSet(CharArraySet.copy(stopwords));
  }

  protected StopwordAnalyzerBase() {
    this(null);
  }

  protected static CharArraySet loadStopwordSet(final boolean ignoreCase,
      final Class<? extends Analyzer> aClass, final String resource,
      final String comment) throws IOException {
    Reader reader = null;
    try {
      reader = IOUtils.getDecodingReader(aClass.getResourceAsStream(resource), StandardCharsets.UTF_8);
      return WordlistLoader.getWordSet(reader, comment, new CharArraySet(16, ignoreCase));
    } finally {
      IOUtils.close(reader);
    }
    
  }
  
  protected static CharArraySet loadStopwordSet(Path stopwords) throws IOException {
    Reader reader = null;
    try {
      reader = Files.newBufferedReader(stopwords, StandardCharsets.UTF_8);
      return WordlistLoader.getWordSet(reader);
    } finally {
      IOUtils.close(reader);
    }
  }
  
  protected static CharArraySet loadStopwordSet(Reader stopwords) throws IOException {
    try {
      return WordlistLoader.getWordSet(stopwords);
    } finally {
      IOUtils.close(stopwords);
    }
  }
}
