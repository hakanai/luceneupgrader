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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;

public final class MultiDocsAndPositionsEnum extends DocsAndPositionsEnum {
  private final MultiTermsEnum parent;
  final DocsAndPositionsEnum[] subDocsAndPositionsEnum;
  private final EnumWithSlice[] subs;
  int numSubs;
  int upto;
  DocsAndPositionsEnum current;
  int currentBase;
  int doc = -1;

  public MultiDocsAndPositionsEnum(MultiTermsEnum parent, int subReaderCount) {
    this.parent = parent;
    subDocsAndPositionsEnum = new DocsAndPositionsEnum[subReaderCount];
    this.subs = new EnumWithSlice[subReaderCount];
    for (int i = 0; i < subs.length; i++) {
      subs[i] = new EnumWithSlice();
    }
  }

  public boolean canReuse(MultiTermsEnum parent) {
    return this.parent == parent;
  }

  public MultiDocsAndPositionsEnum reset(final EnumWithSlice[] subs, final int numSubs) {
    this.numSubs = numSubs;
    for(int i=0;i<numSubs;i++) {
      this.subs[i].docsAndPositionsEnum = subs[i].docsAndPositionsEnum;
      this.subs[i].slice = subs[i].slice;
    }
    upto = -1;
    doc = -1;
    current = null;
    return this;
  }

  public int getNumSubs() {
    return numSubs;
  }

  public EnumWithSlice[] getSubs() {
    return subs;
  }

  @Override
  public int freq() throws IOException {
    assert current != null;
    return current.freq();
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int advance(int target) throws IOException {
    assert target > doc;
    while(true) {
      if (current != null) {
        final int doc;
        if (target < currentBase) {
          // target was in the previous slice but there was no matching doc after it
          doc = current.nextDoc();
        } else {
          doc = current.advance(target-currentBase);
        }
        if (doc == NO_MORE_DOCS) {
          current = null;
        } else {
          return this.doc = doc + currentBase;
        }
      } else if (upto == numSubs-1) {
        return this.doc = NO_MORE_DOCS;
      } else {
        upto++;
        current = subs[upto].docsAndPositionsEnum;
        currentBase = subs[upto].slice.start;
      }
    }
  }

  @Override
  public int nextDoc() throws IOException {
    while(true) {
      if (current == null) {
        if (upto == numSubs-1) {
          return this.doc = NO_MORE_DOCS;
        } else {
          upto++;
          current = subs[upto].docsAndPositionsEnum;
          currentBase = subs[upto].slice.start;
        }
      }

      final int doc = current.nextDoc();
      if (doc != NO_MORE_DOCS) {
        return this.doc = currentBase + doc;
      } else {
        current = null;
      }
    }
  }

  @Override
  public int nextPosition() throws IOException {
    return current.nextPosition();
  }

  @Override
  public int startOffset() throws IOException {
    return current.startOffset();
  }

  @Override
  public int endOffset() throws IOException {
    return current.endOffset();
  }

  @Override
  public BytesRef getPayload() throws IOException {
    return current.getPayload();
  }

  // TODO: implement bulk read more efficiently than super
  public final static class EnumWithSlice {
    EnumWithSlice() {
    }

    public DocsAndPositionsEnum docsAndPositionsEnum;

    public ReaderSlice slice;
    
    @Override
    public String toString() {
      return slice.toString()+":"+docsAndPositionsEnum;
    }
  }
  
  @Override
  public long cost() {
    long cost = 0;
    for (int i = 0; i < numSubs; i++) {
      cost += subs[i].docsAndPositionsEnum.cost();
    }
    return cost;
  }
  
  @Override
  public String toString() {
    return "MultiDocsAndPositionsEnum(" + Arrays.toString(getSubs()) + ")";
  }
}

