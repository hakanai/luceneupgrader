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
import java.util.List;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedLongValues;


public class MergeState {

  public final SegmentInfo segmentInfo;

  public FieldInfos mergeFieldInfos;

  public final StoredFieldsReader[] storedFieldsReaders;

  public final TermVectorsReader[] termVectorsReaders;

  public final NormsProducer[] normsProducers;

  public final DocValuesProducer[] docValuesProducers;

  public final FieldInfos[] fieldInfos;

  public final Bits[] liveDocs;

  public final DocMap[] docMaps;

  public final FieldsProducer[] fieldsProducers;

  public final int[] docBase;

  public final int[] maxDocs;

  public final InfoStream infoStream;

  MergeState(List<CodecReader> readers, SegmentInfo segmentInfo, InfoStream infoStream) throws IOException {

    int numReaders = readers.size();
    docMaps = new DocMap[numReaders];
    docBase = new int[numReaders];
    maxDocs = new int[numReaders];
    fieldsProducers = new FieldsProducer[numReaders];
    normsProducers = new NormsProducer[numReaders];
    storedFieldsReaders = new StoredFieldsReader[numReaders];
    termVectorsReaders = new TermVectorsReader[numReaders];
    docValuesProducers = new DocValuesProducer[numReaders];
    fieldInfos = new FieldInfos[numReaders];
    liveDocs = new Bits[numReaders];

    for(int i=0;i<numReaders;i++) {
      final CodecReader reader = readers.get(i);

      maxDocs[i] = reader.maxDoc();
      liveDocs[i] = reader.getLiveDocs();
      fieldInfos[i] = reader.getFieldInfos();

      normsProducers[i] = reader.getNormsReader();
      if (normsProducers[i] != null) {
        normsProducers[i] = normsProducers[i].getMergeInstance();
      }
      
      docValuesProducers[i] = reader.getDocValuesReader();
      if (docValuesProducers[i] != null) {
        docValuesProducers[i] = docValuesProducers[i].getMergeInstance();
      }
      
      storedFieldsReaders[i] = reader.getFieldsReader();
      if (storedFieldsReaders[i] != null) {
        storedFieldsReaders[i] = storedFieldsReaders[i].getMergeInstance();
      }
      
      termVectorsReaders[i] = reader.getTermVectorsReader();
      if (termVectorsReaders[i] != null) {
        termVectorsReaders[i] = termVectorsReaders[i].getMergeInstance();
      }
      
      fieldsProducers[i] = reader.getPostingsReader().getMergeInstance();
    }

    this.segmentInfo = segmentInfo;
    this.infoStream = infoStream;

    setDocMaps(readers);
  }

  // NOTE: removes any "all deleted" readers from mergeState.readers
  private void setDocMaps(List<CodecReader> readers) throws IOException {
    final int numReaders = maxDocs.length;

    // Remap docIDs
    int docBase = 0;
    for(int i=0;i<numReaders;i++) {
      final CodecReader reader = readers.get(i);
      this.docBase[i] = docBase;
      final DocMap docMap = DocMap.build(reader);
      docMaps[i] = docMap;
      docBase += docMap.numDocs();
    }

    segmentInfo.setMaxDoc(docBase);
  }

  public static abstract class DocMap {

    DocMap() {}

    public abstract int get(int docID);

    public abstract int maxDoc();

    public final int numDocs() {
      return maxDoc() - numDeletedDocs();
    }

    public abstract int numDeletedDocs();

    public boolean hasDeletions() {
      return numDeletedDocs() > 0;
    }

    public static DocMap build(CodecReader reader) {
      final int maxDoc = reader.maxDoc();
      if (!reader.hasDeletions()) {
        return new NoDelDocMap(maxDoc);
      }
      final Bits liveDocs = reader.getLiveDocs();
      return build(maxDoc, liveDocs);
    }

    static DocMap build(final int maxDoc, final Bits liveDocs) {
      assert liveDocs != null;
      final PackedLongValues.Builder docMapBuilder = PackedLongValues.monotonicBuilder(PackedInts.COMPACT);
      int del = 0;
      for (int i = 0; i < maxDoc; ++i) {
        docMapBuilder.add(i - del);
        if (!liveDocs.get(i)) {
          ++del;
        }
      }
      final PackedLongValues docMap = docMapBuilder.build();
      final int numDeletedDocs = del;
      assert docMap.size() == maxDoc;
      return new DocMap() {

        @Override
        public int get(int docID) {
          if (!liveDocs.get(docID)) {
            return -1;
          }
          return (int) docMap.get(docID);
        }

        @Override
        public int maxDoc() {
          return maxDoc;
        }

        @Override
        public int numDeletedDocs() {
          return numDeletedDocs;
        }
      };
    }
  }

  private static final class NoDelDocMap extends DocMap {

    private final int maxDoc;

    NoDelDocMap(int maxDoc) {
      this.maxDoc = maxDoc;
    }

    @Override
    public int get(int docID) {
      return docID;
    }

    @Override
    public int maxDoc() {
      return maxDoc;
    }

    @Override
    public int numDeletedDocs() {
      return 0;
    }
  }
}
