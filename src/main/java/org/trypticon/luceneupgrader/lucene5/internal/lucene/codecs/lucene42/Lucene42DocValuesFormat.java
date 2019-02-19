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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene42;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.packed.PackedInts;

@Deprecated
public class Lucene42DocValuesFormat extends DocValuesFormat {

  static final int MAX_BINARY_FIELD_LENGTH = (1 << 15) - 2;
  
  final float acceptableOverheadRatio;
  

  public Lucene42DocValuesFormat() {
    this(PackedInts.DEFAULT);
  }
  
  public Lucene42DocValuesFormat(float acceptableOverheadRatio) {
    super("Lucene42");
    this.acceptableOverheadRatio = acceptableOverheadRatio;
  }

  @Override
  public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }
  
  @Override
  public final DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
    return new Lucene42DocValuesProducer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION);
  }
  
  static final String DATA_CODEC = "Lucene42DocValuesData";
  static final String DATA_EXTENSION = "dvd";
  static final String METADATA_CODEC = "Lucene42DocValuesMetadata";
  static final String METADATA_EXTENSION = "dvm";
}
