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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSetIterator;

public class OpenBitSetDISI extends OpenBitSet {


  public OpenBitSetDISI(DocIdSetIterator disi, int maxSize) throws IOException {
    super(maxSize);
    inPlaceOr(disi);
  }


  public OpenBitSetDISI(int maxSize) {
    super(maxSize);
  }

  public void inPlaceOr(DocIdSetIterator disi) throws IOException {
    int doc;
    long size = size();
    while ((doc = disi.nextDoc()) < size) {
      fastSet(doc);
    }
  }

  public void inPlaceAnd(DocIdSetIterator disi) throws IOException {
    int bitSetDoc = nextSetBit(0);
    int disiDoc;
    while (bitSetDoc != -1 && (disiDoc = disi.advance(bitSetDoc)) != DocIdSetIterator.NO_MORE_DOCS) {
      clear(bitSetDoc, disiDoc);
      bitSetDoc = nextSetBit(disiDoc + 1);
    }
    if (bitSetDoc != -1) {
      clear(bitSetDoc, size());
    }
  }

  public void inPlaceNot(DocIdSetIterator disi) throws IOException {
    int doc;
    long size = size();
    while ((doc = disi.nextDoc()) < size) {
      fastClear(doc);
    }
  }

  public void inPlaceXor(DocIdSetIterator disi) throws IOException {
    int doc;
    long size = size();
    while ((doc = disi.nextDoc()) < size) {
      fastFlip(doc);
    }
  }
}
