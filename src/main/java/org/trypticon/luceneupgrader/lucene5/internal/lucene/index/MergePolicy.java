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


import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.MergeInfo;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.RateLimiter;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.FixedBitSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class MergePolicy {

  public static abstract class DocMap {
    protected DocMap() {}

    public abstract int map(int old);

    boolean isConsistent(int maxDoc) {
      final FixedBitSet targets = new FixedBitSet(maxDoc);
      for (int i = 0; i < maxDoc; ++i) {
        final int target = map(i);
        if (target < 0 || target >= maxDoc) {
          assert false : "out of range: " + target + " not in [0-" + maxDoc + "[";
          return false;
        } else if (targets.get(target)) {
          assert false : target + " is already taken (" + i + ")";
          return false;
        }
      }
      return true;
    }
  }


  public static class OneMerge {

    SegmentCommitInfo info;         // used by IndexWriter
    boolean registerDone;           // used by IndexWriter
    long mergeGen;                  // used by IndexWriter
    boolean isExternal;             // used by IndexWriter
    int maxNumSegments = -1;        // used by IndexWriter

    public volatile long estimatedMergeBytes;       // used by IndexWriter

    // Sum of sizeInBytes of all SegmentInfos; set by IW.mergeInit
    volatile long totalMergeBytes;

    List<SegmentReader> readers;        // used by IndexWriter

    public final List<SegmentCommitInfo> segments;

    public final MergeRateLimiter rateLimiter;

    volatile long mergeStartNS = -1;

    public final int totalMaxDoc;
    Throwable error;


    public OneMerge(List<SegmentCommitInfo> segments) {
      if (0 == segments.size()) {
        throw new RuntimeException("segments must include at least one segment");
      }
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = new ArrayList<>(segments);
      int count = 0;
      for(SegmentCommitInfo info : segments) {
        count += info.info.maxDoc();
      }
      totalMaxDoc = count;

      rateLimiter = new MergeRateLimiter(this);
    }

    public void mergeFinished() throws IOException {
    }


    public List<CodecReader> getMergeReaders() throws IOException {
      if (readers == null) {
        throw new IllegalStateException("IndexWriter has not initialized readers from the segment infos yet");
      }
      final List<CodecReader> readers = new ArrayList<>(this.readers.size());
      for (SegmentReader reader : this.readers) {
        if (reader.numDocs() > 0) {
          readers.add(reader);
        }
      }
      return Collections.unmodifiableList(readers);
    }
    
    public void setMergeInfo(SegmentCommitInfo info) {
      this.info = info;
    }

    public SegmentCommitInfo getMergeInfo() {
      return info;
    }


    public DocMap getDocMap(MergeState mergeState) {
      return new DocMap() {
        @Override
        public int map(int docID) {
          return docID;
        }
      };
    }

    synchronized void setException(Throwable error) {
      this.error = error;
    }

    synchronized Throwable getException() {
      return error;
    }

    public String segString() {
      StringBuilder b = new StringBuilder();
      final int numSegments = segments.size();
      for(int i=0;i<numSegments;i++) {
        if (i > 0) {
          b.append(' ');
        }
        b.append(segments.get(i).toString());
      }
      if (info != null) {
        b.append(" into ").append(info.info.name);
      }
      if (maxNumSegments != -1) {
        b.append(" [maxNumSegments=" + maxNumSegments + "]");
      }
      if (rateLimiter.getAbort()) {
        b.append(" [ABORTED]");
      }
      return b.toString();
    }
    
    public long totalBytesSize() throws IOException {
      return totalMergeBytes;
    }


    public int totalNumDocs() throws IOException {
      int total = 0;
      for (SegmentCommitInfo info : segments) {
        total += info.info.maxDoc();
      }
      return total;
    }

    public MergeInfo getStoreMergeInfo() {
      return new MergeInfo(totalMaxDoc, estimatedMergeBytes, isExternal, maxNumSegments);
    }    
  }

  public static class MergeSpecification {

    public final List<OneMerge> merges = new ArrayList<>();

    public MergeSpecification() {
    }

    public void add(OneMerge merge) {
      merges.add(merge);
    }

    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      b.append("MergeSpec:\n");
      final int count = merges.size();
      for(int i=0;i<count;i++) {
        b.append("  ").append(1 + i).append(": ").append(merges.get(i).segString());
      }
      return b.toString();
    }
  }

  public static class MergeException extends RuntimeException {
    private Directory dir;

    public MergeException(String message, Directory dir) {
      super(message);
      this.dir = dir;
    }

    public MergeException(Throwable exc, Directory dir) {
      super(exc);
      this.dir = dir;
    }

    public Directory getDirectory() {
      return dir;
    }
  }


  public static class MergeAbortedException extends IOException {
    public MergeAbortedException() {
      super("merge is aborted");
    }

    public MergeAbortedException(String message) {
      super(message);
    }
  }
  
  protected static final double DEFAULT_NO_CFS_RATIO = 1.0;

  protected static final long DEFAULT_MAX_CFS_SEGMENT_SIZE = Long.MAX_VALUE;


  protected double noCFSRatio = DEFAULT_NO_CFS_RATIO;
  
  protected long maxCFSSegmentSize = DEFAULT_MAX_CFS_SEGMENT_SIZE;

  public MergePolicy() {
    this(DEFAULT_NO_CFS_RATIO, DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }
  
  protected MergePolicy(double defaultNoCFSRatio, long defaultMaxCFSSegmentSize) {
    this.noCFSRatio = defaultNoCFSRatio;
    this.maxCFSSegmentSize = defaultMaxCFSSegmentSize;
  }

  public abstract MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos segmentInfos, IndexWriter writer)
      throws IOException;

  public abstract MergeSpecification findForcedMerges(
          SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge, IndexWriter writer)
      throws IOException;

  public abstract MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, IndexWriter writer) throws IOException;

  public boolean useCompoundFile(SegmentInfos infos, SegmentCommitInfo mergedInfo, IndexWriter writer) throws IOException {
    if (getNoCFSRatio() == 0.0) {
      return false;
    }
    long mergedInfoSize = size(mergedInfo, writer);
    if (mergedInfoSize > maxCFSSegmentSize) {
      return false;
    }
    if (getNoCFSRatio() >= 1.0) {
      return true;
    }
    long totalSize = 0;
    for (SegmentCommitInfo info : infos) {
      totalSize += size(info, writer);
    }
    return mergedInfoSize <= getNoCFSRatio() * totalSize;
  }
  

  protected long size(SegmentCommitInfo info, IndexWriter writer) throws IOException {
    long byteSize = info.sizeInBytes();
    int delCount = writer.numDeletedDocs(info);
    double delRatio = info.info.maxDoc() <= 0 ? 0.0f : (float) delCount / (float) info.info.maxDoc();
    assert delRatio <= 1.0;
    return (info.info.maxDoc() <= 0 ? byteSize : (long) (byteSize * (1.0 - delRatio)));
  }
  

  protected final boolean isMerged(SegmentInfos infos, SegmentCommitInfo info, IndexWriter writer) throws IOException {
    assert writer != null;
    boolean hasDeletions = writer.numDeletedDocs(info) > 0;
    return !hasDeletions &&
      info.info.dir == writer.getDirectory() &&
      useCompoundFile(infos, info, writer) == info.info.getUseCompoundFile();
  }
  

  public double getNoCFSRatio() {
    return noCFSRatio;
  }


  public void setNoCFSRatio(double noCFSRatio) {
    if (noCFSRatio < 0.0 || noCFSRatio > 1.0) {
      throw new IllegalArgumentException("noCFSRatio must be 0.0 to 1.0 inclusive; got " + noCFSRatio);
    }
    this.noCFSRatio = noCFSRatio;
  }

  public final double getMaxCFSSegmentSizeMB() {
    return maxCFSSegmentSize/1024/1024.;
  }


  public void setMaxCFSSegmentSizeMB(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("maxCFSSegmentSizeMB must be >=0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    this.maxCFSSegmentSize = v > Long.MAX_VALUE ? Long.MAX_VALUE : (long) v;
  }
}
