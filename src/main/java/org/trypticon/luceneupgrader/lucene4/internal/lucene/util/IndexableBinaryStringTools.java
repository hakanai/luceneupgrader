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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute; // javadoc

@Deprecated
public final class IndexableBinaryStringTools {

  private static final CodingCase[] CODING_CASES = {
    // CodingCase(int initialShift, int finalShift)
    new CodingCase( 7, 1   ),
    // CodingCase(int initialShift, int middleShift, int finalShift)
    new CodingCase(14, 6, 2),
    new CodingCase(13, 5, 3),
    new CodingCase(12, 4, 4),
    new CodingCase(11, 3, 5),
    new CodingCase(10, 2, 6),
    new CodingCase( 9, 1, 7),
    new CodingCase( 8, 0   )
  };

  // Export only static methods
  private IndexableBinaryStringTools() {}

  public static int getEncodedLength(byte[] inputArray, int inputOffset,
      int inputLength) {
    // Use long for intermediaries to protect against overflow
    return (int)((8L * inputLength + 14L) / 15L) + 1;
  }

  public static int getDecodedLength(char[] encoded, int offset, int length) {
    final int numChars = length - 1;
    if (numChars <= 0) {
      return 0;
    } else {
      // Use long for intermediaries to protect against overflow
      final long numFullBytesInFinalChar = encoded[offset + length - 1];
      final long numEncodedChars = numChars - 1;
      return (int)((numEncodedChars * 15L + 7L) / 8L + numFullBytesInFinalChar);
    }
  }

  public static void encode(byte[] inputArray, int inputOffset,
      int inputLength, char[] outputArray, int outputOffset, int outputLength) {
    assert (outputLength == getEncodedLength(inputArray, inputOffset,
        inputLength));
    if (inputLength > 0) {
      int inputByteNum = inputOffset;
      int caseNum = 0;
      int outputCharNum = outputOffset;
      CodingCase codingCase;
      for (; inputByteNum + CODING_CASES[caseNum].numBytes <= inputLength; ++outputCharNum) {
        codingCase = CODING_CASES[caseNum];
        if (2 == codingCase.numBytes) {
          outputArray[outputCharNum] = (char) (((inputArray[inputByteNum] & 0xFF) << codingCase.initialShift)
              + (((inputArray[inputByteNum + 1] & 0xFF) >>> codingCase.finalShift) & codingCase.finalMask) & (short) 0x7FFF);
        } else { // numBytes is 3
          outputArray[outputCharNum] = (char) (((inputArray[inputByteNum] & 0xFF) << codingCase.initialShift)
              + ((inputArray[inputByteNum + 1] & 0xFF) << codingCase.middleShift)
              + (((inputArray[inputByteNum + 2] & 0xFF) >>> codingCase.finalShift) & codingCase.finalMask) & (short) 0x7FFF);
        }
        inputByteNum += codingCase.advanceBytes;
        if (++caseNum == CODING_CASES.length) {
          caseNum = 0;
        }
      }
      // Produce final char (if any) and trailing count chars.
      codingCase = CODING_CASES[caseNum];

      if (inputByteNum + 1 < inputLength) { // codingCase.numBytes must be 3
        outputArray[outputCharNum++] = (char) ((((inputArray[inputByteNum] & 0xFF) << codingCase.initialShift) + ((inputArray[inputByteNum + 1] & 0xFF) << codingCase.middleShift)) & (short) 0x7FFF);
        // Add trailing char containing the number of full bytes in final char
        outputArray[outputCharNum++] = (char) 1;
      } else if (inputByteNum < inputLength) {
        outputArray[outputCharNum++] = (char) (((inputArray[inputByteNum] & 0xFF) << codingCase.initialShift) & (short) 0x7FFF);
        // Add trailing char containing the number of full bytes in final char
        outputArray[outputCharNum++] = caseNum == 0 ? (char) 1 : (char) 0;
      } else { // No left over bits - last char is completely filled.
        // Add trailing char containing the number of full bytes in final char
        outputArray[outputCharNum++] = (char) 1;
      }
    }
  }

  public static void decode(char[] inputArray, int inputOffset,
      int inputLength, byte[] outputArray, int outputOffset, int outputLength) {
    assert (outputLength == getDecodedLength(inputArray, inputOffset,
        inputLength));
    final int numInputChars = inputLength - 1;
    final int numOutputBytes = outputLength;

    if (numOutputBytes > 0) {
      int caseNum = 0;
      int outputByteNum = outputOffset;
      int inputCharNum = inputOffset;
      short inputChar;
      CodingCase codingCase;
      for (; inputCharNum < numInputChars - 1; ++inputCharNum) {
        codingCase = CODING_CASES[caseNum];
        inputChar = (short) inputArray[inputCharNum];
        if (2 == codingCase.numBytes) {
          if (0 == caseNum) {
            outputArray[outputByteNum] = (byte) (inputChar >>> codingCase.initialShift);
          } else {
            outputArray[outputByteNum] += (byte) (inputChar >>> codingCase.initialShift);
          }
          outputArray[outputByteNum + 1] = (byte) ((inputChar & codingCase.finalMask) << codingCase.finalShift);
        } else { // numBytes is 3
          outputArray[outputByteNum] += (byte) (inputChar >>> codingCase.initialShift);
          outputArray[outputByteNum + 1] = (byte) ((inputChar & codingCase.middleMask) >>> codingCase.middleShift);
          outputArray[outputByteNum + 2] = (byte) ((inputChar & codingCase.finalMask) << codingCase.finalShift);
        }
        outputByteNum += codingCase.advanceBytes;
        if (++caseNum == CODING_CASES.length) {
          caseNum = 0;
        }
      }
      // Handle final char
      inputChar = (short) inputArray[inputCharNum];
      codingCase = CODING_CASES[caseNum];
      if (0 == caseNum) {
        outputArray[outputByteNum] = 0;
      }
      outputArray[outputByteNum] += (byte) (inputChar >>> codingCase.initialShift);
      final int bytesLeft = numOutputBytes - outputByteNum;
      if (bytesLeft > 1) {
        if (2 == codingCase.numBytes) {
          outputArray[outputByteNum + 1] = (byte) ((inputChar & codingCase.finalMask) >>> codingCase.finalShift);
        } else { // numBytes is 3
          outputArray[outputByteNum + 1] = (byte) ((inputChar & codingCase.middleMask) >>> codingCase.middleShift);
          if (bytesLeft > 2) {
            outputArray[outputByteNum + 2] = (byte) ((inputChar & codingCase.finalMask) << codingCase.finalShift);
          }
        }
      }
    }
  }

  static class CodingCase {
    int numBytes, initialShift, middleShift, finalShift, advanceBytes = 2;
    short middleMask, finalMask;

    CodingCase(int initialShift, int middleShift, int finalShift) {
      this.numBytes = 3;
      this.initialShift = initialShift;
      this.middleShift = middleShift;
      this.finalShift = finalShift;
      this.finalMask = (short)((short)0xFF >>> finalShift);
      this.middleMask = (short)((short)0xFF << middleShift);
    }

    CodingCase(int initialShift, int finalShift) {
      this.numBytes = 2;
      this.initialShift = initialShift;
      this.finalShift = finalShift;
      this.finalMask = (short)((short)0xFF >>> finalShift);
      if (finalShift != 0) {
        advanceBytes = 1; 
      }
    }
  }
}
