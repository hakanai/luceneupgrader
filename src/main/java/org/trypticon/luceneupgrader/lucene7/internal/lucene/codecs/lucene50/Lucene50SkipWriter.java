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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.lucene50;


import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.MultiLevelSkipListWriter;

final class Lucene50SkipWriter extends MultiLevelSkipListWriter {
  private int[] lastSkipDoc;
  private long[] lastSkipDocPointer;
  private long[] lastSkipPosPointer;
  private long[] lastSkipPayPointer;
  private int[] lastPayloadByteUpto;

  private final IndexOutput docOut;
  private final IndexOutput posOut;
  private final IndexOutput payOut;

  private int curDoc;
  private long curDocPointer;
  private long curPosPointer;
  private long curPayPointer;
  private int curPosBufferUpto;
  private int curPayloadByteUpto;
  private boolean fieldHasPositions;
  private boolean fieldHasOffsets;
  private boolean fieldHasPayloads;

  public Lucene50SkipWriter(int maxSkipLevels, int blockSize, int docCount, IndexOutput docOut, IndexOutput posOut, IndexOutput payOut) {
    super(blockSize, 8, maxSkipLevels, docCount);
    this.docOut = docOut;
    this.posOut = posOut;
    this.payOut = payOut;
    
    lastSkipDoc = new int[maxSkipLevels];
    lastSkipDocPointer = new long[maxSkipLevels];
    if (posOut != null) {
      lastSkipPosPointer = new long[maxSkipLevels];
      if (payOut != null) {
        lastSkipPayPointer = new long[maxSkipLevels];
      }
      lastPayloadByteUpto = new int[maxSkipLevels];
    }
  }

  public void setField(boolean fieldHasPositions, boolean fieldHasOffsets, boolean fieldHasPayloads) {
    this.fieldHasPositions = fieldHasPositions;
    this.fieldHasOffsets = fieldHasOffsets;
    this.fieldHasPayloads = fieldHasPayloads;
  }
  
  // tricky: we only skip data for blocks (terms with more than 128 docs), but re-init'ing the skipper 
  // is pretty slow for rare terms in large segments as we have to fill O(log #docs in segment) of junk.
  // this is the vast majority of terms (worst case: ID field or similar).  so in resetSkip() we save 
  // away the previous pointers, and lazy-init only if we need to buffer skip data for the term.
  private boolean initialized;
  long lastDocFP;
  long lastPosFP;
  long lastPayFP;

  @Override
  public void resetSkip() {
    lastDocFP = docOut.getFilePointer();
    if (fieldHasPositions) {
      lastPosFP = posOut.getFilePointer();
      if (fieldHasOffsets || fieldHasPayloads) {
        lastPayFP = payOut.getFilePointer();
      }
    }
    initialized = false;
  }
  
  public void initSkip() {
    if (!initialized) {
      super.resetSkip();
      Arrays.fill(lastSkipDoc, 0);
      Arrays.fill(lastSkipDocPointer, lastDocFP);
      if (fieldHasPositions) {
        Arrays.fill(lastSkipPosPointer, lastPosFP);
        if (fieldHasPayloads) {
          Arrays.fill(lastPayloadByteUpto, 0);
        }
        if (fieldHasOffsets || fieldHasPayloads) {
          Arrays.fill(lastSkipPayPointer, lastPayFP);
        }
      }
      initialized = true;
    }
  }

  public void bufferSkip(int doc, int numDocs, long posFP, long payFP, int posBufferUpto, int payloadByteUpto) throws IOException {
    initSkip();
    this.curDoc = doc;
    this.curDocPointer = docOut.getFilePointer();
    this.curPosPointer = posFP;
    this.curPayPointer = payFP;
    this.curPosBufferUpto = posBufferUpto;
    this.curPayloadByteUpto = payloadByteUpto;
    bufferSkip(numDocs);
  }
  
  @Override
  protected void writeSkipData(int level, IndexOutput skipBuffer) throws IOException {
    int delta = curDoc - lastSkipDoc[level];

    skipBuffer.writeVInt(delta);
    lastSkipDoc[level] = curDoc;

    skipBuffer.writeVLong(curDocPointer - lastSkipDocPointer[level]);
    lastSkipDocPointer[level] = curDocPointer;

    if (fieldHasPositions) {

      skipBuffer.writeVLong(curPosPointer - lastSkipPosPointer[level]);
      lastSkipPosPointer[level] = curPosPointer;
      skipBuffer.writeVInt(curPosBufferUpto);

      if (fieldHasPayloads) {
        skipBuffer.writeVInt(curPayloadByteUpto);
      }

      if (fieldHasOffsets || fieldHasPayloads) {
        skipBuffer.writeVLong(curPayPointer - lastSkipPayPointer[level]);
        lastSkipPayPointer[level] = curPayPointer;
      }
    }
  }
}
