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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReader.CoreClosedListener;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountables;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RoaringDocIdSet;

@Deprecated
public class LRUFilterCache implements FilterCache, Accountable {

  // memory usage of a simple query-wrapper filter around a term query
  static final long FILTER_DEFAULT_RAM_BYTES_USED = 216;

  static final long HASHTABLE_RAM_BYTES_PER_ENTRY =
      2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF // key + value
      * 2; // hash tables need to be oversized to avoid collisions, assume 2x capacity

  static final long LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY =
      HASHTABLE_RAM_BYTES_PER_ENTRY
      + 2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF; // previous & next references

  private final int maxSize;
  private final long maxRamBytesUsed;
  // maps filters that are contained in the cache to a singleton so that this
  // cache does not store several copies of the same filter
  private final Map<Filter, Filter> uniqueFilters;
  // The contract between this set and the per-leaf caches is that per-leaf caches
  // are only allowed to store sub-sets of the filters that are contained in
  // mostRecentlyUsedFilters. This is why write operations are performed under a lock
  private final Set<Filter> mostRecentlyUsedFilters;
  private final Map<Object, LeafCache> cache;

  // these variables are volatile so that we do not need to sync reads
  // but increments need to be performed under the lock
  private volatile long ramBytesUsed;
  private volatile long hitCount;
  private volatile long missCount;
  private volatile long cacheCount;
  private volatile long cacheSize;

  public LRUFilterCache(int maxSize, long maxRamBytesUsed) {
    this.maxSize = maxSize;
    this.maxRamBytesUsed = maxRamBytesUsed;
    uniqueFilters = new LinkedHashMap<Filter, Filter>(16, 0.75f, true);
    mostRecentlyUsedFilters = uniqueFilters.keySet();
    cache = new IdentityHashMap<>();
    ramBytesUsed = 0;
  }

  protected void onHit(Object readerCoreKey, Filter filter) {
    hitCount += 1;
  }

  protected void onMiss(Object readerCoreKey, Filter filter) {
    assert filter != null;
    missCount += 1;
  }

  protected void onFilterCache(Filter filter, long ramBytesUsed) {
    this.ramBytesUsed += ramBytesUsed;
  }

  protected void onFilterEviction(Filter filter, long ramBytesUsed) {
    this.ramBytesUsed -= ramBytesUsed;
  }

  protected void onDocIdSetCache(Object readerCoreKey, long ramBytesUsed) {
    cacheSize += 1;
    cacheCount += 1;
    this.ramBytesUsed += ramBytesUsed;
  }
  
  protected void onDocIdSetEviction(Object readerCoreKey, int numEntries, long sumRamBytesUsed) {
    this.ramBytesUsed -= sumRamBytesUsed;
    cacheSize -= numEntries;
  }

  protected void onClear() {
    ramBytesUsed = 0;
    cacheSize = 0;
  }

  boolean requiresEviction() {
    final int size = mostRecentlyUsedFilters.size();
    if (size == 0) {
      return false;
    } else {
      return size > maxSize || ramBytesUsed() > maxRamBytesUsed;
    }
  }

  synchronized DocIdSet get(Filter filter, LeafReaderContext context) {
    final Object readerKey = context.reader().getCoreCacheKey();
    final LeafCache leafCache = cache.get(readerKey);
    if (leafCache == null) {
      onMiss(readerKey, filter);
      return null;
    }
    // this get call moves the filter to the most-recently-used position
    final Filter singleton = uniqueFilters.get(filter);
    if (singleton == null) {
      onMiss(readerKey, filter);
      return null;
    }
    final DocIdSet cached = leafCache.get(singleton);
    if (cached == null) {
      onMiss(readerKey, singleton);
    } else {
      onHit(readerKey, singleton);
    }
    return cached;
  }

