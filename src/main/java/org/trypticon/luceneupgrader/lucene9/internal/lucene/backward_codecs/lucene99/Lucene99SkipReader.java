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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene99;

import java.io.IOException;
import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.MultiLevelSkipListReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;

/**
 * Implements the skip list reader for block postings format that stores positions and payloads.
 *
 * <p>Although this skipper uses MultiLevelSkipListReader as an interface, its definition of skip
 * position will be a little different.
 *
 * <p>For example, when skipInterval = blockSize = 3, df = 2*skipInterval = 6,
 *
 * <pre>
 * 0 1 2 3 4 5
 * d d d d d d    (posting list)
 *     ^     ^    (skip point in MultiLeveSkipWriter)
 *       ^        (skip point in Lucene99SkipWriter)
 * </pre>
 *
 * <p>In this case, MultiLevelSkipListReader will use the last document as a skip point, while
 * Lucene99SkipReader should assume no skip point will comes.
 *
 * <p>If we use the interface directly in Lucene99SkipReader, it may silly try to read another skip
 * data after the only skip point is loaded.
 *
 * <p>To illustrate this, we can call skipTo(d[5]), since skip point d[3] has smaller docId, and
 * numSkipped+blockSize== df, the MultiLevelSkipListReader will assume the skip list isn't exhausted
 * yet, and try to load a non-existed skip point
 *
 * <p>Therefore, we'll trim df before passing it to the interface. see trim(int)
 */
public class Lucene99SkipReader extends MultiLevelSkipListReader {
  private long[] docPointer;
  private long[] posPointer;
  private long[] payPointer;
  private int[] posBufferUpto;
  private int[] payloadByteUpto;

  private long lastPosPointer;
  private long lastPayPointer;
  private int lastPayloadByteUpto;
  private long lastDocPointer;
  private int lastPosBufferUpto;

  /** Sole constructor. */
  public Lucene99SkipReader(
      IndexInput skipStream,
      int maxSkipLevels,
      boolean hasPos,
      boolean hasOffsets,
      boolean hasPayloads) {
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

  /**
   * Trim original docFreq to tell skipReader read proper number of skip points.
   *
   * <p>Since our definition in Lucene99Skip* is a little different from MultiLevelSkip* This
   * trimmed docFreq will prevent skipReader from: 1. silly reading a non-existed skip point after
   * the last block boundary 2. moving into the vInt block
   */
  protected int trim(int df) {
    return df % ForUtil.BLOCK_SIZE == 0 ? df - 1 : df;
  }

  /** Initialize state. */
  public void init(
      long skipPointer, long docBasePointer, long posBasePointer, long payBasePointer, int df)
      throws IOException {
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

  /**
   * Returns the doc pointer of the doc to which the last call of {@link
   * MultiLevelSkipListReader#skipTo(int)} has skipped.
   */
  public long getDocPointer() {
    return lastDocPointer;
  }

  /** Returns the pointer in the pos file. */
  public long getPosPointer() {
    return lastPosPointer;
  }

  /** Return the start offset in the position block. */
  public int getPosBufferUpto() {
    return lastPosBufferUpto;
  }

  /** Returns the pointer in the pay file. */
  public long getPayPointer() {
    return lastPayPointer;
  }

  /** Return the number of bytes in the pay block that belongs to docs from the previous block. */
  public int getPayloadByteUpto() {
    return lastPayloadByteUpto;
  }

  /** Return the next skip doc, no skipping can be performed until this doc. */
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

  /** Read impacts. The default implementation skips them. */
  protected void readImpacts(int level, IndexInput skipStream) throws IOException {
    skipStream.skipBytes(skipStream.readVInt());
  }
}
