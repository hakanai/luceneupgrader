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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.NormsConsumer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;

public class Lucene70NormsFormat extends NormsFormat {

  public Lucene70NormsFormat() {}
  
  @Override
  public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
    return new Lucene70NormsConsumer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION);
  }

  @Override
  public NormsProducer normsProducer(SegmentReadState state) throws IOException {
    return new Lucene70NormsProducer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION);
  }
  
  private static final String DATA_CODEC = "Lucene70NormsData";
  private static final String DATA_EXTENSION = "nvd";
  private static final String METADATA_CODEC = "Lucene70NormsMetadata";
  private static final String METADATA_EXTENSION = "nvm";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
}
