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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

@Deprecated
class Lucene40FieldInfosReader extends FieldInfosReader {

  public Lucene40FieldInfosReader() {
  }

  @Override
  public FieldInfos read(Directory directory, String segmentName, String segmentSuffix, IOContext iocontext) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(segmentName, "", Lucene40FieldInfosFormat.FIELD_INFOS_EXTENSION);
    IndexInput input = directory.openInput(fileName, iocontext);
    
    boolean success = false;
    try {
      CodecUtil.checkHeader(input, Lucene40FieldInfosFormat.CODEC_NAME, 
                                   Lucene40FieldInfosFormat.FORMAT_START, 
                                   Lucene40FieldInfosFormat.FORMAT_CURRENT);

      final int size = input.readVInt(); //read in the size
      FieldInfo infos[] = new FieldInfo[size];

      for (int i = 0; i < size; i++) {
        String name = input.readString();
        final int fieldNumber = input.readVInt();
        byte bits = input.readByte();
        boolean isIndexed = (bits & Lucene40FieldInfosFormat.IS_INDEXED) != 0;
        boolean storeTermVector = (bits & Lucene40FieldInfosFormat.STORE_TERMVECTOR) != 0;
        boolean omitNorms = (bits & Lucene40FieldInfosFormat.OMIT_NORMS) != 0;
        boolean storePayloads = (bits & Lucene40FieldInfosFormat.STORE_PAYLOADS) != 0;
        final IndexOptions indexOptions;
        if (!isIndexed) {
          indexOptions = null;
        } else if ((bits & Lucene40FieldInfosFormat.OMIT_TERM_FREQ_AND_POSITIONS) != 0) {
          indexOptions = IndexOptions.DOCS_ONLY;
        } else if ((bits & Lucene40FieldInfosFormat.OMIT_POSITIONS) != 0) {
          indexOptions = IndexOptions.DOCS_AND_FREQS;
        } else if ((bits & Lucene40FieldInfosFormat.STORE_OFFSETS_IN_POSTINGS) != 0) {
          indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
        } else {
          indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
        }

        // LUCENE-3027: past indices were able to write
        // storePayloads=true when omitTFAP is also true,
        // which is invalid.  We correct that, here:
        if (isIndexed && indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
          storePayloads = false;
        }
        // DV Types are packed in one byte
        byte val = input.readByte();
        final LegacyDocValuesType oldValuesType = getDocValuesType((byte) (val & 0x0F));
        final LegacyDocValuesType oldNormsType = getDocValuesType((byte) ((val >>> 4) & 0x0F));
        final Map<String,String> attributes = input.readStringStringMap();;
        if (oldValuesType.mapping != null) {
          attributes.put(LEGACY_DV_TYPE_KEY, oldValuesType.name());
        }
        if (oldNormsType.mapping != null) {
          if (oldNormsType.mapping != DocValuesType.NUMERIC) {
            throw new CorruptIndexException("invalid norm type: " + oldNormsType + " (resource=" + input + ")");
          }
          attributes.put(LEGACY_NORM_TYPE_KEY, oldNormsType.name());
        }
        infos[i] = new FieldInfo(name, isIndexed, fieldNumber, storeTermVector, 
          omitNorms, storePayloads, indexOptions, oldValuesType.mapping, oldNormsType.mapping, -1, Collections.unmodifiableMap(attributes));
      }

      CodecUtil.checkEOF(input);
      FieldInfos fieldInfos = new FieldInfos(infos);
      success = true;
      return fieldInfos;
    } finally {
      if (success) {
        input.close();
      } else {
        IOUtils.closeWhileHandlingException(input);
      }
    }
  }
  
  static final String LEGACY_DV_TYPE_KEY = Lucene40FieldInfosReader.class.getSimpleName() + ".dvtype";
  static final String LEGACY_NORM_TYPE_KEY = Lucene40FieldInfosReader.class.getSimpleName() + ".normtype";
  
  // mapping of 4.0 types -> 4.2 types
  static enum LegacyDocValuesType {
    NONE(null),
    VAR_INTS(DocValuesType.NUMERIC),
    FLOAT_32(DocValuesType.NUMERIC),
    FLOAT_64(DocValuesType.NUMERIC),
    BYTES_FIXED_STRAIGHT(DocValuesType.BINARY),
    BYTES_FIXED_DEREF(DocValuesType.BINARY),
    BYTES_VAR_STRAIGHT(DocValuesType.BINARY),
    BYTES_VAR_DEREF(DocValuesType.BINARY),
    FIXED_INTS_16(DocValuesType.NUMERIC),
    FIXED_INTS_32(DocValuesType.NUMERIC),
    FIXED_INTS_64(DocValuesType.NUMERIC),
    FIXED_INTS_8(DocValuesType.NUMERIC),
    BYTES_FIXED_SORTED(DocValuesType.SORTED),
    BYTES_VAR_SORTED(DocValuesType.SORTED);
    
    final DocValuesType mapping;
    LegacyDocValuesType(DocValuesType mapping) {
      this.mapping = mapping;
    }
  }
  
  // decodes a 4.0 type
  private static LegacyDocValuesType getDocValuesType(byte b) {
    return LegacyDocValuesType.values()[b];
  }
}
