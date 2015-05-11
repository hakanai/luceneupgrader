package org.apache.lucene.analysis.tokenattributes;

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

import org.apache.lucene.util.Attribute;

/**
 * The term text of a Token.
 */
public interface CharTermAttribute extends Attribute, CharSequence, Appendable {
  
  /** Copies the contents of buffer, starting at offset for
   *  length characters, into the termBuffer array.
   *  @param buffer the buffer to copy
   *  @param offset the index in the buffer of the first character to copy
   *  @param length the number of characters to copy
   */
  void copyBuffer(char[] buffer, int offset, int length);
  
  /** Returns the internal termBuffer character array which
   *  you can then directly alter.  If the array is too
   *  small for your token, use {@code
   *  #resizeBuffer(int)} to increase it.  After
   *  altering the buffer be sure to call {@code
   *  #setLength} to record the number of valid
   *  characters that were placed into the termBuffer. */
  char[] buffer();

  /** Grows the termBuffer to at least size newSize, preserving the
   *  existing content.
   *  @param newSize minimum size of the new termBuffer
   *  @return newly created termBuffer with length >= newSize
   */
  char[] resizeBuffer(int newSize);

  // the following methods are redefined to get rid of IOException declaration:
  CharTermAttribute append(CharSequence csq);
  CharTermAttribute append(CharSequence csq, int start, int end);
  CharTermAttribute append(char c);

  /** Appends the specified {@code String} to this character sequence. 
   * <p>The characters of the {@code String} argument are appended, in order, increasing the length of
   * this sequence by the length of the argument. If argument is {@code null}, then the four
   * characters {@code "null"} are appended. 
   */
  CharTermAttribute append(String s);

}
