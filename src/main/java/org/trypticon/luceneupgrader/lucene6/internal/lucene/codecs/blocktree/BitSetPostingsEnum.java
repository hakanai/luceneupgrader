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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.blocktree;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BitSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BitSetIterator;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.FixedBitSet; // javadocs

class BitSetPostingsEnum extends PostingsEnum {
  private final BitSet bits;
  private DocIdSetIterator in;
  
  BitSetPostingsEnum(BitSet bits) {
    this.bits = bits;
    reset();
  }

  @Override
  public int freq() throws IOException {
    return 1;
  }

  @Override
  public int docID() {
    if (in == null) {
      return -1;
    } else {
      return in.docID();
    }
  }

  @Override
  public int nextDoc() throws IOException {
    if (in == null) {
      in = new BitSetIterator(bits, 0);
    }
    return in.nextDoc();
  }

  @Override
  public int advance(int target) throws IOException {
    return in.advance(target);
  }

  @Override
  public long cost() {
    return in.cost();
  }
  
  void reset() {
    in = null;
  }

  @Override
  public BytesRef getPayload() {
    return null;
  }

  @Override
  public int nextPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int startOffset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int endOffset() {
    throw new UnsupportedOperationException();
  }
}
