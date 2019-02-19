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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ByteBlockPool;

final class ByteSliceWriter extends DataOutput {

  private byte[] slice;
  private int upto;
  private final ByteBlockPool pool;

  int offset0;

  public ByteSliceWriter(ByteBlockPool pool) {
    this.pool = pool;
  }

  public void init(int address) {
    slice = pool.buffers[address >> ByteBlockPool.BYTE_BLOCK_SHIFT];
    assert slice != null;
    upto = address & ByteBlockPool.BYTE_BLOCK_MASK;
    offset0 = address;
    assert upto < slice.length;
  }

  @Override
  public void writeByte(byte b) {
    assert slice != null;
    if (slice[upto] != 0) {
      upto = pool.allocSlice(slice, upto);
      slice = pool.buffer;
      offset0 = pool.byteOffset;
      assert slice != null;
    }
    slice[upto++] = b;
    assert upto != slice.length;
  }

  @Override
  public void writeBytes(final byte[] b, int offset, final int len) {
    final int offsetEnd = offset + len;
    while(offset < offsetEnd) {
      if (slice[upto] != 0) {
        // End marker
        upto = pool.allocSlice(slice, upto);
        slice = pool.buffer;
        offset0 = pool.byteOffset;
      }

      slice[upto++] = b[offset++];
      assert upto != slice.length;
    }
  }

  public int getAddress() {
    return upto + (offset0 & DocumentsWriterPerThread.BYTE_BLOCK_NOT_MASK);
  }
}