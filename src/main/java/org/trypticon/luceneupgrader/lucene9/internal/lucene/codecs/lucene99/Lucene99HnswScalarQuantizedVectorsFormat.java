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

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_NUM_MERGE_WORKER;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.MAXIMUM_BEAM_WIDTH;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.MAXIMUM_MAX_CONN;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.TaskExecutor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.hnsw.HnswGraph;

/**
 * Lucene 9.9 vector format, which encodes numeric vector values into an associated graph connecting
 * the documents having values. The graph is used to power HNSW search. The format consists of two
 * files, and uses {@link Lucene99ScalarQuantizedVectorsFormat} to store the actual vectors: For
 * details on graph storage and file extensions, see {@link Lucene99HnswVectorsFormat}.
 *
 * @lucene.experimental
 */
public class Lucene99HnswScalarQuantizedVectorsFormat extends KnnVectorsFormat {

  public static final String NAME = "Lucene99HnswScalarQuantizedVectorsFormat";

  /**
   * Controls how many of the nearest neighbor candidates are connected to the new node. Defaults to
   * {@link Lucene99HnswVectorsFormat#DEFAULT_MAX_CONN}. See {@link HnswGraph} for more details.
   */
  private final int maxConn;

  /**
   * The number of candidate neighbors to track while searching the graph for each newly inserted
   * node. Defaults to {@link Lucene99HnswVectorsFormat#DEFAULT_BEAM_WIDTH}. See {@link HnswGraph}
   * for details.
   */
  private final int beamWidth;

  /** The format for storing, reading, merging vectors on disk */
  private final FlatVectorsFormat flatVectorsFormat;

  private final int numMergeWorkers;
  private final TaskExecutor mergeExec;

  /** Constructs a format using default graph construction parameters with 7 bit quantization */
  public Lucene99HnswScalarQuantizedVectorsFormat() {
    this(DEFAULT_MAX_CONN, DEFAULT_BEAM_WIDTH, DEFAULT_NUM_MERGE_WORKER, 7, false, null, null);
  }

  /**
   * Constructs a format using the given graph construction parameters with 7 bit quantization
   *
   * @param maxConn the maximum number of connections to a node in the HNSW graph
   * @param beamWidth the size of the queue maintained during graph construction.
   */
  public Lucene99HnswScalarQuantizedVectorsFormat(int maxConn, int beamWidth) {
    this(maxConn, beamWidth, DEFAULT_NUM_MERGE_WORKER, 7, false, null, null);
  }

  /**
   * Constructs a format using the given graph construction parameters and scalar quantization.
   *
   * @param maxConn the maximum number of connections to a node in the HNSW graph
   * @param beamWidth the size of the queue maintained during graph construction.
   * @param numMergeWorkers number of workers (threads) that will be used when doing merge. If
   *     larger than 1, a non-null {@link ExecutorService} must be passed as mergeExec
   * @param bits the number of bits to use for scalar quantization (must be 4 or 7)
   * @param compress whether to compress the quantized vectors by another 50% when bits=4. If
   *     `true`, pairs of (4 bit quantized) dimensions are packed into a single byte. This must be
   *     `false` when bits=7. This provides a trade-off of 50% reduction in hot vector memory usage
   *     during searching, at some decode speed penalty.
   * @param confidenceInterval the confidenceInterval for scalar quantizing the vectors, when `null`
   *     it is calculated based on the vector field dimensions. When `0`, the quantiles are
   *     dynamically determined by sampling many confidence intervals and determining the most
   *     accurate pair.
   * @param mergeExec the {@link ExecutorService} that will be used by ALL vector writers that are
   *     generated by this format to do the merge
   */
  public Lucene99HnswScalarQuantizedVectorsFormat(
      int maxConn,
      int beamWidth,
      int numMergeWorkers,
      int bits,
      boolean compress,
      Float confidenceInterval,
      ExecutorService mergeExec) {
    super(NAME);
    if (maxConn <= 0 || maxConn > MAXIMUM_MAX_CONN) {
      throw new IllegalArgumentException(
          "maxConn must be positive and less than or equal to "
              + MAXIMUM_MAX_CONN
              + "; maxConn="
              + maxConn);
    }
    if (beamWidth <= 0 || beamWidth > MAXIMUM_BEAM_WIDTH) {
      throw new IllegalArgumentException(
          "beamWidth must be positive and less than or equal to "
              + MAXIMUM_BEAM_WIDTH
              + "; beamWidth="
              + beamWidth);
    }
    this.maxConn = maxConn;
    this.beamWidth = beamWidth;
    if (numMergeWorkers == 1 && mergeExec != null) {
      throw new IllegalArgumentException(
          "No executor service is needed as we'll use single thread to merge");
    }
    this.numMergeWorkers = numMergeWorkers;
    if (mergeExec != null) {
      this.mergeExec = new TaskExecutor(mergeExec);
    } else {
      this.mergeExec = null;
    }
    this.flatVectorsFormat =
        new Lucene99ScalarQuantizedVectorsFormat(confidenceInterval, bits, compress);
  }

  @Override
  public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new Lucene99HnswVectorsWriter(
        state,
        maxConn,
        beamWidth,
        flatVectorsFormat.fieldsWriter(state),
        numMergeWorkers,
        mergeExec);
  }

  @Override
  public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
    return new Lucene99HnswVectorsReader(state, flatVectorsFormat.fieldsReader(state));
  }

  @Override
  public int getMaxDimensions(String fieldName) {
    return 1024;
  }

  @Override
  public String toString() {
    return "Lucene99HnswScalarQuantizedVectorsFormat(name=Lucene99HnswScalarQuantizedVectorsFormat, maxConn="
        + maxConn
        + ", beamWidth="
        + beamWidth
        + ", flatVectorFormat="
        + flatVectorsFormat
        + ")";
  }
}
