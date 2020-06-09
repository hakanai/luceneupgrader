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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.lucene70;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexWriterConfig;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.SmallFloat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed.DirectWriter;

public final class Lucene70DocValuesFormat extends DocValuesFormat {

  public Lucene70DocValuesFormat() {
    super("Lucene70");
  }

  @Override
  public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    return new Lucene70DocValuesConsumer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }

  @Override
  public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
    return new Lucene70DocValuesProducer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }

  static final String DATA_CODEC = "Lucene70DocValuesData";
  static final String DATA_EXTENSION = "dvd";
  static final String META_CODEC = "Lucene70DocValuesMetadata";
  static final String META_EXTENSION = "dvm";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  // indicates docvalues type
  static final byte NUMERIC = 0;
  static final byte BINARY = 1;
  static final byte SORTED = 2;
  static final byte SORTED_SET = 3;
  static final byte SORTED_NUMERIC = 4;

  static final int DIRECT_MONOTONIC_BLOCK_SHIFT = 16;

  static final int NUMERIC_BLOCK_SHIFT = 14;
  static final int NUMERIC_BLOCK_SIZE = 1 << NUMERIC_BLOCK_SHIFT;

  static final int TERMS_DICT_BLOCK_SHIFT = 4;
  static final int TERMS_DICT_BLOCK_SIZE = 1 << TERMS_DICT_BLOCK_SHIFT;
  static final int TERMS_DICT_BLOCK_MASK = TERMS_DICT_BLOCK_SIZE - 1;

  static final int TERMS_DICT_REVERSE_INDEX_SHIFT = 10;
  static final int TERMS_DICT_REVERSE_INDEX_SIZE = 1 << TERMS_DICT_REVERSE_INDEX_SHIFT;
  static final int TERMS_DICT_REVERSE_INDEX_MASK = TERMS_DICT_REVERSE_INDEX_SIZE - 1;
}
