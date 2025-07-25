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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a {@link MergePolicy} that tries to merge segments into levels of
 * exponentially increasing size, where each level has fewer segments than the value of the merge
 * factor. Whenever extra segments (beyond the merge factor upper bound) are encountered, all
 * segments within the level are merged. You can get or set the merge factor using {@link
 * #getMergeFactor()} and {@link #setMergeFactor(int)} respectively.
 *
 * <p>This class is abstract and requires a subclass to define the {@link #size} method which
 * specifies how a segment's size is determined. {@link LogDocMergePolicy} is one subclass that
 * measures size by document count in the segment. {@link LogByteSizeMergePolicy} is another
 * subclass that measures size as the total byte size of the file(s) for the segment.
 *
 * <p><b>NOTE</b>: This policy returns natural merges whose size is below the {@link #minMergeSize
 * minimum merge size} for {@link #findFullFlushMerges full-flush merges}.
 */
public abstract class LogMergePolicy extends MergePolicy {

  /**
   * Defines the allowed range of log(size) for each level. A level is computed by taking the max
   * segment log size, minus LEVEL_LOG_SPAN, and finding all segments falling within that range.
   */
  public static final double LEVEL_LOG_SPAN = 0.75;

  /** Default merge factor, which is how many segments are merged at a time */
  public static final int DEFAULT_MERGE_FACTOR = 10;

  /**
   * Default maximum segment size. A segment of this size or larger will never be merged. @see
   * setMaxMergeDocs
   */
  public static final int DEFAULT_MAX_MERGE_DOCS = Integer.MAX_VALUE;

  /**
   * Default noCFSRatio. If a merge's size is {@code >= 10%} of the index, then we disable compound
   * file for it.
   *
   * @see MergePolicy#setNoCFSRatio
   */
  public static final double DEFAULT_NO_CFS_RATIO = 0.1;

  /** How many segments to merge at a time. */
  protected int mergeFactor = DEFAULT_MERGE_FACTOR;

  /**
   * Any segments whose size is smaller than this value will be candidates for full-flush merges and
   * merged more aggressively.
   */
  protected long minMergeSize;

  /** If the size of a segment exceeds this value then it will never be merged. */
  protected long maxMergeSize;

  // Although the core MPs set it explicitly, we must default in case someone
  // out there wrote his own LMP ...
  /**
   * If the size of a segment exceeds this value then it will never be merged during {@link
   * IndexWriter#forceMerge}.
   */
  protected long maxMergeSizeForForcedMerge = Long.MAX_VALUE;

  /** If a segment has more than this many documents then it will never be merged. */
  protected int maxMergeDocs = DEFAULT_MAX_MERGE_DOCS;

  /** If true, we pro-rate a segment's size by the percentage of non-deleted documents. */
  protected boolean calibrateSizeByDeletes = true;

  /**
   * Target search concurrency. This merge policy will avoid creating segments that have more than
   * {@code maxDoc / targetSearchConcurrency} documents.
   */
  protected int targetSearchConcurrency = 1;

  /** Sole constructor. (For invocation by subclass constructors, typically implicit.) */
  public LogMergePolicy() {
    super(DEFAULT_NO_CFS_RATIO, MergePolicy.DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }

  /**
   * Returns the number of segments that are merged at once and also controls the total number of
   * segments allowed to accumulate in the index.
   */
  public int getMergeFactor() {
    return mergeFactor;
  }

  /**
   * Determines how often segment indices are merged by addDocument(). With smaller values, less RAM
   * is used while indexing, and searches are faster, but indexing speed is slower. With larger
   * values, more RAM is used during indexing, and while searches is slower, indexing is faster.
   * Thus larger values ({@code > 10}) are best for batch index creation, and smaller values ({@code
   * < 10}) for indices that are interactively maintained.
   */
  public void setMergeFactor(int mergeFactor) {
    if (mergeFactor < 2) throw new IllegalArgumentException("mergeFactor cannot be less than 2");
    this.mergeFactor = mergeFactor;
  }

  /**
   * Sets whether the segment size should be calibrated by the number of deletes when choosing
   * segments for merge.
   */
  public void setCalibrateSizeByDeletes(boolean calibrateSizeByDeletes) {
    this.calibrateSizeByDeletes = calibrateSizeByDeletes;
  }

  /**
   * Returns true if the segment size should be calibrated by the number of deletes when choosing
   * segments for merge.
   */
  public boolean getCalibrateSizeByDeletes() {
    return calibrateSizeByDeletes;
  }

  /**
   * Sets the target search concurrency. This prevents creating segments that are bigger than
   * maxDoc/targetSearchConcurrency, which in turn makes the work parallelizable into
   * targetSearchConcurrency slices of similar doc counts.
   *
   * <p><b>NOTE:</b> Configuring a value greater than 1 will increase the number of segments in the
   * index linearly with the value of {@code targetSearchConcurrency} and also increase write
   * amplification.
   */
  public void setTargetSearchConcurrency(int targetSearchConcurrency) {
    if (targetSearchConcurrency < 1) {
      throw new IllegalArgumentException(
          "targetSearchConcurrency must be >= 1 (got " + targetSearchConcurrency + ")");
    }
    this.targetSearchConcurrency = targetSearchConcurrency;
  }

  /** Returns the target search concurrency. */
  public int getTargetSearchConcurrency() {
    return targetSearchConcurrency;
  }

  /**
   * Return the number of documents in the provided {@link SegmentCommitInfo}, pro-rated by
   * percentage of non-deleted documents if {@link #setCalibrateSizeByDeletes} is set.
   */
  protected long sizeDocs(SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    if (calibrateSizeByDeletes) {
      int delCount = mergeContext.numDeletesToMerge(info);
      assert assertDelCount(delCount, info);
      return (info.info.maxDoc() - (long) delCount);
    } else {
      return info.info.maxDoc();
    }
  }

  /**
   * Return the byte size of the provided {@link SegmentCommitInfo}, pro-rated by percentage of
   * non-deleted documents if {@link #setCalibrateSizeByDeletes} is set.
   */
  protected long sizeBytes(SegmentCommitInfo info, MergeContext mergeContext) throws IOException {
    if (calibrateSizeByDeletes) {
      return super.size(info, mergeContext);
    }
    return info.sizeInBytes();
  }

  /**
   * Returns true if the number of segments eligible for merging is less than or equal to the
   * specified {@code maxNumSegments}.
   */
  protected boolean isMerged(
      SegmentInfos infos,
      int maxNumSegments,
      Map<SegmentCommitInfo, Boolean> segmentsToMerge,
      MergeContext mergeContext)
      throws IOException {
    final int numSegments = infos.size();
    int numToMerge = 0;
    SegmentCommitInfo mergeInfo = null;
    boolean segmentIsOriginal = false;
    for (int i = 0; i < numSegments && numToMerge <= maxNumSegments; i++) {
      final SegmentCommitInfo info = infos.info(i);
      final Boolean isOriginal = segmentsToMerge.get(info);
      if (isOriginal != null) {
        segmentIsOriginal = isOriginal;
        numToMerge++;
        mergeInfo = info;
      }
    }

    return numToMerge <= maxNumSegments
        && (numToMerge != 1 || !segmentIsOriginal || isMerged(infos, mergeInfo, mergeContext));
  }

  @Override
  protected long maxFullFlushMergeSize() {
    return minMergeSize;
  }

  /**
   * Returns the merges necessary to merge the index, taking the max merge size or max merge docs
   * into consideration. This method attempts to respect the {@code maxNumSegments} parameter,
   * however it might be, due to size constraints, that more than that number of segments will
   * remain in the index. Also, this method does not guarantee that exactly {@code maxNumSegments}
   * will remain, but &lt;= that number.
   */
  private MergeSpecification findForcedMergesSizeLimit(
      SegmentInfos infos, int last, MergeContext mergeContext) throws IOException {
    MergeSpecification spec = new MergeSpecification();
    final List<SegmentCommitInfo> segments = infos.asList();

    int start = last - 1;
    while (start >= 0) {
      SegmentCommitInfo info = infos.info(start);
      if (size(info, mergeContext) > maxMergeSizeForForcedMerge
          || sizeDocs(info, mergeContext) > maxMergeDocs) {
        if (verbose(mergeContext)) {
          message(
              "findForcedMergesSizeLimit: skip segment="
                  + info
                  + ": size is > maxMergeSize ("
                  + maxMergeSizeForForcedMerge
                  + ") or sizeDocs is > maxMergeDocs ("
                  + maxMergeDocs
                  + ")",
              mergeContext);
        }
        // need to skip that segment + add a merge for the 'right' segments,
        // unless there is only 1 which is merged.
        if (last - start - 1 > 1
            || (start != last - 1 && !isMerged(infos, infos.info(start + 1), mergeContext))) {
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
    if (last > 0 && (++start + 1 < last || !isMerged(infos, infos.info(start), mergeContext))) {
      spec.add(new OneMerge(segments.subList(start, last)));
    }

    return spec.merges.size() == 0 ? null : spec;
  }

  /**
   * Returns the merges necessary to forceMerge the index. This method constraints the returned
   * merges only by the {@code maxNumSegments} parameter, and guaranteed that exactly that number of
   * segments will remain in the index.
   */
  private MergeSpecification findForcedMergesMaxNumSegments(
      SegmentInfos infos, int maxNumSegments, int last, MergeContext mergeContext)
      throws IOException {
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
        if (last > 1 || !isMerged(infos, infos.info(0), mergeContext)) {
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

        for (int i = 0; i < last - finalMergeSize + 1; i++) {
          long sumSize = 0;
          for (int j = 0; j < finalMergeSize; j++) {
            sumSize += size(infos.info(j + i), mergeContext);
          }
          if (i == 0
              || (sumSize < 2 * size(infos.info(i - 1), mergeContext) && sumSize < bestSize)) {
            bestStart = i;
            bestSize = sumSize;
          }
        }

        spec.add(new OneMerge(segments.subList(bestStart, bestStart + finalMergeSize)));
      }
    }
    return spec.merges.size() == 0 ? null : spec;
  }

  /**
   * Returns the merges necessary to merge the index down to a specified number of segments. This
   * respects the {@link #maxMergeSizeForForcedMerge} setting. By default, and assuming {@code
   * maxNumSegments=1}, only one segment will be left in the index, where that segment has no
   * deletions pending nor separate norms, and it is in compound file format if the current
   * useCompoundFile setting is true. This method returns multiple merges (mergeFactor at a time) so
   * the {@link MergeScheduler} in use may make use of concurrency.
   */
  @Override
  public MergeSpecification findForcedMerges(
      SegmentInfos infos,
      int maxNumSegments,
      Map<SegmentCommitInfo, Boolean> segmentsToMerge,
      MergeContext mergeContext)
      throws IOException {

    assert maxNumSegments > 0;
    if (verbose(mergeContext)) {
      message(
          "findForcedMerges: maxNumSegs=" + maxNumSegments + " segsToMerge=" + segmentsToMerge,
          mergeContext);
    }

    // If the segments are already merged (e.g. there's only 1 segment), or
    // there are <maxNumSegments:.
    if (isMerged(infos, maxNumSegments, segmentsToMerge, mergeContext)) {
      if (verbose(mergeContext)) {
        message("already merged; skip", mergeContext);
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
      if (verbose(mergeContext)) {
        message("last == 0; skip", mergeContext);
      }
      return null;
    }

    // There is only one segment already, and it is merged
    if (maxNumSegments == 1 && last == 1 && isMerged(infos, infos.info(0), mergeContext)) {
      if (verbose(mergeContext)) {
        message("already 1 seg; skip", mergeContext);
      }
      return null;
    }

    // Check if there are any segments above the threshold
    boolean anyTooLarge = false;
    for (int i = 0; i < last; i++) {
      SegmentCommitInfo info = infos.info(i);
      if (size(info, mergeContext) > maxMergeSizeForForcedMerge
          || sizeDocs(info, mergeContext) > maxMergeDocs) {
        anyTooLarge = true;
        break;
      }
    }

    if (anyTooLarge) {
      return findForcedMergesSizeLimit(infos, last, mergeContext);
    } else {
      return findForcedMergesMaxNumSegments(infos, maxNumSegments, last, mergeContext);
    }
  }

  /**
   * Finds merges necessary to force-merge all deletes from the index. We simply merge adjacent
   * segments that have deletes, up to mergeFactor at a time.
   */
  @Override
  public MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
    final List<SegmentCommitInfo> segments = segmentInfos.asList();
    final int numSegments = segments.size();

    if (verbose(mergeContext)) {
      message("findForcedDeleteMerges: " + numSegments + " segments", mergeContext);
    }

    MergeSpecification spec = new MergeSpecification();
    int firstSegmentWithDeletions = -1;
    assert mergeContext != null;
    for (int i = 0; i < numSegments; i++) {
      final SegmentCommitInfo info = segmentInfos.info(i);
      int delCount = mergeContext.numDeletesToMerge(info);
      assert assertDelCount(delCount, info);
      if (delCount > 0) {
        if (verbose(mergeContext)) {
          message("  segment " + info.info.name + " has deletions", mergeContext);
        }
        if (firstSegmentWithDeletions == -1) firstSegmentWithDeletions = i;
        else if (i - firstSegmentWithDeletions == mergeFactor) {
          // We've seen mergeFactor segments in a row with
          // deletions, so force a merge now:
          if (verbose(mergeContext)) {
            message(
                "  add merge " + firstSegmentWithDeletions + " to " + (i - 1) + " inclusive",
                mergeContext);
          }
          spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, i)));
          firstSegmentWithDeletions = i;
        }
      } else if (firstSegmentWithDeletions != -1) {
        // End of a sequence of segments with deletions, so,
        // merge those past segments even if it's fewer than
        // mergeFactor segments
        if (verbose(mergeContext)) {
          message(
              "  add merge " + firstSegmentWithDeletions + " to " + (i - 1) + " inclusive",
              mergeContext);
        }
        spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, i)));
        firstSegmentWithDeletions = -1;
      }
    }

    if (firstSegmentWithDeletions != -1) {
      if (verbose(mergeContext)) {
        message(
            "  add merge " + firstSegmentWithDeletions + " to " + (numSegments - 1) + " inclusive",
            mergeContext);
      }
      spec.add(new OneMerge(segments.subList(firstSegmentWithDeletions, numSegments)));
    }

    return spec;
  }

  private static class SegmentInfoAndLevel implements Comparable<SegmentInfoAndLevel> {
    final SegmentCommitInfo info;
    final float level;

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

  /**
   * Checks if any merges are now necessary and returns a {@link MergePolicy.MergeSpecification} if
   * so. A merge is necessary when there are more than {@link #setMergeFactor} segments at a given
   * level. When multiple levels have too many segments, this method will return multiple merges,
   * allowing the {@link MergeScheduler} to use concurrency.
   */
  @Override
  public MergeSpecification findMerges(
      MergeTrigger mergeTrigger, SegmentInfos infos, MergeContext mergeContext) throws IOException {

    final int numSegments = infos.size();
    if (verbose(mergeContext)) {
      message("findMerges: " + numSegments + " segments", mergeContext);
    }

    // Compute levels, which is just log (base mergeFactor)
    // of the size of each segment
    final List<SegmentInfoAndLevel> levels = new ArrayList<>(numSegments);
    final float norm = (float) Math.log(mergeFactor);

    final Set<SegmentCommitInfo> mergingSegments = mergeContext.getMergingSegments();

    int totalDocCount = 0;
    for (int i = 0; i < numSegments; i++) {
      final SegmentCommitInfo info = infos.info(i);
      totalDocCount += sizeDocs(info, mergeContext);
      long size = size(info, mergeContext);

      // Floor tiny segments
      if (size < 1) {
        size = 1;
      }

      final SegmentInfoAndLevel infoLevel =
          new SegmentInfoAndLevel(info, (float) Math.log((double) size) / norm);
      levels.add(infoLevel);

      if (verbose(mergeContext)) {
        final long segBytes = sizeBytes(info, mergeContext);
        String extra = mergingSegments.contains(info) ? " [merging]" : "";
        if (size >= maxMergeSize) {
          extra += " [skip: too large]";
        }
        message(
            "seg="
                + segString(mergeContext, Collections.singleton(info))
                + " level="
                + infoLevel.level
                + " size="
                + String.format(Locale.ROOT, "%.3f MB", segBytes / 1024. / 1024.)
                + extra,
            mergeContext);
      }
    }

    final float levelFloor;
    if (minMergeSize <= 0) levelFloor = (float) 0.0;
    else levelFloor = (float) (Math.log((double) minMergeSize) / norm);

    // Now, we quantize the log values into levels.  The
    // first level is any segment whose log size is within
    // LEVEL_LOG_SPAN of the max size, or, who has such as
    // segment "to the right".  Then, we find the max of all
    // other segments and use that to define the next level
    // segment, etc.

    MergeSpecification spec = null;

    final int numMergeableSegments = levels.size();

    // precompute the max level on the right side.
    // arr size is numMergeableSegments + 1 to handle the case
    // when numMergeableSegments is 0.
    float[] maxLevels = new float[numMergeableSegments + 1];
    // -1 is definitely the minimum value, because Math.log(1) is 0.
    maxLevels[numMergeableSegments] = -1.0f;
    for (int i = numMergeableSegments - 1; i >= 0; i--) {
      maxLevels[i] = Math.max(levels.get(i).level, maxLevels[i + 1]);
    }

    int start = 0;
    while (start < numMergeableSegments) {

      // Find max level of all segments not already
      // quantized.
      float maxLevel = maxLevels[start];

      // Now search backwards for the rightmost segment that
      // falls into this level:
      float levelBottom;
      if (maxLevel > levelFloor) {
        // With a merge factor of 10, this means that the biggest segment and the smallest segment
        // that take part of a merge have a size difference of at most 5.6x.
        levelBottom = (float) (maxLevel - LEVEL_LOG_SPAN);
      } else {
        // For segments below the floor size, we allow more unbalanced merges, but still somewhat
        // balanced to avoid running into O(n^2) merging.
        // With a merge factor of 10, this means that the biggest segment and the smallest segment
        // that take part of a merge have a size difference of at most 31.6x.
        levelBottom = (float) (maxLevel - 2 * LEVEL_LOG_SPAN);
      }

      int upto = numMergeableSegments - 1;
      while (upto >= start) {
        if (levels.get(upto).level >= levelBottom) {
          break;
        }
        upto--;
      }
      if (verbose(mergeContext)) {
        message(
            "  level " + levelBottom + " to " + maxLevel + ": " + (1 + upto - start) + " segments",
            mergeContext);
      }

      final int maxMergeDocs =
          Math.min(
              this.maxMergeDocs,
              (totalDocCount + targetSearchConcurrency - 1) / targetSearchConcurrency);

      // Finally, record all merges that are viable at this level:
      int end = start + mergeFactor;
      while (end <= 1 + upto) {
        boolean anyMerging = false;
        long mergeSize = 0;
        long mergeDocs = 0;
        for (int i = start; i < end; i++) {
          final SegmentInfoAndLevel segLevel = levels.get(i);
          final SegmentCommitInfo info = segLevel.info;
          if (mergingSegments.contains(info)) {
            anyMerging = true;
            break;
          }
          long segmentSize = size(info, mergeContext);
          long segmentDocs = sizeDocs(info, mergeContext);
          if (mergeSize + segmentSize > maxMergeSize || mergeDocs + segmentDocs > maxMergeDocs) {
            // This merge is full, stop adding more segments to it
            if (i == start) {
              // This segment alone is too large, return a singleton merge
              if (verbose(mergeContext)) {
                message(
                    "    " + i + " is larger than the max merge size/docs; ignoring", mergeContext);
              }
              end = i + 1;
            } else {
              // Previous segments are under the max merge size, return them
              end = i;
            }
            break;
          }
          mergeSize += segmentSize;
          mergeDocs += segmentDocs;
        }

        if (anyMerging || end - start <= 1) {
          // skip: there is an ongoing merge at the current level or the computed merge has a single
          // segment and this merge policy doesn't do singleton merges
        } else {
          if (spec == null) {
            spec = new MergeSpecification();
          }
          final List<SegmentCommitInfo> mergeInfos = new ArrayList<>(end - start);
          for (int i = start; i < end; i++) {
            mergeInfos.add(levels.get(i).info);
            assert infos.contains(levels.get(i).info);
          }
          if (verbose(mergeContext)) {
            message(
                "  add merge="
                    + segString(mergeContext, mergeInfos)
                    + " start="
                    + start
                    + " end="
                    + end,
                mergeContext);
          }
          spec.add(new OneMerge(mergeInfos));
        }

        start = end;
        end = start + mergeFactor;
      }

      start = 1 + upto;
    }

    return spec;
  }

  /**
   * Determines the largest segment (measured by document count) that may be merged with other
   * segments. Small values (e.g., less than 10,000) are best for interactive indexing, as this
   * limits the length of pauses while indexing to a few seconds. Larger values are best for batched
   * indexing and speedier searches.
   *
   * <p>The default value is {@link Integer#MAX_VALUE}.
   *
   * <p>The default merge policy ({@link LogByteSizeMergePolicy}) also allows you to set this limit
   * by net size (in MB) of the segment, using {@link LogByteSizeMergePolicy#setMaxMergeMB}.
   */
  public void setMaxMergeDocs(int maxMergeDocs) {
    this.maxMergeDocs = maxMergeDocs;
  }

  /**
   * Returns the largest segment (measured by document count) that may be merged with other
   * segments.
   *
   * @see #setMaxMergeDocs
   */
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
