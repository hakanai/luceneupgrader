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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.MutablePointTree;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PointsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IORunnable;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.bkd.BKDConfig;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.bkd.BKDWriter;

/** Writes dimensional values */
public class Lucene90PointsWriter extends PointsWriter {

  /** Outputs used to write the BKD tree data files. */
  protected final IndexOutput metaOut, indexOut, dataOut;

  final SegmentWriteState writeState;
  final int maxPointsInLeafNode;
  final double maxMBSortInHeap;
  private boolean finished;

  /** Full constructor */
  public Lucene90PointsWriter(
      SegmentWriteState writeState, int maxPointsInLeafNode, double maxMBSortInHeap)
      throws IOException {
    assert writeState.fieldInfos.hasPointValues();
    this.writeState = writeState;
    this.maxPointsInLeafNode = maxPointsInLeafNode;
    this.maxMBSortInHeap = maxMBSortInHeap;
    String dataFileName =
        IndexFileNames.segmentFileName(
            writeState.segmentInfo.name,
            writeState.segmentSuffix,
            Lucene90PointsFormat.DATA_EXTENSION);
    dataOut = writeState.directory.createOutput(dataFileName, writeState.context);
    boolean success = false;
    try {
      CodecUtil.writeIndexHeader(
          dataOut,
          Lucene90PointsFormat.DATA_CODEC_NAME,
          Lucene90PointsFormat.VERSION_CURRENT,
          writeState.segmentInfo.getId(),
          writeState.segmentSuffix);

      String metaFileName =
          IndexFileNames.segmentFileName(
              writeState.segmentInfo.name,
              writeState.segmentSuffix,
              Lucene90PointsFormat.META_EXTENSION);
      metaOut = writeState.directory.createOutput(metaFileName, writeState.context);
      CodecUtil.writeIndexHeader(
          metaOut,
          Lucene90PointsFormat.META_CODEC_NAME,
          Lucene90PointsFormat.VERSION_CURRENT,
          writeState.segmentInfo.getId(),
          writeState.segmentSuffix);

      String indexFileName =
          IndexFileNames.segmentFileName(
              writeState.segmentInfo.name,
              writeState.segmentSuffix,
              Lucene90PointsFormat.INDEX_EXTENSION);
      indexOut = writeState.directory.createOutput(indexFileName, writeState.context);
      CodecUtil.writeIndexHeader(
          indexOut,
          Lucene90PointsFormat.INDEX_CODEC_NAME,
          Lucene90PointsFormat.VERSION_CURRENT,
          writeState.segmentInfo.getId(),
          writeState.segmentSuffix);

      success = true;
    } finally {
      if (success == false) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
  }

  /**
   * Uses the defaults values for {@code maxPointsInLeafNode} (512) and {@code maxMBSortInHeap}
   * (16.0)
   */
  public Lucene90PointsWriter(SegmentWriteState writeState) throws IOException {
    this(
        writeState,
        BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE,
        BKDWriter.DEFAULT_MAX_MB_SORT_IN_HEAP);
  }

  @Override
  public void writeField(FieldInfo fieldInfo, PointsReader reader) throws IOException {

    PointValues.PointTree values = reader.getValues(fieldInfo.name).getPointTree();

    BKDConfig config =
        new BKDConfig(
            fieldInfo.getPointDimensionCount(),
            fieldInfo.getPointIndexDimensionCount(),
            fieldInfo.getPointNumBytes(),
            maxPointsInLeafNode);

    try (BKDWriter writer =
        new BKDWriter(
            writeState.segmentInfo.maxDoc(),
            writeState.directory,
            writeState.segmentInfo.name,
            config,
            maxMBSortInHeap,
            values.size())) {

      if (values instanceof MutablePointTree) {
        IORunnable finalizer =
            writer.writeField(
                metaOut, indexOut, dataOut, fieldInfo.name, (MutablePointTree) values);
        if (finalizer != null) {
          metaOut.writeInt(fieldInfo.number);
          finalizer.run();
        }
        return;
      }

      values.visitDocValues(
          new IntersectVisitor() {
            @Override
            public void visit(int docID) {
              throw new IllegalStateException();
            }

            @Override
            public void visit(int docID, byte[] packedValue) throws IOException {
              writer.add(packedValue, docID);
            }

            @Override
            public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              return Relation.CELL_CROSSES_QUERY;
            }
          });

      // We could have 0 points on merge since all docs with dimensional fields may be deleted:
      IORunnable finalizer = writer.finish(metaOut, indexOut, dataOut);
      if (finalizer != null) {
        metaOut.writeInt(fieldInfo.number);
        finalizer.run();
      }
    }
  }