  synchronized void putIfAbsent(Filter filter, LeafReaderContext context, DocIdSet set) {
    // under a lock to make sure that mostRecentlyUsedFilters and cache remain sync'ed
    assert set.isCacheable();
    Filter singleton = uniqueFilters.get(filter);
    if (singleton == null) {
      uniqueFilters.put(filter, filter);
      onFilterCache(singleton, LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY + ramBytesUsed(filter));
    } else {
      filter = singleton;
    }
    final Object key = context.reader().getCoreCacheKey();
    LeafCache leafCache = cache.get(key);
    if (leafCache == null) {
      leafCache = new LeafCache(key);
      final LeafCache previous = cache.put(context.reader().getCoreCacheKey(), leafCache);
      ramBytesUsed += HASHTABLE_RAM_BYTES_PER_ENTRY;
      assert previous == null;
      // we just created a new leaf cache, need to register a close listener
      context.reader().addCoreClosedListener(new CoreClosedListener() {
        @Override
        public void onClose(Object ownerCoreCacheKey) {
          clearCoreCacheKey(ownerCoreCacheKey);
        }
      });
    }
    leafCache.putIfAbsent(filter, set);
    evictIfNecessary();
  }

  synchronized void evictIfNecessary() {
    // under a lock to make sure that mostRecentlyUsedFilters and cache keep sync'ed
    if (requiresEviction()) {
      Iterator<Filter> iterator = mostRecentlyUsedFilters.iterator();
      do {
        final Filter filter = iterator.next();
        iterator.remove();
        onEviction(filter);
      } while (iterator.hasNext() && requiresEviction());
    }
  }

  public synchronized void clearCoreCacheKey(Object coreKey) {
    final LeafCache leafCache = cache.remove(coreKey);
    if (leafCache != null) {
      ramBytesUsed -= HASHTABLE_RAM_BYTES_PER_ENTRY;
      onDocIdSetEviction(coreKey, leafCache.cache.size(), leafCache.ramBytesUsed);
    }
  }

  public synchronized void clearFilter(Filter filter) {
    final Filter singleton = uniqueFilters.remove(filter);
    if (singleton != null) {
      onEviction(singleton);
    }
  }

  private void onEviction(Filter singleton) {
    onFilterEviction(singleton, LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY + ramBytesUsed(singleton));
    for (LeafCache leafCache : cache.values()) {
      leafCache.remove(singleton);
    }
  }

  public synchronized void clear() {
    cache.clear();
    mostRecentlyUsedFilters.clear();
    onClear();
  }

  // pkg-private for testing
  synchronized void assertConsistent() {
    if (requiresEviction()) {
      throw new AssertionError("requires evictions: size=" + mostRecentlyUsedFilters.size()
          + ", maxSize=" + maxSize + ", ramBytesUsed=" + ramBytesUsed() + ", maxRamBytesUsed=" + maxRamBytesUsed);
    }
    for (LeafCache leafCache : cache.values()) {
      Set<Filter> keys = Collections.newSetFromMap(new IdentityHashMap<Filter, Boolean>());
      keys.addAll(leafCache.cache.keySet());
      keys.removeAll(mostRecentlyUsedFilters);
      if (!keys.isEmpty()) {
        throw new AssertionError("One leaf cache contains more keys than the top-level cache: " + keys);
      }
    }
    long recomputedRamBytesUsed =
          HASHTABLE_RAM_BYTES_PER_ENTRY * cache.size()
        + LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY * uniqueFilters.size();
    for (Filter filter : mostRecentlyUsedFilters) {
      recomputedRamBytesUsed += ramBytesUsed(filter);
    }
    for (LeafCache leafCache : cache.values()) {
      recomputedRamBytesUsed += HASHTABLE_RAM_BYTES_PER_ENTRY * leafCache.cache.size();
      for (DocIdSet set : leafCache.cache.values()) {
        recomputedRamBytesUsed += set.ramBytesUsed();
      }
    }
    if (recomputedRamBytesUsed != ramBytesUsed) {
      throw new AssertionError("ramBytesUsed mismatch : " + ramBytesUsed + " != " + recomputedRamBytesUsed);
    }

    long recomputedCacheSize = 0;
    for (LeafCache leafCache : cache.values()) {
      recomputedCacheSize += leafCache.cache.size();
    }
    if (recomputedCacheSize != getCacheSize()) {
      throw new AssertionError("cacheSize mismatch : " + getCacheSize() + " != " + recomputedCacheSize);
    }
  }

  // pkg-private for testing
  // return the list of cached filters in LRU order
  synchronized List<Filter> cachedFilters() {
    return new ArrayList<>(mostRecentlyUsedFilters);
  }

