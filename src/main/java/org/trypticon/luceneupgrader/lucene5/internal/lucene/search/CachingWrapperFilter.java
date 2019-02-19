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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search;

import static org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSet.EMPTY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountables;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RoaringDocIdSet;

@Deprecated
public class CachingWrapperFilter extends Filter implements Accountable {
  private final Filter filter;
  private final FilterCachingPolicy policy;
  private final Map<Object,DocIdSet> cache = Collections.synchronizedMap(new WeakHashMap<Object,DocIdSet>());


  public CachingWrapperFilter(Filter filter, FilterCachingPolicy policy) {
    this.filter = filter;
    this.policy = policy;
  }


  public CachingWrapperFilter(Filter filter) {
    this(filter, FilterCachingPolicy.CacheOnLargeSegments.DEFAULT);
  }

  public Filter getFilter() {
    return filter;
  }


  protected DocIdSet docIdSetToCache(DocIdSet docIdSet, LeafReader reader) throws IOException {
    if (docIdSet == null || docIdSet.isCacheable()) {
      return docIdSet;
    } else {
      final DocIdSetIterator it = docIdSet.iterator();
      if (it == null) {
        return null;
      } else {
        return cacheImpl(it, reader);
      }
    }
  }
  
  protected DocIdSet cacheImpl(DocIdSetIterator iterator, LeafReader reader) throws IOException {
    return new RoaringDocIdSet.Builder(reader.maxDoc()).add(iterator).build();
  }

  // for testing
  int hitCount, missCount;

  @Override
  public DocIdSet getDocIdSet(LeafReaderContext context, final Bits acceptDocs) throws IOException {
    final LeafReader reader = context.reader();
    final Object key = reader.getCoreCacheKey();

    DocIdSet docIdSet = cache.get(key);
    if (docIdSet != null) {
      hitCount++;
    } else {
      docIdSet = filter.getDocIdSet(context, null);
      if (policy.shouldCache(filter, context, docIdSet)) {
        missCount++;
        docIdSet = docIdSetToCache(docIdSet, reader);
        if (docIdSet == null) {
          // We use EMPTY as a sentinel for the empty set, which is cacheable
          docIdSet = EMPTY;
        }
        assert docIdSet.isCacheable();
        cache.put(key, docIdSet);
      }
    }

    return docIdSet == EMPTY ? null : BitsFilteredDocIdSet.wrap(docIdSet, acceptDocs);
  }
  
  @Override
  public String toString(String field) {
    return getClass().getSimpleName() + "("+filter.toString(field)+")";
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o) == false) {
      return false;
    }
    final CachingWrapperFilter other = (CachingWrapperFilter) o;
    return this.filter.equals(other.filter);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + filter.hashCode();
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

  @Override
  public Collection<Accountable> getChildResources() {
    // Sync to pull the current set of values:
    synchronized (cache) {
      // no need to clone, Accountable#namedAccountables already copies the data
      return Accountables.namedAccountables("segment", cache);
    }
  }
}
