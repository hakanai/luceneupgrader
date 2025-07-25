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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CompoundDirectory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CompoundFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.PriorityQueue;

/**
 * Lucene 9.0 compound file format
 *
 * <p>Files:
 *
 * <ul>
 *   <li><code>.cfs</code>: An optional "virtual" file consisting of all the other index files for
 *       systems that frequently run out of file handles.
 *   <li><code>.cfe</code>: The "virtual" compound file's entry table holding all entries in the
 *       corresponding .cfs file.
 * </ul>
 *
 * <p>Description:
 *
 * <ul>
 *   <li>Compound (.cfs) --&gt; Header, FileData <sup>FileCount</sup>, Footer
 *   <li>Compound Entry Table (.cfe) --&gt; Header, FileCount, &lt;FileName, DataOffset,
 *       DataLength&gt; <sup>FileCount</sup>
 *   <li>Header --&gt; {@link CodecUtil#writeIndexHeader IndexHeader}
 *   <li>FileCount --&gt; {@link DataOutput#writeVInt VInt}
 *   <li>DataOffset,DataLength,Checksum --&gt; {@link DataOutput#writeLong UInt64}
 *   <li>FileName --&gt; {@link DataOutput#writeString String}
 *   <li>FileData --&gt; raw file data
 *   <li>Footer --&gt; {@link CodecUtil#writeFooter CodecFooter}
 * </ul>
 *
 * <p>Notes:
 *
 * <ul>
 *   <li>FileCount indicates how many files are contained in this compound file. The entry table
 *       that follows has that many entries.
 *   <li>Each directory entry contains a long pointer to the start of this file's data section, the
 *       files length, and a String with that file's name. The start of file's data section is
 *       aligned to 8 bytes to not introduce additional unaligned accesses with mmap.
 * </ul>
 */
public final class Lucene90CompoundFormat extends CompoundFormat {

  /** Extension of compound file */
  static final String DATA_EXTENSION = "cfs";

  /** Extension of compound file entries */
  static final String ENTRIES_EXTENSION = "cfe";

  static final String DATA_CODEC = "Lucene90CompoundData";
  static final String ENTRY_CODEC = "Lucene90CompoundEntries";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  /** Sole constructor. */
  public Lucene90CompoundFormat() {}

  @Override
  public CompoundDirectory getCompoundReader(Directory dir, SegmentInfo si, IOContext context)
      throws IOException {
    return new Lucene90CompoundReader(dir, si, context);
  }

  @Override
  public void write(Directory dir, SegmentInfo si, IOContext context) throws IOException {
    String dataFile = IndexFileNames.segmentFileName(si.name, "", DATA_EXTENSION);
    String entriesFile = IndexFileNames.segmentFileName(si.name, "", ENTRIES_EXTENSION);

    try (IndexOutput data = dir.createOutput(dataFile, context);
        IndexOutput entries = dir.createOutput(entriesFile, context)) {
      CodecUtil.writeIndexHeader(data, DATA_CODEC, VERSION_CURRENT, si.getId(), "");
      CodecUtil.writeIndexHeader(entries, ENTRY_CODEC, VERSION_CURRENT, si.getId(), "");

      writeCompoundFile(entries, data, dir, si);

      CodecUtil.writeFooter(data);
      CodecUtil.writeFooter(entries);
    }
  }

  private static class SizedFile {
    private final String name;
    private final long length;

    private SizedFile(String name, long length) {
      this.name = name;
      this.length = length;
    }
  }

  private static class SizedFileQueue extends PriorityQueue<SizedFile> {
    SizedFileQueue(int maxSize) {
      super(maxSize);
    }

    @Override
    protected boolean lessThan(SizedFile sf1, SizedFile sf2) {
      return sf1.length < sf2.length;
    }
  }

  private void writeCompoundFile(
      IndexOutput entries, IndexOutput data, Directory dir, SegmentInfo si) throws IOException {
    // write number of files
    int numFiles = si.files().size();
    entries.writeVInt(numFiles);
    // first put files in ascending size order so small files fit more likely into one page
    SizedFileQueue pq = new SizedFileQueue(numFiles);
    for (String filename : si.files()) {
      pq.add(new SizedFile(filename, dir.fileLength(filename)));
    }
    while (pq.size() > 0) {
      SizedFile sizedFile = pq.pop();
      String file = sizedFile.name;
      // align file start offset
      long startOffset = data.alignFilePointer(Long.BYTES);
      // write bytes for file
      try (ChecksumIndexInput in = dir.openChecksumInput(file, IOContext.READONCE)) {

        // just copies the index header, verifying that its id matches what we expect
        CodecUtil.verifyAndCopyIndexHeader(in, data, si.getId());

        // copy all bytes except the footer
        long numBytesToCopy = in.length() - CodecUtil.footerLength() - in.getFilePointer();
        data.copyBytes(in, numBytesToCopy);

        // verify footer (checksum) matches for the incoming file we are copying
        long checksum = CodecUtil.checkFooter(in);

        // this is poached from CodecUtil.writeFooter, but we need to use our own checksum, not
        // data.getChecksum(), but I think
        // adding a public method to CodecUtil to do that is somewhat dangerous:
        CodecUtil.writeBEInt(data, CodecUtil.FOOTER_MAGIC);
        CodecUtil.writeBEInt(data, 0);
        CodecUtil.writeBELong(data, checksum);
      }
      long endOffset = data.getFilePointer();

      long length = endOffset - startOffset;

      // write entry for file
      entries.writeString(IndexFileNames.stripSegmentName(file));
      entries.writeLong(startOffset);
      entries.writeLong(length);
    }
  }
}
