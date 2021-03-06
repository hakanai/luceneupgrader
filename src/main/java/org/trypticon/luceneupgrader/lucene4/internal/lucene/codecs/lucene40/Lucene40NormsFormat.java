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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesConsumer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.CompoundFileDirectory;

@Deprecated
public class Lucene40NormsFormat extends NormsFormat {

  public Lucene40NormsFormat() {}
  
  @Override
  public DocValuesConsumer normsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }

  @Override
  public DocValuesProducer normsProducer(SegmentReadState state) throws IOException {
    String filename = IndexFileNames.segmentFileName(state.segmentInfo.name, 
                                                     "nrm", 
                                                     IndexFileNames.COMPOUND_FILE_EXTENSION);
    return new Lucene40DocValuesReader(state, filename, Lucene40FieldInfosReader.LEGACY_NORM_TYPE_KEY);
  }
}
