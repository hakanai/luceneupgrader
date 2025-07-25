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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.*;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.SortField;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.*;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Version;

/**
 * Lucene 9.9 Segment info format.
 *
 * <p>Files:
 *
 * <ul>
 *   <li><code>.si</code>: Header, SegVersion, SegSize, IsCompoundFile, Diagnostics, Files,
 *       Attributes, IndexSort, Footer
 * </ul>
 *
 * Data types:
 *
 * <ul>
 *   <li>Header --&gt; {@link CodecUtil#writeIndexHeader IndexHeader}
 *   <li>SegSize --&gt; {@link DataOutput#writeInt Int32}
 *   <li>SegVersion --&gt; {@link DataOutput#writeString String}
 *   <li>SegMinVersion --&gt; {@link DataOutput#writeString String}
 *   <li>Files --&gt; {@link DataOutput#writeSetOfStrings Set&lt;String&gt;}
 *   <li>Diagnostics,Attributes --&gt; {@link DataOutput#writeMapOfStrings Map&lt;String,String&gt;}
 *   <li>IsCompoundFile --&gt; {@link DataOutput#writeByte Int8}
 *   <li>HasBlocks --&gt; {@link DataOutput#writeByte Int8}
 *   <li>IndexSort --&gt; {@link DataOutput#writeVInt Int32} count, followed by {@code count}
 *       SortField
 *   <li>SortField --&gt; {@link DataOutput#writeString String} sort class, followed by a per-sort
 *       bytestream (see {@link SortFieldProvider#readSortField(DataInput)})
 *   <li>Footer --&gt; {@link CodecUtil#writeFooter CodecFooter}
 * </ul>
 *
 * Field Descriptions:
 *
 * <ul>
 *   <li>SegVersion is the code version that created the segment.
 *   <li>SegMinVersion is the minimum code version that contributed documents to the segment.
 *   <li>SegSize is the number of documents contained in the segment index.
 *   <li>IsCompoundFile records whether the segment is written as a compound file or not. If this is
 *       -1, the segment is not a compound file. If it is 1, the segment is a compound file.
 *   <li>HasBlocks records whether the segment contains documents written as a block and guarantees
 *       consecutive document ids for all documents in the block
 *   <li>The Diagnostics Map is privately written by {@link IndexWriter}, as a debugging aid, for
 *       each segment it creates. It includes metadata like the current Lucene version, OS, Java
 *       version, why the segment was created (merge, flush, addIndexes), etc.
 *   <li>Files is a list of files referred to by this segment.
 * </ul>
 *
 * @see SegmentInfos
 * @lucene.experimental
 */
public class Lucene99SegmentInfoFormat extends SegmentInfoFormat {

  /** File extension used to store {@link SegmentInfo}. */
  public static final String SI_EXTENSION = "si";

  static final String CODEC_NAME = "Lucene90SegmentInfo";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  /** Sole constructor. */
  public Lucene99SegmentInfoFormat() {}

  @Override
  public SegmentInfo read(Directory dir, String segment, byte[] segmentID, IOContext context)
      throws IOException {
    final String fileName = IndexFileNames.segmentFileName(segment, "", SI_EXTENSION);
    try (ChecksumIndexInput input = dir.openChecksumInput(fileName, context)) {
      Throwable priorE = null;
      SegmentInfo si = null;
      try {
        CodecUtil.checkIndexHeader(
            input, CODEC_NAME, VERSION_START, VERSION_CURRENT, segmentID, "");
        si = parseSegmentInfo(dir, input, segment, segmentID);
      } catch (Throwable exception) {
        priorE = exception;
      } finally {
        CodecUtil.checkFooter(input, priorE);
      }
      return si;
    }
  }

