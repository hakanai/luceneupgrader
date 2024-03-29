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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.search;


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

  public static final DocIdSetIterator all(int maxDoc) {
    return new DocIdSetIterator() {
      int doc = -1;

      @Override
      public int docID() {
        return doc;
      }

      @Override
      public int nextDoc() throws IOException {
        return advance(doc + 1);
      }

      @Override
      public int advance(int target) throws IOException {
        doc = target;
        if (doc >= maxDoc) {
          doc = NO_MORE_DOCS;
        }
        return doc;
      }

      @Override
      public long cost() {
        return maxDoc;
      }
    };
  }

  public static final DocIdSetIterator range(int minDoc, int maxDoc) {
    if (minDoc >= maxDoc) {
        throw new IllegalArgumentException("minDoc must be < maxDoc but got minDoc=" + minDoc + " maxDoc=" + maxDoc);
    }
    if (minDoc < 0) {
      throw new IllegalArgumentException("minDoc must be >= 0 but got minDoc=" + minDoc);
    }
    return new DocIdSetIterator() {
      private int doc = -1;

      @Override
      public int docID() {
        return doc;
      }

      @Override
      public int nextDoc() throws IOException {
        return advance(doc + 1);
      }

      @Override
      public int advance(int target) throws IOException {
        if (target < minDoc) {
            doc = minDoc;
        } else if (target >= maxDoc) {
            doc = NO_MORE_DOCS;
        } else {
            doc = target;
        }
        return doc;
      }

      @Override
      public long cost() {
        return maxDoc - minDoc;
      }
    };
  }

  public static final int NO_MORE_DOCS = Integer.MAX_VALUE;

  public abstract int docID();

  public abstract int nextDoc() throws IOException;

  public abstract int advance(int target) throws IOException;

  protected final int slowAdvance(int target) throws IOException {
    assert docID() < target;
    int doc;
    do {
      doc = nextDoc();
    } while (doc < target);
    return doc;
  }

  public abstract long cost();
  
}
