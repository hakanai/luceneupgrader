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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.FrequencyTrackingRingBuffer;

public class UsageTrackingQueryCachingPolicy implements QueryCachingPolicy {

  // the hash code that we use as a sentinel in the ring buffer.
  private static final int SENTINEL = Integer.MIN_VALUE;

  private static boolean isPointQuery(Query query) {
    // we need to check for super classes because we occasionally use anonymous
    // sub classes of eg. PointRangeQuery
    for (Class<?> clazz = query.getClass(); clazz != Query.class; clazz = clazz.getSuperclass()) {
      final String simpleName = clazz.getSimpleName();
      if (simpleName.startsWith("Point") && simpleName.endsWith("Query")) {
        return true;
      }
    }
    return false;
  }

  static boolean isCostly(Query query) {
    // This does not measure the cost of iterating over the filter (for this we
    // already have the DocIdSetIterator#cost API) but the cost to build the
    // DocIdSet in the first place
    return query instanceof MultiTermQuery ||
        query instanceof MultiTermQueryConstantScoreWrapper ||
        query instanceof TermInSetQuery ||
        isPointQuery(query);
  }

  private static boolean shouldNeverCache(Query query) {
    if (query instanceof TermQuery) {
      // We do not bother caching term queries since they are already plenty fast.
      return true;
    }

    if (query instanceof MatchAllDocsQuery) {
      // MatchAllDocsQuery has an iterator that is faster than what a bit set could do.
      return true;
    }

    // For the below queries, it's cheap to notice they cannot match any docs so
    // we do not bother caching them.
    if (query instanceof MatchNoDocsQuery) {
      return false;
    }

    if (query instanceof BooleanQuery) {
      BooleanQuery bq = (BooleanQuery) query;
      if (bq.clauses().isEmpty()) {
        return true;
      }
    }

    if (query instanceof DisjunctionMaxQuery) {
      DisjunctionMaxQuery dmq = (DisjunctionMaxQuery) query;
      if (dmq.getDisjuncts().isEmpty()) {
        return true;
      }
    }

    return false;
  }

  private final FrequencyTrackingRingBuffer recentlyUsedFilters;

  public UsageTrackingQueryCachingPolicy(int historySize) {
    this.recentlyUsedFilters = new FrequencyTrackingRingBuffer(historySize, SENTINEL);
  }

  public UsageTrackingQueryCachingPolicy() {
    this(256);
  }

  protected int minFrequencyToCache(Query query) {
    if (isCostly(query)) {
      return 2;
    } else {
      // default: cache after the filter has been seen 5 times
      int minFrequency = 5;
      if (query instanceof BooleanQuery
          || query instanceof DisjunctionMaxQuery) {
        // Say you keep reusing a boolean query that looks like "A OR B" and
        // never use the A and B queries out of that context. 5 times after it
        // has been used, we would cache both A, B and A OR B, which is
        // wasteful. So instead we cache compound queries a bit earlier so that
        // we would only cache "A OR B" in that case.
        minFrequency--;
      }
      return minFrequency;
    }
  }

  @Override
  public void onUse(Query query) {
    assert query instanceof BoostQuery == false;
    assert query instanceof ConstantScoreQuery == false;

    if (shouldNeverCache(query)) {
      return;
    }

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
  public boolean shouldCache(Query query) throws IOException {
    if (shouldNeverCache(query)) {
      return false;
    }
    final int frequency = frequency(query);
    final int minFrequency = minFrequencyToCache(query);
    return frequency >= minFrequency;
  }

}
