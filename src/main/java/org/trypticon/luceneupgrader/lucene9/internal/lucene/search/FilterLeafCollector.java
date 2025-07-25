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

/**
 * {@link LeafCollector} delegator.
 *
 * @lucene.experimental
 */
public abstract class FilterLeafCollector implements LeafCollector {

  protected final LeafCollector in;

  /** Sole constructor. */
  public FilterLeafCollector(LeafCollector in) {
    this.in = in;
  }

  @Override
  public void setScorer(Scorable scorer) throws IOException {
    in.setScorer(scorer);
  }

  @Override
  public void collect(int doc) throws IOException {
    in.collect(doc);
  }

  @Override
  public void finish() throws IOException {
    in.finish();
  }

  @Override
  public String toString() {
    String name = getClass().getSimpleName();
    if (name.length() == 0) {
      // an anonoymous subclass will have empty name?
      name = "FilterLeafCollector";
    }
    return name + "(" + in + ")";
  }
}
