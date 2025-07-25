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

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.MathUtil;

/**
 * This abstract class reads skip lists with multiple levels.
 *
 * <p>See {@link MultiLevelSkipListWriter} for the information about the encoding of the multi level
 * skip lists.
 *
 * <p>Subclasses must implement the abstract method {@link #readSkipData(int, IndexInput)} which
 * defines the actual format of the skip data.
 *
 * @lucene.experimental
 */
public abstract class MultiLevelSkipListReader implements Closeable {
  /** the maximum number of skip levels possible for this index */
  protected int maxNumberOfSkipLevels;

  /** number of levels in this skip list */
  protected int numberOfSkipLevels;

  private int docCount;

  /** skipStream for each level. */
  private IndexInput[] skipStream;

  /** The start pointer of each skip level. */
  private long[] skipPointer;

  /** skipInterval of each level. */
  private int[] skipInterval;

  /**
   * Number of docs skipped per level. It's possible for some values to overflow a signed int, but
   * this has been accounted for.
   */
  private int[] numSkipped;

  /** Doc id of current skip entry per level. */
  protected int[] skipDoc;

  /** Doc id of last read skip entry with docId &lt;= target. */
  private int lastDoc;

  /** Child pointer of current skip entry per level. */
  private long[] childPointer;

  /** childPointer of last read skip entry with docId &lt;= target. */
  private long lastChildPointer;

  private final int skipMultiplier;

  /** Creates a {@code MultiLevelSkipListReader}. */
  protected MultiLevelSkipListReader(
      IndexInput skipStream, int maxSkipLevels, int skipInterval, int skipMultiplier) {
    this.skipStream = new IndexInput[maxSkipLevels];
    this.skipPointer = new long[maxSkipLevels];
    this.childPointer = new long[maxSkipLevels];
    this.numSkipped = new int[maxSkipLevels];
    this.maxNumberOfSkipLevels = maxSkipLevels;
    this.skipInterval = new int[maxSkipLevels];
    this.skipMultiplier = skipMultiplier;
    this.skipStream[0] = skipStream;
    this.skipInterval[0] = skipInterval;
    for (int i = 1; i < maxSkipLevels; i++) {
      // cache skip intervals
      this.skipInterval[i] = this.skipInterval[i - 1] * skipMultiplier;
    }
    skipDoc = new int[maxSkipLevels];
  }

  /**
   * Creates a {@code MultiLevelSkipListReader}, where {@code skipInterval} and {@code
   * skipMultiplier} are the same.
   */
  protected MultiLevelSkipListReader(IndexInput skipStream, int maxSkipLevels, int skipInterval) {
    this(skipStream, maxSkipLevels, skipInterval, skipInterval);
  }

  /** Returns the id of the doc to which the last call of {@link #skipTo(int)} has skipped. */
  public int getDoc() {
    return lastDoc;
  }

  /**
   * Skips entries to the first beyond the current whose document number is greater than or equal to
   * <i>target</i>. Returns the current doc count.
   */
  public int skipTo(int target) throws IOException {

    // walk up the levels until highest level is found that has a skip
    // for this target
    int level = 0;
    while (level < numberOfSkipLevels - 1 && target > skipDoc[level + 1]) {
      level++;
    }

    while (level >= 0) {
      if (target > skipDoc[level]) {
        if (!loadNextSkip(level)) {
          continue;
        }
      } else {
        // no more skips on this level, go down one level
        if (level > 0 && lastChildPointer > skipStream[level - 1].getFilePointer()) {
          seekChild(level - 1);
        }
        level--;
      }
    }

    return numSkipped[0] - skipInterval[0] - 1;
  }

