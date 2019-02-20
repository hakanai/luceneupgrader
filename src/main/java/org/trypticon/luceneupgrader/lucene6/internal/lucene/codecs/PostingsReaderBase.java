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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;


import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;



// TODO: maybe move under blocktree?  but it's used by other terms dicts (e.g. Block)

// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == PostingsConsumer/Producer
public abstract class PostingsReaderBase implements Closeable, Accountable {

  protected PostingsReaderBase() {
  }


  public abstract void init(IndexInput termsIn, SegmentReadState state) throws IOException;

  public abstract BlockTermState newTermState() throws IOException;


  public abstract void decodeTerm(long[] longs, DataInput in, FieldInfo fieldInfo, BlockTermState state, boolean absolute) throws IOException;

  public abstract PostingsEnum postings(FieldInfo fieldInfo, BlockTermState state, PostingsEnum reuse, int flags) throws IOException;
  

  public abstract void checkIntegrity() throws IOException;

  @Override
  public abstract void close() throws IOException;
}
