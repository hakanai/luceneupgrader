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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs;


import java.io.IOException;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentCommitInfo;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.MutableBits;

public abstract class LiveDocsFormat {

  protected LiveDocsFormat() {
  }

  public abstract MutableBits newLiveDocs(int size) throws IOException;

  public abstract MutableBits newLiveDocs(Bits existing) throws IOException;

  public abstract Bits readLiveDocs(Directory dir, SegmentCommitInfo info, IOContext context) throws IOException;


  public abstract void writeLiveDocs(MutableBits bits, Directory dir, SegmentCommitInfo info, int newDelCount, IOContext context) throws IOException;

  public abstract void files(SegmentCommitInfo info, Collection<String> files) throws IOException;
}
