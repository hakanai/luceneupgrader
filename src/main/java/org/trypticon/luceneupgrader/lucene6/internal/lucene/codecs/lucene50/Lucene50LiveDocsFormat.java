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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.lucene50;


import java.io.IOException;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentCommitInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.MutableBits;

public final class Lucene50LiveDocsFormat extends LiveDocsFormat {
  
  public Lucene50LiveDocsFormat() {
  }
  
  private static final String EXTENSION = "liv";
  
  private static final String CODEC_NAME = "Lucene50LiveDocs";
  
  private static final int VERSION_START = 0;
  private static final int VERSION_CURRENT = VERSION_START;

  @Override
  public MutableBits newLiveDocs(int size) throws IOException {
    FixedBitSet bits = new FixedBitSet(size);
    bits.set(0, size);
    return bits;
  }

  @Override
  public MutableBits newLiveDocs(Bits existing) throws IOException {
    FixedBitSet fbs = (FixedBitSet) existing;
    return fbs.clone();
  }

  @Override
  public Bits readLiveDocs(Directory dir, SegmentCommitInfo info, IOContext context) throws IOException {
    long gen = info.getDelGen();
    String name = IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, gen);
    final int length = info.info.maxDoc();
    try (ChecksumIndexInput input = dir.openChecksumInput(name, context)) {
      Throwable priorE = null;
      try {
        CodecUtil.checkIndexHeader(input, CODEC_NAME, VERSION_START, VERSION_CURRENT, 
                                     info.info.getId(), Long.toString(gen, Character.MAX_RADIX));
        long data[] = new long[FixedBitSet.bits2words(length)];
        for (int i = 0; i < data.length; i++) {
          data[i] = input.readLong();
        }
        FixedBitSet fbs = new FixedBitSet(data, length);
        if (fbs.length() - fbs.cardinality() != info.getDelCount()) {
          throw new CorruptIndexException("bits.deleted=" + (fbs.length() - fbs.cardinality()) + 
                                          " info.delcount=" + info.getDelCount(), input);
        }
        return fbs;
      } catch (Throwable exception) {
        priorE = exception;
      } finally {
        CodecUtil.checkFooter(input, priorE);
      }
    }
    throw new AssertionError();
  }

  @Override
  public void writeLiveDocs(MutableBits bits, Directory dir, SegmentCommitInfo info, int newDelCount, IOContext context) throws IOException {
    long gen = info.getNextDelGen();
    String name = IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, gen);
    FixedBitSet fbs = (FixedBitSet) bits;
    if (fbs.length() - fbs.cardinality() != info.getDelCount() + newDelCount) {
      throw new CorruptIndexException("bits.deleted=" + (fbs.length() - fbs.cardinality()) + 
                                      " info.delcount=" + info.getDelCount() + " newdelcount=" + newDelCount, name);
    }
    long data[] = fbs.getBits();
    try (IndexOutput output = dir.createOutput(name, context)) {
      CodecUtil.writeIndexHeader(output, CODEC_NAME, VERSION_CURRENT, info.info.getId(), Long.toString(gen, Character.MAX_RADIX));
      for (int i = 0; i < data.length; i++) {
        output.writeLong(data[i]);
      }
      CodecUtil.writeFooter(output);
    }
  }

  @Override
  public void files(SegmentCommitInfo info, Collection<String> files) throws IOException {
    if (info.hasDeletions()) {
      files.add(IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, info.getDelGen()));
    }
  }
}
