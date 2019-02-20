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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsBaseFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsReaderBase;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsWriterBase;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;



// TODO: should these also be named / looked up via SPI?
@Deprecated
public final class Lucene40PostingsBaseFormat extends PostingsBaseFormat {

  public Lucene40PostingsBaseFormat() {
    super("Lucene40");
  }

  @Override
  public PostingsReaderBase postingsReaderBase(SegmentReadState state) throws IOException {
    return new Lucene40PostingsReader(state.directory, state.fieldInfos, state.segmentInfo, state.context, state.segmentSuffix);
  }

  @Override
  public PostingsWriterBase postingsWriterBase(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }
}
