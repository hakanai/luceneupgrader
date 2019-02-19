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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class LogMergePolicy extends MergePolicy {


  public static final double LEVEL_LOG_SPAN = 0.75;

  public static final int DEFAULT_MERGE_FACTOR = 10;

  public static final int DEFAULT_MAX_MERGE_DOCS = Integer.MAX_VALUE;


  public static final double DEFAULT_NO_CFS_RATIO = 0.1;

  protected int mergeFactor = DEFAULT_MERGE_FACTOR;


  protected long minMergeSize;

  protected long maxMergeSize;

  // Although the core MPs set it explicitly, we must default in case someone
  // out there wrote his own LMP ...
  protected long maxMergeSizeForForcedMerge = Long.MAX_VALUE;

  protected int maxMergeDocs = DEFAULT_MAX_MERGE_DOCS;

  protected boolean calibrateSizeByDeletes = true;

  public LogMergePolicy() {
    super(DEFAULT_NO_CFS_RATIO, MergePolicy.DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }

  protected boolean verbose(IndexWriter writer) {
    return writer != null && writer.infoStream.isEnabled("LMP");
  }

  protected void message(String message, IndexWriter writer) {
    if (verbose(writer)) {
      writer.infoStream.message("LMP", message);
    }
  }


  public int getMergeFactor() {
    return mergeFactor;
  }


  public void setMergeFactor(int mergeFactor) {
    if (mergeFactor < 2)
      throw new IllegalArgumentException("mergeFactor cannot be less than 2");
    this.mergeFactor = mergeFactor;
  }

  public void setCalibrateSizeByDeletes(boolean calibrateSizeByDeletes) {
    this.calibrateSizeByDeletes = calibrateSizeByDeletes;
  }

  public boolean getCalibrateSizeByDeletes() {
    return calibrateSizeByDeletes;
  }


  protected long sizeDocs(SegmentCommitInfo info, IndexWriter writer) throws IOException {
    if (calibrateSizeByDeletes) {
      int delCount = writer.numDeletedDocs(info);
      assert delCount <= info.info.maxDoc();
      return (info.info.maxDoc() - (long)delCount);
    } else {
      return info.info.maxDoc();
    }
  }


  protected long sizeBytes(SegmentCommitInfo info, IndexWriter writer) throws IOException {
    if (calibrateSizeByDeletes) {
      return super.size(info, writer);
    }
    return info.sizeInBytes();
  }
  

  protected boolean isMerged(SegmentInfos infos, int maxNumSegments, Map<SegmentCommitInfo,Boolean> segmentsToMerge, IndexWriter writer) throws IOException {
    final int numSegments = infos.size();
    int numToMerge = 0;
    SegmentCommitInfo mergeInfo = null;
    boolean segmentIsOriginal = false;
    for(int i=0;i<numSegments && numToMerge <= maxNumSegments;i++) {
      final SegmentCommitInfo info = infos.info(i);
      final Boolean isOriginal = segmentsToMerge.get(info);
      if (isOriginal != null) {
        segmentIsOriginal = isOriginal;
        numToMerge++;
        mergeInfo = info;
      }
    }

    return numToMerge <= maxNumSegments &&
      (numToMerge != 1 || !segmentIsOriginal || isMerged(infos, mergeInfo, writer));
  }

  private MergeSpecification findForcedMergesSizeLimit(
      SegmentInfos infos, int maxNumSegments, int last, IndexWriter writer) throws IOException {
    MergeSpecification spec = new MergeSpecification();
    final List<SegmentCommitInfo> segments = infos.asList();

    int start = last - 1;
    while (start >= 0) {
      SegmentCommitInfo info = infos.info(start);
      if (size(info, writer) > maxMergeSizeForForcedMerge || sizeDocs(info, writer) > maxMergeDocs) {
        if (verbose(writer)) {
          message("findForcedMergesSizeLimit: skip segment=" + info + ": size is > maxMergeSize (" + maxMergeSizeForForcedMerge + ") or sizeDocs is > maxMergeDocs (" + maxMergeDocs + ")", writer);
        }
        // need to skip that segment + add a merge for the 'right' segments,
        // unless there is only 1 which is merged.
        if (last - start - 1 > 1 || (start != last - 1 && !isMerged(infos, infos.info(start + 1), writer))) {
          // there is more than 1 segment to the right of
          // this one, or a mergeable single segment.
          spec.add(new OneMerge(segments.subList(start + 1, last)));
        }
        last = start;
      } else if (last - start == mergeFactor) {
        // mergeFactor eligible segments were found, add them as a merge.
        spec.add(new OneMerge(segments.subList(start, last)));
        last = start;
      }
      --start;
    }

    // Add any left-over segments, unless there is just 1
    // already fully merged
    if (last > 0 && (++start + 1 < last || !isMerged(infos, infos.info(start), writer))) {
      spec.add(new OneMerge(segments.subList(start, last)));
    }

    return spec.merges.size() == 0 ? null : spec;
  }
  
  private MergeSpecification findForcedMergesMaxNumSegments(SegmentInfos infos, int maxNumSegments, int last, IndexWriter writer) throws IOException {
    MergeSpecification spec = new MergeSpecification();
    final List<SegmentCommitInfo> segments = infos.asList();

    // First, enroll all "full" merges (size
    // mergeFactor) to potentially be run concurrently:
    while (last - maxNumSegments + 1 >= mergeFactor) {
      spec.add(new OneMerge(segments.subList(last - mergeFactor, last)));
      last -= mergeFactor;
    }

    // Only if there are no full merges pending do we
    // add a final partial (< mergeFactor segments) merge:
    if (0 == spec.merges.size()) {
      if (maxNumSegments == 1) {

        // Since we must merge down to 1 segment, the
        // choice is simple:
        if (last > 1 || !isMerged(infos, infos.info(0), writer)) {
          spec.add(new OneMerge(segments.subList(0, last)));
        }
      } else if (last > maxNumSegments) {

        // Take care to pick a partial merge that is
        // least cost, but does not make the index too
        // lopsided.  If we always just picked the
        // partial tail then we could produce a highly
        // lopsided index over time:

        // We must merge this many segments to leave
        // maxNumSegments in the index (from when
        // forceMerge was first kicked off):
        final int finalMergeSize = last - maxNumSegments + 1;

        // Consider all possible starting points:
        long bestSize = 0;
        int bestStart = 0;

        for(int i=0;i<last-finalMergeSize+1;i++) {
          long sumSize = 0;
          for(int j=0;j<finalMergeSize;j++) {
            sumSize += size(infos.info(j+i), writer);
          }
          if (i == 0 || (sumSize < 2*size(infos.info(i-1), writer) && sumSize < bestSize)) {
            bestStart = i;
            bestSize = sumSize;
          }
        }

        spec.add(new OneMerge(segments.subList(bestStart, bestStart + finalMergeSize)));
      }
    }
    return spec.merges.size() == 0 ? null : spec;
  }
  

  @Override
  public MergeSpecification findForcedMerges(SegmentInfos infos,
            int maxNumSegments, Map<SegmentCommitInfo,Boolean> segmentsToMerge, IndexWriter writer) throws IOException {

    assert maxNumSegments > 0;
    if (verbose(writer)) {
      message("findForcedMerges: maxNumSegs=" + maxNumSegments + " segsToMerge="+ segmentsToMerge, writer);
    }

    // If the segments are already merged (e.g. there's only 1 segment), or
    // there are <maxNumSegments:.
    if (isMerged(infos, maxNumSegments, segmentsToMerge, writer)) {
      if (verbose(writer)) {
        message("already merged; skip", writer);
      }
      return null;
    }

    // Find the newest (rightmost) segment that needs to
    // be merged (other segments may have been flushed
    // since merging started):
    int last = infos.size();
    while (last > 0) {
      final SegmentCommitInfo info = infos.info(--last);
      if (segmentsToMerge.get(info) != null) {
        last++;
        break;
      }
    }

    if (last == 0) {
      if (verbose(writer)) {
        message("last == 0; skip", writer);
      }
      return null;
    }
    
    // There is only one segment already, and it is merged
    if (maxNumSegments == 1 && last == 1 && isMerged(infos, infos.info(0), writer)) {
      if (verbose(writer)) {
        message("already 1 seg; skip", writer);
      }
      return null;
    }

    // Check if there are any segments above the threshold
    boolean anyTooLarge = false;
    for (int i = 0; i < last; i++) {
      SegmentCommitInfo info = infos.info(i);
      if (size(info, writer) > maxMergeSizeForForcedMerge || sizeDocs(info, writer) > maxMergeDocs) {
        anyTooLarge = true;
        break;
      }
    }

    if (anyTooLarge) {
      return findForcedMergesSizeLimit(infos, maxNumSegments, last, writer);
    } else {
      return findForcedMergesMaxNumSegments(infos, maxNumSegments, last, writer);
    }
  }

  @Override
  public MergeSpecification findForcedDeletesMerges(SegmentInfos segmentInfos, IndexWriter writer)
      throws IOException {
    final List<SegmentCommitInfo> segments = segmentInfos.asList();
    final int numSegments = segments.size();

    if (verbose(writer)) {
      message("findForcedDeleteMerges: " + numSegments + " segments", writer);
    }

    MergeSpecification spec = new MergeSpecification();
    int firstSegmentWithDeletions = -1;
    assert writer != null;
    for(int i=0;i<numSegments;i++) {
      final SegmentCommitInfo info = segmentInfos.info(i);
      int delCount = writer.numDeletedDocs(info);
      if (delCount > 0) {
        if (verbose(writer)) {
          message("  segment " + info.info.name + " has deletions", writer);
        }
        if (firstSegmentWithDeletions == -1)
          firstSegmentWithDeletions = i;
        else if (i - firstSegmentWithDeletions == mergeFactor) {
          // We've seen mergeFactor segments in a row with
          // deletions, so force a merge now:
          if (verbose(writer)) {
            message("  add merge " + firstSegmentWithDeletions + " to " + (i-1) + " inclusive", writer);
          }
          spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, i)));
          firstSegmentWithDeletions = i;
        }
      } else if (firstSegmentWithDeletions != -1) {
        // End of a sequence of segments with deletions, so,
        // merge those past segments even if it's fewer than
        // mergeFactor segments
        if (verbose(writer)) {
          message("  add merge " + firstSegmentWithDeletions + " to " + (i-1) + " inclusive", writer);
        }
        spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, i)));
        firstSegmentWithDeletions = -1;
      }
    }

    if (firstSegmentWithDeletions != -1) {
      if (verbose(writer)) {
        message("  add merge " + firstSegmentWithDeletions + " to " + (numSegments-1) + " inclusive", writer);
      }
      spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, numSegments)));
    }

    return spec;
  }

  private static class SegmentInfoAndLevel implements Comparable<SegmentInfoAndLevel> {
    SegmentCommitInfo info;
    float level;
    
    public SegmentInfoAndLevel(SegmentCommitInfo info, float level) {
      this.info = info;
      this.level = level;
    }

    // Sorts largest to smallest
    @Override
    public int compareTo(SegmentInfoAndLevel other) {
      return Float.compare(other.level, level);
    }
  }


  @Override
  public MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos infos, IndexWriter writer) throws IOException {

    final int numSegments = infos.size();
    if (verbose(writer)) {
      message("findMerges: " + numSegments + " segments", writer);
    }

    // Compute levels, which is just log (base mergeFactor)
    // of the size of each segment
    final List<SegmentInfoAndLevel> levels = new ArrayList<>(numSegments);
    final float norm = (float) Math.log(mergeFactor);

    final Collection<SegmentCommitInfo> mergingSegments = writer.getMergingSegments();

    for(int i=0;i<numSegments;i++) {
      final SegmentCommitInfo info = infos.info(i);
      long size = size(info, writer);

      // Floor tiny segments
      if (size < 1) {
        size = 1;
      }

      final SegmentInfoAndLevel infoLevel = new SegmentInfoAndLevel(info, (float) Math.log(size)/norm);
      levels.add(infoLevel);

      if (verbose(writer)) {
        final long segBytes = sizeBytes(info, writer);
        String extra = mergingSegments.contains(info) ? " [merging]" : "";
        if (size >= maxMergeSize) {
          extra += " [skip: too large]";
        }
        message("seg=" + writer.segString(info) + " level=" + infoLevel.level + " size=" + String.format(Locale.ROOT, "%.3f MB", segBytes/1024/1024.) + extra, writer);
      }
    }

    final float levelFloor;
    if (minMergeSize <= 0)
      levelFloor = (float) 0.0;
    else
      levelFloor = (float) (Math.log(minMergeSize)/norm);

    // Now, we quantize the log values into levels.  The
    // first level is any segment whose log size is within
    // LEVEL_LOG_SPAN of the max size, or, who has such as
    // segment "to the right".  Then, we find the max of all
    // other segments and use that to define the next level
    // segment, etc.

    MergeSpecification spec = null;

    final int numMergeableSegments = levels.size();

    int start = 0;
    while(start < numMergeableSegments) {

      // Find max level of all segments not already
      // quantized.
      float maxLevel = levels.get(start).level;
      for(int i=1+start;i<numMergeableSegments;i++) {
        final float level = levels.get(i).level;
        if (level > maxLevel) {
          maxLevel = level;
        }
      }

      // Now search backwards for the rightmost segment that
      // falls into this level:
      float levelBottom;
      if (maxLevel <= levelFloor) {
        // All remaining segments fall into the min level
        levelBottom = -1.0F;
      } else {
        levelBottom = (float) (maxLevel - LEVEL_LOG_SPAN);

        // Force a boundary at the level floor
        if (levelBottom < levelFloor && maxLevel >= levelFloor) {
          levelBottom = levelFloor;
        }
      }

      int upto = numMergeableSegments-1;
      while(upto >= start) {
        if (levels.get(upto).level >= levelBottom) {
          break;
        }
        upto--;
      }
      if (verbose(writer)) {
        message("  level " + levelBottom + " to " + maxLevel + ": " + (1+upto-start) + " segments", writer);
      }

      // Finally, record all merges that are viable at this level:
      int end = start + mergeFactor;
      while(end <= 1+upto) {
        boolean anyTooLarge = false;
        boolean anyMerging = false;
        for(int i=start;i<end;i++) {
          final SegmentCommitInfo info = levels.get(i).info;
          anyTooLarge |= (size(info, writer) >= maxMergeSize || sizeDocs(info, writer) >= maxMergeDocs);
          if (mergingSegments.contains(info)) {
            anyMerging = true;
            break;
          }
        }

        if (anyMerging) {
          // skip
        } else if (!anyTooLarge) {
          if (spec == null)
            spec = new MergeSpecification();
          final List<SegmentCommitInfo> mergeInfos = new ArrayList<>(end-start);
          for(int i=start;i<end;i++) {
            mergeInfos.add(levels.get(i).info);
            assert infos.contains(levels.get(i).info);
          }
          if (verbose(writer)) {
            message("  add merge=" + writer.segString(mergeInfos) + " start=" + start + " end=" + end, writer);
          }
          spec.add(new OneMerge(mergeInfos));
        } else if (verbose(writer)) {
          message("    " + start + " to " + end + ": contains segment over maxMergeSize or maxMergeDocs; skipping", writer);
        }

        start = end;
        end = start + mergeFactor;
      }

      start = 1+upto;
    }

    return spec;
  }


  public void setMaxMergeDocs(int maxMergeDocs) {
    this.maxMergeDocs = maxMergeDocs;
  }


  public int getMaxMergeDocs() {
    return maxMergeDocs;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[" + getClass().getSimpleName() + ": ");
    sb.append("minMergeSize=").append(minMergeSize).append(", ");
    sb.append("mergeFactor=").append(mergeFactor).append(", ");
    sb.append("maxMergeSize=").append(maxMergeSize).append(", ");
    sb.append("maxMergeSizeForForcedMerge=").append(maxMergeSizeForForcedMerge).append(", ");
    sb.append("calibrateSizeByDeletes=").append(calibrateSizeByDeletes).append(", ");
    sb.append("maxMergeDocs=").append(maxMergeDocs).append(", ");
    sb.append("maxCFSSegmentSizeMB=").append(getMaxCFSSegmentSizeMB()).append(", ");
    sb.append("noCFSRatio=").append(noCFSRatio);
    sb.append("]");
    return sb.toString();
  }

}
