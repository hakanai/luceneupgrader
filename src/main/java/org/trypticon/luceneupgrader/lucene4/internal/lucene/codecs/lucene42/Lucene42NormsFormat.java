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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene42;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed.PackedInts;

public class Lucene42NormsFormat extends NormsFormat {
  final float acceptableOverheadRatio;


  public Lucene42NormsFormat() {
    // note: we choose FASTEST here (otherwise our norms are half as big but 15% slower than previous lucene)
    this(PackedInts.FASTEST);
  }
  
  public Lucene42NormsFormat(float acceptableOverheadRatio) {
    this.acceptableOverheadRatio = acceptableOverheadRatio;
  }
  
  @Override
  public DocValuesConsumer normsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }
  
  @Override
  public DocValuesProducer normsProducer(SegmentReadState state) throws IOException {
    return new Lucene42DocValuesProducer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION);
  }
  
  static final String DATA_CODEC = "Lucene41NormsData";
  static final String DATA_EXTENSION = "nvd";
  static final String METADATA_CODEC = "Lucene41NormsMetadata";
  static final String METADATA_EXTENSION = "nvm";
}
