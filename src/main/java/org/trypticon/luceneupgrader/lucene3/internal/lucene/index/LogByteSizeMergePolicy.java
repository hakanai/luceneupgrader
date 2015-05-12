package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

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

import java.io.IOException;

/** This is a {@code LogMergePolicy} that measures size of a
 *  segment as the total byte size of the segment's files. */
public class LogByteSizeMergePolicy extends LogMergePolicy {

  /** Default minimum segment size.  */
  public static final double DEFAULT_MIN_MERGE_MB = 1.6;

  /** Default maximum segment size.  A segment of this size
   *  or larger will never be merged.  */
  public static final double DEFAULT_MAX_MERGE_MB = 2048;

  /** Default maximum segment size.  A segment of this size
   *  or larger will never be merged during forceMerge.  */
  public static final double DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE = Long.MAX_VALUE;

  public LogByteSizeMergePolicy() {
    minMergeSize = (long) (DEFAULT_MIN_MERGE_MB*1024*1024);
    maxMergeSize = (long) (DEFAULT_MAX_MERGE_MB*1024*1024);
    maxMergeSizeForForcedMerge = (long) (DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE*1024*1024);
  }
  
  @Override
  protected long size(SegmentInfo info) throws IOException {
    return sizeBytes(info);
  }

}
