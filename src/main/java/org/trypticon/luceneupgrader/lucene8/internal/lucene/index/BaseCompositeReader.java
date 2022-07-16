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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class BaseCompositeReader<R extends IndexReader> extends CompositeReader {
  private final R[] subReaders;
  protected final Comparator<R> subReadersSorter;
  private final int[] starts;       // 1st docno for each reader
  private final int maxDoc;
  private int numDocs = -1;         // computed lazily

  private final List<R> subReadersList;

  protected BaseCompositeReader(R[] subReaders, Comparator<R> subReadersSorter) throws IOException {
    if (subReadersSorter != null) {
      Arrays.sort(subReaders, subReadersSorter);
    }
    this.subReaders = subReaders;
    this.subReadersSorter = subReadersSorter;
    this.subReadersList = Collections.unmodifiableList(Arrays.asList(subReaders));
    starts = new int[subReaders.length + 1];    // build starts array
    long maxDoc = 0;
    for (int i = 0; i < subReaders.length; i++) {
      starts[i] = (int) maxDoc;
      final IndexReader r = subReaders[i];
      maxDoc += r.maxDoc();      // compute maxDocs
      r.registerParentReader(this);
    }

    if (maxDoc > IndexWriter.getActualMaxDocs()) {
      if (this instanceof DirectoryReader) {
        // A single index has too many documents and it is corrupt (IndexWriter prevents this as of LUCENE-6299)
        throw new CorruptIndexException("Too many documents: an index cannot exceed " + IndexWriter.getActualMaxDocs() + " but readers have total maxDoc=" + maxDoc, Arrays.toString(subReaders));
      } else {
        // Caller is building a MultiReader and it has too many documents; this case is just illegal arguments:
        throw new IllegalArgumentException("Too many documents: composite IndexReaders cannot exceed " + IndexWriter.getActualMaxDocs() + " but readers have total maxDoc=" + maxDoc);
      }
    }

    this.maxDoc = Math.toIntExact(maxDoc);
    starts[subReaders.length] = this.maxDoc;
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
    // We want to compute numDocs() lazily so that creating a wrapper that hides
    // some documents isn't slow at wrapping time, but on the first time that
    // numDocs() is called. This can help as there are lots of use-cases of a
    // reader that don't involve calling numDocs().
    // However it's not crucial to make sure that we don't call numDocs() more
    // than once on the sub readers, since they likely cache numDocs() anyway,
    // hence the lack of synchronization.
    int numDocs = this.numDocs;
    if (numDocs == -1) {
      numDocs = 0;
      for (IndexReader r : subReaders) {
        numDocs += r.numDocs();
      }
      assert numDocs >= 0;
      this.numDocs = numDocs;
    }
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
      int sub = subReaders[i].docFreq(term);
      assert sub >= 0;
      assert sub <= subReaders[i].getDocCount(term.field());
      total += sub;
    }
    return total;
  }
  
  @Override
  public final long totalTermFreq(Term term) throws IOException {
    ensureOpen();
    long total = 0;        // sum freqs in subreaders
    for (int i = 0; i < subReaders.length; i++) {
      long sub = subReaders[i].totalTermFreq(term);
      assert sub >= 0;
      assert sub <= subReaders[i].getSumTotalTermFreq(term.field());
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
      assert sub >= 0;
      assert sub <= reader.getSumTotalTermFreq(field);
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
      assert sub >= 0;
      assert sub <= reader.maxDoc();
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
      assert sub >= 0;
      assert sub >= reader.getSumDocFreq(field);
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
