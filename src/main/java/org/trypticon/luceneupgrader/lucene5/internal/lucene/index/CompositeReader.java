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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.util.List;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.*;

public abstract class CompositeReader extends IndexReader {

  private volatile CompositeReaderContext readerContext = null; // lazy init

  protected CompositeReader() {
    super();
  }
  
  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    // walk up through class hierarchy to get a non-empty simple name (anonymous classes have no name):
    for (Class<?> clazz = getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      if (!clazz.isAnonymousClass()) {
        buffer.append(clazz.getSimpleName());
        break;
      }
    }
    buffer.append('(');
    final List<? extends IndexReader> subReaders = getSequentialSubReaders();
    assert subReaders != null;
    if (!subReaders.isEmpty()) {
      buffer.append(subReaders.get(0));
      for (int i = 1, c = subReaders.size(); i < c; ++i) {
        buffer.append(" ").append(subReaders.get(i));
      }
    }
    buffer.append(')');
    return buffer.toString();
  }
  

  protected abstract List<? extends IndexReader> getSequentialSubReaders();

  @Override
  public final CompositeReaderContext getContext() {
    ensureOpen();
    // lazy init without thread safety for perf reasons: Building the readerContext twice does not hurt!
    if (readerContext == null) {
      assert getSequentialSubReaders() != null;
      readerContext = CompositeReaderContext.create(this);
    }
    return readerContext;
  }
}
