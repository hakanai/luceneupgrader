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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseCompositeReader<R extends IndexReader> extends CompositeReader {
  private final R[] subReaders;
  private final int[] starts;       // 1st docno for each reader
  private final int maxDoc;
  private final int numDocs;

  private final List<R> subReadersList;

  protected BaseCompositeReader(R[] subReaders) {
    this.subReaders = subReaders;
    this.subReadersList = Collections.unmodifiableList(Arrays.asList(subReaders));
    starts = new int[subReaders.length + 1];    // build starts array
    long maxDoc = 0, numDocs = 0;
    for (int i = 0; i < subReaders.length; i++) {
      starts[i] = (int) maxDoc;
      final IndexReader r = subReaders[i];
      maxDoc += r.maxDoc();      // compute maxDocs
      numDocs += r.numDocs();    // compute numDocs
      r.registerParentReader(this);
    }

    if (maxDoc > IndexWriter.getActualMaxDocs()) {
      throw new IllegalArgumentException("Too many documents: composite IndexReaders cannot exceed " + IndexWriter.getActualMaxDocs() + " but readers have total maxDoc=" + maxDoc);
    }

    starts[subReaders.length] = (int) maxDoc;
    this.maxDoc = (int) maxDoc;
    this.numDocs = (int) numDocs;
  }

  @Override
  public final Fields getTermVectors(int docID) throws IOException {
    ensureOpen();
    final int i = readerIndex(docID);        // find subreader num
    return subReaders[i].getTermVectors(docID - starts[i]); // dispatch to subreader
  }

  @Override
  public final int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return numDocs;
  }

  @Override
  public final int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return maxDoc;
  }

  @Override
  public final void document(int docID, StoredFieldVisitor visitor) throws IOException {
    ensureOpen();
    final int i = readerIndex(docID);                          // find subreader num
    subReaders[i].document(docID - starts[i], visitor);    // dispatch to subreader
  }

  @Override
  public final int docFreq(Term term) throws IOException {
    ensureOpen();
    int total = 0;          // sum freqs in subreaders
    for (int i = 0; i < subReaders.length; i++) {
      total += subReaders[i].docFreq(term);
    }
    return total;
  }
  
  @Override
  public final long totalTermFreq(Term term) throws IOException {
    ensureOpen();
    long total = 0;        // sum freqs in subreaders
    for (int i = 0; i < subReaders.length; i++) {
      long sub = subReaders[i].totalTermFreq(term);
      if (sub == -1) {
        return -1;
      }
      total += sub;
    }
    return total;
  }
  
  @Override
  public final long getSumDocFreq(String field) throws IOException {
    ensureOpen();
    long total = 0; // sum doc freqs in subreaders
    for (R reader : subReaders) {
      long sub = reader.getSumDocFreq(field);
      if (sub == -1) {
        return -1; // if any of the subs doesn't support it, return -1
      }
      total += sub;
    }
    return total;
  }
  
  @Override
  public final int getDocCount(String field) throws IOException {
    ensureOpen();
    int total = 0; // sum doc counts in subreaders
    for (R reader : subReaders) {
      int sub = reader.getDocCount(field);
      if (sub == -1) {
        return -1; // if any of the subs doesn't support it, return -1
      }
      total += sub;
    }
    return total;
  }

  @Override
  public final long getSumTotalTermFreq(String field) throws IOException {
    ensureOpen();
    long total = 0; // sum doc total term freqs in subreaders
    for (R reader : subReaders) {
      long sub = reader.getSumTotalTermFreq(field);
      if (sub == -1) {
        return -1; // if any of the subs doesn't support it, return -1
      }
      total += sub;
    }
    return total;
  }
  
  protected final int readerIndex(int docID) {
    if (docID < 0 || docID >= maxDoc) {
      throw new IllegalArgumentException("docID must be >= 0 and < maxDoc=" + maxDoc + " (got docID=" + docID + ")");
    }
    return ReaderUtil.subIndex(docID, this.starts);
  }
  
  protected final int readerBase(int readerIndex) {
    if (readerIndex < 0 || readerIndex >= subReaders.length) {
      throw new IllegalArgumentException("readerIndex must be >= 0 and < getSequentialSubReaders().size()");
    }
    return this.starts[readerIndex];
  }
  
  @Override
  protected final List<? extends R> getSequentialSubReaders() {
    return subReadersList;
  }
}
