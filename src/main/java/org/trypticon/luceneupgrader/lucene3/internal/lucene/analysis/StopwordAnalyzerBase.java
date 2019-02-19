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
import java.util.Set;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;


public abstract class StopwordAnalyzerBase extends ReusableAnalyzerBase {


  protected final CharArraySet stopwords;

  protected final Version matchVersion;


  public Set<?> getStopwordSet() {
    return stopwords;
  }


  protected StopwordAnalyzerBase(final Version version, final Set<?> stopwords) {
    matchVersion = version;
    // analyzers should use char array set for stopwords!
    this.stopwords = stopwords == null ? CharArraySet.EMPTY_SET : CharArraySet
        .unmodifiableSet(CharArraySet.copy(version, stopwords));
  }


  protected StopwordAnalyzerBase(final Version version) {
    this(version, null);
  }


  protected static CharArraySet loadStopwordSet(final boolean ignoreCase,
      final Class<? extends ReusableAnalyzerBase> aClass, final String resource,
      final String comment) throws IOException {
    Reader reader = null;
    try {
      reader = IOUtils.getDecodingReader(aClass.getResourceAsStream(resource), IOUtils.CHARSET_UTF_8);
      return WordlistLoader.getWordSet(reader, comment, new CharArraySet(Version.LUCENE_31, 16, ignoreCase));
    } finally {
      IOUtils.close(reader);
    }
    
  }
  

  protected static CharArraySet loadStopwordSet(File stopwords,
      Version matchVersion) throws IOException {
    Reader reader = null;
    try {
      reader = IOUtils.getDecodingReader(stopwords, IOUtils.CHARSET_UTF_8);
      return WordlistLoader.getWordSet(reader, matchVersion);
    } finally {
      IOUtils.close(reader);
    }
  }
  

  protected static CharArraySet loadStopwordSet(Reader stopwords,
      Version matchVersion) throws IOException {
    try {
      return WordlistLoader.getWordSet(stopwords, matchVersion);
    } finally {
      IOUtils.close(stopwords);
    }
  }
}
