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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataInput;

public final class PackedDataInput {

  final DataInput in;
  long current;
  int remainingBits;

  public PackedDataInput(DataInput in) {
    this.in = in;
    skipToNextByte();
  }

  public long readLong(int bitsPerValue) throws IOException {
    assert bitsPerValue > 0 && bitsPerValue <= 64 : bitsPerValue;
    long r = 0;
    while (bitsPerValue > 0) {
      if (remainingBits == 0) {
        current = in.readByte() & 0xFF;
        remainingBits = 8;
      }
      final int bits = Math.min(bitsPerValue, remainingBits);
      r = (r << bits) | ((current >>> (remainingBits - bits)) & ((1L << bits) - 1));
      bitsPerValue -= bits;
      remainingBits -= bits;
    }
    return r;
  }

  public void skipToNextByte() {
    remainingBits = 0;
  }

}
