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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene50;


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

public class Lucene50StoredFieldsFormat extends StoredFieldsFormat {
  
  public static enum Mode {
    BEST_SPEED,
    BEST_COMPRESSION
  }
  
  public static final String MODE_KEY = Lucene50StoredFieldsFormat.class.getSimpleName() + ".mode";
  
  final Mode mode;
  
  public Lucene50StoredFieldsFormat() {
    this(Mode.BEST_SPEED);
  }
  
  public Lucene50StoredFieldsFormat(Mode mode) {
    this.mode = Objects.requireNonNull(mode);
  }

  @Override
  public final StoredFieldsReader fieldsReader(Directory directory, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    String value = si.getAttribute(MODE_KEY);
    if (value == null) {
      throw new IllegalStateException("missing value for " + MODE_KEY + " for segment: " + si.name);
    }
    Mode mode = Mode.valueOf(value);
    return impl(mode).fieldsReader(directory, si, fn, context);
  }

  @Override
  public StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo si, IOContext context) throws IOException {
    throw new UnsupportedOperationException("Old codecs may only be used for reading");
  }
  
  StoredFieldsFormat impl(Mode mode) {
    switch (mode) {
      case BEST_SPEED: 
        return new CompressingStoredFieldsFormat("Lucene50StoredFieldsFastData", CompressionMode.FAST, 1 << 14, 128, 10);
      case BEST_COMPRESSION: 
        return new CompressingStoredFieldsFormat("Lucene50StoredFieldsHighData", CompressionMode.HIGH_COMPRESSION, 61440, 512, 10);
      default: throw new AssertionError();
    }
  }
}
