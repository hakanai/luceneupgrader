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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs;


import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FixedBitSet;

import java.io.Closeable;
import java.io.IOException;

// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == FieldsProducer/Consumer
public abstract class PostingsWriterBase implements Closeable {

  protected PostingsWriterBase() {
  }

  public abstract void init(IndexOutput termsOut, SegmentWriteState state) throws IOException;

  public abstract BlockTermState writeTerm(BytesRef term, TermsEnum termsEnum, FixedBitSet docsSeen) throws IOException;

  public abstract void encodeTerm(long[] longs, DataOutput out, FieldInfo fieldInfo, BlockTermState state, boolean absolute) throws IOException;

  // TODO: better name?
  public abstract int setField(FieldInfo fieldInfo);

  @Override
  public abstract void close() throws IOException;
}
