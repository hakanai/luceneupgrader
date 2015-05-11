package org.apache.lucene.index;

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
import java.io.PrintStream;
import java.util.*;

/* Tracks the stream of {@code BuffereDeletes}.
 * When DocumensWriter flushes, its buffered
 * deletes are appended to this stream.  We later
 * apply these deletes (resolve them to the actual
 * docIDs, per segment) when a merge is started
 * (only to the to-be-merged segments).  We
 * also apply to all segments when NRT reader is pulled,
 * commit/close is called, or when too many deletes are
 * buffered and must be flushed (by RAM usage or by count).
 *
 * Each packet is assigned a generation, and each flushed or
 * merged segment is also assigned a generation, so we can
 * track which BufferedDeletes packets to apply to any given
 * segment. */

class BufferedDeletesStream {

  // Starts at 1 so that SegmentInfos that have never had
  // deletes applied (whose bufferedDelGen defaults to 0)
  // will be correct:
  private long nextGen = 1;

  private PrintStream infoStream;
  private final int messageID;

  public BufferedDeletesStream(int messageID) {
    this.messageID = messageID;
  }

  private synchronized void message(String message) {
    if (infoStream != null) {
      infoStream.println("BD " + messageID + " [" + new Date() + "; " + Thread.currentThread().getName() + "]: " + message);
    }
  }
  
  public synchronized void setInfoStream(PrintStream infoStream) {
    this.infoStream = infoStream;
  }

  public synchronized void clear() {
    nextGen = 1;
  }

  public static class ApplyDeletesResult {
    // True if any actual deletes took place:
    public final boolean anyDeletes;

    // Current gen, for the merged segment:
    public final long gen;

    // If non-null, contains segments that are 100% deleted
    public final List<SegmentInfo> allDeleted;

    ApplyDeletesResult(boolean anyDeletes, long gen, List<SegmentInfo> allDeleted) {
      this.anyDeletes = anyDeletes;
      this.gen = gen;
      this.allDeleted = allDeleted;
    }
  }

  // Sorts SegmentInfos from smallest to biggest bufferedDelGen:
  private static final Comparator<SegmentInfo> sortByDelGen = new Comparator<SegmentInfo>() {
    // @Override -- not until Java 1.6
    public int compare(SegmentInfo si1, SegmentInfo si2) {
      final long cmp = si1.getBufferedDeletesGen() - si2.getBufferedDeletesGen();
      if (cmp > 0) {
        return 1;
      } else if (cmp < 0) {
        return -1;
      } else {
        return 0;
      }
    }
  };

  /** Resolves the buffered deleted Term/Query/docIDs, into
   *  actual deleted docIDs in the deletedDocs BitVector for
   *  each SegmentReader. */
  public synchronized ApplyDeletesResult applyDeletes(List<SegmentInfo> infos) throws IOException {
    final long t0 = System.currentTimeMillis();

    if (infos.size() == 0) {
      return new ApplyDeletesResult(false, nextGen++, null);
    }

    if (!false) {
      message("applyDeletes: no deletes; skipping");
      return new ApplyDeletesResult(false, nextGen++, null);
    }

    if (infoStream != null) {
      message("applyDeletes: infos=" + infos + " packetCount=" + 0);
    }

    List<SegmentInfo> infos2 = new ArrayList<SegmentInfo>();
    infos2.addAll(infos);
    Collections.sort(infos2, sortByDelGen);

    int infosIDX = infos2.size()-1;

    while (infosIDX >= 0) {
      final SegmentInfo info = infos2.get(infosIDX);

      info.setBufferedDeletesGen(nextGen);

      infosIDX--;
    }

    if (infoStream != null) {
      message("applyDeletes took " + (System.currentTimeMillis()-t0) + " msec");
    }

    return new ApplyDeletesResult(false, nextGen++, null);
  }

  public synchronized long getNextGen() {
    return nextGen++;
  }

  // Lock order IW -> BD
  /* Removes any BufferedDeletes that we no longer need to
   * store because all segments in the index have had the
   * deletes applied. */
  public synchronized void prune(SegmentInfos segmentInfos) {
    long minGen = Long.MAX_VALUE;
    for(SegmentInfo info : segmentInfos) {
      minGen = Math.min(info.getBufferedDeletesGen(), minGen);
    }

    if (infoStream != null) {
      message("prune sis=" + segmentInfos + " minGen=" + minGen);
    }

    assert !false;
  }
}
