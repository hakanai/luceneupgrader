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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.packed;

import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet; // for javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;


public class EliasFanoEncoder implements Accountable {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(EliasFanoEncoder.class);

  final long numValues;
  private final long upperBound;
  final int numLowBits;
  final long lowerBitsMask;
  final long[] upperLongs;
  final long[] lowerLongs;
  private static final int LOG2_LONG_SIZE = Long.numberOfTrailingZeros(Long.SIZE);

  long numEncoded = 0L;
  long lastEncoded = 0L;

  public static final long DEFAULT_INDEX_INTERVAL = 256;
  final long numIndexEntries;
  final long indexInterval;
  final int nIndexEntryBits;

  final long[] upperZeroBitPositionIndex;
  long currentEntryIndex; // also indicates how many entries in the index are valid.



  public EliasFanoEncoder(long numValues, long upperBound, long indexInterval) {
    if (numValues < 0L) {
      throw new IllegalArgumentException("numValues should not be negative: " + numValues);
    }
    this.numValues = numValues;
    if ((numValues > 0L) && (upperBound < 0L)) {
      throw new IllegalArgumentException("upperBound should not be negative: " + upperBound + " when numValues > 0");
    }
    this.upperBound = numValues > 0 ? upperBound : -1L; // if there is no value, -1 is the best upper bound
    int nLowBits = 0;
    if (this.numValues > 0) { // nLowBits = max(0; floor(2log(upperBound/numValues)))
      long lowBitsFac = this.upperBound / this.numValues;
      if (lowBitsFac > 0) {
        nLowBits = 63 - Long.numberOfLeadingZeros(lowBitsFac); // see Long.numberOfLeadingZeros javadocs
      }
    }
    this.numLowBits = nLowBits;
    this.lowerBitsMask = Long.MAX_VALUE >>> (Long.SIZE - 1 - this.numLowBits);

    long numLongsForLowBits = numLongsForBits(numValues * numLowBits);
    if (numLongsForLowBits > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("numLongsForLowBits too large to index a long array: " + numLongsForLowBits);
    }
    this.lowerLongs = new long[(int) numLongsForLowBits];

    long numHighBitsClear = ((this.upperBound > 0) ? this.upperBound : 0) >>> this.numLowBits;
    assert numHighBitsClear <= (2 * this.numValues);
    long numHighBitsSet = this.numValues;

    long numLongsForHighBits = numLongsForBits(numHighBitsClear + numHighBitsSet);
    if (numLongsForHighBits > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("numLongsForHighBits too large to index a long array: " + numLongsForHighBits);
    }
    this.upperLongs = new long[(int) numLongsForHighBits];
    if (indexInterval < 2) {
      throw new IllegalArgumentException("indexInterval should at least 2: " + indexInterval);
    }
    // For the index:
    long maxHighValue = upperBound >>> this.numLowBits;
    long nIndexEntries = maxHighValue / indexInterval; // no zero value index entry
    this.numIndexEntries = (nIndexEntries >= 0) ? nIndexEntries : 0;
    long maxIndexEntry = maxHighValue + numValues - 1; // clear upper bits, set upper bits, start at zero
    this.nIndexEntryBits = (maxIndexEntry <= 0) ? 0
                          : (64 - Long.numberOfLeadingZeros(maxIndexEntry));
    long numLongsForIndexBits = numLongsForBits(numIndexEntries * nIndexEntryBits);
    if (numLongsForIndexBits > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("numLongsForIndexBits too large to index a long array: " + numLongsForIndexBits);
    }
    this.upperZeroBitPositionIndex = new long[(int) numLongsForIndexBits];
    this.currentEntryIndex = 0;
    this.indexInterval = indexInterval;
  }

  public EliasFanoEncoder(long numValues, long upperBound) {
    this(numValues, upperBound, DEFAULT_INDEX_INTERVAL);
  }

  private static long numLongsForBits(long numBits) { // Note: int version in FixedBitSet.bits2words()
    assert numBits >= 0 : numBits;
    return (numBits + (Long.SIZE-1)) >>> LOG2_LONG_SIZE;
  }


  public void encodeNext(long x) {
    if (numEncoded >= numValues) {
      throw new IllegalStateException("encodeNext called more than " + numValues + " times.");
    }
    if (lastEncoded > x) {
      throw new IllegalArgumentException(x + " smaller than previous " + lastEncoded);
    }
    if (x > upperBound) {
      throw new IllegalArgumentException(x + " larger than upperBound " + upperBound);
    }
    long highValue = x >>> numLowBits;
    encodeUpperBits(highValue);
    encodeLowerBits(x & lowerBitsMask);
    lastEncoded = x;
    // Add index entries:
    long indexValue = (currentEntryIndex + 1) * indexInterval;
    while (indexValue <= highValue) { 
      long afterZeroBitPosition = indexValue + numEncoded;
      packValue(afterZeroBitPosition, upperZeroBitPositionIndex, nIndexEntryBits, currentEntryIndex);
      currentEntryIndex += 1;
      indexValue += indexInterval;
    }
    numEncoded++;
  }

