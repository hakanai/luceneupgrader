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


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.RAMOutputStream;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.MathUtil;

public abstract class MultiLevelSkipListWriter {
  protected int numberOfSkipLevels;
  
  private int skipInterval;

  private int skipMultiplier;
  
  private RAMOutputStream[] skipBuffer;

  protected MultiLevelSkipListWriter(int skipInterval, int skipMultiplier, int maxSkipLevels, int df) {
    this.skipInterval = skipInterval;
    this.skipMultiplier = skipMultiplier;
    
    // calculate the maximum number of skip levels for this document frequency
    if (df <= skipInterval) {
      numberOfSkipLevels = 1;
    } else {
      numberOfSkipLevels = 1+MathUtil.log(df/skipInterval, skipMultiplier);
    }
    
    // make sure it does not exceed maxSkipLevels
    if (numberOfSkipLevels > maxSkipLevels) {
      numberOfSkipLevels = maxSkipLevels;
    }
  }
  

  protected MultiLevelSkipListWriter(int skipInterval, int maxSkipLevels, int df) {
    this(skipInterval, skipInterval, maxSkipLevels, df);
  }

  protected void init() {
    skipBuffer = new RAMOutputStream[numberOfSkipLevels];
    for (int i = 0; i < numberOfSkipLevels; i++) {
      skipBuffer[i] = new RAMOutputStream();
    }
  }

  protected void resetSkip() {
    if (skipBuffer == null) {
      init();
    } else {
      for (int i = 0; i < skipBuffer.length; i++) {
        skipBuffer[i].reset();
      }
    }      
  }

  protected abstract void writeSkipData(int level, IndexOutput skipBuffer) throws IOException;

  public void bufferSkip(int df) throws IOException {

    assert df % skipInterval == 0;
    int numLevels = 1;
    df /= skipInterval;
   
    // determine max level
    while ((df % skipMultiplier) == 0 && numLevels < numberOfSkipLevels) {
      numLevels++;
      df /= skipMultiplier;
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

  public long writeSkip(IndexOutput output) throws IOException {
    long skipPointer = output.getFilePointer();
    //System.out.println("skipper.writeSkip fp=" + skipPointer);
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
