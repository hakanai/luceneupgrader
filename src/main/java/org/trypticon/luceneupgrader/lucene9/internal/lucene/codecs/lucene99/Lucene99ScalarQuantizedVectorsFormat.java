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

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorScorerUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.hnsw.FlatVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentWriteState;

/**
 * Format supporting vector quantization, storage, and retrieval
 *
 * @lucene.experimental
 */
public class Lucene99ScalarQuantizedVectorsFormat extends FlatVectorsFormat {

  // The bits that are allowed for scalar quantization
  // We only allow signed byte (7), and half-byte (4)
  // NOTE: we used to allow 8 bits as well, but it was broken so we removed it
  // (https://github.com/apache/lucene/issues/13519)
  private static final int ALLOWED_BITS = (1 << 7) | (1 << 4);
  public static final String QUANTIZED_VECTOR_COMPONENT = "QVEC";

  public static final String NAME = "Lucene99ScalarQuantizedVectorsFormat";

  static final int VERSION_START = 0;
  static final int VERSION_ADD_BITS = 1;
  static final int VERSION_CURRENT = VERSION_ADD_BITS;
  static final String META_CODEC_NAME = "Lucene99ScalarQuantizedVectorsFormatMeta";
  static final String VECTOR_DATA_CODEC_NAME = "Lucene99ScalarQuantizedVectorsFormatData";
  static final String META_EXTENSION = "vemq";
  static final String VECTOR_DATA_EXTENSION = "veq";

  private static final FlatVectorsFormat rawVectorFormat =
      new Lucene99FlatVectorsFormat(FlatVectorScorerUtil.getLucene99FlatVectorsScorer());

  /** The minimum confidence interval */
  private static final float MINIMUM_CONFIDENCE_INTERVAL = 0.9f;

  /** The maximum confidence interval */
  private static final float MAXIMUM_CONFIDENCE_INTERVAL = 1f;

  /** Dynamic confidence interval */
  public static final float DYNAMIC_CONFIDENCE_INTERVAL = 0f;

  /**
   * Controls the confidence interval used to scalar quantize the vectors the default value is
   * calculated as `1-1/(vector_dimensions + 1)`
   */
  final Float confidenceInterval;

  final byte bits;
  final boolean compress;
  final Lucene99ScalarQuantizedVectorScorer flatVectorScorer;

  /** Constructs a format using default graph construction parameters */
  public Lucene99ScalarQuantizedVectorsFormat() {
    this(null, 7, false);
  }

  /**
   * Constructs a format using the given graph construction parameters.
   *
   * @param confidenceInterval the confidenceInterval for scalar quantizing the vectors, when `null`
   *     it is calculated based on the vector dimension. When `0`, the quantiles are dynamically
   *     determined by sampling many confidence intervals and determining the most accurate pair.
   * @param bits the number of bits to use for scalar quantization (must be between 1 and 8,
   *     inclusive)
   * @param compress whether to compress the quantized vectors by another 50% when bits=4. If
   *     `true`, pairs of (4 bit quantized) dimensions are packed into a single byte. This must be
   *     `false` when bits=7. This provides a trade-off of 50% reduction in hot vector memory usage
   *     during searching, at some decode speed penalty.
   */
  public Lucene99ScalarQuantizedVectorsFormat(
      Float confidenceInterval, int bits, boolean compress) {
    super(NAME);
    if (confidenceInterval != null
        && confidenceInterval != DYNAMIC_CONFIDENCE_INTERVAL
        && (confidenceInterval < MINIMUM_CONFIDENCE_INTERVAL
            || confidenceInterval > MAXIMUM_CONFIDENCE_INTERVAL)) {
      throw new IllegalArgumentException(
          "confidenceInterval must be between "
              + MINIMUM_CONFIDENCE_INTERVAL
              + " and "
              + MAXIMUM_CONFIDENCE_INTERVAL
              + " or 0"
              + "; confidenceInterval="
              + confidenceInterval);
    }
    if (bits < 1 || bits > 8 || (ALLOWED_BITS & (1 << bits)) == 0) {
      throw new IllegalArgumentException("bits must be one of: 4, 7; bits=" + bits);
    }

    if (bits > 4 && compress) {
      // compress=true otherwise silently does nothing when bits=7?
      throw new IllegalArgumentException("compress=true only applies when bits=4");
    }
    this.bits = (byte) bits;
    this.confidenceInterval = confidenceInterval;
    this.compress = compress;
    this.flatVectorScorer =
        new Lucene99ScalarQuantizedVectorScorer(DefaultFlatVectorScorer.INSTANCE);
  }

  public static float calculateDefaultConfidenceInterval(int vectorDimension) {
    return Math.max(MINIMUM_CONFIDENCE_INTERVAL, 1f - (1f / (vectorDimension + 1)));
  }

  @Override
  public String toString() {
    return NAME
        + "(name="
        + NAME
        + ", confidenceInterval="
        + confidenceInterval
        + ", bits="
        + bits
        + ", compress="
        + compress
        + ", flatVectorScorer="
        + flatVectorScorer
        + ", rawVectorFormat="
        + rawVectorFormat
        + ")";
  }

  @Override
  public FlatVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new Lucene99ScalarQuantizedVectorsWriter(
        state,
        confidenceInterval,
        bits,
        compress,
        rawVectorFormat.fieldsWriter(state),
        flatVectorScorer);
  }

  @Override
  public FlatVectorsReader fieldsReader(SegmentReadState state) throws IOException {
    return new Lucene99ScalarQuantizedVectorsReader(
        state, rawVectorFormat.fieldsReader(state), flatVectorScorer);
  }
}
