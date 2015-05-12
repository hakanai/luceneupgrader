package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

/**
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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.UnicodeUtil;

import java.io.IOException;

final class TermVectorsTermsWriterPerField extends TermsHashConsumerPerField {

  final TermVectorsTermsWriterPerThread perThread;
  final TermsHashPerField termsHashPerField;
  final FieldInfo fieldInfo;

  boolean doVectors;
  boolean doVectorPositions;
  boolean doVectorOffsets;

  int maxNumPostings;

  public TermVectorsTermsWriterPerField(TermsHashPerField termsHashPerField, TermVectorsTermsWriterPerThread perThread, FieldInfo fieldInfo) {
    this.termsHashPerField = termsHashPerField;
    this.perThread = perThread;
    this.fieldInfo = fieldInfo;
  }

  @Override
  int getStreamCount() {
    return 2;
  }

  /** Called once per field per document if term vectors
   *  are enabled, to write the vectors to
   *  RAMOutputStream, which is then quickly flushed to
   *  the real term vectors files in the Directory. */
  @Override
  void finish() throws IOException {

    final int numPostings = termsHashPerField.numPostings;

    assert numPostings >= 0;

    if (!doVectors || numPostings == 0)
      return;

    if (numPostings > maxNumPostings)
      maxNumPostings = numPostings;

    final IndexOutput tvf = perThread.doc.perDocTvf;

    // This is called once, after inverting all occurrences
    // of a given field in the doc.  At this point we flush
    // our hash into the DocWriter.

    assert fieldInfo.storeTermVector;

    perThread.doc.addField(termsHashPerField.fieldInfo.number);
    TermVectorsPostingsArray postings = (TermVectorsPostingsArray) termsHashPerField.postingsArray;

    final int[] termIDs = termsHashPerField.sortPostings();

    tvf.writeVInt(numPostings);
    byte bits = 0x0;
    if (doVectorPositions)
      bits |= TermVectorsReader.STORE_POSITIONS_WITH_TERMVECTOR;
    if (doVectorOffsets) 
      bits |= TermVectorsReader.STORE_OFFSET_WITH_TERMVECTOR;
    tvf.writeByte(bits);

    int encoderUpto = 0;
    int lastTermBytesCount = 0;

    final ByteSliceReader reader = perThread.vectorSliceReader;
    final char[][] charBuffers = perThread.termsHashPerThread.charPool.buffers;
    for(int j=0;j<numPostings;j++) {
      final int termID = termIDs[j];
      final int freq = postings.freqs[termID];
          
      final char[] text2 = charBuffers[postings.textStarts[termID] >> DocumentsWriter.CHAR_BLOCK_SHIFT];
      final int start2 = postings.textStarts[termID] & DocumentsWriter.CHAR_BLOCK_MASK;

      // We swap between two encoders to save copying
      // last Term's byte array
      final UnicodeUtil.UTF8Result utf8Result = perThread.utf8Results[encoderUpto];

      // TODO: we could do this incrementally
      UnicodeUtil.UTF16toUTF8(text2, start2, utf8Result);
      final int termBytesCount = utf8Result.length;

      // TODO: UTF16toUTF8 could tell us this prefix
      // Compute common prefix between last term and
      // this term
      int prefix = 0;
      if (j > 0) {
        final byte[] lastTermBytes = perThread.utf8Results[1-encoderUpto].result;
        final byte[] termBytes = perThread.utf8Results[encoderUpto].result;
        while(prefix < lastTermBytesCount && prefix < termBytesCount) {
          if (lastTermBytes[prefix] != termBytes[prefix])
            break;
          prefix++;
        }
      }
      encoderUpto = 1-encoderUpto;
      lastTermBytesCount = termBytesCount;

      final int suffix = termBytesCount - prefix;
      tvf.writeVInt(prefix);
      tvf.writeVInt(suffix);
      tvf.writeBytes(utf8Result.result, prefix, suffix);
      tvf.writeVInt(freq);

      if (doVectorPositions) {
        termsHashPerField.initReader(reader, termID, 0);
        reader.writeTo(tvf);
      }

      if (doVectorOffsets) {
        termsHashPerField.initReader(reader, termID, 1);
        reader.writeTo(tvf);
      }
    }

    termsHashPerField.reset();

    // NOTE: we clear, per-field, at the thread level,
    // because term vectors fully write themselves on each
    // field; this saves RAM (eg if large doc has two large
    // fields w/ term vectors on) because we recycle/reuse
    // all RAM after each field:
    perThread.termsHashPerThread.reset();
  }

  void shrinkHash() {
    termsHashPerField.shrinkHash();
    maxNumPostings = 0;
  }
  

  @Override
  ParallelPostingsArray createPostingsArray(int size) {
    return new TermVectorsPostingsArray(size);
  }

  static final class TermVectorsPostingsArray extends ParallelPostingsArray {
    public TermVectorsPostingsArray(int size) {
      super(size);
      freqs = new int[size];
      lastOffsets = new int[size];
      lastPositions = new int[size];
    }

    int[] freqs;                                       // How many times this term occurred in the current doc
    int[] lastOffsets;                                 // Last offset we saw
    int[] lastPositions;                               // Last position where this term occurred

    @Override
    int bytesPerPosting() {
      return super.bytesPerPosting() + 3 * RamUsageEstimator.NUM_BYTES_INT;
    }
  }
}