  @Override
  public Filter doCache(Filter filter, FilterCachingPolicy policy) {
    while (filter instanceof CachingWrapperFilter) {
      // should we throw an exception instead?
      filter = ((CachingWrapperFilter) filter).in;
    }

    return new CachingWrapperFilter(filter, policy);
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

  @Override
  public long ramBytesUsed() {
    return ramBytesUsed;
  }

  @Override
  public Collection<Accountable> getChildResources() {
    synchronized (this) {
      return Accountables.namedAccountables("segment", cache);
    }
  }

  protected long ramBytesUsed(Filter filter) {
    if (filter instanceof Accountable) {
      return ((Accountable) filter).ramBytesUsed();
    }
    return FILTER_DEFAULT_RAM_BYTES_USED;
  }

  protected DocIdSet cacheImpl(DocIdSetIterator iterator, LeafReader reader) throws IOException {
    return new RoaringDocIdSet.Builder(reader.maxDoc()).add(iterator).build();
  }

  public final long getTotalCount() {
    return getHitCount() + getMissCount();
  }

  public final long getHitCount() {
    return hitCount;
  }

  public final long getMissCount() {
    return missCount;
  }

  public final long getCacheSize() {
    return cacheSize;
  }

  public final long getCacheCount() {
    return cacheCount;
  }

  public final long getEvictionCount() {
    return getCacheCount() - getCacheSize();
  }

  // this class is not thread-safe, everything but ramBytesUsed needs to be called under a lock
  private class LeafCache implements Accountable {

    private final Object key;
    private final Map<Filter, DocIdSet> cache;
    private volatile long ramBytesUsed;

    LeafCache(Object key) {
      this.key = key;
      cache = new IdentityHashMap<>();
      ramBytesUsed = 0;
    }

    private void onDocIdSetCache(long ramBytesUsed) {
      this.ramBytesUsed += ramBytesUsed;
      LRUFilterCache.this.onDocIdSetCache(key, ramBytesUsed);
    }

    private void onDocIdSetEviction(long ramBytesUsed) {
      this.ramBytesUsed -= ramBytesUsed;
      LRUFilterCache.this.onDocIdSetEviction(key, 1, ramBytesUsed);
    }

    DocIdSet get(Filter filter) {
      return cache.get(filter);
    }

    void putIfAbsent(Filter filter, DocIdSet set) {
      if (cache.containsKey(filter) == false) {
        cache.put(filter, set);
        onDocIdSetCache(HASHTABLE_RAM_BYTES_PER_ENTRY + set.ramBytesUsed());
      }
    }

    void remove(Filter filter) {
      DocIdSet removed = cache.remove(filter);
      if (removed != null) {
        onDocIdSetEviction(HASHTABLE_RAM_BYTES_PER_ENTRY + removed.ramBytesUsed());
      }
    }

    @Override
    public long ramBytesUsed() {
      return ramBytesUsed;
    }

    @Override
    public Collection<Accountable> getChildResources() {
      return Collections.emptyList();
    }

  }

  private class CachingWrapperFilter extends Filter {

    private final Filter in;
    private final FilterCachingPolicy policy;

    CachingWrapperFilter(Filter in, FilterCachingPolicy policy) {
      this.in = in;
      this.policy = policy;
    }

    @Override
    public DocIdSet getDocIdSet(LeafReaderContext context, Bits acceptDocs) throws IOException {
      if (context.ord == 0) {
        policy.onUse(in);
      }

      DocIdSet set = get(in, context);
      if (set == null) {
        // do not apply acceptDocs yet, we want the cached filter to not take them into account
        set = in.getDocIdSet(context, null);
        if (policy.shouldCache(in, context, set)) {
          set = docIdSetToCache(set, context.reader());
          if (set == null) {
            // null values are not supported
            set = DocIdSet.EMPTY;
          }
          // it might happen that another thread computed the same set in parallel
          // although this might incur some CPU overhead, it is probably better
          // this way than trying to lock and preventing other filters to be
          // computed at the same time?
          putIfAbsent(in, context, set);
        }
      }
      return set == DocIdSet.EMPTY ? null : BitsFilteredDocIdSet.wrap(set, acceptDocs);
    }

    @Override
    public boolean equals(Object obj) {
      return super.equals(obj)
          && in.equals(((CachingWrapperFilter) obj).in);
    }

    @Override
    public int hashCode() {
      return 31 * super.hashCode() + in.hashCode();
    }

    @Override
    public String toString(String field) {
      return "CachingWrapperFilter(" + in.toString(field) + ")";
    }
  }

}
