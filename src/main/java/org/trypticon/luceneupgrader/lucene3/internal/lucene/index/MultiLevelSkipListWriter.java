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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.RAMOutputStream;

abstract class MultiLevelSkipListWriter {
  // number of levels in this skip list
  private int numberOfSkipLevels;
  
  // the skip interval in the list with level = 0
  private int skipInterval;
  
  // for every skip level a different buffer is used 
  private RAMOutputStream[] skipBuffer;

  protected MultiLevelSkipListWriter(int skipInterval, int maxSkipLevels, int df) {
    this.skipInterval = skipInterval;
    
    // calculate the maximum number of skip levels for this document frequency
    numberOfSkipLevels = df == 0 ? 0 : (int) Math.floor(Math.log(df) / Math.log(skipInterval));
    
    // make sure it does not exceed maxSkipLevels
    if (numberOfSkipLevels > maxSkipLevels) {
      numberOfSkipLevels = maxSkipLevels;
    }
  }
  
  protected void init() {
    skipBuffer = new RAMOutputStream[numberOfSkipLevels];
    for (int i = 0; i < numberOfSkipLevels; i++) {
      skipBuffer[i] = new RAMOutputStream();
    }
  }

  protected void resetSkip() {
    // creates new buffers or empties the existing ones
    if (skipBuffer == null) {
      init();
    } else {
      for (int i = 0; i < skipBuffer.length; i++) {
        skipBuffer[i].reset();
      }
    }      
  }

  protected abstract void writeSkipData(int level, IndexOutput skipBuffer) throws IOException;
  
  void bufferSkip(int df) throws IOException {
    int numLevels;
   
    // determine max level
    for (numLevels = 0; (df % skipInterval) == 0 && numLevels < numberOfSkipLevels; df /= skipInterval) {
      numLevels++;
    }
    
    long childPointer = 0;
    
    for (int level = 0; level < numLevels; level++) {
      writeSkipData(level, skipBuffer[level]);
      
      long newChildPointer = skipBuffer[level].getFilePointer();
      
      if (level != 0) {
        // store child pointers for all levels except the lowest
        skipBuffer[level].writeVLong(childPointer);
      }
      
      //remember the childPointer for the next level
      childPointer = newChildPointer;
    }
  }

  long writeSkip(IndexOutput output) throws IOException {
    long skipPointer = output.getFilePointer();
    if (skipBuffer == null || skipBuffer.length == 0) return skipPointer;
    
    for (int level = numberOfSkipLevels - 1; level > 0; level--) {
      long length = skipBuffer[level].getFilePointer();
      if (length > 0) {
        output.writeVLong(length);
        skipBuffer[level].writeTo(output);
      }
    }
    skipBuffer[0].writeTo(output);
    
    return skipPointer;
  }

}
