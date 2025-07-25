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
 * Base {@link FieldComparator} implementation that is used for all contexts.
 *
 * @lucene.experimental
 */
public abstract class SimpleFieldComparator<T> extends FieldComparator<T>
    implements LeafFieldComparator {

  /** This method is called before collecting <code>context</code>. */
  protected abstract void doSetNextReader(LeafReaderContext context) throws IOException;

  @Override
  public final LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
    doSetNextReader(context);
    return this;
  }

  @Override
  public void setScorer(Scorable scorer) throws IOException {}
}
