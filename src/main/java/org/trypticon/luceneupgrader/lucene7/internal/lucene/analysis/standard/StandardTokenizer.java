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

package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.standard;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Tokenizer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.TypeAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.AttributeFactory;


public final class StandardTokenizer extends Tokenizer {
  private StandardTokenizerImpl scanner;

  public static final int ALPHANUM = 0;
  public static final int NUM = 1;
  public static final int SOUTHEAST_ASIAN = 2;
  public static final int IDEOGRAPHIC = 3;
  public static final int HIRAGANA = 4;
  public static final int KATAKANA = 5;
  public static final int HANGUL = 6;
  public static final int EMOJI = 7;
  
  public static final String [] TOKEN_TYPES = new String [] {
    "<ALPHANUM>",
    "<NUM>",
    "<SOUTHEAST_ASIAN>",
    "<IDEOGRAPHIC>",
    "<HIRAGANA>",
    "<KATAKANA>",
    "<HANGUL>",
    "<EMOJI>"
  };
  
  public static final int MAX_TOKEN_LENGTH_LIMIT = 1024 * 1024;
  
  private int skippedPositions;

  private int maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

  public void setMaxTokenLength(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("maxTokenLength must be greater than zero");
    } else if (length > MAX_TOKEN_LENGTH_LIMIT) {
      throw new IllegalArgumentException("maxTokenLength may not exceed " + MAX_TOKEN_LENGTH_LIMIT);
    }
    if (length != maxTokenLength) {
      maxTokenLength = length;
      scanner.setBufferSize(length);
    }
  }

  public int getMaxTokenLength() {
    return maxTokenLength;
  }

  public StandardTokenizer() {
    init();
  }

  public StandardTokenizer(AttributeFactory factory) {
    super(factory);
    init();
  }

  private void init() {
    this.scanner = new StandardTokenizerImpl(input);
  }

  // this tokenizer generates three attributes:
  // term offset, positionIncrement and type
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

  /*
   * (non-Javadoc)
   *
   * @see org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.TokenStream#next()
   */
  @Override
  public final boolean incrementToken() throws IOException {
    clearAttributes();
    skippedPositions = 0;

    while(true) {
      int tokenType = scanner.getNextToken();

      if (tokenType == StandardTokenizerImpl.YYEOF) {
        return false;
      }

      if (scanner.yylength() <= maxTokenLength) {
        posIncrAtt.setPositionIncrement(skippedPositions+1);
        scanner.getText(termAtt);
        final int start = scanner.yychar();
        offsetAtt.setOffset(correctOffset(start), correctOffset(start+termAtt.length()));
        typeAtt.setType(StandardTokenizer.TOKEN_TYPES[tokenType]);
        return true;
      } else
        // When we skip a too-long term, we still increment the
        // position increment
        skippedPositions++;
    }
  }
  
  @Override
  public final void end() throws IOException {
    super.end();
    // set final offset
    int finalOffset = correctOffset(scanner.yychar() + scanner.yylength());
    offsetAtt.setOffset(finalOffset, finalOffset);
    // adjust any skipped tokens
    posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement()+skippedPositions);
  }

  @Override
  public void close() throws IOException {
    super.close();
    scanner.yyreset(input);
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    scanner.yyreset(input);
    skippedPositions = 0;
  }
}
