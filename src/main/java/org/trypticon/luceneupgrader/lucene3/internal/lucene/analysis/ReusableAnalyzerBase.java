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
import java.io.Reader;


public abstract class ReusableAnalyzerBase extends Analyzer {


  protected abstract TokenStreamComponents createComponents(String fieldName,
      Reader reader);


  @Override
  public final TokenStream reusableTokenStream(final String fieldName,
      final Reader reader) throws IOException {
    TokenStreamComponents streamChain = (TokenStreamComponents)
    getPreviousTokenStream();
    final Reader r = initReader(reader);
    if (streamChain == null || !streamChain.reset(r)) {
      streamChain = createComponents(fieldName, r);
      setPreviousTokenStream(streamChain);
    }
    return streamChain.getTokenStream();
  }


  @Override
  public final TokenStream tokenStream(final String fieldName,
      final Reader reader) {
    return createComponents(fieldName, initReader(reader)).getTokenStream();
  }
  

  protected Reader initReader(Reader reader) {
    return reader;
  }
  

  public static class TokenStreamComponents {
    protected final Tokenizer source;
    protected final TokenStream sink;


    public TokenStreamComponents(final Tokenizer source,
        final TokenStream result) {
      this.source = source;
      this.sink = result;
    }
    

    public TokenStreamComponents(final Tokenizer source) {
      this.source = source;
      this.sink = source;
    }


    protected boolean reset(final Reader reader) throws IOException {
      source.reset(reader);
      return true;
    }


    protected TokenStream getTokenStream() {
      return sink;
    }

  }

}