  private SegmentInfo parseSegmentInfo(
      Directory dir, DataInput input, String segment, byte[] segmentID) throws IOException {
    final Version version = Version.fromBits(input.readInt(), input.readInt(), input.readInt());
    byte hasMinVersion = input.readByte();
    final Version minVersion;
    switch (hasMinVersion) {
      case 0:
        minVersion = null;
        break;
      case 1:
        minVersion = Version.fromBits(input.readInt(), input.readInt(), input.readInt());
        break;
      default:
        throw new CorruptIndexException("Illegal boolean value " + hasMinVersion, input);
    }

    final int docCount = input.readInt();
    if (docCount < 0) {
      throw new CorruptIndexException("invalid docCount: " + docCount, input);
    }
    final boolean isCompoundFile = input.readByte() == SegmentInfo.YES;
    final boolean hasBlocks = input.readByte() == SegmentInfo.YES;

    final Map<String, String> diagnostics = input.readMapOfStrings();
    final Set<String> files = input.readSetOfStrings();
    final Map<String, String> attributes = input.readMapOfStrings();

    int numSortFields = input.readVInt();
    Sort indexSort;
    if (numSortFields > 0) {
      SortField[] sortFields = new SortField[numSortFields];
      for (int i = 0; i < numSortFields; i++) {
        String name = input.readString();
        sortFields[i] = SortFieldProvider.forName(name).readSortField(input);
      }
      indexSort = new Sort(sortFields);
    } else if (numSortFields < 0) {
      throw new CorruptIndexException("invalid index sort field count: " + numSortFields, input);
    } else {
      indexSort = null;
    }

    SegmentInfo si =
        new SegmentInfo(
            dir,
            version,
            minVersion,
            segment,
            docCount,
            isCompoundFile,
            hasBlocks,
            null,
            diagnostics,
            segmentID,
            attributes,
            indexSort);
    si.setFiles(files);
    return si;
  }

  @Override
  public void write(Directory dir, SegmentInfo si, IOContext ioContext) throws IOException {
    final String fileName = IndexFileNames.segmentFileName(si.name, "", SI_EXTENSION);

    try (IndexOutput output = dir.createOutput(fileName, ioContext)) {
      // Only add the file once we've successfully created it, else IFD assert can trip:
      si.addFile(fileName);
      CodecUtil.writeIndexHeader(output, CODEC_NAME, VERSION_CURRENT, si.getId(), "");

      writeSegmentInfo(output, si);

      CodecUtil.writeFooter(output);
    }
  }

  private void writeSegmentInfo(DataOutput output, SegmentInfo si) throws IOException {
    Version version = si.getVersion();
    if (version.major < 7) {
      throw new IllegalArgumentException(
          "invalid major version: should be >= 7 but got: " + version.major + " segment=" + si);
    }
    // Write the Lucene version that created this segment, since 3.1
    output.writeInt(version.major);
    output.writeInt(version.minor);
    output.writeInt(version.bugfix);

    // Write the min Lucene version that contributed docs to the segment, since 7.0
    if (si.getMinVersion() != null) {
      output.writeByte((byte) 1);
      Version minVersion = si.getMinVersion();
      output.writeInt(minVersion.major);
      output.writeInt(minVersion.minor);
      output.writeInt(minVersion.bugfix);
    } else {
      output.writeByte((byte) 0);
    }

    assert version.prerelease == 0;
    output.writeInt(si.maxDoc());

    output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
    output.writeByte((byte) (si.getHasBlocks() ? SegmentInfo.YES : SegmentInfo.NO));
    output.writeMapOfStrings(si.getDiagnostics());
    Set<String> files = si.files();
    for (String file : files) {
      if (!IndexFileNames.parseSegmentName(file).equals(si.name)) {
        throw new IllegalArgumentException(
            "invalid files: expected segment=" + si.name + ", got=" + files);
      }
    }
    output.writeSetOfStrings(files);
    output.writeMapOfStrings(si.getAttributes());

    Sort indexSort = si.getIndexSort();
    int numSortFields = indexSort == null ? 0 : indexSort.getSort().length;
    output.writeVInt(numSortFields);
    for (int i = 0; i < numSortFields; ++i) {
      SortField sortField = indexSort.getSort()[i];
      IndexSorter sorter = sortField.getIndexSorter();
      if (sorter == null) {
        throw new IllegalArgumentException("cannot serialize SortField " + sortField);
      }
      output.writeString(sorter.getProviderName());
      SortFieldProvider.write(sortField, output);
    }
  }
}
