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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.DocumentsWriterPerThreadPool.ThreadState;

class FlushByRamOrCountsPolicy extends FlushPolicy {

  @Override
  public void onDelete(DocumentsWriterFlushControl control, ThreadState state) {
    if (flushOnDeleteTerms()) {
      // Flush this state by num del terms
      final int maxBufferedDeleteTerms = indexWriterConfig
          .getMaxBufferedDeleteTerms();
      if (control.getNumGlobalTermDeletes() >= maxBufferedDeleteTerms) {
        control.setApplyAllDeletes();
      }
    }
    if ((flushOnRAM() &&
        control.getDeleteBytesUsed() > (1024*1024*indexWriterConfig.getRAMBufferSizeMB()))) {
      control.setApplyAllDeletes();
     if (infoStream.isEnabled("FP")) {
       infoStream.message("FP", "force apply deletes bytesUsed=" + control.getDeleteBytesUsed() + " vs ramBufferMB=" + indexWriterConfig.getRAMBufferSizeMB());
     }
   }
  }

  @Override
  public void onInsert(DocumentsWriterFlushControl control, ThreadState state) {
    if (flushOnDocCount()
        && state.dwpt.getNumDocsInRAM() >= indexWriterConfig
            .getMaxBufferedDocs()) {
      // Flush this state by num docs
      control.setFlushPending(state);
    } else if (flushOnRAM()) {// flush by RAM
      final long limit = (long) (indexWriterConfig.getRAMBufferSizeMB() * 1024.d * 1024.d);
      final long totalRam = control.activeBytes() + control.getDeleteBytesUsed();
      if (totalRam >= limit) {
        if (infoStream.isEnabled("FP")) {
          infoStream.message("FP", "trigger flush: activeBytes=" + control.activeBytes() + " deleteBytes=" + control.getDeleteBytesUsed() + " vs limit=" + limit);
        }
        markLargestWriterPending(control, state, totalRam);
      }
    }
  }
  
  protected void markLargestWriterPending(DocumentsWriterFlushControl control,
      ThreadState perThreadState, final long currentBytesPerThread) {
    control.setFlushPending(findLargestNonPendingWriter(control, perThreadState));
  }
  
  protected boolean flushOnDocCount() {
    return indexWriterConfig.getMaxBufferedDocs() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }

  protected boolean flushOnDeleteTerms() {
    return indexWriterConfig.getMaxBufferedDeleteTerms() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }

  protected boolean flushOnRAM() {
    return indexWriterConfig.getRAMBufferSizeMB() != IndexWriterConfig.DISABLE_AUTO_FLUSH;
  }
}
