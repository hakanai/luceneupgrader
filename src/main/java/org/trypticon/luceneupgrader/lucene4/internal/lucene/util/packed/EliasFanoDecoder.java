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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BroadWord; // bit selection in long


public class EliasFanoDecoder {
  private static final int LOG2_LONG_SIZE = Long.numberOfTrailingZeros(Long.SIZE);

  private final EliasFanoEncoder efEncoder;
  private final long numEncoded;
  private long efIndex = -1; // the decoding index.
  private long setBitForIndex = -1; // the index of the high bit at the decoding index.

  public final static long NO_MORE_VALUES = -1L;

  private final long numIndexEntries;
  private final long indexMask;


  public EliasFanoDecoder(EliasFanoEncoder efEncoder) {
    this.efEncoder = efEncoder;
    this.numEncoded = efEncoder.numEncoded; // not final in EliasFanoEncoder
    this.numIndexEntries = efEncoder.currentEntryIndex;  // not final in EliasFanoEncoder
    this.indexMask = (1L << efEncoder.nIndexEntryBits) - 1;
  }

  public EliasFanoEncoder getEliasFanoEncoder() {
    return efEncoder;
  }
  

  public long numEncoded() { 
    return numEncoded;
  }



  public long currentIndex() {
    if (efIndex < 0) {
      throw new IllegalStateException("index before sequence");
    }
    if (efIndex >= numEncoded) {
      throw new IllegalStateException("index after sequence");
    }
    return efIndex;
  }


  public long currentValue() {
    return combineHighLowValues(currentHighValue(), currentLowValue());
  }

  private long currentHighValue() {
    return setBitForIndex - efIndex; // sequence of unary gaps
  }

  private static long unPackValue(long[] longArray, int numBits, long packIndex, long bitsMask) {
    if (numBits == 0) {
      return 0;
    }
    long bitPos = packIndex * numBits;
    int index = (int) (bitPos >>> LOG2_LONG_SIZE);
    int bitPosAtIndex = (int) (bitPos & (Long.SIZE-1));
    long value = longArray[index] >>> bitPosAtIndex;
    if ((bitPosAtIndex + numBits) > Long.SIZE) {
      value |= (longArray[index + 1] << (Long.SIZE - bitPosAtIndex));
    }
    value &= bitsMask;
    return value;
  }

  private long currentLowValue() {
    assert ((efIndex >= 0) && (efIndex < numEncoded)) : "efIndex " + efIndex;
    return unPackValue(efEncoder.lowerLongs, efEncoder.numLowBits, efIndex, efEncoder.lowerBitsMask);
  }


  private long combineHighLowValues(long highValue, long lowValue) {
    return (highValue << efEncoder.numLowBits) | lowValue;
  }

  private long curHighLong;


  /* The implementation of forward decoding and backward decoding is done by the following method pairs.
   *
   * toBeforeSequence - toAfterSequence
   * getCurrentRightShift - getCurrentLeftShift
   * toAfterCurrentHighBit - toBeforeCurrentHighBit
   * toNextHighLong - toPreviousHighLong
   * nextHighValue - previousHighValue
   * nextValue - previousValue
   * advanceToValue - backToValue
   *
   */

  /* Forward decoding section */


  public void toBeforeSequence() {
    efIndex = -1;
    setBitForIndex = -1;
  }

  private int getCurrentRightShift() {
    int s = (int) (setBitForIndex & (Long.SIZE-1));
    return s;
  }


  private boolean toAfterCurrentHighBit() {
    efIndex += 1;
    if (efIndex >= numEncoded) {
      return false;
    }
    setBitForIndex += 1;
    int highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
    curHighLong = efEncoder.upperLongs[highIndex] >>> getCurrentRightShift();
    return true;
  }


  private void toNextHighLong() {
    setBitForIndex += Long.SIZE - (setBitForIndex & (Long.SIZE-1));
    //assert getCurrentRightShift() == 0;
    int highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
    curHighLong = efEncoder.upperLongs[highIndex];
  }


  private void toNextHighValue() {
    while (curHighLong == 0L) {
      toNextHighLong(); // inlining and unrolling would simplify somewhat
    }
    setBitForIndex += Long.numberOfTrailingZeros(curHighLong);
  }


  private long nextHighValue() {
    toNextHighValue();
    return currentHighValue();
  }


  public long nextValue() {
    if (! toAfterCurrentHighBit()) {
      return NO_MORE_VALUES;
    }
    long highValue = nextHighValue();
    return combineHighLowValues(highValue, currentLowValue());
  }


