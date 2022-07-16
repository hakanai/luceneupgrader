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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.MergePolicy;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.packed.DirectMonotonicWriter;


public class CompressingStoredFieldsFormat extends StoredFieldsFormat {

  private final String formatName;
  private final String segmentSuffix;
  private final CompressionMode compressionMode;
  private final int chunkSize;
  private final int maxDocsPerChunk;
  private final int blockShift;

  public CompressingStoredFieldsFormat(String formatName, CompressionMode compressionMode, int chunkSize, int maxDocsPerChunk, int blockShift) {
    this(formatName, "", compressionMode, chunkSize, maxDocsPerChunk, blockShift);
  }
  
  public CompressingStoredFieldsFormat(String formatName, String segmentSuffix, 
                                       CompressionMode compressionMode, int chunkSize, int maxDocsPerChunk, int blockShift) {
    this.formatName = formatName;
    this.segmentSuffix = segmentSuffix;
    this.compressionMode = compressionMode;
    if (chunkSize < 1) {
      throw new IllegalArgumentException("chunkSize must be >= 1");
    }
    this.chunkSize = chunkSize;
    if (maxDocsPerChunk < 1) {
      throw new IllegalArgumentException("maxDocsPerChunk must be >= 1");
    }
    this.maxDocsPerChunk = maxDocsPerChunk;
    if (blockShift < DirectMonotonicWriter.MIN_BLOCK_SHIFT || blockShift > DirectMonotonicWriter.MAX_BLOCK_SHIFT) {
      throw new IllegalArgumentException("blockSize must be in " + DirectMonotonicWriter.MIN_BLOCK_SHIFT + "-" +
          DirectMonotonicWriter.MAX_BLOCK_SHIFT + ", got " + blockShift);
    }
    this.blockShift = blockShift;
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
        formatName, compressionMode, chunkSize, maxDocsPerChunk, blockShift);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(compressionMode=" + compressionMode
        + ", chunkSize=" + chunkSize + ", maxDocsPerChunk=" + maxDocsPerChunk + ", blockShift=" + blockShift + ")";
  }

}
