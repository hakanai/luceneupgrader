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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene86;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.store.EndiannessReverserUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.bkd.BKDReader;

/** Reads point values previously written with Lucene86PointsWriter */
public class Lucene86PointsReader extends PointsReader {
  final IndexInput indexIn, dataIn;
  final SegmentReadState readState;
  final Map<Integer, PointValues> readers = new HashMap<>();

  /** Sole constructor */
  public Lucene86PointsReader(SegmentReadState readState) throws IOException {
    this.readState = readState;

    String metaFileName =
        IndexFileNames.segmentFileName(
            readState.segmentInfo.name,
            readState.segmentSuffix,
            Lucene86PointsFormat.META_EXTENSION);
    String indexFileName =
        IndexFileNames.segmentFileName(
            readState.segmentInfo.name,
            readState.segmentSuffix,
            Lucene86PointsFormat.INDEX_EXTENSION);
    String dataFileName =
        IndexFileNames.segmentFileName(
            readState.segmentInfo.name,
            readState.segmentSuffix,
            Lucene86PointsFormat.DATA_EXTENSION);

    boolean success = false;
    try {
      indexIn =
          EndiannessReverserUtil.openInput(readState.directory, indexFileName, readState.context);
      CodecUtil.checkIndexHeader(
          indexIn,
          Lucene86PointsFormat.INDEX_CODEC_NAME,
          Lucene86PointsFormat.VERSION_START,
          Lucene86PointsFormat.VERSION_CURRENT,
          readState.segmentInfo.getId(),
          readState.segmentSuffix);

      dataIn =
          EndiannessReverserUtil.openInput(readState.directory, dataFileName, readState.context);
      CodecUtil.checkIndexHeader(
          dataIn,
          Lucene86PointsFormat.DATA_CODEC_NAME,
          Lucene86PointsFormat.VERSION_START,
          Lucene86PointsFormat.VERSION_CURRENT,
          readState.segmentInfo.getId(),
          readState.segmentSuffix);

      long indexLength = -1, dataLength = -1;
      try (ChecksumIndexInput metaIn =
          EndiannessReverserUtil.openChecksumInput(
              readState.directory, metaFileName, readState.context)) {
        Throwable priorE = null;
        try {
          CodecUtil.checkIndexHeader(
              metaIn,
              Lucene86PointsFormat.META_CODEC_NAME,
              Lucene86PointsFormat.VERSION_START,
              Lucene86PointsFormat.VERSION_CURRENT,
              readState.segmentInfo.getId(),
              readState.segmentSuffix);

          while (true) {
            int fieldNumber = metaIn.readInt();
            if (fieldNumber == -1) {
              break;
            } else if (fieldNumber < 0) {
              throw new CorruptIndexException("Illegal field number: " + fieldNumber, metaIn);
            }
            PointValues reader = new BKDReader(metaIn, indexIn, dataIn);
            readers.put(fieldNumber, reader);
          }
          indexLength = metaIn.readLong();
          dataLength = metaIn.readLong();
        } catch (Throwable t) {
          priorE = t;
        } finally {
          CodecUtil.checkFooter(metaIn, priorE);
        }
      }
      // At this point, checksums of the meta file have been validated so we
      // know that indexLength and dataLength are very likely correct.
      CodecUtil.retrieveChecksum(indexIn, indexLength);
      CodecUtil.retrieveChecksum(dataIn, dataLength);
      success = true;
    } finally {
      if (success == false) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
  }

  /**
   * Returns the underlying {@link PointValues}.
   *
   * @lucene.internal
   */
  @Override
  public PointValues getValues(String fieldName) {
    FieldInfo fieldInfo = readState.fieldInfos.fieldInfo(fieldName);
    if (fieldInfo == null) {
      throw new IllegalArgumentException("field=\"" + fieldName + "\" is unrecognized");
    }
    if (fieldInfo.getPointDimensionCount() == 0) {
      throw new IllegalArgumentException("field=\"" + fieldName + "\" did not index point values");
    }

    return readers.get(fieldInfo.number);
  }

  @Override
  public void checkIntegrity() throws IOException {
    CodecUtil.checksumEntireFile(indexIn);
    CodecUtil.checksumEntireFile(dataIn);
  }

  @Override
  public void close() throws IOException {
    IOUtils.close(indexIn, dataIn);
    // Free up heap:
    readers.clear();
  }
}
