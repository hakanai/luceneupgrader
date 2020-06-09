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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PostingsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.perfield.PerFieldPostingsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.InfoStream;

public class SegmentWriteState {

  public final InfoStream infoStream;

  public final Directory directory;

  public final SegmentInfo segmentInfo;

  public final FieldInfos fieldInfos;

  public int delCountOnFlush;
  public int softDelCountOnFlush;
  public final BufferedUpdates segUpdates;

  public FixedBitSet liveDocs;

  public final String segmentSuffix;
  
  public final IOContext context;

  public SegmentWriteState(InfoStream infoStream, Directory directory, SegmentInfo segmentInfo, FieldInfos fieldInfos,
      BufferedUpdates segUpdates, IOContext context) {
    this(infoStream, directory, segmentInfo, fieldInfos, segUpdates, context, "");
  }

  public SegmentWriteState(InfoStream infoStream, Directory directory, SegmentInfo segmentInfo, FieldInfos fieldInfos,
      BufferedUpdates segUpdates, IOContext context, String segmentSuffix) {
    this.infoStream = infoStream;
    this.segUpdates = segUpdates;
    this.directory = directory;
    this.segmentInfo = segmentInfo;
    this.fieldInfos = fieldInfos;
    assert assertSegmentSuffix(segmentSuffix);
    this.segmentSuffix = segmentSuffix;
    this.context = context;
  }
  
  public SegmentWriteState(SegmentWriteState state, String segmentSuffix) {
    infoStream = state.infoStream;
    directory = state.directory;
    segmentInfo = state.segmentInfo;
    fieldInfos = state.fieldInfos;
    context = state.context;
    this.segmentSuffix = segmentSuffix;
    segUpdates = state.segUpdates;
    delCountOnFlush = state.delCountOnFlush;
    liveDocs = state.liveDocs;
  }
  
  // currently only used by assert? clean up and make real check?
  // either it's a segment suffix (_X_Y) or it's a parseable generation
  // TODO: this is very confusing how ReadersAndUpdates passes generations via
  // this mechanism, maybe add 'generation' explicitly to ctor create the 'actual suffix' here?
  private boolean assertSegmentSuffix(String segmentSuffix) {
    assert segmentSuffix != null;
    if (!segmentSuffix.isEmpty()) {
      int numParts = segmentSuffix.split("_").length;
      if (numParts == 2) {
        return true;
      } else if (numParts == 1) {
        Long.parseLong(segmentSuffix, Character.MAX_RADIX);
        return true;
      }
      return false; // invalid
    }
    return true;
  }
}
