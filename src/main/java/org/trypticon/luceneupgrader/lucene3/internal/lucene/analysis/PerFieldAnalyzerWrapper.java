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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Fieldable;

import java.io.Reader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;


public final class PerFieldAnalyzerWrapper extends Analyzer {
  private final Analyzer defaultAnalyzer;
  private final Map<String,Analyzer> analyzerMap = new HashMap<String,Analyzer>();



  public PerFieldAnalyzerWrapper(Analyzer defaultAnalyzer) {
    this(defaultAnalyzer, null);
  }
  

  public PerFieldAnalyzerWrapper(Analyzer defaultAnalyzer, 
      Map<String,Analyzer> fieldAnalyzers) {
    this.defaultAnalyzer = defaultAnalyzer;
    if (fieldAnalyzers != null) {
      analyzerMap.putAll(fieldAnalyzers);
    }
  }
  


  @Deprecated
  public void addAnalyzer(String fieldName, Analyzer analyzer) {
    analyzerMap.put(fieldName, analyzer);
  }

  @Override
  public TokenStream tokenStream(String fieldName, Reader reader) {
    Analyzer analyzer = analyzerMap.get(fieldName);
    if (analyzer == null) {
      analyzer = defaultAnalyzer;
    }

    return analyzer.tokenStream(fieldName, reader);
  }
  
  @Override
  public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
    Analyzer analyzer = analyzerMap.get(fieldName);
    if (analyzer == null)
      analyzer = defaultAnalyzer;

    return analyzer.reusableTokenStream(fieldName, reader);
  }
  
  @Override
  public int getPositionIncrementGap(String fieldName) {
    Analyzer analyzer = analyzerMap.get(fieldName);
    if (analyzer == null)
      analyzer = defaultAnalyzer;
    return analyzer.getPositionIncrementGap(fieldName);
  }

  @Override
  public int getOffsetGap(Fieldable field) {
    Analyzer analyzer = analyzerMap.get(field.name());
    if (analyzer == null)
      analyzer = defaultAnalyzer;
    return analyzer.getOffsetGap(field);
  }
  
  @Override
  public String toString() {
    return "PerFieldAnalyzerWrapper(" + analyzerMap + ", default=" + defaultAnalyzer + ")";
  }
}
