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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


import java.io.IOException;
import java.util.Comparator;

public class MultiReader extends BaseCompositeReader<IndexReader> {
  private final boolean closeSubReaders;
  
  public MultiReader(IndexReader... subReaders) throws IOException {
    this(subReaders, null, true);
  }

  public MultiReader(IndexReader[] subReaders, boolean closeSubReaders) throws IOException {
    this(subReaders, null, closeSubReaders);
  }

  public MultiReader(
      IndexReader[] subReaders, Comparator<IndexReader> subReadersSorter, boolean closeSubReaders)
      throws IOException {
    super(subReaders.clone(), subReadersSorter);
    this.closeSubReaders = closeSubReaders;
    if (!closeSubReaders) {
      for (int i = 0; i < subReaders.length; i++) {
        subReaders[i].incRef();
      }
    }
  }

  @Override
  public CacheHelper getReaderCacheHelper() {
    // MultiReader instances can be short-lived, which would make caching trappy
    // so we do not cache on them, unless they wrap a single reader in which
    // case we delegate
    if (getSequentialSubReaders().size() == 1) {
      return getSequentialSubReaders().get(0).getReaderCacheHelper();
    }
    return null;
  }

  @Override
  protected synchronized void doClose() throws IOException {
    IOException ioe = null;
    for (final IndexReader r : getSequentialSubReaders()) {
      try {
        if (closeSubReaders) {
          r.close();
        } else {
          r.decRef();
        }
      } catch (IOException e) {
        if (ioe == null) ioe = e;
      }
    }
    // throw the first exception
    if (ioe != null) throw ioe;
  }
}
