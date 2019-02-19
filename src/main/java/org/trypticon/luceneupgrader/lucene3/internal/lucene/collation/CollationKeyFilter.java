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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.collation;


import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenFilter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IndexableBinaryStringTools;

import java.io.IOException;
import java.text.Collator;


public final class CollationKeyFilter extends TokenFilter {
  private final Collator collator;
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

  public CollationKeyFilter(TokenStream input, Collator collator) {
    super(input);
    // clone in case JRE doesnt properly sync,
    // or to reduce contention in case they do
    this.collator = (Collator) collator.clone();
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (input.incrementToken()) {
      byte[] collationKey = collator.getCollationKey(termAtt.toString()).toByteArray();
      int encodedLength = IndexableBinaryStringTools.getEncodedLength(
          collationKey, 0, collationKey.length);
      termAtt.resizeBuffer(encodedLength);
      termAtt.setLength(encodedLength);
      IndexableBinaryStringTools.encode(collationKey, 0, collationKey.length,
          termAtt.buffer(), 0, encodedLength);
      return true;
    } else {
      return false;
    }
  }
}
