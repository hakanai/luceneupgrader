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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;

import java.io.Reader;
import java.io.IOException;

public abstract class Tokenizer extends TokenStream {
  protected Reader input;


  @Deprecated
  protected Tokenizer() {}
  
  protected Tokenizer(Reader input) {
    this.input = input;
  }
  

  @Deprecated
  protected Tokenizer(AttributeFactory factory) {
    super(factory);
  }

  protected Tokenizer(AttributeFactory factory, Reader input) {
    super(factory);
    this.input = input;
  }


  @Deprecated
  protected Tokenizer(AttributeSource source) {
    super(source);
  }

  protected Tokenizer(AttributeSource source, Reader input) {
    super(source);
    this.input = input;
  }
  
  @Override
  public void close() throws IOException {
    if (input != null) {
      input.close();
      // LUCENE-2387: don't hold onto Reader after close, so
      // GC can reclaim
      input = null;
    }
  }
  

  protected final int correctOffset(int currentOff) {
    return (input instanceof CharStream) ? ((CharStream) input).correctOffset(currentOff) : currentOff;
  }


  public void reset(Reader input) throws IOException {
    this.input = input;
  }
}

