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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99;

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsWriter.MergedVectorValues.hasVectorValues;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DIRECT_MONOTONIC_BLOCK_SHIFT;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsReader.SIMILARITY_FUNCTIONS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnFieldVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatFieldVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocsWithFieldSet;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Sorter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.VectorSimilarityFunction;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TaskExecutor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.CloseableRandomVectorScorerSupplier;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.ConcurrentHnswMerger;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.HnswGraph;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.HnswGraph.NodesIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.HnswGraphBuilder;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.HnswGraphMerger;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.IncrementalHnswGraphMerger;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.NeighborArray;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.OnHeapHnswGraph;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.RandomAccessVectorValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.RandomVectorScorerSupplier;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.packed.DirectMonotonicWriter;

/**
 * Writes vector values and knn graphs to index segments.
 *
 * @lucene.experimental
 */
public final class Lucene99HnswVectorsWriter extends KnnVectorsWriter {

  private static final long SHALLOW_RAM_BYTES_USED =
      RamUsageEstimator.shallowSizeOfInstance(Lucene99HnswVectorsWriter.class);
  private final SegmentWriteState segmentWriteState;
  private final IndexOutput meta, vectorIndex;
  private final int M;
  private final int beamWidth;
  private final FlatVectorsWriter flatVectorWriter;
  private final int numMergeWorkers;
  private final TaskExecutor mergeExec;

  private final List<FieldWriter<?>> fields = new ArrayList<>();
  private boolean finished;

  public Lucene99HnswVectorsWriter(
      SegmentWriteState state,
      int M,
      int beamWidth,
      FlatVectorsWriter flatVectorWriter,
      int numMergeWorkers,
      TaskExecutor mergeExec)
      throws IOException {
    this.M = M;
    this.flatVectorWriter = flatVectorWriter;
    this.beamWidth = beamWidth;
    this.numMergeWorkers = numMergeWorkers;
    this.mergeExec = mergeExec;
    segmentWriteState = state;

    String metaFileName =
        IndexFileNames.segmentFileName(
            state.segmentInfo.name, state.segmentSuffix, Lucene99HnswVectorsFormat.META_EXTENSION);

    String indexDataFileName =
        IndexFileNames.segmentFileName(
            state.segmentInfo.name,
            state.segmentSuffix,
            Lucene99HnswVectorsFormat.VECTOR_INDEX_EXTENSION);

    boolean success = false;
    try {
      meta = state.directory.createOutput(metaFileName, state.context);
      vectorIndex = state.directory.createOutput(indexDataFileName, state.context);

      CodecUtil.writeIndexHeader(
          meta,
          Lucene99HnswVectorsFormat.META_CODEC_NAME,
          Lucene99HnswVectorsFormat.VERSION_CURRENT,
          state.segmentInfo.getId(),
          state.segmentSuffix);
      CodecUtil.writeIndexHeader(
          vectorIndex,
          Lucene99HnswVectorsFormat.VECTOR_INDEX_CODEC_NAME,
          Lucene99HnswVectorsFormat.VERSION_CURRENT,
          state.segmentInfo.getId(),
          state.segmentSuffix);
      success = true;
    } finally {
      if (success == false) {
        IOUtils.closeWhileHandlingException(this);
      }
    }
  }

  @Override
  public KnnFieldVectorsWriter<?> addField(FieldInfo fieldInfo) throws IOException {
    FieldWriter<?> newField =
        FieldWriter.create(
            flatVectorWriter.getFlatVectorScorer(),
            flatVectorWriter.addField(fieldInfo),
            fieldInfo,
            M,
            beamWidth,
            segmentWriteState.infoStream);
    fields.add(newField);
    return newField;
  }

  @Override
  public void flush(int maxDoc, Sorter.DocMap sortMap) throws IOException {
    flatVectorWriter.flush(maxDoc, sortMap);
    for (FieldWriter<?> field : fields) {
      if (sortMap == null) {
        writeField(field);
      } else {
        writeSortingField(field, sortMap);
      }
    }
  }

  @Override
  public void finish() throws IOException {
    if (finished) {
      throw new IllegalStateException("already finished");
    }
    finished = true;
    flatVectorWriter.finish();

    if (meta != null) {
      // write end of fields marker
      meta.writeInt(-1);
      CodecUtil.writeFooter(meta);
    }
    if (vectorIndex != null) {
      CodecUtil.writeFooter(vectorIndex);
    }
  }

  @Override
  public long ramBytesUsed() {
    long total = SHALLOW_RAM_BYTES_USED;
    for (FieldWriter<?> field : fields) {
      // the field tracks the delegate field usage
      total += field.ramBytesUsed();
    }
    return total;
  }

