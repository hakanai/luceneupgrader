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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene94;

import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene90.Lucene90PostingsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.backward_codecs.lucene90.Lucene90SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CompoundFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.FilterCodec;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PointsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90CompoundFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90DocValuesFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90NormsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90PointsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene90.Lucene90TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.lucene94.Lucene94FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.perfield.PerFieldPostingsFormat;

/**
 * Implements the Lucene 9.4 index format
 *
 * <p>If you want to reuse functionality of this codec in another codec, extend {@link FilterCodec}.
 *
 * @see org.apache.lucene.backward_codecs.lucene94 package documentation for file format details.
 * @lucene.experimental
 */
public class Lucene94Codec extends Codec {

  /** Configuration option for the codec. */
  public enum Mode {
    /** Trade compression ratio for retrieval speed. */
    BEST_SPEED(Lucene90StoredFieldsFormat.Mode.BEST_SPEED),
    /** Trade retrieval speed for compression ratio. */
    BEST_COMPRESSION(Lucene90StoredFieldsFormat.Mode.BEST_COMPRESSION);

    private final Lucene90StoredFieldsFormat.Mode storedMode;

    Mode(Lucene90StoredFieldsFormat.Mode storedMode) {
      this.storedMode = Objects.requireNonNull(storedMode);
    }
  }

  private final TermVectorsFormat vectorsFormat = new Lucene90TermVectorsFormat();
  private final FieldInfosFormat fieldInfosFormat = new Lucene94FieldInfosFormat();
  private final SegmentInfoFormat segmentInfosFormat = new Lucene90SegmentInfoFormat();
  private final LiveDocsFormat liveDocsFormat = new Lucene90LiveDocsFormat();
  private final CompoundFormat compoundFormat = new Lucene90CompoundFormat();
  private final NormsFormat normsFormat = new Lucene90NormsFormat();

  private final PostingsFormat defaultPostingsFormat;
  private final PostingsFormat postingsFormat =
      new PerFieldPostingsFormat() {
        @Override
        public PostingsFormat getPostingsFormatForField(String field) {
          return Lucene94Codec.this.getPostingsFormatForField(field);
        }
      };

  private final DocValuesFormat defaultDVFormat;
  private final DocValuesFormat docValuesFormat =
      new PerFieldDocValuesFormat() {
        @Override
        public DocValuesFormat getDocValuesFormatForField(String field) {
          return Lucene94Codec.this.getDocValuesFormatForField(field);
        }
      };

  private final KnnVectorsFormat defaultKnnVectorsFormat;
  private final KnnVectorsFormat knnVectorsFormat =
      new PerFieldKnnVectorsFormat() {
        @Override
        public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
          return Lucene94Codec.this.getKnnVectorsFormatForField(field);
        }
      };

  private final StoredFieldsFormat storedFieldsFormat;

  /** Instantiates a new codec. */
  public Lucene94Codec() {
    this(Mode.BEST_SPEED);
  }

  /**
   * Instantiates a new codec, specifying the stored fields compression mode to use.
   *
   * @param mode stored fields compression mode to use for newly flushed/merged segments.
   */
  public Lucene94Codec(Mode mode) {
    super("Lucene94");
    this.storedFieldsFormat =
        new Lucene90StoredFieldsFormat(Objects.requireNonNull(mode).storedMode);
    this.defaultPostingsFormat = new Lucene90PostingsFormat();
    this.defaultDVFormat = new Lucene90DocValuesFormat();
    this.defaultKnnVectorsFormat = new Lucene94HnswVectorsFormat();
  }

  @Override
  public final StoredFieldsFormat storedFieldsFormat() {
    return storedFieldsFormat;
  }

  @Override
  public final TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }

  @Override
  public final PostingsFormat postingsFormat() {
    return postingsFormat;
  }

  @Override
  public final FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }

  @Override
  public SegmentInfoFormat segmentInfoFormat() {
    return segmentInfosFormat;
  }

  @Override
  public final LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }

  @Override
  public final CompoundFormat compoundFormat() {
    return compoundFormat;
  }

  @Override
  public final PointsFormat pointsFormat() {
    return new Lucene90PointsFormat();
  }

  @Override
  public KnnVectorsFormat knnVectorsFormat() {
    return knnVectorsFormat;
  }

  /**
   * Returns the postings format that should be used for writing new segments of <code>field</code>.
   *
   * <p>The default implementation always returns "Lucene90".
   *
   * <p><b>WARNING:</b> if you subclass, you are responsible for index backwards compatibility:
   * future version of Lucene are only guaranteed to be able to read the default implementation,
   */
  public PostingsFormat getPostingsFormatForField(String field) {
    return defaultPostingsFormat;
  }

  /**
   * Returns the docvalues format that should be used for writing new segments of <code>field</code>
   * .
   *
   * <p>The default implementation always returns "Lucene90".
   *
   * <p><b>WARNING:</b> if you subclass, you are responsible for index backwards compatibility:
   * future version of Lucene are only guaranteed to be able to read the default implementation.
   */
  public DocValuesFormat getDocValuesFormatForField(String field) {
    return defaultDVFormat;
  }

  /**
   * Returns the vectors format that should be used for writing new segments of <code>field</code>
   *
   * <p>The default implementation always returns "lucene94".
   *
   * <p><b>WARNING:</b> if you subclass, you are responsible for index backwards compatibility:
   * future version of Lucene are only guaranteed to be able to read the default implementation.
   */
  public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
    return defaultKnnVectorsFormat;
  }

  @Override
  public final DocValuesFormat docValuesFormat() {
    return docValuesFormat;
  }

  @Override
  public final NormsFormat normsFormat() {
    return normsFormat;
  }
}
