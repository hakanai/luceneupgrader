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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedInts.Mutable;

public final class PagedMutable extends AbstractPagedMutable<PagedMutable> {

  final PackedInts.Format format;

  public PagedMutable(long size, int pageSize, int bitsPerValue, float acceptableOverheadRatio) {
    this(size, pageSize, PackedInts.fastestFormatAndBits(pageSize, bitsPerValue, acceptableOverheadRatio));
    fillPages();
  }

  PagedMutable(long size, int pageSize, PackedInts.FormatAndBits formatAndBits) {
    this(size, pageSize, formatAndBits.bitsPerValue, formatAndBits.format);
  }

  PagedMutable(long size, int pageSize, int bitsPerValue, PackedInts.Format format) {
    super(bitsPerValue, size, pageSize);
    this.format = format;
  }

  @Override
  protected Mutable newMutable(int valueCount, int bitsPerValue) {
    assert this.bitsPerValue >= bitsPerValue;
    return PackedInts.getMutable(valueCount, this.bitsPerValue, format);
  }

  @Override
  protected PagedMutable newUnfilledCopy(long newSize) {
    return new PagedMutable(newSize, pageSize(), bitsPerValue, format);
  }

  @Override
  protected long baseRamBytesUsed() {
    return super.baseRamBytesUsed() + RamUsageEstimator.NUM_BYTES_OBJECT_REF;
  }

}
