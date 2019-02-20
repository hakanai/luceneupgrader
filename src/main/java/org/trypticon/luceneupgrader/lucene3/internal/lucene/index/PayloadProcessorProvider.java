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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;

public abstract class PayloadProcessorProvider {

  public static abstract class ReaderPayloadProcessor {

    public abstract PayloadProcessor getProcessor(Term term) throws IOException;
    
  }

  @Deprecated
  public static abstract class DirPayloadProcessor extends ReaderPayloadProcessor {}

  public static abstract class PayloadProcessor {

    public abstract int payloadLength() throws IOException;

    public abstract byte[] processPayload(byte[] payload, int start, int length) throws IOException;

  }

  public ReaderPayloadProcessor getReaderProcessor(IndexReader reader) throws IOException {
    return this.getDirProcessor(reader.directory());
  }

  @Deprecated
  public DirPayloadProcessor getDirProcessor(Directory dir) throws IOException {
    throw new UnsupportedOperationException("You must either implement getReaderProcessor() or getDirProcessor().");
  }
  
}
