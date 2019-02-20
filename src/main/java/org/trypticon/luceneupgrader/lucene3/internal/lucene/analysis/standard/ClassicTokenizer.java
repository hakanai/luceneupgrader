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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.standard;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.Tokenizer;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.TypeAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;



public final class ClassicTokenizer extends Tokenizer {
  private StandardTokenizerInterface scanner;

  public static final int ALPHANUM          = 0;
  public static final int APOSTROPHE        = 1;
  public static final int ACRONYM           = 2;
  public static final int COMPANY           = 3;
  public static final int EMAIL             = 4;
  public static final int HOST              = 5;
  public static final int NUM               = 6;
  public static final int CJ                = 7;


  @Deprecated
  public static final int ACRONYM_DEP       = 8;
  
  public static final String [] TOKEN_TYPES = new String [] {
    "<ALPHANUM>",
    "<APOSTROPHE>",
    "<ACRONYM>",
    "<COMPANY>",
    "<EMAIL>",
    "<HOST>",
    "<NUM>",
    "<CJ>",
    "<ACRONYM_DEP>"
  };

  private boolean replaceInvalidAcronym;
    
  private int maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

  public void setMaxTokenLength(int length) {
    this.maxTokenLength = length;
  }

  public int getMaxTokenLength() {
    return maxTokenLength;
  }


  public ClassicTokenizer(Version matchVersion, Reader input) {
    super(input);
    init(matchVersion);
  }


  public ClassicTokenizer(Version matchVersion, AttributeSource source, Reader input) {
    super(source, input);
    init(matchVersion);
  }


  public ClassicTokenizer(Version matchVersion, AttributeFactory factory, Reader input) {
    super(factory, input);
    init(matchVersion);
  }

  private final void init(Version matchVersion) {
    this.scanner = new ClassicTokenizerImpl(input);

    if (matchVersion.onOrAfter(Version.LUCENE_24)) {
      replaceInvalidAcronym = true;
    } else {
      replaceInvalidAcronym = false;
    }  
  }

  // this tokenizer generates three attributes:
  // term offset, positionIncrement and type
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
  private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

  @Override
  public final boolean incrementToken() throws IOException {
    clearAttributes();
    int posIncr = 1;

    while(true) {
      int tokenType = scanner.getNextToken();

      if (tokenType == StandardTokenizerInterface.YYEOF) {
        return false;
      }

      if (scanner.yylength() <= maxTokenLength) {
        posIncrAtt.setPositionIncrement(posIncr);
        scanner.getText(termAtt);
        final int start = scanner.yychar();
        offsetAtt.setOffset(correctOffset(start), correctOffset(start+termAtt.length()));
        // This 'if' should be removed in the next release. For now, it converts
        // invalid acronyms to HOST. When removed, only the 'else' part should
        // remain.
        if (tokenType == ClassicTokenizer.ACRONYM_DEP) {
          if (replaceInvalidAcronym) {
            typeAtt.setType(ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.HOST]);
            termAtt.setLength(termAtt.length() - 1); // remove extra '.'
          } else {
            typeAtt.setType(ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.ACRONYM]);
          }
        } else {
          typeAtt.setType(ClassicTokenizer.TOKEN_TYPES[tokenType]);
        }
        return true;
      } else
        // When we skip a too-long term, we still increment the
        // position increment
        posIncr++;
    }
  }
  
  @Override
  public final void end() {
    // set final offset
    int finalOffset = correctOffset(scanner.yychar() + scanner.yylength());
    offsetAtt.setOffset(finalOffset, finalOffset);
  }

  @Override
  public void reset(Reader reader) throws IOException {
    super.reset(reader);
    scanner.yyreset(reader);
  }


  @Deprecated
  public boolean isReplaceInvalidAcronym() {
    return replaceInvalidAcronym;
  }


  @Deprecated
  public void setReplaceInvalidAcronym(boolean replaceInvalidAcronym) {
    this.replaceInvalidAcronym = replaceInvalidAcronym;
  }
}
