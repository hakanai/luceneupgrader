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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexWriter; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfos; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput; // javadocs

@Deprecated
public class Lucene40SegmentInfoFormat extends SegmentInfoFormat {
  private final SegmentInfoReader reader = new Lucene40SegmentInfoReader();
  private final SegmentInfoWriter writer = new Lucene40SegmentInfoWriter();

  public Lucene40SegmentInfoFormat() {
  }
  
  @Override
  public SegmentInfoReader getSegmentInfoReader() {
    return reader;
  }

  // we must unfortunately support write, to allow addIndexes to write a new .si with rewritten filenames:
  // see LUCENE-5377
  @Override
  public SegmentInfoWriter getSegmentInfoWriter() {
    return writer;
  }

  public final static String SI_EXTENSION = "si";
  static final String CODEC_NAME = "Lucene40SegmentInfo";
  static final int VERSION_START = 0;
  static final int VERSION_CURRENT = VERSION_START;
}
