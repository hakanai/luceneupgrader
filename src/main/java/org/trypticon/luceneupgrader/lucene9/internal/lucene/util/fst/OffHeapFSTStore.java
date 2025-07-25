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

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;

/**
 * Provides off heap storage of finite state machine (FST), using underlying index input instead of
 * byte store on heap
 *
 * @lucene.experimental
 */
public final class OffHeapFSTStore implements FSTReader {

  private static final long BASE_RAM_BYTES_USED =
      RamUsageEstimator.shallowSizeOfInstance(OffHeapFSTStore.class);

  private final IndexInput in;
  private final long offset;
  private final long numBytes;

  public OffHeapFSTStore(IndexInput in, long offset, FST.FSTMetadata<?> metadata) {
    this.in = in;
    this.offset = offset;
    this.numBytes = metadata.numBytes;
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED;
  }

  public long size() {
    return numBytes;
  }

  @Override
  public FST.BytesReader getReverseBytesReader() {
    try {
      return new ReverseRandomAccessReader(in.randomAccessSlice(offset, numBytes));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void writeTo(DataOutput out) throws IOException {
    throw new UnsupportedOperationException(
        "writeToOutput operation is not supported for OffHeapFSTStore");
  }
}
