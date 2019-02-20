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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.io.IOException;
import java.io.Reader;

public abstract class CharacterUtils {
  private static final Java4CharacterUtils JAVA_4 = new Java4CharacterUtils();
  private static final Java5CharacterUtils JAVA_5 = new Java5CharacterUtils();

  public static CharacterUtils getInstance(final Version matchVersion) {
    return matchVersion.onOrAfter(Version.LUCENE_31) ? JAVA_5 : JAVA_4;
  }

  public abstract int codePointAt(final char[] chars, final int offset);

  public abstract int codePointAt(final CharSequence seq, final int offset);
  
  public abstract int codePointAt(final char[] chars, final int offset, final int limit);
  
  public static CharacterBuffer newCharacterBuffer(final int bufferSize) {
    if (bufferSize < 2) {
      throw new IllegalArgumentException("buffersize must be >= 2");
    }
    return new CharacterBuffer(new char[bufferSize], 0, 0);
  }

  public abstract boolean fill(CharacterBuffer buffer, Reader reader) throws IOException;

  private static final class Java5CharacterUtils extends CharacterUtils {
    Java5CharacterUtils() {
    }

    @Override
    public int codePointAt(final char[] chars, final int offset) {
      return Character.codePointAt(chars, offset);
    }

    @Override
    public int codePointAt(final CharSequence seq, final int offset) {
      return Character.codePointAt(seq, offset);
    }

    @Override
    public int codePointAt(final char[] chars, final int offset, final int limit) {
     return Character.codePointAt(chars, offset, limit);
    }

    @Override
    public boolean fill(final CharacterBuffer buffer, final Reader reader) throws IOException {
      final char[] charBuffer = buffer.buffer;
      buffer.offset = 0;
      final int offset;

      // Install the previously saved ending high surrogate:
      if (buffer.lastTrailingHighSurrogate != 0) {
        charBuffer[0] = buffer.lastTrailingHighSurrogate;
        offset = 1;
      } else {
        offset = 0;
      }

      final int read = reader.read(charBuffer,
                                   offset,
                                   charBuffer.length - offset);
      if (read == -1) {
        buffer.length = offset;
        buffer.lastTrailingHighSurrogate = 0;
        return offset != 0;
      }
      assert read > 0;
      buffer.length = read + offset;

      // If we read only a single char, and that char was a
      // high surrogate, read again:
      if (buffer.length == 1
          && Character.isHighSurrogate(charBuffer[buffer.length - 1])) {
        final int read2 = reader.read(charBuffer,
                                      1,
                                      charBuffer.length - 1);
        if (read2 == -1) {
          // NOTE: mal-formed input (ended on a high
          // surrogate)!  Consumer must deal with it...
          return true;
        }
        assert read2 > 0;

        buffer.length += read2;
      }

      if (buffer.length > 1
          && Character.isHighSurrogate(charBuffer[buffer.length - 1])) {
        buffer.lastTrailingHighSurrogate = charBuffer[--buffer.length];
      } else {
        buffer.lastTrailingHighSurrogate = 0;
      }

      return true;
    }
  }

  private static final class Java4CharacterUtils extends CharacterUtils {
    Java4CharacterUtils() {
    }

    @Override
    public int codePointAt(final char[] chars, final int offset) {
      return chars[offset];
    }

    @Override
    public int codePointAt(final CharSequence seq, final int offset) {
      return seq.charAt(offset);
    }

    @Override
    public int codePointAt(final char[] chars, final int offset, final int limit) {
      if(offset >= limit)
        throw new IndexOutOfBoundsException("offset must be less than limit");
      return chars[offset];
    }

    @Override
    public boolean fill(final CharacterBuffer buffer, final Reader reader) throws IOException {
      buffer.offset = 0;
      final int read = reader.read(buffer.buffer);
      if(read == -1)
        return false;
      buffer.length = read;
      return true;
    }

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
