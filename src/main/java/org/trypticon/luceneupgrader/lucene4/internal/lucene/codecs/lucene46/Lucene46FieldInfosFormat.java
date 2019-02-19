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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene46;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput;

public final class Lucene46FieldInfosFormat extends FieldInfosFormat {
  private final FieldInfosReader reader = new Lucene46FieldInfosReader();
  private final FieldInfosWriter writer = new Lucene46FieldInfosWriter();
  
  public Lucene46FieldInfosFormat() {
  }

  @Override
  public FieldInfosReader getFieldInfosReader() throws IOException {
    return reader;
  }

  @Override
  public FieldInfosWriter getFieldInfosWriter() throws IOException {
    return writer;
  }
  
  static final String EXTENSION = "fnm";
  
  // Codec header
  static final String CODEC_NAME = "Lucene46FieldInfos";
  static final int FORMAT_START = 0;
  static final int FORMAT_CHECKSUM = 1;
  static final int FORMAT_SORTED_NUMERIC = 2;
  static final int FORMAT_CURRENT = FORMAT_SORTED_NUMERIC;
  
  // Field flags
  static final byte IS_INDEXED = 0x1;
  static final byte STORE_TERMVECTOR = 0x2;
  static final byte STORE_OFFSETS_IN_POSTINGS = 0x4;
  static final byte OMIT_NORMS = 0x10;
  static final byte STORE_PAYLOADS = 0x20;
  static final byte OMIT_TERM_FREQ_AND_POSITIONS = 0x40;
  static final byte OMIT_POSITIONS = -128;
}
