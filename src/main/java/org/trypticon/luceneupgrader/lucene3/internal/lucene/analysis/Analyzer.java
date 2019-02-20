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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Fieldable;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CloseableThreadLocal;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;


public abstract class Analyzer implements Closeable {

  protected Analyzer() {
    super();
    assert assertFinal();
  }
  
  private boolean assertFinal() {
    try {
      final Class<?> clazz = getClass();
      if (!clazz.desiredAssertionStatus())
        return true;
      assert clazz.isAnonymousClass() ||
        (clazz.getModifiers() & (Modifier.FINAL | Modifier.PRIVATE)) != 0 ||
        (
          Modifier.isFinal(clazz.getMethod("tokenStream", String.class, Reader.class).getModifiers()) &&
          Modifier.isFinal(clazz.getMethod("reusableTokenStream", String.class, Reader.class).getModifiers())
        ) :
        "Analyzer implementation classes or at least their tokenStream() and reusableTokenStream() implementations must be final";
      return true;
    } catch (NoSuchMethodException nsme) {
      return false;
    }
  }


  public abstract TokenStream tokenStream(String fieldName, Reader reader);


  public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
    return tokenStream(fieldName, reader);
  }

  private CloseableThreadLocal<Object> tokenStreams = new CloseableThreadLocal<Object>();


  protected Object getPreviousTokenStream() {
    try {
      return tokenStreams.get();
    } catch (NullPointerException npe) {
      if (tokenStreams == null) {
        throw new AlreadyClosedException("this Analyzer is closed");
      } else {
        throw npe;
      }
    }
  }


  protected void setPreviousTokenStream(Object obj) {
    try {
      tokenStreams.set(obj);
    } catch (NullPointerException npe) {
      if (tokenStreams == null) {
        throw new AlreadyClosedException("this Analyzer is closed");
      } else {
        throw npe;
      }
    }
  }


  public int getPositionIncrementGap(String fieldName) {
    return 0;
  }


  public int getOffsetGap(Fieldable field) {
    if (field.isTokenized())
      return 1;
    else
      return 0;
  }

  public void close() {
    tokenStreams.close();
    tokenStreams = null;
  }
}
