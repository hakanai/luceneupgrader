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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene46;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;

public class Lucene46SegmentInfoReader extends SegmentInfoReader {

  public Lucene46SegmentInfoReader() {
  }

  @Override
  public SegmentInfo read(Directory dir, String segment, IOContext context) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(segment, "", Lucene46SegmentInfoFormat.SI_EXTENSION);
    final ChecksumIndexInput input = dir.openChecksumInput(fileName, context);
    boolean success = false;
    try {
      int codecVersion = CodecUtil.checkHeader(input, Lucene46SegmentInfoFormat.CODEC_NAME,
                                                      Lucene46SegmentInfoFormat.VERSION_START,
                                                      Lucene46SegmentInfoFormat.VERSION_CURRENT);
      final Version version;
      try {
        version = Version.parse(input.readString());
      } catch (ParseException pe) {
        throw new CorruptIndexException("unable to parse version string (resource=" + input + "): " + pe.getMessage(), pe);
      }

      final int docCount = input.readInt();
      if (docCount < 0) {
        throw new CorruptIndexException("invalid docCount: " + docCount + " (resource=" + input + ")");
      }
      final boolean isCompoundFile = input.readByte() == SegmentInfo.YES;
      final Map<String,String> diagnostics = input.readStringStringMap();
      final Set<String> files = input.readStringSet();
      
      if (codecVersion >= Lucene46SegmentInfoFormat.VERSION_CHECKSUM) {
        CodecUtil.checkFooter(input);
      } else {
        CodecUtil.checkEOF(input);
      }

      final SegmentInfo si = new SegmentInfo(dir, version, segment, docCount, isCompoundFile, null, diagnostics);
      si.setFiles(files);

      success = true;

      return si;

    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(input);
      } else {
        input.close();
      }
    }
  }
}
