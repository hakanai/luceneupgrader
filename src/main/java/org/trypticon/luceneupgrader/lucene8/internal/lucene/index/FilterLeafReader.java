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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


import java.io.IOException;
import java.util.Iterator;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;

public abstract class FilterLeafReader extends LeafReader {

  public static LeafReader unwrap(LeafReader reader) {
    while (reader instanceof FilterLeafReader) {
      reader = ((FilterLeafReader) reader).getDelegate();
    }
    return reader;
  }

  public abstract static class FilterFields extends Fields {
    protected final Fields in;

    public FilterFields(Fields in) {
      if (in == null) {
        throw new NullPointerException("incoming Fields must not be null");
      }
      this.in = in;
    }

    @Override
    public Iterator<String> iterator() {
      return in.iterator();
    }

    @Override
    public Terms terms(String field) throws IOException {
      return in.terms(field);
    }

    @Override
    public int size() {
      return in.size();
    }
  }

  public abstract static class FilterTerms extends Terms {
    protected final Terms in;

    public FilterTerms(Terms in) {
      if (in == null) {
        throw new NullPointerException("incoming Terms must not be null");
      }
      this.in = in;
    }

    @Override
    public TermsEnum iterator() throws IOException {
      return in.iterator();
    }

    @Override
    public long size() throws IOException {
      return in.size();
    }

    @Override
    public long getSumTotalTermFreq() throws IOException {
      return in.getSumTotalTermFreq();
    }

    @Override
    public long getSumDocFreq() throws IOException {
      return in.getSumDocFreq();
    }

    @Override
    public int getDocCount() throws IOException {
      return in.getDocCount();
    }

    @Override
    public boolean hasFreqs() {
      return in.hasFreqs();
    }

    @Override
    public boolean hasOffsets() {
      return in.hasOffsets();
    }

    @Override
    public boolean hasPositions() {
      return in.hasPositions();
    }
    
    @Override
    public boolean hasPayloads() {
      return in.hasPayloads();
    }

    @Override
    public Object getStats() throws IOException {
      return in.getStats();
    }
  }

  public abstract static class FilterTermsEnum extends TermsEnum {
    protected final TermsEnum in;

    public FilterTermsEnum(TermsEnum in) {
      if (in == null) {
        throw new NullPointerException("incoming TermsEnum must not be null");
      }
      this.in = in;
    }

    @Override
    public AttributeSource attributes() {
      return in.attributes();
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {
      return in.seekCeil(text);
    }
    
    @Override
    public boolean seekExact(BytesRef text) throws IOException {
      return in.seekExact(text);
    }

    @Override
    public void seekExact(long ord) throws IOException {
      in.seekExact(ord);
    }

    @Override
    public BytesRef next() throws IOException {
      return in.next();
    }

    @Override
    public BytesRef term() throws IOException {
      return in.term();
    }

    @Override
    public long ord() throws IOException {
      return in.ord();
    }

    @Override
    public int docFreq() throws IOException {
      return in.docFreq();
    }

    @Override
    public long totalTermFreq() throws IOException {
      return in.totalTermFreq();
    }

    @Override
    public PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException {
      return in.postings(reuse, flags);
    }

    @Override
    public ImpactsEnum impacts(int flags) throws IOException {
      return in.impacts(flags);
    }

    @Override
    public void seekExact(BytesRef term, TermState state) throws IOException {
      in.seekExact(term, state);
    }

    @Override
    public TermState termState() throws IOException {
      return in.termState();
    }
  }

  public abstract static class FilterPostingsEnum extends PostingsEnum {
    protected final PostingsEnum in;

    public FilterPostingsEnum(PostingsEnum in) {
      if (in == null) {
        throw new NullPointerException("incoming PostingsEnum must not be null");
      }
      this.in = in;
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int freq() throws IOException {
      return in.freq();
    }

    @Override
    public int nextDoc() throws IOException {
      return in.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
      return in.advance(target);
    }

    @Override
    public int nextPosition() throws IOException {
      return in.nextPosition();
    }

    @Override
    public int startOffset() throws IOException {
      return in.startOffset();
    }

    @Override
    public int endOffset() throws IOException {
      return in.endOffset();
    }

    @Override
    public BytesRef getPayload() throws IOException {
      return in.getPayload();
    }

    @Override
    public long cost() {
      return in.cost();
    }
  }

  protected final LeafReader in;

  public FilterLeafReader(LeafReader in) {
    super();
    if (in == null) {
      throw new NullPointerException("incoming LeafReader must not be null");
    }
    this.in = in;
    in.registerParentReader(this);
  }

  @Override
  public Bits getLiveDocs() {
    ensureOpen();
    return in.getLiveDocs();
  }
  
  @Override
  public FieldInfos getFieldInfos() {
    return in.getFieldInfos();
  }

  @Override
  public PointValues getPointValues(String field) throws IOException {
    return in.getPointValues(field);
  }

  @Override
  public Fields getTermVectors(int docID)
          throws IOException {
    ensureOpen();
    return in.getTermVectors(docID);
  }

  @Override
  public int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return in.numDocs();
  }

  @Override
  public int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return in.maxDoc();
  }

  @Override
  public void document(int docID, StoredFieldVisitor visitor) throws IOException {
    ensureOpen();
    in.document(docID, visitor);
  }

  @Override
  protected void doClose() throws IOException {
    in.close();
  }

  @Override
  public Terms terms(String field) throws IOException {
    ensureOpen();
    return in.terms(field);
  }

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder("FilterLeafReader(");
    buffer.append(in);
    buffer.append(')');
    return buffer.toString();
  }

  @Override
  public NumericDocValues getNumericDocValues(String field) throws IOException {
    ensureOpen();
    return in.getNumericDocValues(field);
  }
  
  @Override
  public BinaryDocValues getBinaryDocValues(String field) throws IOException {
    ensureOpen();
    return in.getBinaryDocValues(field);
  }

  @Override
  public SortedDocValues getSortedDocValues(String field) throws IOException {
    ensureOpen();
    return in.getSortedDocValues(field);
  }
  
  @Override
  public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
    ensureOpen();
    return in.getSortedNumericDocValues(field);
  }

  @Override
  public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
    ensureOpen();
    return in.getSortedSetDocValues(field);
  }

  @Override
  public NumericDocValues getNormValues(String field) throws IOException {
    ensureOpen();
    return in.getNormValues(field);
  }

  @Override
  public LeafMetaData getMetaData() {
    ensureOpen();
    return in.getMetaData();
  }

  @Override
  public void checkIntegrity() throws IOException {
    ensureOpen();
    in.checkIntegrity();
  }

  public LeafReader getDelegate() {
    return in;
  }
}
