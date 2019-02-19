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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis;


import java.io.Closeable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.CloseableThreadLocal;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Version;

public abstract class Analyzer implements Closeable {

  private final ReuseStrategy reuseStrategy;
  private Version version = Version.LATEST;

  // non final as it gets nulled if closed; pkg private for access by ReuseStrategy's final helper methods:
  CloseableThreadLocal<Object> storedValue = new CloseableThreadLocal<>();

  public Analyzer() {
    this(GLOBAL_REUSE_STRATEGY);
  }

  public Analyzer(ReuseStrategy reuseStrategy) {
    this.reuseStrategy = reuseStrategy;
  }

  protected abstract TokenStreamComponents createComponents(String fieldName);

  public final TokenStream tokenStream(final String fieldName,
                                       final Reader reader) {
    TokenStreamComponents components = reuseStrategy.getReusableComponents(this, fieldName);
    final Reader r = initReader(fieldName, reader);
    if (components == null) {
      components = createComponents(fieldName);
      reuseStrategy.setReusableComponents(this, fieldName, components);
    }
    components.setReader(r);
    return components.getTokenStream();
  }
  
  public final TokenStream tokenStream(final String fieldName, final String text) {
    TokenStreamComponents components = reuseStrategy.getReusableComponents(this, fieldName);
    @SuppressWarnings("resource") final ReusableStringReader strReader = 
        (components == null || components.reusableStringReader == null) ?
        new ReusableStringReader() : components.reusableStringReader;
    strReader.setValue(text);
    final Reader r = initReader(fieldName, strReader);
    if (components == null) {
      components = createComponents(fieldName);
      reuseStrategy.setReusableComponents(this, fieldName, components);
    }

    components.setReader(r);
    components.reusableStringReader = strReader;
    return components.getTokenStream();
  }
    
  protected Reader initReader(String fieldName, Reader reader) {
    return reader;
  }

  public int getPositionIncrementGap(String fieldName) {
    return 0;
  }

  public int getOffsetGap(String fieldName) {
    return 1;
  }

  public final ReuseStrategy getReuseStrategy() {
    return reuseStrategy;
  }

  public void setVersion(Version v) {
    version = v; // TODO: make write once?
  }

  public Version getVersion() {
    return version;
  }

  @Override
  public void close() {
    if (storedValue != null) {
      storedValue.close();
      storedValue = null;
    }
  }

  public static class TokenStreamComponents {
    protected final Tokenizer source;
    protected final TokenStream sink;
    
    transient ReusableStringReader reusableStringReader;

    public TokenStreamComponents(final Tokenizer source,
        final TokenStream result) {
      this.source = source;
      this.sink = result;
    }
    
    public TokenStreamComponents(final Tokenizer source) {
      this.source = source;
      this.sink = source;
    }

    protected void setReader(final Reader reader) {
      source.setReader(reader);
    }

    public TokenStream getTokenStream() {
      return sink;
    }

    public Tokenizer getTokenizer() {
      return source;
    }
  }

  public static abstract class ReuseStrategy {

    public ReuseStrategy() {}

    public abstract TokenStreamComponents getReusableComponents(Analyzer analyzer, String fieldName);

    public abstract void setReusableComponents(Analyzer analyzer, String fieldName, TokenStreamComponents components);

    protected final Object getStoredValue(Analyzer analyzer) {
      if (analyzer.storedValue == null) {
        throw new AlreadyClosedException("this Analyzer is closed");
      }
      return analyzer.storedValue.get();
    }

    protected final void setStoredValue(Analyzer analyzer, Object storedValue) {
      if (analyzer.storedValue == null) {
        throw new AlreadyClosedException("this Analyzer is closed");
      }
      analyzer.storedValue.set(storedValue);
    }

  }

  public static final ReuseStrategy GLOBAL_REUSE_STRATEGY = new ReuseStrategy() {

    @Override
    public TokenStreamComponents getReusableComponents(Analyzer analyzer, String fieldName) {
      return (TokenStreamComponents) getStoredValue(analyzer);
    }

    @Override
    public void setReusableComponents(Analyzer analyzer, String fieldName, TokenStreamComponents components) {
      setStoredValue(analyzer, components);
    }
  };

  public static final ReuseStrategy PER_FIELD_REUSE_STRATEGY = new ReuseStrategy() {

    @SuppressWarnings("unchecked")
    @Override
    public TokenStreamComponents getReusableComponents(Analyzer analyzer, String fieldName) {
      Map<String, TokenStreamComponents> componentsPerField = (Map<String, TokenStreamComponents>) getStoredValue(analyzer);
      return componentsPerField != null ? componentsPerField.get(fieldName) : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setReusableComponents(Analyzer analyzer, String fieldName, TokenStreamComponents components) {
      Map<String, TokenStreamComponents> componentsPerField = (Map<String, TokenStreamComponents>) getStoredValue(analyzer);
      if (componentsPerField == null) {
        componentsPerField = new HashMap<>();
        setStoredValue(analyzer, componentsPerField);
      }
      componentsPerField.put(fieldName, components);
    }
  };

}