  public boolean advanceToIndex(long index) {
    assert index > efIndex;
    if (index >= numEncoded) {
      efIndex = numEncoded;
      return false;
    }
    if (! toAfterCurrentHighBit()) {
      assert false;
    }
    /* CHECKME: Add a (binary) search in the upperZeroBitPositions here. */
    int curSetBits = Long.bitCount(curHighLong);
    while ((efIndex + curSetBits) < index) { // curHighLong has not enough set bits to reach index
      efIndex += curSetBits;
      toNextHighLong();
      curSetBits = Long.bitCount(curHighLong);
    }
    // curHighLong has enough set bits to reach index
    while (efIndex < index) {
      /* CHECKME: Instead of the linear search here, use (forward) broadword selection from
       * "Broadword Implementation of Rank/Select Queries", Sebastiano Vigna, January 30, 2012.
       */
      if (! toAfterCurrentHighBit()) {
        assert false;
      }
      toNextHighValue();
    }
    return true;
  }




  public long advanceToValue(long target) {
    efIndex += 1;
    if (efIndex >= numEncoded) {
      return NO_MORE_VALUES;
    }
    setBitForIndex += 1; // the high bit at setBitForIndex belongs to the unary code for efIndex

    int highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
    long upperLong = efEncoder.upperLongs[highIndex];
    curHighLong = upperLong >>> ((int) (setBitForIndex & (Long.SIZE-1))); // may contain the unary 1 bit for efIndex

    // determine index entry to advance to
    long highTarget = target >>> efEncoder.numLowBits;

    long indexEntryIndex = (highTarget / efEncoder.indexInterval) - 1;
    if (indexEntryIndex >= 0) { // not before first index entry
      if (indexEntryIndex >= numIndexEntries) {
        indexEntryIndex = numIndexEntries - 1; // no further than last index entry
      }
      long indexHighValue = (indexEntryIndex + 1) * efEncoder.indexInterval;
      assert indexHighValue <= highTarget;
      if (indexHighValue > (setBitForIndex - efIndex)) { // advance to just after zero bit position of index entry.
        setBitForIndex = unPackValue(efEncoder.upperZeroBitPositionIndex, efEncoder.nIndexEntryBits, indexEntryIndex, indexMask);
        efIndex = setBitForIndex - indexHighValue; // the high bit at setBitForIndex belongs to the unary code for efIndex
        highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
        upperLong = efEncoder.upperLongs[highIndex];
        curHighLong = upperLong >>> ((int) (setBitForIndex & (Long.SIZE-1))); // may contain the unary 1 bit for efIndex
      }
      assert efIndex < numEncoded; // there is a high value to be found.
    }

    int curSetBits = Long.bitCount(curHighLong); // shifted right.
    int curClearBits = Long.SIZE - curSetBits - ((int) (setBitForIndex & (Long.SIZE-1))); // subtract right shift, may be more than encoded

    while (((setBitForIndex - efIndex) + curClearBits) < highTarget) {
      // curHighLong has not enough clear bits to reach highTarget
      efIndex += curSetBits;
      if (efIndex >= numEncoded) {
        return NO_MORE_VALUES;
      }
      setBitForIndex += Long.SIZE - (setBitForIndex & (Long.SIZE-1));
      // highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
      assert (highIndex + 1) == (int)(setBitForIndex >>> LOG2_LONG_SIZE);
      highIndex += 1;
      upperLong = efEncoder.upperLongs[highIndex];
      curHighLong = upperLong;
      curSetBits = Long.bitCount(curHighLong);
      curClearBits = Long.SIZE - curSetBits;
    }
    // curHighLong has enough clear bits to reach highTarget, and may not have enough set bits.
    while (curHighLong == 0L) {
      setBitForIndex += Long.SIZE - (setBitForIndex & (Long.SIZE-1));
      assert (highIndex + 1) == (int)(setBitForIndex >>> LOG2_LONG_SIZE);
      highIndex += 1;
      upperLong = efEncoder.upperLongs[highIndex];
      curHighLong = upperLong;
    }

    // curHighLong has enough clear bits to reach highTarget, has at least 1 set bit, and may not have enough set bits.
    int rank = (int) (highTarget - (setBitForIndex - efIndex)); // the rank of the zero bit for highValue.
    assert (rank <= Long.SIZE) : ("rank " + rank);
    if (rank >= 1) {
      long invCurHighLong = ~curHighLong;
      int clearBitForValue = (rank <= 8)
                              ? BroadWord.selectNaive(invCurHighLong, rank)
                              : BroadWord.select(invCurHighLong, rank);
      assert clearBitForValue <= (Long.SIZE-1);
      setBitForIndex += clearBitForValue + 1; // the high bit just before setBitForIndex is zero
      int oneBitsBeforeClearBit = clearBitForValue - rank + 1;
      efIndex += oneBitsBeforeClearBit; // the high bit at setBitForIndex and belongs to the unary code for efIndex
      if (efIndex >= numEncoded) {
        return NO_MORE_VALUES;
      }

      if ((setBitForIndex & (Long.SIZE - 1)) == 0L) { // exhausted curHighLong
        assert (highIndex + 1) == (int)(setBitForIndex >>> LOG2_LONG_SIZE);
        highIndex += 1;
        upperLong = efEncoder.upperLongs[highIndex];
        curHighLong = upperLong;
      }
      else {
        assert highIndex == (int)(setBitForIndex >>> LOG2_LONG_SIZE);
        curHighLong = upperLong >>> ((int) (setBitForIndex & (Long.SIZE-1)));
      }
      // curHighLong has enough clear bits to reach highTarget, and may not have enough set bits.
 
      while (curHighLong == 0L) {
        setBitForIndex += Long.SIZE - (setBitForIndex & (Long.SIZE-1));
        assert (highIndex + 1) == (int)(setBitForIndex >>> LOG2_LONG_SIZE);
        highIndex += 1;
        upperLong = efEncoder.upperLongs[highIndex];
        curHighLong = upperLong;
      }
    }
    setBitForIndex += Long.numberOfTrailingZeros(curHighLong);
    assert (setBitForIndex - efIndex) >= highTarget; // highTarget reached

    // Linear search also with low values
    long currentValue = combineHighLowValues((setBitForIndex - efIndex), currentLowValue());
    while (currentValue < target) {
      currentValue = nextValue();
      if (currentValue == NO_MORE_VALUES) {
        return NO_MORE_VALUES;
      }
    }
    return currentValue;
  }


