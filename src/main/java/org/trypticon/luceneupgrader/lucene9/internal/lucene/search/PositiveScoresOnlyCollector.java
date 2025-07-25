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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.search;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.LeafReaderContext;

/**
 * A {@link Collector} implementation which wraps another {@link Collector} and makes sure only
 * documents with scores &gt; 0 are collected.
 */
public class PositiveScoresOnlyCollector extends FilterCollector {

  public PositiveScoresOnlyCollector(Collector in) {
    super(in);
  }

  @Override
  public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
    return new FilterLeafCollector(super.getLeafCollector(context)) {

      private Scorable scorer;

      @Override
      public void setScorer(Scorable scorer) throws IOException {
        this.scorer = ScoreCachingWrappingScorer.wrap(scorer);
        in.setScorer(this.scorer);
      }

      @Override
      public void collect(int doc) throws IOException {
        if (scorer.score() > 0) {
          in.collect(doc);
        }
      }
    };
  }
}
