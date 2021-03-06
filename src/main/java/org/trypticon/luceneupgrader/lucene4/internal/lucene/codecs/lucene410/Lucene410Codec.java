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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene410;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FilterCodec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40.Lucene40LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene41.Lucene41StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene42.Lucene42TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene46.Lucene46FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene46.Lucene46SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene49.Lucene49NormsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.perfield.PerFieldPostingsFormat;

// NOTE: if we make largish changes in a minor release, easier to just make Lucene411Codec or whatever
// if they are backwards compatible or smallish we can probably do the backwards in the postingsreader
// (it writes a minor version, etc).
public class Lucene410Codec extends Codec {
  private final StoredFieldsFormat fieldsFormat = new Lucene41StoredFieldsFormat();
  private final TermVectorsFormat vectorsFormat = new Lucene42TermVectorsFormat();
  private final FieldInfosFormat fieldInfosFormat = new Lucene46FieldInfosFormat();
  private final SegmentInfoFormat segmentInfosFormat = new Lucene46SegmentInfoFormat();
  private final LiveDocsFormat liveDocsFormat = new Lucene40LiveDocsFormat();
  
  private final PostingsFormat postingsFormat = new PerFieldPostingsFormat() {
    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
      return Lucene410Codec.this.getPostingsFormatForField(field);
    }
  };
  
  private final DocValuesFormat docValuesFormat = new PerFieldDocValuesFormat() {
    @Override
    public DocValuesFormat getDocValuesFormatForField(String field) {
      return Lucene410Codec.this.getDocValuesFormatForField(field);
    }
  };

  public Lucene410Codec() {
    super("Lucene410");
  }
  
  @Override
  public final StoredFieldsFormat storedFieldsFormat() {
    return fieldsFormat;
  }
  
  @Override
  public final TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }

  @Override
  public final PostingsFormat postingsFormat() {
    return postingsFormat;
  }
  
  @Override
  public final FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }
  
  @Override
  public final SegmentInfoFormat segmentInfoFormat() {
    return segmentInfosFormat;
  }
  
  @Override
  public final LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }


  public PostingsFormat getPostingsFormatForField(String field) {
    return defaultFormat;
  }
  

  public DocValuesFormat getDocValuesFormatForField(String field) {
    return defaultDVFormat;
  }
  
  @Override
  public final DocValuesFormat docValuesFormat() {
    return docValuesFormat;
  }

  private final PostingsFormat defaultFormat = PostingsFormat.forName("Lucene41");
  private final DocValuesFormat defaultDVFormat = DocValuesFormat.forName("Lucene410");

  private final NormsFormat normsFormat = new Lucene49NormsFormat();

  @Override
  public final NormsFormat normsFormat() {
    return normsFormat;
  }
}
