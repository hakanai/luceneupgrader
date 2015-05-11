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

}
