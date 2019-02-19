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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40.Lucene40LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;

@Deprecated
public class Lucene3xCodec extends Codec {
  public Lucene3xCodec() {
    super("Lucene3x");
  }

  private final PostingsFormat postingsFormat = new Lucene3xPostingsFormat();
  
  private final StoredFieldsFormat fieldsFormat = new Lucene3xStoredFieldsFormat();
  
  private final TermVectorsFormat vectorsFormat = new Lucene3xTermVectorsFormat();
  
  private final FieldInfosFormat fieldInfosFormat = new Lucene3xFieldInfosFormat();

  private final SegmentInfoFormat infosFormat = new Lucene3xSegmentInfoFormat();
  
  private final Lucene3xNormsFormat normsFormat = new Lucene3xNormsFormat();
  
  static final String COMPOUND_FILE_STORE_EXTENSION = "cfx";
  
  // TODO: this should really be a different impl
  private final LiveDocsFormat liveDocsFormat = new Lucene40LiveDocsFormat();
  
  // 3.x doesn't support docvalues
  private final DocValuesFormat docValuesFormat = new DocValuesFormat("Lucene3x") {
    @Override
    public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
      throw new UnsupportedOperationException("this codec cannot write docvalues");
    }

    @Override
    public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
      return null; // we have no docvalues, ever
    }
  };
  
  @Override
  public PostingsFormat postingsFormat() {
    return postingsFormat;
  }
  
  @Override
  public DocValuesFormat docValuesFormat() {
    return docValuesFormat;
  }

  @Override
  public StoredFieldsFormat storedFieldsFormat() {
    return fieldsFormat;
  }
  
  @Override
  public TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }
  
  @Override
  public FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }

  @Override
  public SegmentInfoFormat segmentInfoFormat() {
    return infosFormat;
  }

  @Override
  public NormsFormat normsFormat() {
    return normsFormat;
  }
  
  @Override
  public LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }

  public static Set<String> getDocStoreFiles(SegmentInfo info) {
    if (Lucene3xSegmentInfoFormat.getDocStoreOffset(info) != -1) {
      final String dsName = Lucene3xSegmentInfoFormat.getDocStoreSegment(info);
      Set<String> files = new HashSet<>();
      if (Lucene3xSegmentInfoFormat.getDocStoreIsCompoundFile(info)) {
        files.add(IndexFileNames.segmentFileName(dsName, "", COMPOUND_FILE_STORE_EXTENSION));
      } else {
        files.add(IndexFileNames.segmentFileName(dsName, "", Lucene3xStoredFieldsReader.FIELDS_INDEX_EXTENSION));
        files.add(IndexFileNames.segmentFileName(dsName, "", Lucene3xStoredFieldsReader.FIELDS_EXTENSION));
        files.add(IndexFileNames.segmentFileName(dsName, "", Lucene3xTermVectorsReader.VECTORS_INDEX_EXTENSION));
        files.add(IndexFileNames.segmentFileName(dsName, "", Lucene3xTermVectorsReader.VECTORS_FIELDS_EXTENSION));
        files.add(IndexFileNames.segmentFileName(dsName, "", Lucene3xTermVectorsReader.VECTORS_DOCUMENTS_EXTENSION));
      }
      return files;
    } else {
      return null;
    }
  }
}
