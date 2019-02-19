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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene49;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsConsumer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.UndeadNormsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentWriteState;

@Deprecated
public class Lucene49NormsFormat extends NormsFormat {

  public Lucene49NormsFormat() {}
  
  @Override
  public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }

  @Override
  public final NormsProducer normsProducer(SegmentReadState state) throws IOException {
    if (UndeadNormsProducer.isUndeadArmy(state.fieldInfos)) {
      return UndeadNormsProducer.INSTANCE;
    } else {
      return new Lucene49NormsProducer(state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION);
    }
  }
  
  static final String DATA_CODEC = "Lucene49NormsData";
  static final String DATA_EXTENSION = "nvd";
  static final String METADATA_CODEC = "Lucene49NormsMetadata";
  static final String METADATA_EXTENSION = "nvm";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
}
