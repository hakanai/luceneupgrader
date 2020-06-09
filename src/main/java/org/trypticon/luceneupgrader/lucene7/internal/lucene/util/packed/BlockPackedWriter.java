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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed;


import static org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BitUtil.zigZagEncode;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;

public final class BlockPackedWriter extends AbstractBlockPackedWriter {
  
  public BlockPackedWriter(DataOutput out, int blockSize) {
    super(out, blockSize);
  }

  protected void flush() throws IOException {
    assert off > 0;
    long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
    for (int i = 0; i < off; ++i) {
      min = Math.min(values[i], min);
      max = Math.max(values[i], max);
    }

    final long delta = max - min;
    int bitsRequired = delta == 0 ? 0 : PackedInts.unsignedBitsRequired(delta);
    if (bitsRequired == 64) {
      // no need to delta-encode
      min = 0L;
    } else if (min > 0L) {
      // make min as small as possible so that writeVLong requires fewer bytes
      min = Math.max(0L, max - PackedInts.maxValue(bitsRequired));
    }

    final int token = (bitsRequired << BPV_SHIFT) | (min == 0 ? MIN_VALUE_EQUALS_0 : 0);
    out.writeByte((byte) token);

    if (min != 0) {
      writeVLong(out, zigZagEncode(min) - 1);
    }

    if (bitsRequired > 0) {
      if (min != 0) {
        for (int i = 0; i < off; ++i) {
          values[i] -= min;
        }
      }
      writeValues(bitsRequired);
    }

    off = 0;
  }

}
