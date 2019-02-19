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
import java.util.Set;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.KeywordAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;


public final class KeywordMarkerFilter extends TokenFilter {

  private final KeywordAttribute keywordAttr = addAttribute(KeywordAttribute.class);
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final CharArraySet keywordSet;


  public KeywordMarkerFilter(final TokenStream in,
      final CharArraySet keywordSet) {
    super(in);
    this.keywordSet = keywordSet;
  }


  public KeywordMarkerFilter(final TokenStream in, final Set<?> keywordSet) {
    this(in, keywordSet instanceof CharArraySet ? (CharArraySet) keywordSet
        : CharArraySet.copy(Version.LUCENE_31, keywordSet));
  }

  @Override
  public final boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      if (keywordSet.contains(termAtt.buffer(), 0, termAtt.length())) { 
        keywordAttr.setKeyword(true);
      }
      return true;
    } else {
      return false;
    }
  }
}
