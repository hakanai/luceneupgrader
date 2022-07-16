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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene87;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.StoredFieldsWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing.CompressingStoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.compressing.CompressionMode;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.packed.DirectMonotonicWriter;

public class Lucene87StoredFieldsFormat extends StoredFieldsFormat {
  
  public static enum Mode {
    BEST_SPEED,
    BEST_COMPRESSION
  }
  
  public static final String MODE_KEY = Lucene87StoredFieldsFormat.class.getSimpleName() + ".mode";
  
  final Mode mode;
  
  public Lucene87StoredFieldsFormat() {
    this(Mode.BEST_SPEED);
  }

  public Lucene87StoredFieldsFormat(Mode mode) {
    this.mode = Objects.requireNonNull(mode);
  }

  @Override
  public StoredFieldsReader fieldsReader(Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    String value = si.getAttribute(MODE_KEY);
    if (value == null) {
      throw new IllegalStateException("missing value for " + MODE_KEY + " for segment: " + si.name);
    }
    Mode mode = Mode.valueOf(value);
    return impl(mode).fieldsReader(directory, si, fn, context);
  }

  @Override
  public StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo si, IOContext context) throws IOException {
    String previous = si.putAttribute(MODE_KEY, mode.name());
    if (previous != null && previous.equals(mode.name()) == false) {
      throw new IllegalStateException("found existing value for " + MODE_KEY + " for segment: " + si.name +
                                      "old=" + previous + ", new=" + mode.name());
    }
    return impl(mode).fieldsWriter(directory, si, context);
  }
  
  StoredFieldsFormat impl(Mode mode) {
    switch (mode) {
      case BEST_SPEED:
        return new CompressingStoredFieldsFormat("Lucene87StoredFieldsFastData", BEST_SPEED_MODE, BEST_SPEED_BLOCK_LENGTH, 1024, 10);
      case BEST_COMPRESSION:
        return new CompressingStoredFieldsFormat("Lucene87StoredFieldsHighData", BEST_COMPRESSION_MODE, BEST_COMPRESSION_BLOCK_LENGTH, 4096, 10);
      default: throw new AssertionError();
    }
  }

  // Shoot for 10 sub blocks of 48kB each.
  private static final int BEST_COMPRESSION_BLOCK_LENGTH = 10 * 48 * 1024;

  public static final CompressionMode BEST_COMPRESSION_MODE = new DeflateWithPresetDictCompressionMode();

  // Shoot for 10 sub blocks of 8kB each.
  private static final int BEST_SPEED_BLOCK_LENGTH = 10 * 8 * 1024;

  public static final CompressionMode BEST_SPEED_MODE = new LZ4WithPresetDictCompressionMode();

}
