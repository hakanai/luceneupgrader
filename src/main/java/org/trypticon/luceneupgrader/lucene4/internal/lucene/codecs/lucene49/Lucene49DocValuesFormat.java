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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene49;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.SmallFloat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.fst.FST;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.DirectWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.MonotonicBlockPackedWriter;

import java.io.IOException;

public class Lucene49DocValuesFormat extends DocValuesFormat {

  public Lucene49DocValuesFormat() {
    super("Lucene49");
  }

  @Override
  public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    return new Lucene49DocValuesConsumer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }

  @Override
  public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
    return new Lucene49DocValuesProducer(state, DATA_CODEC, DATA_EXTENSION, META_CODEC, META_EXTENSION);
  }
  
  static final String DATA_CODEC = "Lucene49DocValuesData";
  static final String DATA_EXTENSION = "dvd";
  static final String META_CODEC = "Lucene49ValuesMetadata";
  static final String META_EXTENSION = "dvm";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
  static final byte NUMERIC = 0;
  static final byte BINARY = 1;
  static final byte SORTED = 2;
  static final byte SORTED_SET = 3;
  static final byte SORTED_NUMERIC = 4;
}
