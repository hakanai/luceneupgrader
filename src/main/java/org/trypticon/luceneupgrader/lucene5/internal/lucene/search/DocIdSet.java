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
import java.util.Collection;
import java.util.Collections;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;

public abstract class DocIdSet implements Accountable {

  public static final DocIdSet EMPTY = new DocIdSet() {
    
    @Override
    public DocIdSetIterator iterator() {
      return DocIdSetIterator.empty();
    }
    
    @Override
    public boolean isCacheable() {
      return true;
    }
    
    // we explicitly provide no random access, as this filter is 100% sparse and iterator exits faster
    @Override
    public Bits bits() {
      return null;
    }

    @Override
    public long ramBytesUsed() {
      return 0L;
    }
  };


  public abstract DocIdSetIterator iterator() throws IOException;

  // TODO: somehow this class should express the cost of
  // iteration vs the cost of random access Bits; for
  // expensive Filters (e.g. distance < 1 km) we should use
  // bits() after all other Query/Filters have matched, but
  // this is the opposite of what bits() is for now
  // (down-low filtering using e.g. FixedBitSet)


  public Bits bits() throws IOException {
    return null;
  }

  public boolean isCacheable() {
    return false;
  }

  @Override
  public Collection<Accountable> getChildResources() {
    return Collections.emptyList();
  }
}
