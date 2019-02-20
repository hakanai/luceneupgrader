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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader.ReaderClosedListener;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;

public abstract class LeafReader extends IndexReader {

  private final LeafReaderContext readerContext = new LeafReaderContext(this);

  protected LeafReader() {
    super();
  }

  @Override
  public final LeafReaderContext getContext() {
    ensureOpen();
    return readerContext;
  }

  public static interface CoreClosedListener {

    void onClose(Object ownerCoreCacheKey) throws IOException;
  }

  private static class CoreClosedListenerWrapper implements ReaderClosedListener {

    private final CoreClosedListener listener;

    CoreClosedListenerWrapper(CoreClosedListener listener) {
      this.listener = listener;
    }

    @Override
    public void onClose(IndexReader reader) throws IOException {
      listener.onClose(reader.getCoreCacheKey());
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CoreClosedListenerWrapper)) {
        return false;
      }
      return listener.equals(((CoreClosedListenerWrapper) other).listener);
    }

  }


  protected static void addCoreClosedListenerAsReaderClosedListener(IndexReader reader, CoreClosedListener listener) {
    reader.addReaderClosedListener(new CoreClosedListenerWrapper(listener));
  }

  protected static void removeCoreClosedListenerAsReaderClosedListener(IndexReader reader, CoreClosedListener listener) {
    reader.removeReaderClosedListener(new CoreClosedListenerWrapper(listener));
  }

  public abstract void addCoreClosedListener(CoreClosedListener listener);

  public abstract void removeCoreClosedListener(CoreClosedListener listener);

  public abstract Fields fields() throws IOException;

  @Override
  public final int docFreq(Term term) throws IOException {
    final Terms terms = terms(term.field());
    if (terms == null) {
      return 0;
    }
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum.docFreq();
    } else {
      return 0;
    }
  }


  @Override
  public final long totalTermFreq(Term term) throws IOException {
    final Terms terms = terms(term.field());
    if (terms == null) {
      return 0;
    }
    final TermsEnum termsEnum = terms.iterator();
    if (termsEnum.seekExact(term.bytes())) {
      return termsEnum.totalTermFreq();
    } else {
      return 0;
    }
  }

  @Override
  public final long getSumDocFreq(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getSumDocFreq();
  }

  @Override
  public final int getDocCount(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getDocCount();
  }

  @Override
  public final long getSumTotalTermFreq(String field) throws IOException {
    final Terms terms = terms(field);
    if (terms == null) {
      return 0;
    }
    return terms.getSumTotalTermFreq();
  }

  public final Terms terms(String field) throws IOException {
    return fields().terms(field);
  }


  public final PostingsEnum postings(Term term, int flags) throws IOException {
    assert term.field() != null;
    assert term.bytes() != null;
    final Terms terms = terms(term.field());
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator();
      if (termsEnum.seekExact(term.bytes())) {
        return termsEnum.postings(null, flags);
      }
    }
    return null;
  }


  public final PostingsEnum postings(Term term) throws IOException {
    return postings(term, PostingsEnum.FREQS);
  }


  public abstract NumericDocValues getNumericDocValues(String field) throws IOException;


  public abstract BinaryDocValues getBinaryDocValues(String field) throws IOException;


  public abstract SortedDocValues getSortedDocValues(String field) throws IOException;
  

  public abstract SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException;


  public abstract SortedSetDocValues getSortedSetDocValues(String field) throws IOException;


  public abstract Bits getDocsWithField(String field) throws IOException;


  public abstract NumericDocValues getNormValues(String field) throws IOException;

  public abstract FieldInfos getFieldInfos();


  public abstract Bits getLiveDocs();

  public abstract PointValues getPointValues();

  public abstract void checkIntegrity() throws IOException;

  public abstract Sort getIndexSort();
}
