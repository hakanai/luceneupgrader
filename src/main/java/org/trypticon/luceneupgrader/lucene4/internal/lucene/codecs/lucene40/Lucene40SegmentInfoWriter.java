package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

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

import java.io.IOException;
import java.util.Collections;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

/**
 * Lucene 4.0 implementation of {@link SegmentInfoWriter}.
 * 
 * @see Lucene40SegmentInfoFormat
 * @lucene.experimental
 */
@Deprecated
public class Lucene40SegmentInfoWriter extends SegmentInfoWriter {

  /** Sole constructor. */
  public Lucene40SegmentInfoWriter() {
  }

  /** Save a single segment's info. */
  @Override
  public void write(Directory dir, SegmentInfo si, FieldInfos fis, IOContext ioContext) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(si.name, "", Lucene40SegmentInfoFormat.SI_EXTENSION);
    si.addFile(fileName);

    final IndexOutput output = dir.createOutput(fileName, ioContext);

    boolean success = false;
    try {
      CodecUtil.writeHeader(output, Lucene40SegmentInfoFormat.CODEC_NAME, Lucene40SegmentInfoFormat.VERSION_CURRENT);
      // Write the Lucene version that created this segment, since 3.1
      output.writeString(si.getVersion().toString());
      output.writeInt(si.getDocCount());

      output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
      output.writeStringStringMap(si.getDiagnostics());
      output.writeStringStringMap(Collections.<String,String>emptyMap());
      output.writeStringSet(si.files());

      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(output);
        // TODO: why must we do this? do we not get tracking dir wrapper?
        IOUtils.deleteFilesIgnoringExceptions(si.dir, fileName);
      } else {
        output.close();
      }
    }
  }
}
