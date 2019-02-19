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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.collation;


import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.KeywordTokenizer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Tokenizer;

import java.text.Collator;
import java.io.Reader;
import java.io.IOException;

public final class CollationKeyAnalyzer extends Analyzer {
  private Collator collator;

  public CollationKeyAnalyzer(Collator collator) {
    this.collator = collator;
  }

  @Override
  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result = new KeywordTokenizer(reader);
    result = new CollationKeyFilter(result, collator);
    return result;
  }
  
  private class SavedStreams {
    Tokenizer source;
    TokenStream result;
  }
  
  @Override
  public TokenStream reusableTokenStream(String fieldName, Reader reader) 
    throws IOException {
    
    SavedStreams streams = (SavedStreams)getPreviousTokenStream();
    if (streams == null) {
      streams = new SavedStreams();
      streams.source = new KeywordTokenizer(reader);
      streams.result = new CollationKeyFilter(streams.source, collator);
      setPreviousTokenStream(streams);
    } else {
      streams.source.reset(reader);
    }
    return streams.result;
  }
}
