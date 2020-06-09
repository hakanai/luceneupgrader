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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedSetDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedSetDocValues.NO_MORE_ORDS;

public class SortedSetSelector {
  
  public enum Type {
    MIN,
    MAX,
    MIDDLE_MIN,
    MIDDLE_MAX
  }
  
  public static SortedDocValues wrap(SortedSetDocValues sortedSet, Type selector) {
    if (sortedSet.getValueCount() >= Integer.MAX_VALUE) {
      throw new UnsupportedOperationException("fields containing more than " + (Integer.MAX_VALUE-1) + " unique terms are unsupported");
    }
    
    SortedDocValues singleton = DocValues.unwrapSingleton(sortedSet);
    if (singleton != null) {
      // it's actually single-valued in practice, but indexed as multi-valued,
      // so just sort on the underlying single-valued dv directly.
      // regardless of selector type, this optimization is safe!
      return singleton;
    } else {
      switch(selector) {
        case MIN: return new MinValue(sortedSet);
        case MAX: return new MaxValue(sortedSet);
        case MIDDLE_MIN: return new MiddleMinValue(sortedSet);
        case MIDDLE_MAX: return new MiddleMaxValue(sortedSet);
        default: 
          throw new AssertionError();
      }
    }
  }
  
  static class MinValue extends SortedDocValues {
    final SortedSetDocValues in;
    private int ord;
    
    MinValue(SortedSetDocValues in) {
      this.in = in;
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      in.nextDoc();
      setOrd();
      return docID();
    }

    @Override
    public int advance(int target) throws IOException {
      in.advance(target);
      setOrd();
      return docID();
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      if (in.advanceExact(target)) {
        setOrd();
        return true;
      }
      return false;
    }

    @Override
    public long cost() {
      return in.cost();
    }
    
    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public BytesRef lookupOrd(int ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }

    @Override
    public int lookupTerm(BytesRef key) throws IOException {
      return (int) in.lookupTerm(key);
    }

    private void setOrd() throws IOException {
      if (docID() != NO_MORE_DOCS) {
        ord = (int) in.nextOrd();
      } else {
        ord = (int) NO_MORE_ORDS;
      }
    }
  }
  
  static class MaxValue extends SortedDocValues {
    final SortedSetDocValues in;
    private int ord;
    
    MaxValue(SortedSetDocValues in) {
      this.in = in;
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      in.nextDoc();
      setOrd();
      return docID();
    }

    @Override
    public int advance(int target) throws IOException {
      in.advance(target);
      setOrd();
      return docID();
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      if (in.advanceExact(target)) {
        setOrd();
        return true;
      }
      return false;
    }

    @Override
    public long cost() {
      return in.cost();
    }
    
    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public BytesRef lookupOrd(int ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }

    @Override
    public int lookupTerm(BytesRef key) throws IOException {
      return (int) in.lookupTerm(key);
    }

    private void setOrd() throws IOException {
      if (docID() != NO_MORE_DOCS) {
        while(true) {
          long nextOrd = in.nextOrd();
          if (nextOrd == NO_MORE_ORDS) {
            break;
          }
          ord = (int) nextOrd;
        }
      } else {
        ord = (int) NO_MORE_ORDS;
      }
    }
  }
  
  static class MiddleMinValue extends SortedDocValues {
    final SortedSetDocValues in;
    private int ord;
    private int[] ords = new int[8];
    
    MiddleMinValue(SortedSetDocValues in) {
      this.in = in;
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      in.nextDoc();
      setOrd();
      return docID();
    }

    @Override
    public int advance(int target) throws IOException {
      in.advance(target);
      setOrd();
      return docID();
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      if (in.advanceExact(target)) {
        setOrd();
        return true;
      }
      return false;
    }

    @Override
    public long cost() {
      return in.cost();
    }
    
    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public BytesRef lookupOrd(int ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }

    @Override
    public int lookupTerm(BytesRef key) throws IOException {
      return (int) in.lookupTerm(key);
    }

    private void setOrd() throws IOException {
      if (docID() != NO_MORE_DOCS) {
        int upto = 0;
        while (true) {
          long nextOrd = in.nextOrd();
          if (nextOrd == NO_MORE_ORDS) {
            break;
          }
          if (upto == ords.length) {
            ords = ArrayUtil.grow(ords);
          }
          ords[upto++] = (int) nextOrd;
        }

        if (upto == 0) {
          // iterator should not have returned this docID if it has no ords:
          assert false;
          ord = (int) NO_MORE_ORDS;
        } else {
          ord = ords[(upto-1) >>> 1];
        }
      } else {
        ord = (int) NO_MORE_ORDS;
      }
    }
  }
  
  static class MiddleMaxValue extends SortedDocValues {
    final SortedSetDocValues in;
    private int ord;
    private int[] ords = new int[8];
    
    MiddleMaxValue(SortedSetDocValues in) {
      this.in = in;
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      in.nextDoc();
      setOrd();
      return docID();
    }

    @Override
    public int advance(int target) throws IOException {
      in.advance(target);
      setOrd();
      return docID();
    }

    @Override
    public boolean advanceExact(int target) throws IOException {
      if (in.advanceExact(target)) {
        setOrd();
        return true;
      }
      return false;
    }

    @Override
    public long cost() {
      return in.cost();
    }
    
    @Override
    public int ordValue() {
      return ord;
    }

    @Override
    public BytesRef lookupOrd(int ord) throws IOException {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }

    @Override
    public int lookupTerm(BytesRef key) throws IOException {
      return (int) in.lookupTerm(key);
    }

    private void setOrd() throws IOException {
      if (docID() != NO_MORE_DOCS) {
        int upto = 0;
        while (true) {
          long nextOrd = in.nextOrd();
          if (nextOrd == NO_MORE_ORDS) {
            break;
          }
          if (upto == ords.length) {
            ords = ArrayUtil.grow(ords);
          }
          ords[upto++] = (int) nextOrd;
        }

        if (upto == 0) {
          // iterator should not have returned this docID if it has no ords:
          assert false;
          ord = (int) NO_MORE_ORDS;
        } else {
          ord = ords[upto >>> 1];
        }
      } else {
        ord = (int) NO_MORE_ORDS;
      }
    }
  }
}
