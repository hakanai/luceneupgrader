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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst;

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst.FSTCompiler.getOnHeapReaderWriter;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;

/**
 * Provides storage of finite state machine (FST), using byte array or byte store allocated on heap.
 *
 * @lucene.experimental
 */
public final class OnHeapFSTStore implements FSTReader {

  private static final long BASE_RAM_BYTES_USED =
      RamUsageEstimator.shallowSizeOfInstance(OnHeapFSTStore.class);

  /**
   * A {@link ReadWriteDataOutput}, used during reading when the FST is very large (more than 1 GB).
   * If the FST is less than 1 GB then bytesArray is set instead.
   */
  private ReadWriteDataOutput dataOutput;

  /** Used at read time when the FST fits into a single byte[]. */
  private final byte[] bytesArray;

  public OnHeapFSTStore(int maxBlockBits, DataInput in, long numBytes) throws IOException {
    if (maxBlockBits < 1 || maxBlockBits > 30) {
      throw new IllegalArgumentException("maxBlockBits should be 1 .. 30; got " + maxBlockBits);
    }

    if (numBytes > 1 << maxBlockBits) {
      // FST is big: we need multiple pages
      dataOutput = (ReadWriteDataOutput) getOnHeapReaderWriter(maxBlockBits);
      dataOutput.copyBytes(in, numBytes);
      dataOutput.freeze();
      bytesArray = null;
    } else {
      // FST fits into a single block: use ByteArrayBytesStoreReader for less overhead
      bytesArray = new byte[(int) numBytes];
      in.readBytes(bytesArray, 0, bytesArray.length);
    }
  }

  @Override
  public long ramBytesUsed() {
    long size = BASE_RAM_BYTES_USED;
    if (bytesArray != null) {
      size += bytesArray.length;
    } else {
      size += dataOutput.ramBytesUsed();
    }
    return size;
  }

  @Override
  public FST.BytesReader getReverseBytesReader() {
    if (bytesArray != null) {
      return new ReverseBytesReader(bytesArray);
    } else {
      return dataOutput.getReverseBytesReader();
    }
  }

  @Override
  public void writeTo(DataOutput out) throws IOException {
    if (dataOutput != null) {
      dataOutput.writeTo(out);
    } else {
      assert bytesArray != null;
      out.writeBytes(bytesArray, 0, bytesArray.length);
    }
  }
}
