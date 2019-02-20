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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.util.List;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedLongValues;


public class MergeState {

  public static abstract class DocMap {

    DocMap() {}

    public abstract int get(int docID);

    public abstract int maxDoc();

    public final int numDocs() {
      return maxDoc() - numDeletedDocs();
    }

    public abstract int numDeletedDocs();

    public boolean hasDeletions() {
      return numDeletedDocs() > 0;
    }

    public static DocMap build(AtomicReader reader) {
      final int maxDoc = reader.maxDoc();
      if (!reader.hasDeletions()) {
        return new NoDelDocMap(maxDoc);
      }
      final Bits liveDocs = reader.getLiveDocs();
      return build(maxDoc, liveDocs);
    }

    static DocMap build(final int maxDoc, final Bits liveDocs) {
      assert liveDocs != null;
      final PackedLongValues.Builder docMapBuilder = PackedLongValues.monotonicBuilder(PackedInts.COMPACT);
      int del = 0;
      for (int i = 0; i < maxDoc; ++i) {
        docMapBuilder.add(i - del);
        if (!liveDocs.get(i)) {
          ++del;
        }
      }
      final PackedLongValues docMap = docMapBuilder.build();
      final int numDeletedDocs = del;
      assert docMap.size() == maxDoc;
      return new DocMap() {

        @Override
        public int get(int docID) {
          if (!liveDocs.get(docID)) {
            return -1;
          }
          return (int) docMap.get(docID);
        }

        @Override
        public int maxDoc() {
          return maxDoc;
        }

        @Override
        public int numDeletedDocs() {
          return numDeletedDocs;
        }

      };
    }

  }

  private static final class NoDelDocMap extends DocMap {

    private final int maxDoc;

    NoDelDocMap(int maxDoc) {
      this.maxDoc = maxDoc;
    }

    @Override
    public int get(int docID) {
      return docID;
    }

    @Override
    public int maxDoc() {
      return maxDoc;
    }

    @Override
    public int numDeletedDocs() {
      return 0;
    }
  }

  public final SegmentInfo segmentInfo;

  public FieldInfos fieldInfos;

  public final List<AtomicReader> readers;

  public DocMap[] docMaps;

  public int[] docBase;

  public final CheckAbort checkAbort;

  public final InfoStream infoStream;

  // TODO: get rid of this? it tells you which segments are 'aligned' (e.g. for bulk merging)
  // but is this really so expensive to compute again in different components, versus once in SM?


  public SegmentReader[] matchingSegmentReaders;

  public int matchedCount;

  MergeState(List<AtomicReader> readers, SegmentInfo segmentInfo, InfoStream infoStream, CheckAbort checkAbort) {
    this.readers = readers;
    this.segmentInfo = segmentInfo;
    this.infoStream = infoStream;
    this.checkAbort = checkAbort;
  }

  public static class CheckAbort {
    private double workCount;
    private final MergePolicy.OneMerge merge;
    private final Directory dir;

    public CheckAbort(MergePolicy.OneMerge merge, Directory dir) {
      this.merge = merge;
      this.dir = dir;
    }

    public void work(double units) throws MergePolicy.MergeAbortedException {
      workCount += units;
      if (workCount >= 10000.0) {
        merge.checkAborted(dir);
        workCount = 0;
      }
    }

    static final MergeState.CheckAbort NONE = new MergeState.CheckAbort(null, null) {
      @Override
      public void work(double units) {
        // do nothing
      }
    };
  }
}
