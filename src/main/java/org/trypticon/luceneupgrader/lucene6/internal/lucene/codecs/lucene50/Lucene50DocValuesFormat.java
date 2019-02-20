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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.lucene50;


import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentWriteState;

import java.io.IOException;

@Deprecated
public class Lucene50DocValuesFormat extends DocValuesFormat {

  public Lucene50DocValuesFormat() {
    super("Lucene50");
  }

  @Override
  public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    return new Lucene50DocValuesConsumer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }

  @Override
  public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
    return new Lucene50DocValuesProducer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }
  
  static final String DATA_CODEC = "Lucene50DocValuesData";
  static final String DATA_EXTENSION = "dvd";
  static final String META_CODEC = "Lucene50DocValuesMetadata";
  static final String META_EXTENSION = "dvm";
  static final int VERSION_START = 0;
  static final int VERSION_SORTEDSET_TABLE = 1;
  static final int VERSION_CURRENT = VERSION_SORTEDSET_TABLE;
  
  // indicates docvalues type
  static final byte NUMERIC = 0;
  static final byte BINARY = 1;
  static final byte SORTED = 2;
  static final byte SORTED_SET = 3;
  static final byte SORTED_NUMERIC = 4;
  
  // address terms in blocks of 16 terms
  static final int INTERVAL_SHIFT = 4;
  static final int INTERVAL_COUNT = 1 << INTERVAL_SHIFT;
  static final int INTERVAL_MASK = INTERVAL_COUNT - 1;
  
  // build reverse index from every 1024th term
  static final int REVERSE_INTERVAL_SHIFT = 10;
  static final int REVERSE_INTERVAL_COUNT = 1 << REVERSE_INTERVAL_SHIFT;
  static final int REVERSE_INTERVAL_MASK = REVERSE_INTERVAL_COUNT - 1;
  
  // for conversion from reverse index to block
  static final int BLOCK_INTERVAL_SHIFT = REVERSE_INTERVAL_SHIFT - INTERVAL_SHIFT;
  static final int BLOCK_INTERVAL_COUNT = 1 << BLOCK_INTERVAL_SHIFT;
  static final int BLOCK_INTERVAL_MASK = BLOCK_INTERVAL_COUNT - 1;

  static final int DELTA_COMPRESSED = 0;
  static final int GCD_COMPRESSED = 1;
  static final int TABLE_COMPRESSED = 2;
  static final int MONOTONIC_COMPRESSED = 3;
  static final int CONST_COMPRESSED = 4;
  
  static final int BINARY_FIXED_UNCOMPRESSED = 0;
  static final int BINARY_VARIABLE_UNCOMPRESSED = 1;
  static final int BINARY_PREFIX_COMPRESSED = 2;

  static final int SORTED_WITH_ADDRESSES = 0;
  static final int SORTED_SINGLE_VALUED = 1;
  static final int SORTED_SET_TABLE = 2;
  
  static final int ALL_LIVE = -1;
  static final int ALL_MISSING = -2;
  
  // addressing uses 16k blocks
  static final int MONOTONIC_BLOCK_SIZE = 16384;
}
