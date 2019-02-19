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
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.TermVectorsWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;

public class CompressingTermVectorsFormat extends TermVectorsFormat {

  private final String formatName;
  private final String segmentSuffix;
  private final CompressionMode compressionMode;
  private final int chunkSize;

  public CompressingTermVectorsFormat(String formatName, String segmentSuffix,
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
  public final TermVectorsReader vectorsReader(Directory directory,
      SegmentInfo segmentInfo, FieldInfos fieldInfos, IOContext context)
      throws IOException {
    return new CompressingTermVectorsReader(directory, segmentInfo, segmentSuffix,
        fieldInfos, context, formatName, compressionMode);
  }

  @Override
  public final TermVectorsWriter vectorsWriter(Directory directory,
      SegmentInfo segmentInfo, IOContext context) throws IOException {
    return new CompressingTermVectorsWriter(directory, segmentInfo, segmentSuffix,
        context, formatName, compressionMode, chunkSize);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(compressionMode=" + compressionMode
        + ", chunkSize=" + chunkSize + ")";
  }

}
