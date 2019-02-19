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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class UpgradeIndexMergePolicy extends MergePolicy {

  protected final MergePolicy base;

  public UpgradeIndexMergePolicy(MergePolicy base) {
    this.base = base;
  }
  

  protected boolean shouldUpgradeSegment(SegmentInfo si) {
    return !Constants.LUCENE_MAIN_VERSION.equals(si.getVersion());
  }

  @Override
  public void setIndexWriter(IndexWriter writer) {
    super.setIndexWriter(writer);
    base.setIndexWriter(writer);
  }
  
  @Override
  public MergeSpecification findMerges(SegmentInfos segmentInfos) throws CorruptIndexException, IOException {
    return base.findMerges(segmentInfos);
  }
  
  @Override
  public MergeSpecification findForcedMerges(SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentInfo,Boolean> segmentsToMerge) throws CorruptIndexException, IOException {
    // first find all old segments
    final Map<SegmentInfo,Boolean> oldSegments = new HashMap<SegmentInfo,Boolean>();
    for (final SegmentInfo si : segmentInfos) {
      final Boolean v = segmentsToMerge.get(si);
      if (v != null && shouldUpgradeSegment(si)) {
        oldSegments.put(si, v);
      }
    }
    
    if (verbose()) message("findForcedMerges: segmentsToUpgrade=" + oldSegments);
      
    if (oldSegments.isEmpty())
      return null;

    MergeSpecification spec = base.findForcedMerges(segmentInfos, maxSegmentCount, oldSegments);
    
    if (spec != null) {
      // remove all segments that are in merge specification from oldSegments,
      // the resulting set contains all segments that are left over
      // and will be merged to one additional segment:
      for (final OneMerge om : spec.merges) {
        oldSegments.keySet().removeAll(om.segments);
      }
    }

    if (!oldSegments.isEmpty()) {
      if (verbose())
        message("findForcedMerges: " +  base.getClass().getSimpleName() +
        " does not want to merge all old segments, merge remaining ones into new segment: " + oldSegments);
      final List<SegmentInfo> newInfos = new ArrayList<SegmentInfo>();
      for (final SegmentInfo si : segmentInfos) {
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
  
  @Override
  public MergeSpecification findForcedDeletesMerges(SegmentInfos segmentInfos) throws CorruptIndexException, IOException {
    return base.findForcedDeletesMerges(segmentInfos);
  }
  
  @Override
  public boolean useCompoundFile(SegmentInfos segments, SegmentInfo newSegment) throws IOException {
    return base.useCompoundFile(segments, newSegment);
  }
  
  @Override
  public void close() {
    base.close();
  }
  
  @Override
  public String toString() {
    return "[" + getClass().getSimpleName() + "->" + base + "]";
  }
  
  private boolean verbose() {
    IndexWriter w = writer.get();
    return w != null && w.verbose();
  }

  private void message(String message) {
    if (verbose())
      writer.get().message("UPGMP: " + message);
  }
  
}
