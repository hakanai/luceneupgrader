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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene84;


import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.MultiLevelSkipListReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IndexInput;

class Lucene84SkipReader extends MultiLevelSkipListReader {
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

  public Lucene84SkipReader(
      IndexInput skipStream, int maxSkipLevels,
      boolean hasPos, boolean hasOffsets, boolean hasPayloads) {
    super(skipStream, maxSkipLevels, ForUtil.BLOCK_SIZE, 8);
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
    return df % ForUtil.BLOCK_SIZE == 0? df - 1: df;
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

    if (posPointer != null) {
      lastPosPointer = posPointer[level];
      lastPosBufferUpto = posBufferUpto[level];
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
    int delta = skipStream.readVInt();
    docPointer[level] += skipStream.readVLong();

    if (posPointer != null) {
      posPointer[level] += skipStream.readVLong();
      posBufferUpto[level] = skipStream.readVInt();

      if (payloadByteUpto != null) {
        payloadByteUpto[level] = skipStream.readVInt();
      }

      if (payPointer != null) {
        payPointer[level] += skipStream.readVLong();
      }
    }
    readImpacts(level, skipStream);
    return delta;
  }

  // The default impl skips impacts
  protected void readImpacts(int level, IndexInput skipStream) throws IOException {
    skipStream.skipBytes(skipStream.readVInt());
  }

}
