package org.trypticon.lucene3.analysis.tokenattributes;

/**
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

import org.trypticon.lucene3.util.Attribute;

/**
 * The term text of a Token.
 * @deprecated Use {@code CharTermAttribute} instead.
 */
@Deprecated
public interface TermAttribute extends Attribute {
  /** Returns the Token's term text.
   * 
   * This method has a performance penalty
   * because the text is stored internally in a char[].  If
   * possible, use {@code #termBuffer()} and {@code
   * #termLength()} directly instead.  If you really need a
   * String, use this method, which is nothing more than
   * a convenience call to <b>new String(token.termBuffer(), 0, token.termLength())</b>
   */
  String term();
  
  /** Copies the contents of buffer, starting at offset for
   *  length characters, into the termBuffer array.
   *  @param buffer the buffer to copy
   *  @param offset the index in the buffer of the first character to copy
   *  @param length the number of characters to copy
   */
  void setTermBuffer(char[] buffer, int offset, int length);

  /** Copies the contents of buffer into the termBuffer array.
   *  @param buffer the buffer to copy
   */
  void setTermBuffer(String buffer);

  /** Copies the contents of buffer, starting at offset and continuing
   *  for length characters, into the termBuffer array.
   *  @param buffer the buffer to copy
   *  @param offset the index in the buffer of the first character to copy
   *  @param length the number of characters to copy
   */
  void setTermBuffer(String buffer, int offset, int length);
  
  /** Returns the internal termBuffer character array which
   *  you can then directly alter.  If the array is too
   *  small for your token, use {@code
   *  #resizeTermBuffer(int)} to increase it.  After
   *  altering the buffer be sure to call {@code
   *  #setTermLength} to record the number of valid
   *  characters that were placed into the termBuffer. */
  char[] termBuffer();

  /** Grows the termBuffer to at least size newSize, preserving the
   *  existing content. Note: If the next operation is to change
   *  the contents of the term buffer use
   *  {@code #setTermBuffer(char[], int, int)},
   *  {@code #setTermBuffer(String)}, or
   *  {@code #setTermBuffer(String, int, int)}
   *  to optimally combine the resize with the setting of the termBuffer.
   *  @param newSize minimum size of the new termBuffer
   *  @return newly created termBuffer with length >= newSize
   */
  char[] resizeTermBuffer(int newSize);

  /** Return number of valid characters (length of the term)
   *  in the termBuffer array. */
  int termLength();
  
  /** Set number of valid characters (length of the term) in
   *  the termBuffer array. Use this to truncate the termBuffer
   *  or to synchronize with external manipulation of the termBuffer.
   *  Note: to grow the size of the array,
   *  use {@code #resizeTermBuffer(int)} first.
   *  @param length the truncated length
   */
  void setTermLength(int length);
}
