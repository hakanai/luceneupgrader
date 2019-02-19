package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Iterator;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocumentsWriterPerThreadPool.ThreadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream;

abstract class FlushPolicy {
  protected LiveIndexWriterConfig indexWriterConfig;
  protected InfoStream infoStream;

  public abstract void onDelete(DocumentsWriterFlushControl control,
      ThreadState state);

  public void onUpdate(DocumentsWriterFlushControl control, ThreadState state) {
    onInsert(control, state);
    onDelete(control, state);
  }

  public abstract void onInsert(DocumentsWriterFlushControl control,
      ThreadState state);

  protected synchronized void init(LiveIndexWriterConfig indexWriterConfig) {
    this.indexWriterConfig = indexWriterConfig;
    infoStream = indexWriterConfig.getInfoStream();
  }

  protected ThreadState findLargestNonPendingWriter(
      DocumentsWriterFlushControl control, ThreadState perThreadState) {
    assert perThreadState.dwpt.getNumDocsInRAM() > 0;
    long maxRamSoFar = perThreadState.bytesUsed;
    // the dwpt which needs to be flushed eventually
    ThreadState maxRamUsingThreadState = perThreadState;
    assert !perThreadState.flushPending : "DWPT should have flushed";
    Iterator<ThreadState> activePerThreadsIterator = control.allActiveThreadStates();
    int count = 0;
    while (activePerThreadsIterator.hasNext()) {
      ThreadState next = activePerThreadsIterator.next();
      if (!next.flushPending) {
        final long nextRam = next.bytesUsed;
        if (nextRam > 0 && next.dwpt.getNumDocsInRAM() > 0) {
          if (infoStream.isEnabled("FP")) {
            infoStream.message("FP", "thread state has " + nextRam + " bytes; docInRAM=" + next.dwpt.getNumDocsInRAM());
          }
          count++;
          if (nextRam > maxRamSoFar) {
            maxRamSoFar = nextRam;
            maxRamUsingThreadState = next;
          }
        }
      }
    }
    if (infoStream.isEnabled("FP")) {
      infoStream.message("FP", count + " in-use non-flushing threads states");
    }
    assert assertMessage("set largest ram consuming thread pending on lower watermark");
    return maxRamUsingThreadState;
  }
  
  private boolean assertMessage(String s) {
    if (infoStream.isEnabled("FP")) {
      infoStream.message("FP", s);
    }
    return true;
  }
}
