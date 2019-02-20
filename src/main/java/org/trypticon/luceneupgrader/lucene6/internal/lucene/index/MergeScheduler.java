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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.MergePolicy.OneMerge;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.RateLimitedIndexOutput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.InfoStream;

public abstract class MergeScheduler implements Closeable {

  protected MergeScheduler() {
  }


  public abstract void merge(IndexWriter writer, MergeTrigger trigger, boolean newMergesFound) throws IOException;


  public Directory wrapForMerge(OneMerge merge, Directory in) {
    // A no-op by default.
    return in;
  }

  @Override
  public abstract void close() throws IOException;

  protected InfoStream infoStream;

  final void setInfoStream(InfoStream infoStream) {
    this.infoStream = infoStream;
  }

  protected boolean verbose() {
    return infoStream != null && infoStream.isEnabled("MS");
  }
 
  protected void message(String message) {
    infoStream.message("MS", message);
  }
}
