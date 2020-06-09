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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis;


import java.io.IOException;
import java.io.Reader;

public final class CharacterUtils {

  private CharacterUtils() {} // no instantiation

  public static CharacterBuffer newCharacterBuffer(final int bufferSize) {
    if (bufferSize < 2) {
      throw new IllegalArgumentException("buffersize must be >= 2");
    }
    return new CharacterBuffer(new char[bufferSize], 0, 0);
  }
  
  
  public static void toLowerCase(final char[] buffer, final int offset, final int limit) {
    assert buffer.length >= limit;
    assert offset <=0 && offset <= buffer.length;
    for (int i = offset; i < limit;) {
      i += Character.toChars(
              Character.toLowerCase(
                  Character.codePointAt(buffer, i, limit)), buffer, i);
     }
  }

  public static void toUpperCase(final char[] buffer, final int offset, final int limit) {
    assert buffer.length >= limit;
    assert offset <=0 && offset <= buffer.length;
    for (int i = offset; i < limit;) {
      i += Character.toChars(
              Character.toUpperCase(
                  Character.codePointAt(buffer, i, limit)), buffer, i);
     }
  }

  public static int toCodePoints(char[] src, int srcOff, int srcLen, int[] dest, int destOff) {
    if (srcLen < 0) {
      throw new IllegalArgumentException("srcLen must be >= 0");
    }
    int codePointCount = 0;
    for (int i = 0; i < srcLen; ) {
      final int cp = Character.codePointAt(src, srcOff + i, srcOff + srcLen);
      final int charCount = Character.charCount(cp);
      dest[destOff + codePointCount++] = cp;
      i += charCount;
    }
    return codePointCount;
  }

  public static int toChars(int[] src, int srcOff, int srcLen, char[] dest, int destOff) {
    if (srcLen < 0) {
      throw new IllegalArgumentException("srcLen must be >= 0");
    }
    int written = 0;
    for (int i = 0; i < srcLen; ++i) {
      written += Character.toChars(src[srcOff + i], dest, destOff + written);
    }
    return written;
  }

  public static boolean fill(CharacterBuffer buffer, Reader reader, int numChars) throws IOException {
    assert buffer.buffer.length >= 2;
    if (numChars < 2 || numChars > buffer.buffer.length) {
      throw new IllegalArgumentException("numChars must be >= 2 and <= the buffer size");
    }
    final char[] charBuffer = buffer.buffer;
    buffer.offset = 0;
    final int offset;

    // Install the previously saved ending high surrogate:
    if (buffer.lastTrailingHighSurrogate != 0) {
      charBuffer[0] = buffer.lastTrailingHighSurrogate;
      buffer.lastTrailingHighSurrogate = 0;
      offset = 1;
    } else {
      offset = 0;
    }

    final int read = readFully(reader, charBuffer, offset, numChars - offset);

    buffer.length = offset + read;
    final boolean result = buffer.length == numChars;
    if (buffer.length < numChars) {
      // We failed to fill the buffer. Even if the last char is a high
      // surrogate, there is nothing we can do
      return result;
    }

    if (Character.isHighSurrogate(charBuffer[buffer.length - 1])) {
      buffer.lastTrailingHighSurrogate = charBuffer[--buffer.length];
    }
    return result;
  }

  public static boolean fill(CharacterBuffer buffer, Reader reader) throws IOException {
    return fill(buffer, reader, buffer.buffer.length);
  }

  static int readFully(Reader reader, char[] dest, int offset, int len) throws IOException {
    int read = 0;
    while (read < len) {
      final int r = reader.read(dest, offset + read, len - read);
      if (r == -1) {
        break;
      }
      read += r;
    }
    return read;
  }

  public static final class CharacterBuffer {
    
    private final char[] buffer;
    private int offset;
    private int length;
    // NOTE: not private so outer class can access without
    // $access methods:
    char lastTrailingHighSurrogate;
    
    CharacterBuffer(char[] buffer, int offset, int length) {
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }
    
    public char[] getBuffer() {
      return buffer;
    }
    
    public int getOffset() {
      return offset;
    }
    
    public int getLength() {
      return length;
    }
    
    public void reset() {
      offset = 0;
      length = 0;
      lastTrailingHighSurrogate = 0;
    }
  }

}
