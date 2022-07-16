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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.lucene86;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.PointsFormat;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.PointsWriter;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.SegmentWriteState;

public final class Lucene86PointsFormat extends PointsFormat {

  static final String DATA_CODEC_NAME = "Lucene86PointsFormatData";
  static final String INDEX_CODEC_NAME = "Lucene86PointsFormatIndex";
  static final String META_CODEC_NAME = "Lucene86PointsFormatMeta";

  public static final String DATA_EXTENSION = "kdd";

  public static final String INDEX_EXTENSION = "kdi";

  public static final String META_EXTENSION = "kdm";

  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;

  public Lucene86PointsFormat() {
  }

  @Override
  public PointsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new Lucene86PointsWriter(state);
  }

  @Override
  public PointsReader fieldsReader(SegmentReadState state) throws IOException {
    return new Lucene86PointsReader(state);
  }
}
