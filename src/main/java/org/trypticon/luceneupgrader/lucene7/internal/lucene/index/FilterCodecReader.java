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


import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.DocValuesProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.NormsProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.PointsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.TermVectorsReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;

public abstract class FilterCodecReader extends CodecReader {

  public static CodecReader unwrap(CodecReader reader) {
    while (reader instanceof FilterCodecReader) {
      reader = ((FilterCodecReader) reader).getDelegate();
    }
    return reader;
  }
  protected final CodecReader in;
  
  public FilterCodecReader(CodecReader in) {
    this.in = Objects.requireNonNull(in);
  }

  @Override
  public StoredFieldsReader getFieldsReader() {
    return in.getFieldsReader();
  }

  @Override
  public TermVectorsReader getTermVectorsReader() {
    return in.getTermVectorsReader();
  }

  @Override
  public NormsProducer getNormsReader() {
    return in.getNormsReader();
  }

  @Override
  public DocValuesProducer getDocValuesReader() {
    return in.getDocValuesReader();
  }

  @Override
  public FieldsProducer getPostingsReader() {
    return in.getPostingsReader();
  }

  @Override
  public Bits getLiveDocs() {
    return in.getLiveDocs();
  }

  @Override
  public FieldInfos getFieldInfos() {
    return in.getFieldInfos();
  }

  @Override
  public PointsReader getPointsReader() {
    return in.getPointsReader();
  }

  @Override
  public int numDocs() {
    return in.numDocs();
  }

  @Override
  public int maxDoc() {
    return in.maxDoc();
  }

  @Override
  public LeafMetaData getMetaData() {
    return in.getMetaData();
  }

  @Override
  protected void doClose() throws IOException {
    in.doClose();
  }

  @Override
  public long ramBytesUsed() {
    return in.ramBytesUsed();
  }

  @Override
  public Collection<Accountable> getChildResources() {
    return in.getChildResources();
  }

  @Override
  public void checkIntegrity() throws IOException {
    in.checkIntegrity();
  }

  public CodecReader getDelegate() {
    return in;
  }

  static FilterCodecReader wrapLiveDocs(CodecReader reader, Bits liveDocs, int numDocs) {
    return new FilterCodecReader(reader) {
      @Override
      public CacheHelper getCoreCacheHelper() {
        return reader.getCoreCacheHelper();
      }
      @Override
      public CacheHelper getReaderCacheHelper() {
        return null; // we are altering live docs
      }
      @Override
      public Bits getLiveDocs() {
        return liveDocs;
      }
      @Override
      public int numDocs() {
        return numDocs;
      }
    };
  }
}
