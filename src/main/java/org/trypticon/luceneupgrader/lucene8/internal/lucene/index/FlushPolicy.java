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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.InfoStream;

abstract class FlushPolicy {
  protected LiveIndexWriterConfig indexWriterConfig;
  protected InfoStream infoStream;

  public abstract void onDelete(DocumentsWriterFlushControl control,
                                DocumentsWriterPerThread perThread);

  public void onUpdate(DocumentsWriterFlushControl control, DocumentsWriterPerThread perThread) {
    onInsert(control, perThread);
    onDelete(control, perThread);
  }

  public abstract void onInsert(DocumentsWriterFlushControl control,
                                DocumentsWriterPerThread perThread);

  protected synchronized void init(LiveIndexWriterConfig indexWriterConfig) {
    this.indexWriterConfig = indexWriterConfig;
    infoStream = indexWriterConfig.getInfoStream();
  }

  protected DocumentsWriterPerThread findLargestNonPendingWriter(
      DocumentsWriterFlushControl control, DocumentsWriterPerThread perThread) {
    assert perThread.getNumDocsInRAM() > 0;
    // the dwpt which needs to be flushed eventually
    DocumentsWriterPerThread maxRamUsingWriter = control.findLargestNonPendingWriter();
    assert assertMessage("set largest ram consuming thread pending on lower watermark");
    return maxRamUsingWriter;
  }
  
  private boolean assertMessage(String s) {
    if (infoStream.isEnabled("FP")) {
      infoStream.message("FP", s);
    }
    return true;
  }
}
