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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.Serializable;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.FixedBitSet;

public class CachingWrapperFilter extends Filter {
  Filter filter;

  public static enum DeletesMode {IGNORE, RECACHE, DYNAMIC};

  protected final FilterCache<DocIdSet> cache;

  static abstract class FilterCache<T> implements Serializable {

    // NOTE: not final so that we can dynamically re-init
    // after de-serialize
    transient Map<Object,T> cache;

    private final DeletesMode deletesMode;

    public FilterCache(DeletesMode deletesMode) {
      this.deletesMode = deletesMode;
    }

    public synchronized T get(IndexReader reader, Object coreKey, Object delCoreKey) throws IOException {
      T value;

      if (cache == null) {
        cache = new WeakHashMap<Object,T>();
      }

      if (deletesMode == DeletesMode.IGNORE) {
        // key on core
        value = cache.get(coreKey);
      } else if (deletesMode == DeletesMode.RECACHE) {
        // key on deletes, if any, else core
        value = cache.get(delCoreKey);
      } else {
        assert deletesMode == DeletesMode.DYNAMIC;

        // first try for exact match
        value = cache.get(delCoreKey);

        if (value == null) {
          // now for core match, but dynamically AND NOT
          // deletions
          value = cache.get(coreKey);
          if (value != null && reader.hasDeletions()) {
            value = mergeDeletes(reader, value);
          }
        }
      }

      return value;
    }

    protected abstract T mergeDeletes(IndexReader reader, T value);

    public synchronized void put(Object coreKey, Object delCoreKey, T value) {
      if (deletesMode == DeletesMode.IGNORE) {
        cache.put(coreKey, value);
      } else if (deletesMode == DeletesMode.RECACHE) {
        cache.put(delCoreKey, value);
      } else {
        cache.put(coreKey, value);
        cache.put(delCoreKey, value);
      }
    }
  }

  public CachingWrapperFilter(Filter filter) {
    this(filter, DeletesMode.IGNORE);
  }

  public CachingWrapperFilter(Filter filter, DeletesMode deletesMode) {
    this.filter = filter;
    cache = new FilterCache<DocIdSet>(deletesMode) {
      @Override
      public DocIdSet mergeDeletes(final IndexReader r, final DocIdSet docIdSet) {
        return new FilteredDocIdSet(docIdSet) {
          @Override
          protected boolean match(int docID) {
            return !r.isDeleted(docID);
          }
        };
      }
    };
  }


  protected DocIdSet docIdSetToCache(DocIdSet docIdSet, IndexReader reader) throws IOException {
    if (docIdSet == null) {
      // this is better than returning null, as the nonnull result can be cached
      return DocIdSet.EMPTY_DOCIDSET;
    } else if (docIdSet.isCacheable()) {
      return docIdSet;
    } else {
      final DocIdSetIterator it = docIdSet.iterator();
      // null is allowed to be returned by iterator(),
      // in this case we wrap with the empty set,
      // which is cacheable.
      if (it == null) {
        return DocIdSet.EMPTY_DOCIDSET;
      } else {
        final FixedBitSet bits = new FixedBitSet(reader.maxDoc());
        bits.or(it);
        return bits;
      }
    }
  }

  // for testing
  int hitCount, missCount;

  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {

    final Object coreKey = reader.getCoreCacheKey();
    final Object delCoreKey = reader.hasDeletions() ? reader.getDeletesCacheKey() : coreKey;

    DocIdSet docIdSet = cache.get(reader, coreKey, delCoreKey);
    if (docIdSet != null) {
      hitCount++;
      return docIdSet;
    }

    missCount++;

    // cache miss
    docIdSet = docIdSetToCache(filter.getDocIdSet(reader), reader);

    if (docIdSet != null) {
      cache.put(coreKey, delCoreKey, docIdSet);
    }
    
    return docIdSet;
  }

  @Override
  public String toString() {
    return "CachingWrapperFilter("+filter+")";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CachingWrapperFilter)) return false;
    return this.filter.equals(((CachingWrapperFilter)o).filter);
  }

  @Override
  public int hashCode() {
    return filter.hashCode() ^ 0x1117BF25;  
  }
}
