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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene41;

import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.MultiLevelSkipListReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexInput;

@Deprecated
final class Lucene41SkipReader extends MultiLevelSkipListReader {
  // private boolean DEBUG = Lucene41PostingsReader.DEBUG;
  private final int blockSize;

  private long docPointer[];
  private long posPointer[];
  private long payPointer[];
  private int posBufferUpto[];
  private int payloadByteUpto[];

  private long lastPosPointer;
  private long lastPayPointer;
  private int lastPayloadByteUpto;
  private long lastDocPointer;
  private int lastPosBufferUpto;

  public Lucene41SkipReader(IndexInput skipStream, int maxSkipLevels, int blockSize, boolean hasPos, boolean hasOffsets, boolean hasPayloads) {
    super(skipStream, maxSkipLevels, blockSize, 8);
    this.blockSize = blockSize;
    docPointer = new long[maxSkipLevels];
    if (hasPos) {
      posPointer = new long[maxSkipLevels];
      posBufferUpto = new int[maxSkipLevels];
      if (hasPayloads) {
        payloadByteUpto = new int[maxSkipLevels];
      } else {
        payloadByteUpto = null;
      }
      if (hasOffsets || hasPayloads) {
        payPointer = new long[maxSkipLevels];
      } else {
        payPointer = null;
      }
    } else {
      posPointer = null;
    }
  }

  protected int trim(int df) {
    return df % blockSize == 0? df - 1: df;
  }

  public void init(long skipPointer, long docBasePointer, long posBasePointer, long payBasePointer, int df) throws IOException {
    super.init(skipPointer, trim(df));
    lastDocPointer = docBasePointer;
    lastPosPointer = posBasePointer;
    lastPayPointer = payBasePointer;

    Arrays.fill(docPointer, docBasePointer);
    if (posPointer != null) {
      Arrays.fill(posPointer, posBasePointer);
      if (payPointer != null) {
        Arrays.fill(payPointer, payBasePointer);
      }
    } else {
      assert posBasePointer == 0;
    }
  }

  public long getDocPointer() {
    return lastDocPointer;
  }

  public long getPosPointer() {
    return lastPosPointer;
  }

  public int getPosBufferUpto() {
    return lastPosBufferUpto;
  }

  public long getPayPointer() {
    return lastPayPointer;
  }

  public int getPayloadByteUpto() {
    return lastPayloadByteUpto;
  }

  public int getNextSkipDoc() {
    return skipDoc[0];
  }

  @Override
  protected void seekChild(int level) throws IOException {
    super.seekChild(level);
    // if (DEBUG) {
    //   System.out.println("seekChild level=" + level);
    // }
    docPointer[level] = lastDocPointer;
    if (posPointer != null) {
      posPointer[level] = lastPosPointer;
      posBufferUpto[level] = lastPosBufferUpto;
      if (payloadByteUpto != null) {
        payloadByteUpto[level] = lastPayloadByteUpto;
      }
      if (payPointer != null) {
        payPointer[level] = lastPayPointer;
      }
    }
  }
  
  @Override
  protected void setLastSkipData(int level) {
    super.setLastSkipData(level);
    lastDocPointer = docPointer[level];
    // if (DEBUG) {
    //   System.out.println("setLastSkipData level=" + level);
    //   System.out.println("  lastDocPointer=" + lastDocPointer);
    // }
    if (posPointer != null) {
      lastPosPointer = posPointer[level];
      lastPosBufferUpto = posBufferUpto[level];
      // if (DEBUG) {
      //   System.out.println("  lastPosPointer=" + lastPosPointer + " lastPosBUfferUpto=" + lastPosBufferUpto);
      // }
      if (payPointer != null) {
        lastPayPointer = payPointer[level];
      }
      if (payloadByteUpto != null) {
        lastPayloadByteUpto = payloadByteUpto[level];
      }
    }
  }

  @Override
  protected int readSkipData(int level, IndexInput skipStream) throws IOException {
    // if (DEBUG) {
    //   System.out.println("readSkipData level=" + level);
    // }
    int delta = skipStream.readVInt();
    // if (DEBUG) {
    //   System.out.println("  delta=" + delta);
    // }
    docPointer[level] += skipStream.readVLong();
    // if (DEBUG) {
    //   System.out.println("  docFP=" + docPointer[level]);
    // }

    if (posPointer != null) {
      posPointer[level] += skipStream.readVLong();
      // if (DEBUG) {
      //   System.out.println("  posFP=" + posPointer[level]);
      // }
      posBufferUpto[level] = skipStream.readVInt();
      // if (DEBUG) {
      //   System.out.println("  posBufferUpto=" + posBufferUpto[level]);
      // }

      if (payloadByteUpto != null) {
        payloadByteUpto[level] = skipStream.readVInt();
      }

      if (payPointer != null) {
        payPointer[level] += skipStream.readVLong();
      }
    }
    return delta;
  }
}
