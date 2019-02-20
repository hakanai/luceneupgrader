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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util.packed;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.packed.PackedInts.Mutable;

public final class PagedGrowableWriter extends AbstractPagedMutable<PagedGrowableWriter> {

  final float acceptableOverheadRatio;

  public PagedGrowableWriter(long size, int pageSize,
      int startBitsPerValue, float acceptableOverheadRatio) {
    this(size, pageSize, startBitsPerValue, acceptableOverheadRatio, true);
  }

  PagedGrowableWriter(long size, int pageSize,int startBitsPerValue, float acceptableOverheadRatio, boolean fillPages) {
    super(startBitsPerValue, size, pageSize);
    this.acceptableOverheadRatio = acceptableOverheadRatio;
    if (fillPages) {
      fillPages();
    }
  }

  @Override
  protected Mutable newMutable(int valueCount, int bitsPerValue) {
    return new GrowableWriter(bitsPerValue, valueCount, acceptableOverheadRatio);
  }

  @Override
  protected PagedGrowableWriter newUnfilledCopy(long newSize) {
    return new PagedGrowableWriter(newSize, pageSize(), bitsPerValue, acceptableOverheadRatio, false);
  }

  @Override
  protected long baseRamBytesUsed() {
    return super.baseRamBytesUsed() + Float.BYTES;
  }

}
