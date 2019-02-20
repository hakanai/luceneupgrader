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


import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.PostingsWriterBase;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BitSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;



class BitSetTermsEnum extends TermsEnum {
  private final BitSetPostingsEnum postingsEnum;

  public BitSetTermsEnum(BitSet docs) {
    postingsEnum = new BitSetPostingsEnum(docs);
  }

  @Override
  public SeekStatus seekCeil(BytesRef text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void seekExact(long ord) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BytesRef term() {
    throw new UnsupportedOperationException();
  }

  @Override
  public BytesRef next() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long ord() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int docFreq() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long totalTermFreq() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PostingsEnum postings(PostingsEnum reuse, int flags) {
    if (flags != PostingsEnum.NONE) {
      // We only work with DOCS_ONLY fields
      return null;
    }
    postingsEnum.reset();
    return postingsEnum;
  }
}
