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
import java.io.Closeable;
import java.lang.reflect.Modifier;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Attribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeImpl;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;


public abstract class TokenStream extends AttributeSource implements Closeable {


  protected TokenStream() {
    super();
    assert assertFinal();
  }
  

  protected TokenStream(AttributeSource input) {
    super(input);
    assert assertFinal();
  }
  

  protected TokenStream(AttributeFactory factory) {
    super(factory);
    assert assertFinal();
  }
  
  private boolean assertFinal() {
    try {
      final Class<?> clazz = getClass();
      if (!clazz.desiredAssertionStatus())
        return true;
      assert clazz.isAnonymousClass() ||
        (clazz.getModifiers() & (Modifier.FINAL | Modifier.PRIVATE)) != 0 ||
        Modifier.isFinal(clazz.getMethod("incrementToken").getModifiers()) :
        "TokenStream implementation classes or at least their incrementToken() implementation must be final";
      return true;
    } catch (NoSuchMethodException nsme) {
      return false;
    }
  }
  

  public abstract boolean incrementToken() throws IOException;
  

  public void end() throws IOException {
    // do nothing by default
  }


  public void reset() throws IOException {}
  
  public void close() throws IOException {}
  
}