  private void writeField(FieldWriter<?> fieldData) throws IOException {
    // write graph
    long vectorIndexOffset = vectorIndex.getFilePointer();
    OnHeapHnswGraph graph = fieldData.getGraph();
    int[][] graphLevelNodeOffsets = writeGraph(graph);
    long vectorIndexLength = vectorIndex.getFilePointer() - vectorIndexOffset;

    writeMeta(
        fieldData.fieldInfo,
        vectorIndexOffset,
        vectorIndexLength,
        fieldData.getDocsWithFieldSet().cardinality(),
        graph,
        graphLevelNodeOffsets);
  }

  private void writeSortingField(FieldWriter<?> fieldData, Sorter.DocMap sortMap)
      throws IOException {
    final int[] ordMap =
        new int[fieldData.getDocsWithFieldSet().cardinality()]; // new ord to old ord
    final int[] oldOrdMap =
        new int[fieldData.getDocsWithFieldSet().cardinality()]; // old ord to new ord

    mapOldOrdToNewOrd(fieldData.getDocsWithFieldSet(), sortMap, oldOrdMap, ordMap, null);
    // write graph
    long vectorIndexOffset = vectorIndex.getFilePointer();
    OnHeapHnswGraph graph = fieldData.getGraph();
    int[][] graphLevelNodeOffsets = graph == null ? new int[0][] : new int[graph.numLevels()][];
    HnswGraph mockGraph = reconstructAndWriteGraph(graph, ordMap, oldOrdMap, graphLevelNodeOffsets);
    long vectorIndexLength = vectorIndex.getFilePointer() - vectorIndexOffset;

    writeMeta(
        fieldData.fieldInfo,
        vectorIndexOffset,
        vectorIndexLength,
        fieldData.getDocsWithFieldSet().cardinality(),
        mockGraph,
        graphLevelNodeOffsets);
  }

  /**
   * Reconstructs the graph given the old and new node ids.
   *
   * <p>Additionally, the graph node connections are written to the vectorIndex.
   *
   * @param graph The current on heap graph
   * @param newToOldMap the new node ids indexed to the old node ids
   * @param oldToNewMap the old node ids indexed to the new node ids
   * @param levelNodeOffsets where to place the new offsets for the nodes in the vector index.
   * @return The graph
   * @throws IOException if writing to vectorIndex fails
   */
  private HnswGraph reconstructAndWriteGraph(
      OnHeapHnswGraph graph, int[] newToOldMap, int[] oldToNewMap, int[][] levelNodeOffsets)
      throws IOException {
    if (graph == null) return null;

    List<int[]> nodesByLevel = new ArrayList<>(graph.numLevels());
    nodesByLevel.add(null);

    int maxOrd = graph.size();
    NodesIterator nodesOnLevel0 = graph.getNodesOnLevel(0);
    levelNodeOffsets[0] = new int[nodesOnLevel0.size()];
    while (nodesOnLevel0.hasNext()) {
      int node = nodesOnLevel0.nextInt();
      NeighborArray neighbors = graph.getNeighbors(0, newToOldMap[node]);
      long offset = vectorIndex.getFilePointer();
      reconstructAndWriteNeighbours(neighbors, oldToNewMap, maxOrd);
      levelNodeOffsets[0][node] = Math.toIntExact(vectorIndex.getFilePointer() - offset);
    }

    for (int level = 1; level < graph.numLevels(); level++) {
      NodesIterator nodesOnLevel = graph.getNodesOnLevel(level);
      int[] newNodes = new int[nodesOnLevel.size()];
      for (int n = 0; nodesOnLevel.hasNext(); n++) {
        newNodes[n] = oldToNewMap[nodesOnLevel.nextInt()];
      }
      Arrays.sort(newNodes);
      nodesByLevel.add(newNodes);
      levelNodeOffsets[level] = new int[newNodes.length];
      int nodeOffsetIndex = 0;
      for (int node : newNodes) {
        NeighborArray neighbors = graph.getNeighbors(level, newToOldMap[node]);
        long offset = vectorIndex.getFilePointer();
        reconstructAndWriteNeighbours(neighbors, oldToNewMap, maxOrd);
        levelNodeOffsets[level][nodeOffsetIndex++] =
            Math.toIntExact(vectorIndex.getFilePointer() - offset);
      }
    }
    return new HnswGraph() {
      @Override
      public int nextNeighbor() {
        throw new UnsupportedOperationException("Not supported on a mock graph");
      }

      @Override
      public void seek(int level, int target) {
        throw new UnsupportedOperationException("Not supported on a mock graph");
      }

      @Override
      public int size() {
        return graph.size();
      }

      @Override
      public int numLevels() {
        return graph.numLevels();
      }

      @Override
      public int entryNode() {
        throw new UnsupportedOperationException("Not supported on a mock graph");
      }

      @Override
      public NodesIterator getNodesOnLevel(int level) {
        if (level == 0) {
          return graph.getNodesOnLevel(0);
        } else {
          return new ArrayNodesIterator(nodesByLevel.get(level), nodesByLevel.get(level).length);
        }
      }
    };
  }

