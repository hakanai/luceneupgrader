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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DirectoryReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;

public final class SearcherManager extends ReferenceManager<IndexSearcher> {

  private final SearcherFactory searcherFactory;

  public SearcherManager(IndexWriter writer, boolean applyAllDeletes, SearcherFactory searcherFactory) throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = getSearcher(searcherFactory, DirectoryReader.open(writer, applyAllDeletes));
  }
  
  public SearcherManager(Directory dir, SearcherFactory searcherFactory) throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = getSearcher(searcherFactory, DirectoryReader.open(dir));
  }

  @Override
  protected void decRef(IndexSearcher reference) throws IOException {
    reference.getIndexReader().decRef();
  }
  
  @Override
  protected IndexSearcher refreshIfNeeded(IndexSearcher referenceToRefresh) throws IOException {
    final IndexReader r = referenceToRefresh.getIndexReader();
    assert r instanceof DirectoryReader: "searcher's IndexReader should be a DirectoryReader, but got " + r;
    final IndexReader newReader = DirectoryReader.openIfChanged((DirectoryReader) r);
    if (newReader == null) {
      return null;
    } else {
      return getSearcher(searcherFactory, newReader);
    }
  }
  
  @Override
  protected boolean tryIncRef(IndexSearcher reference) {
    return reference.getIndexReader().tryIncRef();
  }

  @Override
  protected int getRefCount(IndexSearcher reference) {
    return reference.getIndexReader().getRefCount();
  }

  public boolean isSearcherCurrent() throws IOException {
    final IndexSearcher searcher = acquire();
    try {
      final IndexReader r = searcher.getIndexReader();
      assert r instanceof DirectoryReader: "searcher's IndexReader should be a DirectoryReader, but got " + r;
      return ((DirectoryReader) r).isCurrent();
    } finally {
      release(searcher);
    }
  }


  public static IndexSearcher getSearcher(SearcherFactory searcherFactory, IndexReader reader) throws IOException {
    boolean success = false;
    final IndexSearcher searcher;
    try {
      searcher = searcherFactory.newSearcher(reader);
      if (searcher.getIndexReader() != reader) {
        throw new IllegalStateException("SearcherFactory must wrap exactly the provided reader (got " + searcher.getIndexReader() + " but expected " + reader + ")");
      }
      success = true;
    } finally {
      if (!success) {
        reader.decRef();
      }
    }
    return searcher;
  }
}
