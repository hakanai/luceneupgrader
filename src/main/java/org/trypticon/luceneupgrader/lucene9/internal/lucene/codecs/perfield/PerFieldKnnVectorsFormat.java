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

package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.perfield;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnFieldVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsFormat;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsReader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.KnnVectorsWriter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.ByteVectorValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FloatVectorValues;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Sorter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.KnnCollector;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;

/**
 * Enables per field numeric vector support.
 *
 * <p>Note, when extending this class, the name ({@link #getName}) is written into the index. In
 * order for the field to be read, the name must resolve to your implementation via {@link
 * #forName(String)}. This method uses Java's {@link ServiceLoader Service Provider Interface} to
 * resolve format names.
 *
 * <p>Files written by each numeric vectors format have an additional suffix containing the format
 * name. For example, in a per-field configuration instead of <code>_1.dat</code> filenames would
 * look like <code>_1_Lucene40_0.dat</code>.
 *
 * @see ServiceLoader
 * @lucene.experimental
 */
public abstract class PerFieldKnnVectorsFormat extends KnnVectorsFormat {
  /** Name of this {@link KnnVectorsFormat}. */
  public static final String PER_FIELD_NAME = "PerFieldVectors90";

  /** {@link FieldInfo} attribute name used to store the format name for each field. */
  public static final String PER_FIELD_FORMAT_KEY =
      PerFieldKnnVectorsFormat.class.getSimpleName() + ".format";

  /** {@link FieldInfo} attribute name used to store the segment suffix name for each field. */
  public static final String PER_FIELD_SUFFIX_KEY =
      PerFieldKnnVectorsFormat.class.getSimpleName() + ".suffix";

  /** Sole constructor. */
  protected PerFieldKnnVectorsFormat() {
    super(PER_FIELD_NAME);
  }

