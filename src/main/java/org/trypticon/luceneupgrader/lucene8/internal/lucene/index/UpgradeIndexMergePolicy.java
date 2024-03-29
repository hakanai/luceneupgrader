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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class UpgradeIndexMergePolicy extends FilterMergePolicy {

  public UpgradeIndexMergePolicy(MergePolicy in) {
    super(in);
  }
  
  protected boolean shouldUpgradeSegment(SegmentCommitInfo si) {
    return !Version.LATEST.equals(si.info.getVersion());
  }

  @Override
  public MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
    return in.findMerges(null, segmentInfos, mergeContext);
  }
  
  @Override
  public MergeSpecification findForcedMerges(SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge, MergeContext mergeContext) throws IOException {
    // first find all old segments
    final Map<SegmentCommitInfo,Boolean> oldSegments = new HashMap<>();
    for (final SegmentCommitInfo si : segmentInfos) {
      final Boolean v = segmentsToMerge.get(si);
      if (v != null && shouldUpgradeSegment(si)) {
        oldSegments.put(si, v);
      }
    }
    
    if (verbose(mergeContext)) {
      message("findForcedMerges: segmentsToUpgrade=" + oldSegments, mergeContext);
    }
      
    if (oldSegments.isEmpty())
      return null;

    MergeSpecification spec = in.findForcedMerges(segmentInfos, maxSegmentCount, oldSegments, mergeContext);
    
    if (spec != null) {
      // remove all segments that are in merge specification from oldSegments,
      // the resulting set contains all segments that are left over
      // and will be merged to one additional segment:
      for (final OneMerge om : spec.merges) {
        oldSegments.keySet().removeAll(om.segments);
      }
    }

    if (!oldSegments.isEmpty()) {
      if (verbose(mergeContext)) {
        message("findForcedMerges: " +  in.getClass().getSimpleName() +
        " does not want to merge all old segments, merge remaining ones into new segment: " + oldSegments, mergeContext);
      }
      final List<SegmentCommitInfo> newInfos = new ArrayList<>();
      for (final SegmentCommitInfo si : segmentInfos) {
        if (oldSegments.containsKey(si)) {
          newInfos.add(si);
        }
      }
      // add the final merge
      if (spec == null) {
        spec = new MergeSpecification();
      }
      spec.add(new OneMerge(newInfos));
    }

    return spec;
  }
  
}