  private void reconstructAndWriteNeighbours(NeighborArray neighbors, int[] oldToNewMap, int maxOrd)
      throws IOException {
    int size = neighbors.size();
    vectorIndex.writeVInt(size);

    // Destructively modify; it's ok we are discarding it after this
    int[] nnodes = neighbors.nodes();
    for (int i = 0; i < size; i++) {
      nnodes[i] = oldToNewMap[nnodes[i]];
    }
    Arrays.sort(nnodes, 0, size);
    // Now that we have sorted, do delta encoding to minimize the required bits to store the
    // information
    for (int i = size - 1; i > 0; --i) {
      assert nnodes[i] < maxOrd : "node too large: " + nnodes[i] + ">=" + maxOrd;
      nnodes[i] -= nnodes[i - 1];
    }
    for (int i = 0; i < size; i++) {
      vectorIndex.writeVInt(nnodes[i]);
    }
  }

  @Override
  public void mergeOneField(FieldInfo fieldInfo, MergeState mergeState) throws IOException {
    CloseableRandomVectorScorerSupplier scorerSupplier =
        flatVectorWriter.mergeOneFieldToIndex(fieldInfo, mergeState);
    boolean success = false;
    try {
      long vectorIndexOffset = vectorIndex.getFilePointer();
      // build the graph using the temporary vector data
      // we use Lucene99HnswVectorsReader.DenseOffHeapVectorValues for the graph construction
      // doesn't need to know docIds
      // TODO: separate random access vector values from DocIdSetIterator?
      OnHeapHnswGraph graph = null;
      int[][] vectorIndexNodeOffsets = null;
      if (scorerSupplier.totalVectorCount() > 0) {
        // build graph
        HnswGraphMerger merger =
            createGraphMerger(
                fieldInfo,
                scorerSupplier,
                mergeState.intraMergeTaskExecutor == null
                    ? null
                    : new TaskExecutor(mergeState.intraMergeTaskExecutor),
                numMergeWorkers);
        for (int i = 0; i < mergeState.liveDocs.length; i++) {
          if (hasVectorValues(mergeState.fieldInfos[i], fieldInfo.name)) {
            merger.addReader(
                mergeState.knnVectorsReaders[i], mergeState.docMaps[i], mergeState.liveDocs[i]);
          }
        }
        final DocIdSetIterator mergedVectorIterator;
        switch (fieldInfo.getVectorEncoding()) {
          case BYTE:
            mergedVectorIterator =
                KnnVectorsWriter.MergedVectorValues.mergeByteVectorValues(fieldInfo, mergeState);
            break;
          case FLOAT32:
            mergedVectorIterator =
                KnnVectorsWriter.MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState);
            break;
          default:
            throw new IllegalStateException(
                "Unsupported vector encoding: " + fieldInfo.getVectorEncoding());
        }
        graph =
            merger.merge(
                mergedVectorIterator,
                segmentWriteState.infoStream,
                scorerSupplier.totalVectorCount());
        vectorIndexNodeOffsets = writeGraph(graph);
      }
      long vectorIndexLength = vectorIndex.getFilePointer() - vectorIndexOffset;
      writeMeta(
          fieldInfo,
          vectorIndexOffset,
          vectorIndexLength,
          scorerSupplier.totalVectorCount(),
          graph,
          vectorIndexNodeOffsets);
      success = true;
    } finally {
      if (success) {
        IOUtils.close(scorerSupplier);
      } else {
        IOUtils.closeWhileHandlingException(scorerSupplier);
      }
    }
  }

  /**
   * @param graph Write the graph in a compressed format
   * @return The non-cumulative offsets for the nodes. Should be used to create cumulative offsets.
   * @throws IOException if writing to vectorIndex fails
   */
  private int[][] writeGraph(OnHeapHnswGraph graph) throws IOException {
    if (graph == null) return new int[0][0];
    // write vectors' neighbours on each level into the vectorIndex file
    int countOnLevel0 = graph.size();
    int[][] offsets = new int[graph.numLevels()][];
    for (int level = 0; level < graph.numLevels(); level++) {
      int[] sortedNodes = NodesIterator.getSortedNodes(graph.getNodesOnLevel(level));
      offsets[level] = new int[sortedNodes.length];
      int nodeOffsetId = 0;
      for (int node : sortedNodes) {
        NeighborArray neighbors = graph.getNeighbors(level, node);
        int size = neighbors.size();
        // Write size in VInt as the neighbors list is typically small
        long offsetStart = vectorIndex.getFilePointer();
        vectorIndex.writeVInt(size);
        // Destructively modify; it's ok we are discarding it after this
        int[] nnodes = neighbors.nodes();
        Arrays.sort(nnodes, 0, size);
        // Now that we have sorted, do delta encoding to minimize the required bits to store the
        // information
        for (int i = size - 1; i > 0; --i) {
          assert nnodes[i] < countOnLevel0 : "node too large: " + nnodes[i] + ">=" + countOnLevel0;
          nnodes[i] -= nnodes[i - 1];
        }
        for (int i = 0; i < size; i++) {
          vectorIndex.writeVInt(nnodes[i]);
        }
        offsets[level][nodeOffsetId++] =
            Math.toIntExact(vectorIndex.getFilePointer() - offsetStart);
      }
    }
    return offsets;
  }

  private void writeMeta(
      FieldInfo field,
      long vectorIndexOffset,
      long vectorIndexLength,
      int count,
      HnswGraph graph,
      int[][] graphLevelNodeOffsets)
      throws IOException {
    meta.writeInt(field.number);
    meta.writeInt(field.getVectorEncoding().ordinal());
    meta.writeInt(distFuncToOrd(field.getVectorSimilarityFunction()));
    meta.writeVLong(vectorIndexOffset);
    meta.writeVLong(vectorIndexLength);
    meta.writeVInt(field.getVectorDimension());
    meta.writeInt(count);
    meta.writeVInt(M);
    // write graph nodes on each level
    if (graph == null) {
      meta.writeVInt(0);
    } else {
      meta.writeVInt(graph.numLevels());
      long valueCount = 0;
      for (int level = 0; level < graph.numLevels(); level++) {
        NodesIterator nodesOnLevel = graph.getNodesOnLevel(level);
        valueCount += nodesOnLevel.size();
        if (level > 0) {
          int[] nol = new int[nodesOnLevel.size()];
          int numberConsumed = nodesOnLevel.consume(nol);
          Arrays.sort(nol);
          assert numberConsumed == nodesOnLevel.size();
          meta.writeVInt(nol.length); // number of nodes on a level
          for (int i = nodesOnLevel.size() - 1; i > 0; --i) {
            nol[i] -= nol[i - 1];
          }
          for (int n : nol) {
            assert n >= 0 : "delta encoding for nodes failed; expected nodes to be sorted";
            meta.writeVInt(n);
          }
        } else {
          assert nodesOnLevel.size() == count : "Level 0 expects to have all nodes";
        }
      }
      long start = vectorIndex.getFilePointer();
      meta.writeLong(start);
      meta.writeVInt(DIRECT_MONOTONIC_BLOCK_SHIFT);
      final DirectMonotonicWriter memoryOffsetsWriter =
          DirectMonotonicWriter.getInstance(
              meta, vectorIndex, valueCount, DIRECT_MONOTONIC_BLOCK_SHIFT);
      long cumulativeOffsetSum = 0;
      for (int[] levelOffsets : graphLevelNodeOffsets) {
        for (int v : levelOffsets) {
          memoryOffsetsWriter.add(cumulativeOffsetSum);
          cumulativeOffsetSum += v;
        }
      }
      memoryOffsetsWriter.finish();
      meta.writeLong(vectorIndex.getFilePointer() - start);
    }
  }

  private HnswGraphMerger createGraphMerger(
      FieldInfo fieldInfo,
      RandomVectorScorerSupplier scorerSupplier,
      TaskExecutor parallelMergeTaskExecutor,
      int numParallelMergeWorkers) {
    if (mergeExec != null) {
      return new ConcurrentHnswMerger(
          fieldInfo, scorerSupplier, M, beamWidth, mergeExec, numMergeWorkers);
    }
    if (parallelMergeTaskExecutor != null) {
      return new ConcurrentHnswMerger(
          fieldInfo,
          scorerSupplier,
          M,
          beamWidth,
          parallelMergeTaskExecutor,
          numParallelMergeWorkers);
    }
    return new IncrementalHnswGraphMerger(fieldInfo, scorerSupplier, M, beamWidth);
  }

  @Override
  public void close() throws IOException {
    IOUtils.close(meta, vectorIndex, flatVectorWriter);
  }

  static int distFuncToOrd(VectorSimilarityFunction func) {
    for (int i = 0; i < SIMILARITY_FUNCTIONS.size(); i++) {
      if (SIMILARITY_FUNCTIONS.get(i).equals(func)) {
        return (byte) i;
      }
    }
    throw new IllegalArgumentException("invalid distance function: " + func);
  }

  private static class FieldWriter<T> extends KnnFieldVectorsWriter<T> {

    private static final long SHALLOW_SIZE =
        RamUsageEstimator.shallowSizeOfInstance(FieldWriter.class);

    private final FieldInfo fieldInfo;
    private final HnswGraphBuilder hnswGraphBuilder;
    private int lastDocID = -1;
    private int node = 0;
    private final FlatFieldVectorsWriter<T> flatFieldVectorsWriter;

    @SuppressWarnings("unchecked")
    static FieldWriter<?> create(
        FlatVectorsScorer scorer,
        FlatFieldVectorsWriter<?> flatFieldVectorsWriter,
        FieldInfo fieldInfo,
        int M,
        int beamWidth,
        InfoStream infoStream)
        throws IOException {
      switch (fieldInfo.getVectorEncoding()) {
        case BYTE:
          return new FieldWriter<>(
              scorer,
              (FlatFieldVectorsWriter<byte[]>) flatFieldVectorsWriter,
              fieldInfo,
              M,
              beamWidth,
              infoStream);
        case FLOAT32:
          return new FieldWriter<>(
              scorer,
              (FlatFieldVectorsWriter<float[]>) flatFieldVectorsWriter,
              fieldInfo,
              M,
              beamWidth,
              infoStream);
        default:
          throw new IllegalStateException(
              "Unsupported vector encoding: " + fieldInfo.getVectorEncoding());
      }
    }

    @SuppressWarnings("unchecked")
    FieldWriter(
        FlatVectorsScorer scorer,
        FlatFieldVectorsWriter<T> flatFieldVectorsWriter,
        FieldInfo fieldInfo,
        int M,
        int beamWidth,
        InfoStream infoStream)
        throws IOException {
      this.fieldInfo = fieldInfo;
      final RandomVectorScorerSupplier scorerSupplier;
      switch (fieldInfo.getVectorEncoding()) {
        case BYTE:
          scorerSupplier =
              scorer.getRandomVectorScorerSupplier(
                  fieldInfo.getVectorSimilarityFunction(),
                  RandomAccessVectorValues.fromBytes(
                      (List<byte[]>) flatFieldVectorsWriter.getVectors(),
                      fieldInfo.getVectorDimension()));
          break;
        case FLOAT32:
          scorerSupplier =
              scorer.getRandomVectorScorerSupplier(
                  fieldInfo.getVectorSimilarityFunction(),
                  RandomAccessVectorValues.fromFloats(
                      (List<float[]>) flatFieldVectorsWriter.getVectors(),
                      fieldInfo.getVectorDimension()));
          break;
        default:
          throw new IllegalStateException(
              "Unsupported vector encoding: " + fieldInfo.getVectorEncoding());
      }
      hnswGraphBuilder =
          HnswGraphBuilder.create(scorerSupplier, M, beamWidth, HnswGraphBuilder.randSeed);
      hnswGraphBuilder.setInfoStream(infoStream);
      this.flatFieldVectorsWriter = Objects.requireNonNull(flatFieldVectorsWriter);
    }

    @Override
    public void addValue(int docID, T vectorValue) throws IOException {
      if (docID == lastDocID) {
        throw new IllegalArgumentException(
            "VectorValuesField \""
                + fieldInfo.name
                + "\" appears more than once in this document (only one value is allowed per field)");
      }
      flatFieldVectorsWriter.addValue(docID, vectorValue);
      hnswGraphBuilder.addGraphNode(node);
      node++;
      lastDocID = docID;
    }

    public DocsWithFieldSet getDocsWithFieldSet() {
      return flatFieldVectorsWriter.getDocsWithFieldSet();
    }

    @Override
    public T copyValue(T vectorValue) {
      throw new UnsupportedOperationException();
    }

    OnHeapHnswGraph getGraph() throws IOException {
      assert flatFieldVectorsWriter.isFinished();
      if (node > 0) {
        return hnswGraphBuilder.getCompletedGraph();
      } else {
        return null;
      }
    }

    @Override
    public long ramBytesUsed() {
      return SHALLOW_SIZE
          + flatFieldVectorsWriter.ramBytesUsed()
          + hnswGraphBuilder.getGraph().ramBytesUsed();
    }
  }
}
