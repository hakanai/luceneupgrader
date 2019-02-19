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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.SetOnce;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.SetOnce.AlreadySetException;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public abstract class MergePolicy implements java.io.Closeable {



  public static class OneMerge {

    SegmentInfo info;               // used by IndexWriter
    boolean registerDone;           // used by IndexWriter
    long mergeGen;                  // used by IndexWriter
    boolean isExternal;             // used by IndexWriter
    int maxNumSegments = -1;        // used by IndexWriter
    public long estimatedMergeBytes;       // used by IndexWriter
    List<SegmentReader> readers;        // used by IndexWriter
    List<SegmentReader> readerClones;   // used by IndexWriter
    public final List<SegmentInfo> segments;
    public final int totalDocCount;
    boolean aborted;
    Throwable error;
    boolean paused;

    public OneMerge(List<SegmentInfo> segments) {
      if (0 == segments.size())
        throw new RuntimeException("segments must include at least one segment");
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = new ArrayList<SegmentInfo>(segments);
      int count = 0;
      for(SegmentInfo info : segments) {
        count += info.docCount;
      }
      totalDocCount = count;
    }

    synchronized void setException(Throwable error) {
      this.error = error;
    }

    synchronized Throwable getException() {
      return error;
    }


    synchronized void abort() {
      aborted = true;
      notifyAll();
    }

    synchronized boolean isAborted() {
      return aborted;
    }

    synchronized void checkAborted(Directory dir) throws MergeAbortedException {
      if (aborted) {
        throw new MergeAbortedException("merge is aborted: " + segString(dir));
      }

      while (paused) {
        try {
          // In theory we could wait() indefinitely, but we
          // do 1000 msec, defensively
          wait(1000);
        } catch (InterruptedException ie) {
          throw new RuntimeException(ie);
        }
        if (aborted) {
          throw new MergeAbortedException("merge is aborted: " + segString(dir));
        }
      }
    }

    synchronized public void setPause(boolean paused) {
      this.paused = paused;
      if (!paused) {
        // Wakeup merge thread, if it's waiting
        notifyAll();
      }
    }

    synchronized public boolean getPause() {
      return paused;
    }

    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      final int numSegments = segments.size();
      for(int i=0;i<numSegments;i++) {
        if (i > 0) b.append(' ');
        b.append(segments.get(i).toString(dir, 0));
      }
      if (info != null)
        b.append(" into ").append(info.name);
      if (maxNumSegments != -1)
        b.append(" [maxNumSegments=" + maxNumSegments + "]");
      if (aborted) {
        b.append(" [ABORTED]");
      }
      return b.toString();
    }
    

    public long totalBytesSize() throws IOException {
      long total = 0;
      for (SegmentInfo info : segments) {
        total += info.sizeInBytes(true);
      }
      return total;
    }


    public int totalNumDocs() throws IOException {
      int total = 0;
      for (SegmentInfo info : segments) {
        total += info.docCount;
      }
      return total;
    }
  }

  public static class MergeSpecification {

    public final List<OneMerge> merges = new ArrayList<OneMerge>();

    public void add(OneMerge merge) {
      merges.add(merge);
    }

    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      b.append("MergeSpec:\n");
      final int count = merges.size();
      for(int i=0;i<count;i++)
        b.append("  ").append(1 + i).append(": ").append(merges.get(i).segString(dir));
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

  protected final SetOnce<IndexWriter> writer;

  public MergePolicy() {
    writer = new SetOnce<IndexWriter>();
  }

  public void setIndexWriter(IndexWriter writer) {
    this.writer.set(writer);
  }
  
  public abstract MergeSpecification findMerges(SegmentInfos segmentInfos)
      throws CorruptIndexException, IOException;

  public abstract MergeSpecification findForcedMerges(
          SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentInfo,Boolean> segmentsToMerge)
      throws CorruptIndexException, IOException;

  public abstract MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos) throws CorruptIndexException, IOException;

  public abstract void close();

  public abstract boolean useCompoundFile(SegmentInfos segments, SegmentInfo newSegment) throws IOException;
}
