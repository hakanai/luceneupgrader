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

import java.io.Reader;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.AttributeSource;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;



public class LetterTokenizer extends CharTokenizer {
  

  public LetterTokenizer(Version matchVersion, Reader in) {
    super(matchVersion, in);
  }
  

  public LetterTokenizer(Version matchVersion, AttributeSource source, Reader in) {
    super(matchVersion, source, in);
  }
  

  public LetterTokenizer(Version matchVersion, AttributeFactory factory, Reader in) {
    super(matchVersion, factory, in);
  }
  

  @Deprecated
  public LetterTokenizer(Reader in) {
    super(Version.LUCENE_30, in);
  }
  

  @Deprecated
  public LetterTokenizer(AttributeSource source, Reader in) {
    super(Version.LUCENE_30, source, in);
  }
  

  @Deprecated
  public LetterTokenizer(AttributeFactory factory, Reader in) {
    super(Version.LUCENE_30, factory, in);
  }
  
  @Override
  protected boolean isTokenChar(int c) {
    return Character.isLetter(c);
  }
}