  @Override
  public void merge(MergeState mergeState) throws IOException {
    /**
     * If indexSort is activated and some of the leaves are not sorted the next test will catch that
     * and the non-optimized merge will run. If the readers are all sorted then it's safe to perform
     * a bulk merge of the points.
     */
    for (PointsReader reader : mergeState.pointsReaders) {
      if (reader instanceof Lucene90PointsReader == false) {
        // We can only bulk merge when all to-be-merged segments use our format:
        super.merge(mergeState);
        return;
      }
    }
    for (PointsReader reader : mergeState.pointsReaders) {
      if (reader != null) {
        reader.checkIntegrity();
      }
    }

    for (FieldInfo fieldInfo : mergeState.mergeFieldInfos) {
      if (fieldInfo.getPointDimensionCount() != 0) {
        if (fieldInfo.getPointDimensionCount() == 1) {

          // Worst case total maximum size (if none of the points are deleted):
          long totMaxSize = 0;
          for (int i = 0; i < mergeState.pointsReaders.length; i++) {
            PointsReader reader = mergeState.pointsReaders[i];
            if (reader != null) {
              FieldInfos readerFieldInfos = mergeState.fieldInfos[i];
              FieldInfo readerFieldInfo = readerFieldInfos.fieldInfo(fieldInfo.name);
              if (readerFieldInfo != null && readerFieldInfo.getPointDimensionCount() > 0) {
                PointValues values = reader.getValues(fieldInfo.name);
                if (values != null) {
                  totMaxSize += values.size();
                }
              }
            }
          }

          BKDConfig config =
              new BKDConfig(
                  fieldInfo.getPointDimensionCount(),
                  fieldInfo.getPointIndexDimensionCount(),
                  fieldInfo.getPointNumBytes(),
                  maxPointsInLeafNode);

          // System.out.println("MERGE: field=" + fieldInfo.name);
          // Optimize the 1D case to use BKDWriter.merge, which does a single merge sort of the
          // already sorted incoming segments, instead of trying to sort all points again as if
          // we were simply reindexing them:
          try (BKDWriter writer =
              new BKDWriter(
                  writeState.segmentInfo.maxDoc(),
                  writeState.directory,
                  writeState.segmentInfo.name,
                  config,
                  maxMBSortInHeap,
                  totMaxSize)) {
            List<PointValues> pointValues = new ArrayList<>();
            List<MergeState.DocMap> docMaps = new ArrayList<>();
            for (int i = 0; i < mergeState.pointsReaders.length; i++) {
              PointsReader reader = mergeState.pointsReaders[i];

              if (reader != null) {

                // we confirmed this up above
                assert reader instanceof Lucene90PointsReader;
                Lucene90PointsReader reader90 = (Lucene90PointsReader) reader;

                // NOTE: we cannot just use the merged fieldInfo.number (instead of resolving to
                // this
                // reader's FieldInfo as we do below) because field numbers can easily be different
                // when addIndexes(Directory...) copies over segments from another index:

                FieldInfos readerFieldInfos = mergeState.fieldInfos[i];
                FieldInfo readerFieldInfo = readerFieldInfos.fieldInfo(fieldInfo.name);
                if (readerFieldInfo != null && readerFieldInfo.getPointDimensionCount() > 0) {
                  PointValues aPointValues = reader90.getValues(readerFieldInfo.name);
                  if (aPointValues != null) {
                    pointValues.add(aPointValues);
                    docMaps.add(mergeState.docMaps[i]);
                  }
                }
              }
            }

            IORunnable finalizer = writer.merge(metaOut, indexOut, dataOut, docMaps, pointValues);
            if (finalizer != null) {
              metaOut.writeInt(fieldInfo.number);
              finalizer.run();
            }
          }
        } else {
          mergeOneField(mergeState, fieldInfo);
        }
      }
    }

    finish();
  }

  @Override
  public void finish() throws IOException {
    if (finished) {
      throw new IllegalStateException("already finished");
    }
    finished = true;
    metaOut.writeInt(-1);
    CodecUtil.writeFooter(indexOut);
    CodecUtil.writeFooter(dataOut);
    metaOut.writeLong(indexOut.getFilePointer());
    metaOut.writeLong(dataOut.getFilePointer());
    CodecUtil.writeFooter(metaOut);
  }

  @Override
  public void close() throws IOException {
    IOUtils.close(metaOut, indexOut, dataOut);
  }
}