  /* Backward decoding section */

  public void toAfterSequence() {
    efIndex = numEncoded; // just after last index
    setBitForIndex = (efEncoder.lastEncoded >>> efEncoder.numLowBits) + numEncoded;
  }

  private int getCurrentLeftShift() {
    int s = Long.SIZE - 1 - (int) (setBitForIndex & (Long.SIZE-1));
    return s;
  }


  private boolean toBeforeCurrentHighBit() {
    efIndex -= 1;
    if (efIndex < 0) {
      return false;
    }
    setBitForIndex -= 1;
    int highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
    curHighLong = efEncoder.upperLongs[highIndex] << getCurrentLeftShift();
    return true;
  }


  private void toPreviousHighLong() {
    setBitForIndex -= (setBitForIndex & (Long.SIZE-1)) + 1;
    //assert getCurrentLeftShift() == 0;
    int highIndex = (int)(setBitForIndex >>> LOG2_LONG_SIZE);
    curHighLong = efEncoder.upperLongs[highIndex];
  }


  private long previousHighValue() {
    while (curHighLong == 0L) {
      toPreviousHighLong(); // inlining and unrolling would simplify somewhat
    }
    setBitForIndex -= Long.numberOfLeadingZeros(curHighLong);
    return currentHighValue();
  }


  public long previousValue() {
    if (! toBeforeCurrentHighBit()) {
      return NO_MORE_VALUES;
    }
    long highValue = previousHighValue();
    return combineHighLowValues(highValue, currentLowValue());
  }



  private long backToHighValue(long highTarget) {
    /* CHECKME: Add using the index as in advanceToHighValue */
    int curSetBits = Long.bitCount(curHighLong); // is shifted by getCurrentLeftShift()
    int curClearBits = Long.SIZE - curSetBits - getCurrentLeftShift();
    while ((currentHighValue() - curClearBits) > highTarget) {
      // curHighLong has not enough clear bits to reach highTarget
      efIndex -= curSetBits;
      if (efIndex < 0) {
        return NO_MORE_VALUES;
      }
      toPreviousHighLong();
      //assert getCurrentLeftShift() == 0;
      curSetBits = Long.bitCount(curHighLong);
      curClearBits = Long.SIZE - curSetBits;
    }
    // curHighLong has enough clear bits to reach highTarget, but may not have enough set bits.
    long highValue = previousHighValue();
    while (highValue > highTarget) {
      /* CHECKME: See at advanceToHighValue on using broadword bit selection. */
      if (! toBeforeCurrentHighBit()) {
        return NO_MORE_VALUES;
      }
      highValue = previousHighValue();
    }
    return highValue;
  }


  public long backToValue(long target) {
    if (! toBeforeCurrentHighBit()) {
      return NO_MORE_VALUES;
    }
    long highTarget = target >>> efEncoder.numLowBits;
    long highValue = backToHighValue(highTarget);
    if (highValue == NO_MORE_VALUES) {
      return NO_MORE_VALUES;
    }
    // Linear search with low values:
    long currentValue = combineHighLowValues(highValue, currentLowValue());
    while (currentValue > target) {
      currentValue = previousValue();
      if (currentValue == NO_MORE_VALUES) {
        return NO_MORE_VALUES;
      }
    }
    return currentValue;
  }
}

