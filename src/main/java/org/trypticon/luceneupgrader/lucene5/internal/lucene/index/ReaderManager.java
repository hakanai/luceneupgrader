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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.ReferenceManager;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.SearcherManager;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;

public final class ReaderManager extends ReferenceManager<DirectoryReader> {

  public ReaderManager(IndexWriter writer) throws IOException {
    this(writer, true);
  }

  public ReaderManager(IndexWriter writer, boolean applyAllDeletes) throws IOException {
    current = DirectoryReader.open(writer, applyAllDeletes);
  }
  
  public ReaderManager(Directory dir) throws IOException {
    current = DirectoryReader.open(dir);
  }

  public ReaderManager(DirectoryReader reader) throws IOException {
    current = reader;
  }

  @Override
  protected void decRef(DirectoryReader reference) throws IOException {
    reference.decRef();
  }
  
  @Override
  protected DirectoryReader refreshIfNeeded(DirectoryReader referenceToRefresh) throws IOException {
    return DirectoryReader.openIfChanged(referenceToRefresh);
  }
  
  @Override
  protected boolean tryIncRef(DirectoryReader reference) {
    return reference.tryIncRef();
  }

  @Override
  protected int getRefCount(DirectoryReader reference) {
    return reference.getRefCount();
  }

}
