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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs;

import java.io.IOException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.ByteBuffersDataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.MathUtil;

/**
 * This abstract class writes skip lists with multiple levels.
 *
 * <pre>
 *
 * Example for skipInterval = 3:
 *                                                     c            (skip level 2)
 *                 c                 c                 c            (skip level 1)
 *     x     x     x     x     x     x     x     x     x     x      (skip level 0)
 * d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d d  (posting list)
 *     3     6     9     12    15    18    21    24    27    30     (df)
 *
 * d - document
 * x - skip data
 * c - skip data with child pointer
 *
 * Skip level i contains every skipInterval-th entry from skip level i-1.
 * Therefore the number of entries on level i is: floor(df / ((skipInterval ^ (i + 1))).
 *
 * Each skip entry on a level {@code i>0} contains a pointer to the corresponding skip entry in list i-1.
 * This guarantees a logarithmic amount of skips to find the target document.
 *
 * While this class takes care of writing the different skip levels,
 * subclasses must define the actual format of the skip data.
 * </pre>
 *
 * @lucene.experimental
 */
public abstract class MultiLevelSkipListWriter {
  /** number of levels in this skip list */
  protected final int numberOfSkipLevels;

  /** the skip interval in the list with level = 0 */
  private final int skipInterval;

  /** skipInterval used for level &gt; 0 */
  private final int skipMultiplier;

  /** for every skip level a different buffer is used */
  private ByteBuffersDataOutput[] skipBuffer;

  /** Length of the window at which the skips are placed on skip level 1 */
  private final int windowLength;

  /** Creates a {@code MultiLevelSkipListWriter}. */
  protected MultiLevelSkipListWriter(
      int skipInterval, int skipMultiplier, int maxSkipLevels, int df) {
    this.skipInterval = skipInterval;
    this.skipMultiplier = skipMultiplier;

    // calculate the maximum number of skip levels for this document frequency
    if (df > skipInterval) {
      // also make sure it does not exceed maxSkipLevels
      this.numberOfSkipLevels =
          Math.min(1 + MathUtil.log(df / skipInterval, skipMultiplier), maxSkipLevels);
    } else {
      this.numberOfSkipLevels = 1;
    }
    this.windowLength = Math.toIntExact(skipInterval * (long) skipMultiplier);
  }

  /**
   * Creates a {@code MultiLevelSkipListWriter}, where {@code skipInterval} and {@code
   * skipMultiplier} are the same.
   */
  protected MultiLevelSkipListWriter(int skipInterval, int maxSkipLevels, int df) {
    this(skipInterval, skipInterval, maxSkipLevels, df);
  }

  /** Allocates internal skip buffers. */
  protected void init() {
    skipBuffer = new ByteBuffersDataOutput[numberOfSkipLevels];
    for (int i = 0; i < numberOfSkipLevels; i++) {
      skipBuffer[i] = ByteBuffersDataOutput.newResettableInstance();
    }
  }

  /** Creates new buffers or empties the existing ones */
  protected void resetSkip() {
    if (skipBuffer == null) {
      init();
    } else {
      for (int i = 0; i < skipBuffer.length; i++) {
        skipBuffer[i].reset();
      }
    }
  }

  /**
   * Subclasses must implement the actual skip data encoding in this method.
   *
   * @param level the level skip data shall be writing for
   * @param skipBuffer the skip buffer to write to
   */
  protected abstract void writeSkipData(int level, DataOutput skipBuffer) throws IOException;

  /**
   * Writes the current skip data to the buffers. The current document frequency determines the max
   * level is skip data is to be written to.
   *
   * @param df the current document frequency
   * @throws IOException If an I/O error occurs
   */
  public void bufferSkip(int df) throws IOException {

    assert df % skipInterval == 0;
    int numLevels = 1;
    // This optimizes the most common case i.e. numLevels = 1, it does a single modulo check to
    // catch that case
    if (df % windowLength == 0) {
      numLevels++;
      df /= windowLength;
      // determine max level
      while ((df % skipMultiplier) == 0 && numLevels < numberOfSkipLevels) {
        numLevels++;
        df /= skipMultiplier;
      }
    }

    long childPointer = 0;

    for (int level = 0; level < numLevels; level++) {
      writeSkipData(level, skipBuffer[level]);

      long newChildPointer = skipBuffer[level].size();

      if (level != 0) {
        // store child pointers for all levels except the lowest
        writeChildPointer(childPointer, skipBuffer[level]);
      }

      // remember the childPointer for the next level
      childPointer = newChildPointer;
    }
  }

  /**
   * Writes the buffered skip lists to the given output.
   *
   * @param output the IndexOutput the skip lists shall be written to
   * @return the pointer the skip list starts
   */
  public long writeSkip(IndexOutput output) throws IOException {
    long skipPointer = output.getFilePointer();
    // System.out.println("skipper.writeSkip fp=" + skipPointer);
    if (skipBuffer == null || skipBuffer.length == 0) return skipPointer;

    for (int level = numberOfSkipLevels - 1; level > 0; level--) {
      long length = skipBuffer[level].size();
      if (length > 0) {
        writeLevelLength(length, output);
        skipBuffer[level].copyTo(output);
      }
    }
    skipBuffer[0].copyTo(output);

    return skipPointer;
  }

  /**
   * Writes the length of a level to the given output.
   *
   * @param levelLength the length of a level
   * @param output the IndexOutput the length shall be written to
   */
  protected void writeLevelLength(long levelLength, IndexOutput output) throws IOException {
    output.writeVLong(levelLength);
  }

  /**
   * Writes the child pointer of a block to the given output.
   *
   * @param childPointer block of higher level point to the lower level
   * @param skipBuffer the skip buffer to write to
   */
  protected void writeChildPointer(long childPointer, DataOutput skipBuffer) throws IOException {
    skipBuffer.writeVLong(childPointer);
  }
}
