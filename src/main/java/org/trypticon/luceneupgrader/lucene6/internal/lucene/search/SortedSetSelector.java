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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.RandomAccessOrds;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SortedSetDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;

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
    } else if (selector == Type.MIN) {
      return new MinValue(sortedSet);
    } else {
      if (sortedSet instanceof RandomAccessOrds == false) {
        throw new UnsupportedOperationException("codec does not support random access ordinals, cannot use selector: " + selector + " docValsImpl: " + sortedSet.toString());
      }
      RandomAccessOrds randomOrds = (RandomAccessOrds) sortedSet;
      switch(selector) {
        case MAX: return new MaxValue(randomOrds);
        case MIDDLE_MIN: return new MiddleMinValue(randomOrds);
        case MIDDLE_MAX: return new MiddleMaxValue(randomOrds);
        case MIN: 
        default: 
          throw new AssertionError();
      }
    }
  }
  
  static class MinValue extends SortedDocValues {
    final SortedSetDocValues in;
    
    MinValue(SortedSetDocValues in) {
      this.in = in;
    }

    @Override
    public int getOrd(int docID) {
      in.setDocument(docID);
      return (int) in.nextOrd();
    }

    @Override
    public BytesRef lookupOrd(int ord) {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }

    @Override
    public int lookupTerm(BytesRef key) {
      return (int) in.lookupTerm(key);
    }
  }
  
  static class MaxValue extends SortedDocValues {
    final RandomAccessOrds in;
    
    MaxValue(RandomAccessOrds in) {
      this.in = in;
    }

    @Override
    public int getOrd(int docID) {
      in.setDocument(docID);
      final int count = in.cardinality();
      if (count == 0) {
        return -1;
      } else {
        return (int) in.ordAt(count-1);
      }
    }

    @Override
    public BytesRef lookupOrd(int ord) {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }
    
    @Override
    public int lookupTerm(BytesRef key) {
      return (int) in.lookupTerm(key);
    }
  }
  
  static class MiddleMinValue extends SortedDocValues {
    final RandomAccessOrds in;
    
    MiddleMinValue(RandomAccessOrds in) {
      this.in = in;
    }

    @Override
    public int getOrd(int docID) {
      in.setDocument(docID);
      final int count = in.cardinality();
      if (count == 0) {
        return -1;
      } else {
        return (int) in.ordAt((count-1) >>> 1);
      }
    }

    @Override
    public BytesRef lookupOrd(int ord) {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }
    
    @Override
    public int lookupTerm(BytesRef key) {
      return (int) in.lookupTerm(key);
    }
  }
  
  static class MiddleMaxValue extends SortedDocValues {
    final RandomAccessOrds in;
    
    MiddleMaxValue(RandomAccessOrds in) {
      this.in = in;
    }

    @Override
    public int getOrd(int docID) {
      in.setDocument(docID);
      final int count = in.cardinality();
      if (count == 0) {
        return -1;
      } else {
        return (int) in.ordAt(count >>> 1);
      }
    }

    @Override
    public BytesRef lookupOrd(int ord) {
      return in.lookupOrd(ord);
    }

    @Override
    public int getValueCount() {
      return (int) in.getValueCount();
    }
    
    @Override
    public int lookupTerm(BytesRef key) {
      return (int) in.lookupTerm(key);
    }
  }
}