  private boolean loadNextSkip(int level) throws IOException {
    // we have to skip, the target document is greater than the current
    // skip list entry
    setLastSkipData(level);

    numSkipped[level] += skipInterval[level];

    // numSkipped may overflow a signed int, so compare as unsigned.
    if (Integer.compareUnsigned(numSkipped[level], docCount) > 0) {
      // this skip list is exhausted
      skipDoc[level] = Integer.MAX_VALUE;
      if (numberOfSkipLevels > level) numberOfSkipLevels = level;
      return false;
    }

    // read next skip entry
    skipDoc[level] += readSkipData(level, skipStream[level]);

    if (level != 0) {
      // read the child pointer if we are not on the leaf level
      childPointer[level] = readChildPointer(skipStream[level]) + skipPointer[level - 1];
    }

    return true;
  }

  /** Seeks the skip entry on the given level */
  protected void seekChild(int level) throws IOException {
    skipStream[level].seek(lastChildPointer);
    numSkipped[level] = numSkipped[level + 1] - skipInterval[level + 1];
    skipDoc[level] = lastDoc;
    if (level > 0) {
      childPointer[level] = readChildPointer(skipStream[level]) + skipPointer[level - 1];
    }
  }

  @Override
  public void close() throws IOException {
    for (int i = 1; i < skipStream.length; i++) {
      if (skipStream[i] != null) {
        skipStream[i].close();
      }
    }
  }

  /** Initializes the reader, for reuse on a new term. */
  public void init(long skipPointer, int df) throws IOException {
    this.skipPointer[0] = skipPointer;
    this.docCount = df;
    assert skipPointer >= 0 && skipPointer <= skipStream[0].length()
        : "invalid skip pointer: " + skipPointer + ", length=" + skipStream[0].length();
    Arrays.fill(skipDoc, 0);
    Arrays.fill(numSkipped, 0);
    Arrays.fill(childPointer, 0);

    for (int i = 1; i < numberOfSkipLevels; i++) {
      skipStream[i] = null;
    }
    loadSkipLevels();
  }

  /** Loads the skip levels */
  private void loadSkipLevels() throws IOException {
    if (docCount <= skipInterval[0]) {
      numberOfSkipLevels = 1;
    } else {
      numberOfSkipLevels = 1 + MathUtil.log(docCount / skipInterval[0], skipMultiplier);
    }

    if (numberOfSkipLevels > maxNumberOfSkipLevels) {
      numberOfSkipLevels = maxNumberOfSkipLevels;
    }

    skipStream[0].seek(skipPointer[0]);

    for (int i = numberOfSkipLevels - 1; i > 0; i--) {
      // the length of the current level
      long length = readLevelLength(skipStream[0]);

      // the start pointer of the current level
      skipPointer[i] = skipStream[0].getFilePointer();

      // clone this stream, it is already at the start of the current level
      skipStream[i] = skipStream[0].clone();

      // move base stream beyond the current level
      skipStream[0].seek(skipStream[0].getFilePointer() + length);
    }

    // use base stream for the lowest level
    skipPointer[0] = skipStream[0].getFilePointer();
  }

  /**
   * Subclasses must implement the actual skip data encoding in this method.
   *
   * @param level the level skip data shall be read from
   * @param skipStream the skip stream to read from
   */
  protected abstract int readSkipData(int level, IndexInput skipStream) throws IOException;

  /**
   * read the length of the current level written via {@link
   * MultiLevelSkipListWriter#writeLevelLength(long, IndexOutput)}.
   *
   * @param skipStream the IndexInput the length shall be read from
   * @return level length
   */
  protected long readLevelLength(IndexInput skipStream) throws IOException {
    return skipStream.readVLong();
  }

  /**
   * read the child pointer written via {@link MultiLevelSkipListWriter#writeChildPointer(long,
   * DataOutput)}.
   *
   * @param skipStream the IndexInput the child pointer shall be read from
   * @return child pointer
   */
  protected long readChildPointer(IndexInput skipStream) throws IOException {
    return skipStream.readVLong();
  }

  /** Copies the values of the last read skip entry on this level */
  protected void setLastSkipData(int level) {
    lastDoc = skipDoc[level];
    lastChildPointer = childPointer[level];
  }
}
