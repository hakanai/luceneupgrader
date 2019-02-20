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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import java.io.IOException;
import java.io.Reader;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CharacterUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.VirtualMethod;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CharacterUtils.CharacterBuffer;


public abstract class CharTokenizer extends Tokenizer {
  

  public CharTokenizer(Version matchVersion, Reader input) {
    super(input);
    charUtils = CharacterUtils.getInstance(matchVersion);
    useOldAPI = useOldAPI(matchVersion);

  }
  

  public CharTokenizer(Version matchVersion, AttributeSource source,
      Reader input) {
    super(source, input);
    charUtils = CharacterUtils.getInstance(matchVersion);
    useOldAPI = useOldAPI(matchVersion);
  }
  

  public CharTokenizer(Version matchVersion, AttributeFactory factory,
      Reader input) {
    super(factory, input);
    charUtils = CharacterUtils.getInstance(matchVersion);
    useOldAPI = useOldAPI(matchVersion);
  }
  

  @Deprecated
  public CharTokenizer(Reader input) {
    this(Version.LUCENE_30, input);
  }


  @Deprecated
  public CharTokenizer(AttributeSource source, Reader input) {
    this(Version.LUCENE_30, source, input);
  }


  @Deprecated
  public CharTokenizer(AttributeFactory factory, Reader input) {
    this(Version.LUCENE_30, factory, input);
  }
  
  private int offset = 0, bufferIndex = 0, dataLen = 0, finalOffset = 0;
  private static final int MAX_WORD_LEN = 255;
  private static final int IO_BUFFER_SIZE = 4096;
  
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);;
  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
  
  private final CharacterUtils charUtils;
  private final CharacterBuffer ioBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE);
  

  @Deprecated
  private final boolean useOldAPI;
  

  @Deprecated
  private static final VirtualMethod<CharTokenizer> isTokenCharMethod =
    new VirtualMethod<CharTokenizer>(CharTokenizer.class, "isTokenChar", char.class);
  

  @Deprecated
  private static final VirtualMethod<CharTokenizer> normalizeMethod =
    new VirtualMethod<CharTokenizer>(CharTokenizer.class, "normalize", char.class);


  @Deprecated  
  protected boolean isTokenChar(char c) {
    return isTokenChar((int)c); 
  }


  @Deprecated 
  protected char normalize(char c) {
    return (char) normalize((int) c);
  }


  protected boolean isTokenChar(int c) {
    throw new UnsupportedOperationException("since LUCENE_31 subclasses of CharTokenizer must implement isTokenChar(int)");
  }


  protected int normalize(int c) {
    return c;
  }

  @Override
  public final boolean incrementToken() throws IOException {
    clearAttributes();
    if(useOldAPI) // TODO remove this in LUCENE 4.0
      return incrementTokenOld();
    int length = 0;
    int start = -1; // this variable is always initialized
    int end = -1;
    char[] buffer = termAtt.buffer();
    while (true) {
      if (bufferIndex >= dataLen) {
        offset += dataLen;
        if(!charUtils.fill(ioBuffer, input)) { // read supplementary char aware with CharacterUtils
          dataLen = 0; // so next offset += dataLen won't decrement offset
          if (length > 0) {
            break;
          } else {
            finalOffset = correctOffset(offset);
            return false;
          }
        }
        dataLen = ioBuffer.getLength();
        bufferIndex = 0;
      }
      // use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based methods are gone
      final int c = charUtils.codePointAt(ioBuffer.getBuffer(), bufferIndex);
      final int charCount = Character.charCount(c);
      bufferIndex += charCount;

      if (isTokenChar(c)) {               // if it's a token char
        if (length == 0) {                // start of token
          assert start == -1;
          start = offset + bufferIndex - charCount;
          end = start;
        } else if (length >= buffer.length-1) { // check if a supplementary could run out of bounds
          buffer = termAtt.resizeBuffer(2+length); // make sure a supplementary fits in the buffer
        }
        end += charCount;
        length += Character.toChars(normalize(c), buffer, length); // buffer it, normalized
        if (length >= MAX_WORD_LEN) // buffer overflow! make sure to check for >= surrogate pair could break == test
          break;
      } else if (length > 0)             // at non-Letter w/ chars
        break;                           // return 'em
    }

    termAtt.setLength(length);
    assert start != -1;
    offsetAtt.setOffset(correctOffset(start), finalOffset = correctOffset(end));
    return true;
    
  }
  

  @Deprecated
  private boolean incrementTokenOld() throws IOException {
    int length = 0;
    int start = -1; // this variable is always initialized
    char[] buffer = termAtt.buffer();
    final char[] oldIoBuffer = ioBuffer.getBuffer();
    while (true) {

      if (bufferIndex >= dataLen) {
        offset += dataLen;
        dataLen = input.read(oldIoBuffer);
        if (dataLen == -1) {
          dataLen = 0;                            // so next offset += dataLen won't decrement offset
          if (length > 0) {
            break;
          } else {
            finalOffset = correctOffset(offset);
            return false;
          }
        }
        bufferIndex = 0;
      }

      final char c = oldIoBuffer[bufferIndex++];

      if (isTokenChar(c)) {               // if it's a token char

        if (length == 0) {                // start of token
          assert start == -1;
          start = offset + bufferIndex - 1;
        } else if (length == buffer.length) {
          buffer = termAtt.resizeBuffer(1+length);
        }

        buffer[length++] = normalize(c); // buffer it, normalized

        if (length == MAX_WORD_LEN)      // buffer overflow!
          break;

      } else if (length > 0)             // at non-Letter w/ chars
        break;                           // return 'em
    }

    termAtt.setLength(length);
    assert start != -1;
    offsetAtt.setOffset(correctOffset(start), correctOffset(start+length));
    return true;
  }  
  
  
  
  @Override
  public final void end() {
    // set final offset
    offsetAtt.setOffset(finalOffset, finalOffset);
  }

  @Override
  public void reset(Reader input) throws IOException {
    super.reset(input);
    bufferIndex = 0;
    offset = 0;
    dataLen = 0;
    finalOffset = 0;
    ioBuffer.reset(); // make sure to reset the IO buffer!!
  }


  @Deprecated
  private boolean useOldAPI(Version matchVersion) {
    final Class<? extends CharTokenizer> clazz = this.getClass();
    if (matchVersion.onOrAfter(Version.LUCENE_31)
        && (isTokenCharMethod.isOverriddenAsOf(clazz) || normalizeMethod
            .isOverriddenAsOf(clazz))) throw new IllegalArgumentException(
        "For matchVersion >= LUCENE_31, CharTokenizer subclasses must not override isTokenChar(char) or normalize(char).");
    return !matchVersion.onOrAfter(Version.LUCENE_31);
  } 
}