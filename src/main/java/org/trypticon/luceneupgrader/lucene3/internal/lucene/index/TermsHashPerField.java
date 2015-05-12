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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.SorterTemplate;

import java.util.Arrays;

final class TermsHashPerField extends InvertedDocConsumerPerField {

  final TermsHashConsumerPerField consumer;

  final TermsHashPerField nextPerField;
  final TermsHashPerThread perThread;

  // Copied from our perThread
  final CharBlockPool charPool;
  final IntBlockPool intPool;
  final ByteBlockPool bytePool;

  final int streamCount;

  final FieldInfo fieldInfo;

  boolean postingsCompacted;
  int numPostings;
  private int postingsHashSize = 4;
  private int[] postingsHash;
 
  ParallelPostingsArray postingsArray;
  
  public TermsHashPerField(DocInverterPerField docInverterPerField, final TermsHashPerThread perThread, final TermsHashPerThread nextPerThread, final FieldInfo fieldInfo) {
    this.perThread = perThread;
    intPool = perThread.intPool;
    charPool = perThread.charPool;
    bytePool = perThread.bytePool;

    postingsHash = new int[postingsHashSize];
    Arrays.fill(postingsHash, -1);
    bytesUsed(postingsHashSize * RamUsageEstimator.NUM_BYTES_INT);

    this.consumer = perThread.consumer.addField(this, fieldInfo);
    initPostingsArray();

    streamCount = consumer.getStreamCount();
    this.fieldInfo = fieldInfo;
    if (nextPerThread != null)
      nextPerField = (TermsHashPerField) nextPerThread.addField(docInverterPerField, fieldInfo);
    else
      nextPerField = null;
  }

  private void initPostingsArray() {
    postingsArray = consumer.createPostingsArray(2);
    bytesUsed(postingsArray.size * postingsArray.bytesPerPosting());
  }

  // sugar: just forwards to DW
  private void bytesUsed(long size) {
    if (perThread.termsHash.trackAllocations) {
      perThread.termsHash.docWriter.bytesUsed(size);
    }
  }
  
  void shrinkHash() {
    assert postingsCompacted || numPostings == 0;

    final int newSize = 4;
    if (newSize != postingsHash.length) {
      final long previousSize = postingsHash.length;
      postingsHash = new int[newSize];
      bytesUsed((newSize-previousSize)*RamUsageEstimator.NUM_BYTES_INT);
      Arrays.fill(postingsHash, -1);
      postingsHashSize = newSize;
    }

    // Fully free the postings array on each flush:
    if (postingsArray != null) {
      bytesUsed(-postingsArray.bytesPerPosting() * postingsArray.size);
      postingsArray = null;
    }
  }

  public void reset() {
    if (!postingsCompacted)
      compactPostings();
    assert numPostings <= postingsHash.length;
    if (numPostings > 0) {
      Arrays.fill(postingsHash, 0, numPostings, -1);
      numPostings = 0;
    }
    postingsCompacted = false;
    if (nextPerField != null)
      nextPerField.reset();
  }

  public void initReader(ByteSliceReader reader, int termID, int stream) {
    assert stream < streamCount;
    int intStart = postingsArray.intStarts[termID];
    final int[] ints = intPool.buffers[intStart >> DocumentsWriter.INT_BLOCK_SHIFT];
    final int upto = intStart & DocumentsWriter.INT_BLOCK_MASK;
    reader.init(bytePool,
                postingsArray.byteStarts[termID]+stream*ByteBlockPool.FIRST_LEVEL_SIZE,
                ints[upto+stream]);
  }

  private void compactPostings() {
    int upto = 0;
    for(int i=0;i<postingsHashSize;i++) {
      if (postingsHash[i] != -1) {
        if (upto < i) {
          postingsHash[upto] = postingsHash[i];
          postingsHash[i] = -1;
        }
        upto++;
      }
    }

    assert upto == numPostings: "upto=" + upto + " numPostings=" + numPostings;
    postingsCompacted = true;
  }

  /** Collapse the hash table & sort in-place. */
  public int[] sortPostings() {
    compactPostings();
    final int[] postingsHash = this.postingsHash;
    new SorterTemplate() {
      @Override
      protected void swap(int i, int j) {
        final int o = postingsHash[i];
        postingsHash[i] = postingsHash[j];
        postingsHash[j] = o;
      }
      
      @Override
      protected int compare(int i, int j) {
        final int term1 = postingsHash[i], term2 = postingsHash[j];
        if (term1 == term2)
          return 0;
        final int textStart1 = postingsArray.textStarts[term1],
          textStart2 = postingsArray.textStarts[term2];
        final char[] text1 = charPool.buffers[textStart1 >> DocumentsWriter.CHAR_BLOCK_SHIFT];
        final int pos1 = textStart1 & DocumentsWriter.CHAR_BLOCK_MASK;
        final char[] text2 = charPool.buffers[textStart2 >> DocumentsWriter.CHAR_BLOCK_SHIFT];
        final int pos2 = textStart2 & DocumentsWriter.CHAR_BLOCK_MASK;
        return comparePostings(text1, pos1, text2, pos2);
      }

      @Override
      protected void setPivot(int i) {
        pivotTerm = postingsHash[i];
        final int textStart = postingsArray.textStarts[pivotTerm];
        pivotBuf = charPool.buffers[textStart >> DocumentsWriter.CHAR_BLOCK_SHIFT];
        pivotBufPos = textStart & DocumentsWriter.CHAR_BLOCK_MASK;
      }
  
      @Override
      protected int comparePivot(int j) {
        final int term = postingsHash[j];
        if (pivotTerm == term)
          return 0;
        final int textStart = postingsArray.textStarts[term];
        final char[] text = charPool.buffers[textStart >> DocumentsWriter.CHAR_BLOCK_SHIFT];
        final int pos = textStart & DocumentsWriter.CHAR_BLOCK_MASK;
        return comparePostings(pivotBuf, pivotBufPos, text, pos);
      }
      
      private int pivotTerm, pivotBufPos;
      private char[] pivotBuf;

      /** Compares term text for two Posting instance and
       *  returns -1 if p1 < p2; 1 if p1 > p2; else 0. */
      private int comparePostings(final char[] text1, int pos1, final char[] text2, int pos2) {
        assert text1 != text2 || pos1 != pos2;

        while(true) {
          final char c1 = text1[pos1++];
          final char c2 = text2[pos2++];
          if (c1 != c2) {
            if (0xffff == c2)
              return 1;
            else if (0xffff == c1)
              return -1;
            else
              return c1-c2;
          } else
            // This method should never compare equal postings
            // unless p1==p2
            assert c1 != 0xffff;
        }
      }
    }.quickSort(0, numPostings-1);
    return postingsHash;
  }

}
