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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.io.IOException;

public class LogByteSizeMergePolicy extends LogMergePolicy {

  public static final double DEFAULT_MIN_MERGE_MB = 1.6;

  public static final double DEFAULT_MAX_MERGE_MB = 2048;

  public static final double DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE = Long.MAX_VALUE;

  public LogByteSizeMergePolicy() {
    minMergeSize = (long) (DEFAULT_MIN_MERGE_MB*1024*1024);
    maxMergeSize = (long) (DEFAULT_MAX_MERGE_MB*1024*1024);
    // NOTE: in Java, if you cast a too-large double to long, as we are doing here, then it becomes Long.MAX_VALUE
    maxMergeSizeForForcedMerge = (long) (DEFAULT_MAX_MERGE_MB_FOR_FORCED_MERGE*1024*1024);
  }
  
  @Override
  protected long size(SegmentCommitInfo info, IndexWriter writer) throws IOException {
    return sizeBytes(info, writer);
  }


  public void setMaxMergeMB(double mb) {
    maxMergeSize = (long) (mb*1024*1024);
  }


  public double getMaxMergeMB() {
    return ((double) maxMergeSize)/1024/1024;
  }


  public void setMaxMergeMBForForcedMerge(double mb) {
    maxMergeSizeForForcedMerge = (long) (mb*1024*1024);
  }


  public double getMaxMergeMBForForcedMerge() {
    return ((double) maxMergeSizeForForcedMerge)/1024/1024;
  }


  public void setMinMergeMB(double mb) {
    minMergeSize = (long) (mb*1024*1024);
  }


  public double getMinMergeMB() {
    return ((double) minMergeSize)/1024/1024;
  }
}
