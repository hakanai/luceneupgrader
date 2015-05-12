package org.apache.lucene.index;

/**
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

import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMOutputStream;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.IOUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

final class TermVectorsTermsWriter extends TermsHashConsumer {

  final DocumentsWriter docWriter;
  PerDoc[] docFreeList = new PerDoc[1];
  int freeCount;
  IndexOutput tvx;
  IndexOutput tvd;
  IndexOutput tvf;
  int lastDocID;
  boolean hasVectors;

  public TermVectorsTermsWriter(DocumentsWriter docWriter) {
    this.docWriter = docWriter;
  }

  @Override
  public TermsHashConsumerPerThread addThread(TermsHashPerThread termsHashPerThread) {
    return new TermVectorsTermsWriterPerThread(termsHashPerThread, this);
  }

  @Override
  synchronized void flush(Map<TermsHashConsumerPerThread,Collection<TermsHashConsumerPerField>> threadsAndFields, final SegmentWriteState state) throws IOException {
    if (tvx != null) {
      // At least one doc in this run had term vectors enabled
      assert state.segmentName != null;
      fill(state.numDocs);
      try {
        if (4 + ((long) state.numDocs) * 16 != tvx.getFilePointer()) {
          // This is most likely a bug in Sun JRE 1.6.0_04/_05;
          // we detect that the bug has struck, here, and
          // throw an exception to prevent the corruption from
          // entering the index.  See LUCENE-1282 for
          // details
          String idxName = IndexFileNames.segmentFileName(state.segmentName, IndexFileNames.VECTORS_INDEX_EXTENSION);
          throw new RuntimeException("tvx size mismatch: " + state.numDocs + " docs vs " + tvx.getFilePointer() + " length in bytes of " + idxName + " file exists?=" + state.directory.fileExists(idxName));
        }
      } finally {
        IOUtils.close(tvx, tvf, tvd);
        tvx = tvd = tvf = null;
      }

      lastDocID = 0;
      state.hasVectors = hasVectors;
      hasVectors = false;
    }

    for (Map.Entry<TermsHashConsumerPerThread,Collection<TermsHashConsumerPerField>> entry : threadsAndFields.entrySet()) {
      for (final TermsHashConsumerPerField field : entry.getValue() ) {
        TermVectorsTermsWriterPerField perField = (TermVectorsTermsWriterPerField) field;
        perField.termsHashPerField.reset();
        perField.shrinkHash();
      }

      TermVectorsTermsWriterPerThread perThread = (TermVectorsTermsWriterPerThread) entry.getKey();
      perThread.termsHashPerThread.reset(true);
    }
  }

  /** Fills in no-term-vectors for all docs we haven't seen
   *  since the last doc that had term vectors. */
  void fill(int docID) throws IOException {
    if (lastDocID < docID) {
      final long tvfPosition = tvf.getFilePointer();
      while(lastDocID < docID) {
        tvx.writeLong(tvd.getFilePointer());
        tvd.writeVInt(0);
        tvx.writeLong(tvfPosition);
        lastDocID++;
      }
    }
  }

  @Override
  public void abort() {
    hasVectors = false;
    try {
      IOUtils.closeWhileHandlingException(tvx, tvd, tvf);
    } catch (IOException e) {
      // cannot happen since we suppress exceptions
      throw new RuntimeException(e);
    }
    
    try {
      docWriter.directory.deleteFile(IndexFileNames.segmentFileName(docWriter.getSegment(), IndexFileNames.VECTORS_INDEX_EXTENSION));
    } catch (IOException ignored) {
    }
    
    try {
      docWriter.directory.deleteFile(IndexFileNames.segmentFileName(docWriter.getSegment(), IndexFileNames.VECTORS_DOCUMENTS_EXTENSION));
    } catch (IOException ignored) {
    }
    
    try {
      docWriter.directory.deleteFile(IndexFileNames.segmentFileName(docWriter.getSegment(), IndexFileNames.VECTORS_FIELDS_EXTENSION));
    } catch (IOException ignored) {
    }
    
    tvx = tvd = tvf = null;
    lastDocID = 0;
  }

  synchronized void free(PerDoc doc) {
    assert freeCount < docFreeList.length;
    docFreeList[freeCount++] = doc;
  }

  class PerDoc extends DocumentsWriter.DocWriter {

    final DocumentsWriter.PerDocBuffer buffer = docWriter.newPerDocBuffer();
    RAMOutputStream perDocTvf = new RAMOutputStream(buffer);

    int numVectorFields;

    int[] fieldNumbers = new int[1];
    long[] fieldPointers = new long[1];

    void reset() {
      perDocTvf.reset();
      buffer.recycle();
      numVectorFields = 0;
    }

    @Override
    void abort() {
      reset();
      free(this);
    }

    void addField(final int fieldNumber) {
      if (numVectorFields == fieldNumbers.length) {
        fieldNumbers = ArrayUtil.grow(fieldNumbers);
      }
      if (numVectorFields == fieldPointers.length) {
        fieldPointers = ArrayUtil.grow(fieldPointers);
      }
      fieldNumbers[numVectorFields] = fieldNumber;
      fieldPointers[numVectorFields] = perDocTvf.getFilePointer();
      numVectorFields++;
    }

  }
}
