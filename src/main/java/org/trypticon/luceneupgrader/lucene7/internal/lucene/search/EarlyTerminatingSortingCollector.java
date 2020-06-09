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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexWriterConfig;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.CollectionTerminatedException;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Collector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.FilterCollector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.FilterLeafCollector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.LeafCollector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.TopDocsCollector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.TotalHitCountCollector;

@Deprecated
public class EarlyTerminatingSortingCollector extends FilterCollector {

  public static boolean canEarlyTerminate(Sort searchSort, Sort mergePolicySort) {
    final SortField[] fields1 = searchSort.getSort();
    final SortField[] fields2 = mergePolicySort.getSort();
    // early termination is possible if fields1 is a prefix of fields2
    if (fields1.length > fields2.length) {
      return false;
    }
    return Arrays.asList(fields1).equals(Arrays.asList(fields2).subList(0, fields1.length));
  }

  protected final Sort sort;
  protected final int numDocsToCollect;
  private final AtomicBoolean terminatedEarly = new AtomicBoolean(false);

  public EarlyTerminatingSortingCollector(Collector in, Sort sort, int numDocsToCollect) {
    super(in);
    if (numDocsToCollect <= 0) {
      throw new IllegalArgumentException("numDocsToCollect must always be > 0, got " + numDocsToCollect);
    }
    this.sort = sort;
    this.numDocsToCollect = numDocsToCollect;
  }

  @Override
  public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
    Sort segmentSort = context.reader().getMetaData().getSort();
    if (segmentSort != null && canEarlyTerminate(sort, segmentSort) == false) {
      throw new IllegalStateException("Cannot early terminate with sort order " + sort + " if segments are sorted with " + segmentSort);
    }

    if (segmentSort != null) {
      // segment is sorted, can early-terminate
      return new FilterLeafCollector(super.getLeafCollector(context)) {
        private int numCollected;

        @Override
        public void collect(int doc) throws IOException {
          super.collect(doc);
          if (++numCollected >= numDocsToCollect) {
            terminatedEarly.set(true);
            throw new CollectionTerminatedException();
          }
        }

      };
    } else {
      return super.getLeafCollector(context);
    }
  }

  public boolean terminatedEarly() {
    return terminatedEarly.get();
  }
}
