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

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.ReaderUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TieredMergePolicy;

// TODO: add APIs for integration with IndexWriter.IndexReaderWarmer
public interface QueryCachingPolicy {

  public static final QueryCachingPolicy ALWAYS_CACHE = new QueryCachingPolicy() {

    @Override
    public void onUse(Query query) {}

    @Override
    public boolean shouldCache(Query query, LeafReaderContext context) throws IOException {
      return true;
    }

  };


  public static class CacheOnLargeSegments implements QueryCachingPolicy {


    public static final CacheOnLargeSegments DEFAULT = new CacheOnLargeSegments(10000, 0.03f);

    private final int minIndexSize;
    private final float minSizeRatio;

    public CacheOnLargeSegments(int minIndexSize, float minSizeRatio) {
      if (minSizeRatio <= 0 || minSizeRatio >= 1) {
        throw new IllegalArgumentException("minSizeRatio must be in ]0, 1[, got " + minSizeRatio);
      }
      this.minIndexSize = minIndexSize;
      this.minSizeRatio = minSizeRatio;
    }

    @Override
    public void onUse(Query query) {}

    @Override
    public boolean shouldCache(Query query, LeafReaderContext context) throws IOException {
      final IndexReaderContext topLevelContext = ReaderUtil.getTopLevelContext(context);
      if (topLevelContext.reader().maxDoc() < minIndexSize) {
        return false;
      }
      final float sizeRatio = (float) context.reader().maxDoc() / topLevelContext.reader().maxDoc();
      return sizeRatio >= minSizeRatio;
    }

  };


  void onUse(Query query);


  boolean shouldCache(Query query, LeafReaderContext context) throws IOException;

}
