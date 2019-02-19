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

import static org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSet.EMPTY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.WAH8DocIdSet;

public class CachingWrapperFilter extends Filter implements Accountable {
  private final Filter filter;
  private final Map<Object,DocIdSet> cache = Collections.synchronizedMap(new WeakHashMap<Object,DocIdSet>());


  public CachingWrapperFilter(Filter filter) {
    this.filter = filter;
  }

  public Filter getFilter() {
    return filter;
  }


  protected DocIdSet docIdSetToCache(DocIdSet docIdSet, AtomicReader reader) throws IOException {
    if (docIdSet == null) {
      // this is better than returning null, as the nonnull result can be cached
      return EMPTY;
    } else if (docIdSet.isCacheable()) {
      return docIdSet;
    } else {
      final DocIdSetIterator it = docIdSet.iterator();
      // null is allowed to be returned by iterator(),
      // in this case we wrap with the sentinel set,
      // which is cacheable.
      if (it == null) {
        return EMPTY;
      } else {
        return cacheImpl(it, reader);
      }
    }
  }
  
  protected DocIdSet cacheImpl(DocIdSetIterator iterator, AtomicReader reader) throws IOException {
    WAH8DocIdSet.Builder builder = new WAH8DocIdSet.Builder();
    builder.add(iterator);
    return builder.build();
  }

  // for testing
  int hitCount, missCount;

  @Override
  public DocIdSet getDocIdSet(AtomicReaderContext context, final Bits acceptDocs) throws IOException {
    final AtomicReader reader = context.reader();
    final Object key = reader.getCoreCacheKey();

    DocIdSet docIdSet = cache.get(key);
    if (docIdSet != null) {
      hitCount++;
    } else {
      missCount++;
      docIdSet = docIdSetToCache(filter.getDocIdSet(context, null), reader);
      assert docIdSet.isCacheable();
      cache.put(key, docIdSet);
    }

    return docIdSet == EMPTY ? null : BitsFilteredDocIdSet.wrap(docIdSet, acceptDocs);
  }
  
  @Override
  public String toString() {
    return getClass().getSimpleName() + "("+filter+")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !getClass().equals(o.getClass())) return false;
    final CachingWrapperFilter other = (CachingWrapperFilter) o;
    return this.filter.equals(other.filter);
  }

  @Override
  public int hashCode() {
    return (filter.hashCode() ^ getClass().hashCode());
  }

  @Override
  public long ramBytesUsed() {

    // Sync only to pull the current set of values:
    List<DocIdSet> docIdSets;
    synchronized(cache) {
      docIdSets = new ArrayList<>(cache.values());
    }

    long total = 0;
    for(DocIdSet dis : docIdSets) {
      total += dis.ramBytesUsed();
    }

    return total;
  }
}
