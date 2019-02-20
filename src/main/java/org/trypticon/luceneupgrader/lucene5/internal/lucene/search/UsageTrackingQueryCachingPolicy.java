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

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.FrequencyTrackingRingBuffer;


public final class UsageTrackingQueryCachingPolicy implements QueryCachingPolicy {

  // the hash code that we use as a sentinel in the ring buffer.
  private static final int SENTINEL = Integer.MIN_VALUE;

  static boolean isCostly(Query query) {
    // This does not measure the cost of iterating over the filter (for this we
    // already have the DocIdSetIterator#cost API) but the cost to build the
    // DocIdSet in the first place
    return query instanceof MultiTermQuery ||
        query instanceof MultiTermQueryConstantScoreWrapper;
  }

  static boolean isCheap(Query query) {
    // same for cheap queries
    // these queries are so cheap that they usually do not need caching
    return query instanceof TermQuery;
  }

  private final QueryCachingPolicy.CacheOnLargeSegments segmentPolicy;
  private final FrequencyTrackingRingBuffer recentlyUsedFilters;

  public UsageTrackingQueryCachingPolicy(
      int minIndexSize,
      float minSizeRatio,
      int historySize) {
    this(new QueryCachingPolicy.CacheOnLargeSegments(minIndexSize, minSizeRatio), historySize);
  }

  public UsageTrackingQueryCachingPolicy() {
    this(QueryCachingPolicy.CacheOnLargeSegments.DEFAULT, 256);
  }

  private UsageTrackingQueryCachingPolicy(
      QueryCachingPolicy.CacheOnLargeSegments segmentPolicy,
      int historySize) {
    this.segmentPolicy = segmentPolicy;
    this.recentlyUsedFilters = new FrequencyTrackingRingBuffer(historySize, SENTINEL);
  }

  protected int minFrequencyToCache(Query query) {
    if (isCostly(query)) {
      return 2;
    } else if (isCheap(query)) {
      return 20;
    } else {
      // default: cache after the filter has been seen 5 times
      return 5;
    }
  }

  @Override
  public void onUse(Query query) {
    assert query instanceof BoostQuery == false;
    assert query instanceof ConstantScoreQuery == false;

    // call hashCode outside of sync block
    // in case it's somewhat expensive:
    int hashCode = query.hashCode();

    // we only track hash codes to avoid holding references to possible
    // large queries; this may cause rare false positives, but at worse
    // this just means we cache a query that was not in fact used enough:
    synchronized (this) {
      recentlyUsedFilters.add(hashCode);
    }
  }

  int frequency(Query query) {
    assert query instanceof BoostQuery == false;
    assert query instanceof ConstantScoreQuery == false;

    // call hashCode outside of sync block
    // in case it's somewhat expensive:
    int hashCode = query.hashCode();

    synchronized (this) {
      return recentlyUsedFilters.frequency(hashCode);
    }
  }

  @Override
  public boolean shouldCache(Query query, LeafReaderContext context) throws IOException {
    if (query instanceof MatchAllDocsQuery
        // MatchNoDocsQuery currently rewrites to a BooleanQuery,
        // but who knows, it might get its own Weight one day
        || query instanceof MatchNoDocsQuery) {
      return false;
    }
    if (query instanceof BooleanQuery) {
      BooleanQuery bq = (BooleanQuery) query;
      if (bq.clauses().isEmpty()) {
        return false;
      }
    }
    if (query instanceof DisjunctionMaxQuery) {
      DisjunctionMaxQuery dmq = (DisjunctionMaxQuery) query;
      if (dmq.getDisjuncts().isEmpty()) {
        return false;
      }
    }
    if (segmentPolicy.shouldCache(query, context) == false) {
      return false;
    }
    final int frequency = frequency(query);
    final int minFrequency = minFrequencyToCache(query);
    return frequency >= minFrequency;
  }

}
