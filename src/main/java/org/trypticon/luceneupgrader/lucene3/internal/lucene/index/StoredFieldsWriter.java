package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.RAMOutputStream;

import java.io.IOException;

/** This is a DocFieldConsumer that writes stored fields. */
final class StoredFieldsWriter {

  FieldsWriter fieldsWriter;
  final DocumentsWriter docWriter;
  final FieldInfos fieldInfos;
  int lastDocID;

  PerDoc[] docFreeList = new PerDoc[1];
  int freeCount;

  public StoredFieldsWriter(DocumentsWriter docWriter, FieldInfos fieldInfos) {
    this.docWriter = docWriter;
    this.fieldInfos = fieldInfos;
  }

  synchronized public void flush(SegmentWriteState state) throws IOException {
    if (state.numDocs > lastDocID) {
      initFieldsWriter();
      fill(state.numDocs);
    }

    if (fieldsWriter != null) {
      try {
        fieldsWriter.finish(state.numDocs);
      } finally {
        fieldsWriter.close();
        fieldsWriter = null;
        lastDocID = 0;
      }
    }
  }

  private synchronized void initFieldsWriter() throws IOException {
    if (fieldsWriter == null) {
      fieldsWriter = new FieldsWriter(docWriter.directory, docWriter.getSegment(), fieldInfos);
      lastDocID = 0;
    }
  }

  synchronized void abort() {
    if (fieldsWriter != null) {
      fieldsWriter.abort();
      fieldsWriter = null;
      lastDocID = 0;
    }
  }

  /** Fills in any hole in the docIDs */
  void fill(int docID) throws IOException {
    // We must "catch up" for all docs before us
    // that had no stored fields:
    while(lastDocID < docID) {
      fieldsWriter.skipDocument();
      lastDocID++;
    }
  }

  synchronized void free(PerDoc perDoc) {
    assert freeCount < docFreeList.length;
    assert 0 == perDoc.numStoredFields;
    assert 0 == perDoc.fdt.length();
    assert 0 == perDoc.fdt.getFilePointer();
    docFreeList[freeCount++] = perDoc;
  }

  class PerDoc extends DocumentsWriter.DocWriter {
    final DocumentsWriter.PerDocBuffer buffer = docWriter.newPerDocBuffer();
    RAMOutputStream fdt = new RAMOutputStream(buffer);
    int numStoredFields;

    void reset() {
      fdt.reset();
      buffer.recycle();
      numStoredFields = 0;
    }

    @Override
    void abort() {
      reset();
      free(this);
    }

  }
}
