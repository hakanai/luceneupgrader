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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRefBuilder;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.automaton.CompiledAutomaton;


public abstract class Terms {

  protected Terms() {
  }

  public abstract TermsEnum iterator() throws IOException;

  public TermsEnum intersect(CompiledAutomaton compiled, final BytesRef startTerm) throws IOException {
    
    // TODO: could we factor out a common interface b/w
    // CompiledAutomaton and FST?  Then we could pass FST there too,
    // and likely speed up resolving terms to deleted docs ... but
    // AutomatonTermsEnum makes this tricky because of its on-the-fly cycle
    // detection
    
    // TODO: eventually we could support seekCeil/Exact on
    // the returned enum, instead of only being able to seek
    // at the start

    TermsEnum termsEnum = iterator();

    if (compiled.type != CompiledAutomaton.AUTOMATON_TYPE.NORMAL) {
      throw new IllegalArgumentException("please use CompiledAutomaton.getTermsEnum instead");
    }

    if (startTerm == null) {
      return new AutomatonTermsEnum(termsEnum, compiled);
    } else {
      return new AutomatonTermsEnum(termsEnum, compiled) {
        @Override
        protected BytesRef nextSeekTerm(BytesRef term) throws IOException {
          if (term == null) {
            term = startTerm;
          }
          return super.nextSeekTerm(term);
        }
      };
    }
  }

  public abstract long size() throws IOException;
  
  public abstract long getSumTotalTermFreq() throws IOException;

  public abstract long getSumDocFreq() throws IOException;

  public abstract int getDocCount() throws IOException;

  public abstract boolean hasFreqs();

  public abstract boolean hasOffsets();
  
  public abstract boolean hasPositions();
  
  public abstract boolean hasPayloads();

  public final static Terms[] EMPTY_ARRAY = new Terms[0];
  
  public BytesRef getMin() throws IOException {
    return iterator().next();
  }

  @SuppressWarnings("fallthrough")
  public BytesRef getMax() throws IOException {
    long size = size();
    
    if (size == 0) {
      // empty: only possible from a FilteredTermsEnum...
      return null;
    } else if (size >= 0) {
      // try to seek-by-ord
      try {
        TermsEnum iterator = iterator();
        iterator.seekExact(size - 1);
        return iterator.term();
      } catch (UnsupportedOperationException e) {
        // ok
      }
    }
    
    // otherwise: binary search
    TermsEnum iterator = iterator();
    BytesRef v = iterator.next();
    if (v == null) {
      // empty: only possible from a FilteredTermsEnum...
      return v;
    }

    BytesRefBuilder scratch = new BytesRefBuilder();
    scratch.append((byte) 0);

    // Iterates over digits:
    while (true) {

      int low = 0;
      int high = 256;

      // Binary search current digit to find the highest
      // digit before END:
      while (low != high) {
        int mid = (low+high) >>> 1;
        scratch.setByteAt(scratch.length()-1, (byte) mid);
        if (iterator.seekCeil(scratch.get()) == TermsEnum.SeekStatus.END) {
          // Scratch was too high
          if (mid == 0) {
            scratch.setLength(scratch.length() - 1);
            return scratch.get();
          }
          high = mid;
        } else {
          // Scratch was too low; there is at least one term
          // still after it:
          if (low == mid) {
            break;
          }
          low = mid;
        }
      }

      // Recurse to next digit:
      scratch.setLength(scratch.length() + 1);
      scratch.grow(scratch.length());
    }
  }
  
  public Object getStats() throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("impl=" + getClass().getSimpleName());
    sb.append(",size=" + size());
    sb.append(",docCount=" + getDocCount());
    sb.append(",sumTotalTermFreq=" + getSumTotalTermFreq());
    sb.append(",sumDocFreq=" + getSumDocFreq());
    return sb.toString();
  }
}
