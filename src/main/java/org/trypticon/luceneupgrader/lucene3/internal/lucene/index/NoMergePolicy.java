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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.IOException;
import java.util.Map;

public final class NoMergePolicy extends MergePolicy {

  public static final MergePolicy NO_COMPOUND_FILES = new NoMergePolicy(false);

  public static final MergePolicy COMPOUND_FILES = new NoMergePolicy(true);

  private final boolean useCompoundFile;
  
  private NoMergePolicy(boolean useCompoundFile) {
    // prevent instantiation
    this.useCompoundFile = useCompoundFile;
  }

  @Override
  public void close() {}

  @Override
  public MergeSpecification findMerges(SegmentInfos segmentInfos)
      throws CorruptIndexException, IOException { return null; }

  @Override
  public MergeSpecification findForcedMerges(SegmentInfos segmentInfos,
             int maxSegmentCount, Map<SegmentInfo,Boolean> segmentsToMerge)
      throws CorruptIndexException, IOException { return null; }

  @Override
  public MergeSpecification findForcedDeletesMerges(SegmentInfos segmentInfos)
      throws CorruptIndexException, IOException { return null; }

  @Override
  public boolean useCompoundFile(SegmentInfos segments, SegmentInfo newSegment) { return useCompoundFile; }

  @Override
  public void setIndexWriter(IndexWriter writer) {}

  @Override
  public String toString() {
    return "NoMergePolicy";
  }
}
