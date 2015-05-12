package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.DataOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Represents a logical byte[] as a series of pages.  You
 *  can write-once into the logical byte[] (append only),
 *  using copy, and then retrieve slices (BytesRef) into it
 *  using fill.
 *
 * @lucene.internal
 **/
public final class PagedBytes {
  private final List<byte[]> blocks = new ArrayList<byte[]>();
  private final int blockSize;
  private final int blockBits;
  private final int blockMask;
  private boolean didSkipBytes;
  private boolean frozen;
  private int upto;
  private byte[] currentBlock;

  private static final byte[] EMPTY_BYTES = new byte[0];

  /** 1<<blockBits must be bigger than biggest single
   *  BytesRef slice that will be pulled */
  public PagedBytes(int blockBits) {
    this.blockSize = 1 << blockBits;
    this.blockBits = blockBits;
    blockMask = blockSize-1;
    upto = blockSize;
  }

  /** Commits final byte[], trimming it if necessary and if trim=true */
  public void freeze(boolean trim) {
    if (frozen) {
      throw new IllegalStateException("already frozen");
    }
    if (didSkipBytes) {
      throw new IllegalStateException("cannot freeze when copy(BytesRef, BytesRef) was used");
    }
    if (trim && upto < blockSize) {
      final byte[] newBlock = new byte[upto];
      System.arraycopy(currentBlock, 0, newBlock, 0, upto);
      currentBlock = newBlock;
    }
    if (currentBlock == null) {
      currentBlock = EMPTY_BYTES;
    }
    blocks.add(currentBlock);
    frozen = true;
    currentBlock = null;
  }

  public final class PagedBytesDataInput extends DataInput {
    private int currentBlockIndex;
    private int currentBlockUpto;
    private byte[] currentBlock;

    PagedBytesDataInput() {
      currentBlock = blocks.get(0);
    }

    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public Object clone() {
      PagedBytesDataInput clone = getDataInput();
      clone.setPosition(getPosition());
      return clone;
    }

    /** Returns the current byte position. */
    public long getPosition() {
      return ((long) currentBlockIndex * blockSize) + currentBlockUpto;
    }
  
    /** Seek to a position previously obtained from
     *  {@code #getPosition}. */
    public void setPosition(long pos) {
      currentBlockIndex = (int) (pos >> blockBits);
      currentBlock = blocks.get(currentBlockIndex);
      currentBlockUpto = (int) (pos & blockMask);
    }

    @Override
    public byte readByte() {
      if (currentBlockUpto == blockSize) {
        nextBlock();
      }
      return currentBlock[currentBlockUpto++];
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) {
      assert b.length >= offset + len;
      final int offsetEnd = offset + len;
      while (true) {
        final int blockLeft = blockSize - currentBlockUpto;
        final int left = offsetEnd - offset;
        if (blockLeft < left) {
          System.arraycopy(currentBlock, currentBlockUpto,
                           b, offset,
                           blockLeft);
          nextBlock();
          offset += blockLeft;
        } else {
          // Last block
          System.arraycopy(currentBlock, currentBlockUpto,
                           b, offset,
                           left);
          currentBlockUpto += left;
          break;
        }
      }
    }

    private void nextBlock() {
      currentBlockIndex++;
      currentBlockUpto = 0;
      currentBlock = blocks.get(currentBlockIndex);
    }
  }

  public final class PagedBytesDataOutput extends DataOutput {
    @Override
    public void writeByte(byte b) {
      if (upto == blockSize) {
        if (currentBlock != null) {
          blocks.add(currentBlock);
        }
        currentBlock = new byte[blockSize];
        upto = 0;
      }
      currentBlock[upto++] = b;
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
      assert b.length >= offset + length;
      if (length == 0) {
        return;
      }

      if (upto == blockSize) {
        if (currentBlock != null) {
          blocks.add(currentBlock);
        }
        currentBlock = new byte[blockSize];
        upto = 0;
      }
          
      final int offsetEnd = offset + length;
      while(true) {
        final int left = offsetEnd - offset;
        final int blockLeft = blockSize - upto;
        if (blockLeft < left) {
          System.arraycopy(b, offset, currentBlock, upto, blockLeft);
          blocks.add(currentBlock);
          currentBlock = new byte[blockSize];
          upto = 0;
          offset += blockLeft;
        } else {
          // Last block
          System.arraycopy(b, offset, currentBlock, upto, left);
          upto += left;
          break;
        }
      }
    }

    /** Return the current byte position. */
    public long getPosition() {
      if (currentBlock == null) {
        return 0;
      } else {
        return ((long) blocks.size() * blockSize) + upto;
      }
    }
  }

  /** Returns a DataInput to read values from this
   *  PagedBytes instance. */
  public PagedBytesDataInput getDataInput() {
    if (!frozen) {
      throw new IllegalStateException("must call freeze() before getDataInput");
    }
    return new PagedBytesDataInput();
  }

  /** Returns a DataOutput that you may use to write into
   *  this PagedBytes instance.  If you do this, you should
   *  not call the other writing methods (eg, copy);
   *  results are undefined. */
  public PagedBytesDataOutput getDataOutput() {
    if (frozen) {
      throw new IllegalStateException("cannot get DataOutput after freeze()");
    }
    return new PagedBytesDataOutput();
  }
}