  @Override
  public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new FieldsWriter(state);
  }

  @Override
  public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
    return new FieldsReader(state);
  }

  @Override
  public int getMaxDimensions(String fieldName) {
    return getKnnVectorsFormatForField(fieldName).getMaxDimensions(fieldName);
  }

  /**
   * Returns the numeric vector format that should be used for writing new segments of <code>field
   * </code>.
   *
   * <p>The field to format mapping is written to the index, so this method is only invoked when
   * writing, not when reading.
   */
  public abstract KnnVectorsFormat getKnnVectorsFormatForField(String field);

  private class FieldsWriter extends KnnVectorsWriter {
    private final Map<KnnVectorsFormat, WriterAndSuffix> formats;
    private final Map<String, Integer> suffixes = new HashMap<>();
    private final SegmentWriteState segmentWriteState;

    FieldsWriter(SegmentWriteState segmentWriteState) {
      this.segmentWriteState = segmentWriteState;
      formats = new HashMap<>();
    }

    @Override
    public KnnFieldVectorsWriter<?> addField(FieldInfo fieldInfo) throws IOException {
      KnnVectorsWriter writer = getInstance(fieldInfo);
      return writer.addField(fieldInfo);
    }

    @Override
    public void flush(int maxDoc, Sorter.DocMap sortMap) throws IOException {
      for (WriterAndSuffix was : formats.values()) {
        was.writer.flush(maxDoc, sortMap);
      }
    }

    @Override
    public void mergeOneField(FieldInfo fieldInfo, MergeState mergeState) throws IOException {
      getInstance(fieldInfo).mergeOneField(fieldInfo, mergeState);
    }

    @Override
    public void finish() throws IOException {
      for (WriterAndSuffix was : formats.values()) {
        was.writer.finish();
      }
    }

    @Override
    public void close() throws IOException {
      IOUtils.close(formats.values());
    }

    private KnnVectorsWriter getInstance(FieldInfo field) throws IOException {
      KnnVectorsFormat format = getKnnVectorsFormatForField(field.name);
      if (format == null) {
        throw new IllegalStateException(
            "invalid null KnnVectorsFormat for field=\"" + field.name + "\"");
      }
      final String formatName = format.getName();

      field.putAttribute(PER_FIELD_FORMAT_KEY, formatName);
      Integer suffix;

      WriterAndSuffix writerAndSuffix = formats.get(format);
      if (writerAndSuffix == null) {
        // First time we are seeing this format; create a new instance

        suffix = suffixes.get(formatName);
        if (suffix == null) {
          suffix = 0;
        } else {
          suffix = suffix + 1;
        }
        suffixes.put(formatName, suffix);

        String segmentSuffix =
            getFullSegmentSuffix(
                segmentWriteState.segmentSuffix, getSuffix(formatName, Integer.toString(suffix)));
        writerAndSuffix =
            new WriterAndSuffix(
                format.fieldsWriter(new SegmentWriteState(segmentWriteState, segmentSuffix)),
                suffix);
        formats.put(format, writerAndSuffix);
      } else {
        // we've already seen this format, so just grab its suffix
        assert suffixes.containsKey(formatName);
        suffix = writerAndSuffix.suffix;
      }
      field.putAttribute(PER_FIELD_SUFFIX_KEY, Integer.toString(suffix));
      return writerAndSuffix.writer;
    }

    @Override
    public long ramBytesUsed() {
      long total = 0;
      for (WriterAndSuffix was : formats.values()) {
        total += was.writer.ramBytesUsed();
      }
      return total;
    }
  }

  /** VectorReader that can wrap multiple delegate readers, selected by field. */
  public static class FieldsReader extends KnnVectorsReader {

    private final Map<String, KnnVectorsReader> fields = new HashMap<>();

    /**
     * Create a FieldsReader over a segment, opening VectorReaders for each KnnVectorsFormat
     * specified by the indexed numeric vector fields.
     *
     * @param readState defines the fields
     * @throws IOException if one of the delegate readers throws
     */
    public FieldsReader(final SegmentReadState readState) throws IOException {

      // Init each unique format:
      boolean success = false;
      Map<String, KnnVectorsReader> formats = new HashMap<>();
      try {
        // Read field name -> format name
        for (FieldInfo fi : readState.fieldInfos) {
          if (fi.hasVectorValues()) {
            final String fieldName = fi.name;
            final String formatName = fi.getAttribute(PER_FIELD_FORMAT_KEY);
            if (formatName != null) {
              // null formatName means the field is in fieldInfos, but has no vectors!
              final String suffix = fi.getAttribute(PER_FIELD_SUFFIX_KEY);
              if (suffix == null) {
                throw new IllegalStateException(
                    "missing attribute: " + PER_FIELD_SUFFIX_KEY + " for field: " + fieldName);
              }
              KnnVectorsFormat format = KnnVectorsFormat.forName(formatName);
              String segmentSuffix =
                  getFullSegmentSuffix(readState.segmentSuffix, getSuffix(formatName, suffix));
              if (!formats.containsKey(segmentSuffix)) {
                formats.put(
                    segmentSuffix,
                    format.fieldsReader(new SegmentReadState(readState, segmentSuffix)));
              }
              fields.put(fieldName, formats.get(segmentSuffix));
            }
          }
        }
        success = true;
      } finally {
        if (!success) {
          IOUtils.closeWhileHandlingException(formats.values());
        }
      }
    }

    /**
     * Return the underlying VectorReader for the given field
     *
     * @param field the name of a numeric vector field
     */
    public KnnVectorsReader getFieldReader(String field) {
      return fields.get(field);
    }

    @Override
    public void checkIntegrity() throws IOException {
      for (KnnVectorsReader reader : fields.values()) {
        reader.checkIntegrity();
      }
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) throws IOException {
      KnnVectorsReader knnVectorsReader = fields.get(field);
      if (knnVectorsReader == null) {
        return null;
      } else {
        return knnVectorsReader.getFloatVectorValues(field);
      }
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) throws IOException {
      KnnVectorsReader knnVectorsReader = fields.get(field);
      if (knnVectorsReader == null) {
        return null;
      } else {
        return knnVectorsReader.getByteVectorValues(field);
      }
    }

    @Override
    public void search(String field, float[] target, KnnCollector knnCollector, Bits acceptDocs)
        throws IOException {
      fields.get(field).search(field, target, knnCollector, acceptDocs);
    }

    @Override
    public void search(String field, byte[] target, KnnCollector knnCollector, Bits acceptDocs)
        throws IOException {
      fields.get(field).search(field, target, knnCollector, acceptDocs);
    }

    @Override
    public void close() throws IOException {
      IOUtils.close(fields.values());
    }

    @Override
    public long ramBytesUsed() {
      long total = 0;
      for (KnnVectorsReader reader : fields.values()) {
        total += reader.ramBytesUsed();
      }
      return total;
    }
  }

  static String getSuffix(String formatName, String suffix) {
    return formatName + "_" + suffix;
  }

  static String getFullSegmentSuffix(String outerSegmentSuffix, String segmentSuffix) {
    if (outerSegmentSuffix.length() == 0) {
      return segmentSuffix;
    } else {
      return outerSegmentSuffix + "_" + segmentSuffix;
    }
  }

  private static class WriterAndSuffix implements Closeable {
    final KnnVectorsWriter writer;
    final int suffix;

    WriterAndSuffix(KnnVectorsWriter writer, int suffix) {
      this.writer = writer;
      this.suffix = suffix;
    }

    @Override
    public void close() throws IOException {
      writer.close();
    }
  }
}