  private void encodeUpperBits(long highValue) {
    long nextHighBitNum = numEncoded + highValue; // sequence of unary gaps
    upperLongs[(int)(nextHighBitNum >>> LOG2_LONG_SIZE)] |= (1L << (nextHighBitNum & (Long.SIZE-1)));
  }

  private void encodeLowerBits(long lowValue) {
    packValue(lowValue, lowerLongs, numLowBits, numEncoded);
  }

  private static void packValue(long value, long[] longArray, int numBits, long packIndex) {
    if (numBits != 0) {
      long bitPos = numBits * packIndex;
      int index = (int) (bitPos >>> LOG2_LONG_SIZE);
      int bitPosAtIndex = (int) (bitPos & (Long.SIZE-1));
      longArray[index] |= (value << bitPosAtIndex);
      if ((bitPosAtIndex + numBits) > Long.SIZE) {
        longArray[index+1] = (value >>> (Long.SIZE - bitPosAtIndex));
      }
    }
  }


  public static boolean sufficientlySmallerThanBitSet(long numValues, long upperBound) {
    /* When (upperBound / 6) == numValues,
     * the number of bits per entry for the EliasFanoEncoder is 2 + ceil(2log(upperBound/numValues)) == 5.
     *
     * For intersecting two bit sets upperBound bits are accessed, roughly half of one, half of the other.
     * For intersecting two EliasFano sequences without index on the upper bits,
     * all (2 * 3 * numValues) upper bits are accessed.
     */
    return (upperBound > (4 * Long.SIZE)) // prefer a bit set when it takes no more than 4 longs.
            && (upperBound / 7) > numValues; // 6 + 1 to allow some room for the index.
  }

  public EliasFanoDecoder getDecoder() {
    // decode as far as currently encoded as determined by numEncoded.
    return new EliasFanoDecoder(this);
  }

  public long[] getLowerBits() {
    return lowerLongs;
  }

  public long[] getUpperBits() {
    return upperLongs;
  }
  
  public long[] getIndexBits() {
    return upperZeroBitPositionIndex;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder("EliasFanoSequence");
    s.append(" numValues " + numValues);
    s.append(" numEncoded " + numEncoded);
    s.append(" upperBound " + upperBound);
    s.append(" lastEncoded " + lastEncoded);
    s.append(" numLowBits " + numLowBits);
    s.append("\nupperLongs[" + upperLongs.length + "]");
    for (int i = 0; i < upperLongs.length; i++) {
      s.append(" " + ToStringUtils.longHex(upperLongs[i]));
    }
    s.append("\nlowerLongs[" + lowerLongs.length + "]");
    for (int i = 0; i < lowerLongs.length; i++) {
      s.append(" " + ToStringUtils.longHex(lowerLongs[i]));
    }
    s.append("\nindexInterval: " + indexInterval + ", nIndexEntryBits: " + nIndexEntryBits);
    s.append("\nupperZeroBitPositionIndex[" + upperZeroBitPositionIndex.length + "]");
    for (int i = 0; i < upperZeroBitPositionIndex.length; i++) { 
      s.append(" " + ToStringUtils.longHex(upperZeroBitPositionIndex[i]));
    }
    return s.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (! (other instanceof EliasFanoEncoder)) {
      return false;
    }
    EliasFanoEncoder oefs = (EliasFanoEncoder) other;
    // no equality needed for upperBound
    return (this.numValues == oefs.numValues)
        && (this.numEncoded == oefs.numEncoded)
        && (this.numLowBits == oefs.numLowBits)
        && (this.numIndexEntries == oefs.numIndexEntries)
        && (this.indexInterval == oefs.indexInterval) // no need to check index content
        && Arrays.equals(this.upperLongs, oefs.upperLongs)
        && Arrays.equals(this.lowerLongs, oefs.lowerLongs);
  }

  @Override
  public int hashCode() {
    int h = ((int) (31*(numValues + 7*(numEncoded + 5*(numLowBits + 3*(numIndexEntries + 11*indexInterval))))))
            ^ Arrays.hashCode(upperLongs)
            ^ Arrays.hashCode(lowerLongs);
    return h;
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED
        + RamUsageEstimator.sizeOf(lowerLongs)
        + RamUsageEstimator.sizeOf(upperLongs)
        + RamUsageEstimator.sizeOf(upperZeroBitPositionIndex);
  }
}

