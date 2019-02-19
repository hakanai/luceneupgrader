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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import java.io.IOException;

public abstract class DocIdSetIterator {
  
  public static final DocIdSetIterator empty() {
    return new DocIdSetIterator() {
      boolean exhausted = false;
      
      @Override
      public int advance(int target) {
        assert !exhausted;
        assert target >= 0;
        exhausted = true;
        return NO_MORE_DOCS;
      }
      
      @Override
      public int docID() {
        return exhausted ? NO_MORE_DOCS : -1;
      }
      @Override
      public int nextDoc() {
        assert !exhausted;
        exhausted = true;
        return NO_MORE_DOCS;
      }
      
      @Override
      public long cost() {
        return 0;
      }
    };
  }
  
  public static final int NO_MORE_DOCS = Integer.MAX_VALUE;

  public abstract int docID();

  public abstract int nextDoc() throws IOException;

  public abstract int advance(int target) throws IOException;

  protected final int slowAdvance(int target) throws IOException {
    assert docID() == NO_MORE_DOCS // can happen when the enum is not positioned yet
        || docID() < target;
    int doc;
    do {
      doc = nextDoc();
    } while (doc < target);
    return doc;
  }

  public abstract long cost();
}
