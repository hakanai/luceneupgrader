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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits; // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;

@Deprecated
public abstract class DocsAndPositionsEnum extends DocsEnum {
  
  public static final int FLAG_OFFSETS = 0x1;

  public static final int FLAG_PAYLOADS = 0x2;
  
  @Deprecated
  public static final short OLD_NULL_SEMANTICS = 1 << 14;

  protected DocsAndPositionsEnum() {
  }


  public abstract int nextPosition() throws IOException;

  public abstract int startOffset() throws IOException;

  public abstract int endOffset() throws IOException;


  public abstract BytesRef getPayload() throws IOException;
  

  static DocsAndPositionsEnum wrap(final PostingsEnum postings, Bits liveDocs) {
    return new DocsAndPositionsEnumWrapper(postings, liveDocs);
  }
  
  static PostingsEnum unwrap(final DocsEnum docs) {
    if (docs instanceof DocsAndPositionsEnumWrapper) {
      return ((DocsAndPositionsEnumWrapper)docs).in;
    } else if (docs == null) {
      return null; // e.g. user is not reusing
    } else {
      throw new AssertionError();
    }
  }

  static Bits unwrapliveDocs(final DocsEnum docs) {
    if (docs instanceof DocsAndPositionsEnumWrapper) {
      return ((DocsAndPositionsEnumWrapper)docs).liveDocs;
    } else if (docs == null) {
      return null; // e.g. user is not reusing
    } else {
      throw new AssertionError();
    }
  }

  static class DocsAndPositionsEnumWrapper extends DocsAndPositionsEnum {
    final PostingsEnum in;
    final Bits liveDocs;
    
    DocsAndPositionsEnumWrapper(PostingsEnum in, Bits liveDocs) {
      this.in = Objects.requireNonNull(in);
      this.liveDocs = liveDocs;
    }

    private int doNext(int doc) throws IOException {
      while (doc != NO_MORE_DOCS && liveDocs != null && liveDocs.get(doc) == false) {
        doc = in.nextDoc();
      }
      return doc;
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
    public int freq() throws IOException {
      return in.freq();
    }

    @Override
    public AttributeSource attributes() {
      return in.attributes();
    }

    @Override
    public int docID() {
      return in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
      return doNext(in.nextDoc());
    }

    @Override
    public int advance(int target) throws IOException {
      return doNext(in.advance(target));
    }

    @Override
    public long cost() {
      return in.cost();
    }
  }
}

