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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.compressing;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40.Lucene40StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergePolicy;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;


public class CompressingStoredFieldsFormat extends StoredFieldsFormat {

  private final String formatName;
  private final String segmentSuffix;
  private final CompressionMode compressionMode;
  private final int chunkSize;

  public CompressingStoredFieldsFormat(String formatName, CompressionMode compressionMode, int chunkSize) {
    this(formatName, "", compressionMode, chunkSize);
  }
  
  public CompressingStoredFieldsFormat(String formatName, String segmentSuffix,
                                       CompressionMode compressionMode, int chunkSize) {
    this.formatName = formatName;
    this.segmentSuffix = segmentSuffix;
    this.compressionMode = compressionMode;
    if (chunkSize < 1) {
      throw new IllegalArgumentException("chunkSize must be >= 1");
    }
    this.chunkSize = chunkSize;
    
  }

  @Override
  public StoredFieldsReader fieldsReader(Directory directory, SegmentInfo si,
      FieldInfos fn, IOContext context) throws IOException {
    return new CompressingStoredFieldsReader(directory, si, segmentSuffix, fn, 
        context, formatName, compressionMode);
  }

  @Override
  public StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo si,
      IOContext context) throws IOException {
    return new CompressingStoredFieldsWriter(directory, si, segmentSuffix, context,
        formatName, compressionMode, chunkSize);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(compressionMode=" + compressionMode
        + ", chunkSize=" + chunkSize + ")";
  }

}
