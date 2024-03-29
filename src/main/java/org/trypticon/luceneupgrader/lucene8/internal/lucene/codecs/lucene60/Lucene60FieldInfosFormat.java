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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene60;


import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IndexOutput;

public final class Lucene60FieldInfosFormat extends FieldInfosFormat {

  public Lucene60FieldInfosFormat() {
  }
  
  @Override
  public FieldInfos read(Directory directory, SegmentInfo segmentInfo, String segmentSuffix, IOContext context) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, EXTENSION);
    try (ChecksumIndexInput input = directory.openChecksumInput(fileName, context)) {
      Throwable priorE = null;
      FieldInfo infos[] = null;
      try {
        int version = CodecUtil.checkIndexHeader(input,
                                   Lucene60FieldInfosFormat.CODEC_NAME, 
                                   Lucene60FieldInfosFormat.FORMAT_START, 
                                   Lucene60FieldInfosFormat.FORMAT_CURRENT,
                                   segmentInfo.getId(), segmentSuffix);
        
        final int size = input.readVInt(); //read in the size
        infos = new FieldInfo[size];
        
        // previous field's attribute map, we share when possible:
        Map<String,String> lastAttributes = Collections.emptyMap();
        
        for (int i = 0; i < size; i++) {
          String name = input.readString();
          final int fieldNumber = input.readVInt();
          if (fieldNumber < 0) {
            throw new CorruptIndexException("invalid field number for field: " + name + ", fieldNumber=" + fieldNumber, input);
          }
          byte bits = input.readByte();
          boolean storeTermVector = (bits & STORE_TERMVECTOR) != 0;
          boolean omitNorms = (bits & OMIT_NORMS) != 0;
          boolean storePayloads = (bits & STORE_PAYLOADS) != 0;
          boolean isSoftDeletesField = (bits & SOFT_DELETES_FIELD) != 0;

          final IndexOptions indexOptions = getIndexOptions(input, input.readByte());
          
          // DV Types are packed in one byte
          final DocValuesType docValuesType = getDocValuesType(input, input.readByte());
          final long dvGen = input.readLong();
          Map<String,String> attributes = input.readMapOfStrings();
          // just use the last field's map if its the same
          if (attributes.equals(lastAttributes)) {
            attributes = lastAttributes;
          }
          lastAttributes = attributes;
          int pointDataDimensionCount = input.readVInt();
          int pointNumBytes;
          int pointIndexDimensionCount = pointDataDimensionCount;
          if (pointDataDimensionCount != 0) {
            if (version >= Lucene60FieldInfosFormat.FORMAT_SELECTIVE_INDEXING) {
              pointIndexDimensionCount = input.readVInt();
            }
            pointNumBytes = input.readVInt();
          } else {
            pointNumBytes = 0;
          }

          try {
            infos[i] = new FieldInfo(name, fieldNumber, storeTermVector, omitNorms, storePayloads, 
                                     indexOptions, docValuesType, dvGen, attributes,
                                     pointDataDimensionCount, pointIndexDimensionCount, pointNumBytes, isSoftDeletesField);
          } catch (IllegalStateException e) {
            throw new CorruptIndexException("invalid fieldinfo for field: " + name + ", fieldNumber=" + fieldNumber, input, e);
          }
        }
      } catch (Throwable exception) {
        priorE = exception;
      } finally {
        CodecUtil.checkFooter(input, priorE);
      }
      return new FieldInfos(infos);
    }
  }
  
  static {
    // We "mirror" DocValues enum values with the constants below; let's try to ensure if we add a new DocValuesType while this format is
    // still used for writing, we remember to fix this encoding:
    assert DocValuesType.values().length == 6;
  }

  private static byte docValuesByte(DocValuesType type) {
    switch(type) {
    case NONE:
      return 0;
    case NUMERIC:
      return 1;
    case BINARY:
      return 2;
    case SORTED:
      return 3;
    case SORTED_SET:
      return 4;
    case SORTED_NUMERIC:
      return 5;
    default:
      // BUG
      throw new AssertionError("unhandled DocValuesType: " + type);
    }
  }

  private static DocValuesType getDocValuesType(IndexInput input, byte b) throws IOException {
    switch(b) {
    case 0:
      return DocValuesType.NONE;
    case 1:
      return DocValuesType.NUMERIC;
    case 2:
      return DocValuesType.BINARY;
    case 3:
      return DocValuesType.SORTED;
    case 4:
      return DocValuesType.SORTED_SET;
    case 5:
      return DocValuesType.SORTED_NUMERIC;
    default:
      throw new CorruptIndexException("invalid docvalues byte: " + b, input);
    }
  }

  static {
    // We "mirror" IndexOptions enum values with the constants below; let's try to ensure if we add a new IndexOption while this format is
    // still used for writing, we remember to fix this encoding:
    assert IndexOptions.values().length == 5;
  }

  private static byte indexOptionsByte(IndexOptions indexOptions) {
    switch (indexOptions) {
    case NONE:
      return 0;
    case DOCS:
      return 1;
    case DOCS_AND_FREQS:
      return 2;
    case DOCS_AND_FREQS_AND_POSITIONS:
      return 3;
    case DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS:
      return 4;
    default:
      // BUG:
      throw new AssertionError("unhandled IndexOptions: " + indexOptions);
    }
  }
  
  private static IndexOptions getIndexOptions(IndexInput input, byte b) throws IOException {
    switch (b) {
    case 0:
      return IndexOptions.NONE;
    case 1:
      return IndexOptions.DOCS;
    case 2:
      return IndexOptions.DOCS_AND_FREQS;
    case 3:
      return IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    case 4:
      return IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
    default:
      // BUG
      throw new CorruptIndexException("invalid IndexOptions byte: " + b, input);
    }
  }

  @Override
  public void write(Directory directory, SegmentInfo segmentInfo, String segmentSuffix, FieldInfos infos, IOContext context) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, EXTENSION);
    try (IndexOutput output = directory.createOutput(fileName, context)) {
      CodecUtil.writeIndexHeader(output, Lucene60FieldInfosFormat.CODEC_NAME, Lucene60FieldInfosFormat.FORMAT_CURRENT, segmentInfo.getId(), segmentSuffix);
      output.writeVInt(infos.size());
      for (FieldInfo fi : infos) {
        fi.checkConsistency();

        output.writeString(fi.name);
        output.writeVInt(fi.number);

        byte bits = 0x0;
        if (fi.hasVectors()) bits |= STORE_TERMVECTOR;
        if (fi.omitsNorms()) bits |= OMIT_NORMS;
        if (fi.hasPayloads()) bits |= STORE_PAYLOADS;
        if (fi.isSoftDeletesField()) bits |= SOFT_DELETES_FIELD;
        output.writeByte(bits);

        output.writeByte(indexOptionsByte(fi.getIndexOptions()));

        // pack the DV type and hasNorms in one byte
        output.writeByte(docValuesByte(fi.getDocValuesType()));
        output.writeLong(fi.getDocValuesGen());
        output.writeMapOfStrings(fi.attributes());
        output.writeVInt(fi.getPointDimensionCount());
        if (fi.getPointDimensionCount() != 0) {
          output.writeVInt(fi.getPointIndexDimensionCount());
          output.writeVInt(fi.getPointNumBytes());
        }
      }
      CodecUtil.writeFooter(output);
    }
  }
  
  static final String EXTENSION = "fnm";
  
  // Codec header
  static final String CODEC_NAME = "Lucene60FieldInfos";
  static final int FORMAT_START = 0;
  static final int FORMAT_SOFT_DELETES = 1;
  static final int FORMAT_SELECTIVE_INDEXING = 2;
  static final int FORMAT_CURRENT = FORMAT_SELECTIVE_INDEXING;
  
  // Field flags
  static final byte STORE_TERMVECTOR = 0x1;
  static final byte OMIT_NORMS = 0x2;
  static final byte STORE_PAYLOADS = 0x4;
  static final byte SOFT_DELETES_FIELD = 0x8;
}
