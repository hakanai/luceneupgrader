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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.lucene60;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PointsFormat;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PointsWriter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;


public final class Lucene60PointsFormat extends PointsFormat {

  static final String DATA_CODEC_NAME = "Lucene60PointsFormatData";
  static final String META_CODEC_NAME = "Lucene60PointsFormatMeta";

  public static final String DATA_EXTENSION = "dim";

  public static final String INDEX_EXTENSION = "dii";

  static final int DATA_VERSION_START = 0;
  static final int DATA_VERSION_CURRENT = DATA_VERSION_START;

  static final int INDEX_VERSION_START = 0;
  static final int INDEX_VERSION_CURRENT = INDEX_VERSION_START;

  public Lucene60PointsFormat() {
  }

  @Override
  public PointsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new Lucene60PointsWriter(state);
  }

  @Override
  public PointsReader fieldsReader(SegmentReadState state) throws IOException {
    return new Lucene60PointsReader(state);
  }
}
