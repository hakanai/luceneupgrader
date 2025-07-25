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
 * {@link Collector} delegator.
 *
 * @lucene.experimental
 */
public abstract class FilterCollector implements Collector {

  protected final Collector in;

  /** Sole constructor. */
  public FilterCollector(Collector in) {
    this.in = in;
  }

  @Override
  public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
    return in.getLeafCollector(context);
  }

  @Override
  public void setWeight(Weight weight) {
    in.setWeight(weight);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + in + ")";
  }

  @Override
  public ScoreMode scoreMode() {
    return in.scoreMode();
  }
}
