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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis;


import java.io.Reader;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.AttributeFactory;

public abstract class AnalyzerWrapper extends Analyzer {

  protected AnalyzerWrapper(ReuseStrategy reuseStrategy) {
    super(reuseStrategy);
  }

  protected abstract Analyzer getWrappedAnalyzer(String fieldName);

  protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
    return components;
  }

  protected TokenStream wrapTokenStreamForNormalization(String fieldName, TokenStream in) {
    return in;
  }

  protected Reader wrapReader(String fieldName, Reader reader) {
    return reader;
  }

  protected Reader wrapReaderForNormalization(String fieldName, Reader reader) {
    return reader;
  }

  @Override
  protected final TokenStreamComponents createComponents(String fieldName) {
    return wrapComponents(fieldName, getWrappedAnalyzer(fieldName).createComponents(fieldName));
  }

  @Override
  protected final TokenStream normalize(String fieldName, TokenStream in) {
    return wrapTokenStreamForNormalization(fieldName, getWrappedAnalyzer(fieldName).normalize(fieldName, in));
  }

  @Override
  public int getPositionIncrementGap(String fieldName) {
    return getWrappedAnalyzer(fieldName).getPositionIncrementGap(fieldName);
  }

  @Override
  public int getOffsetGap(String fieldName) {
    return getWrappedAnalyzer(fieldName).getOffsetGap(fieldName);
  }

  @Override
  public final Reader initReader(String fieldName, Reader reader) {
    return getWrappedAnalyzer(fieldName).initReader(fieldName, wrapReader(fieldName, reader));
  }

  @Override
  protected final Reader initReaderForNormalization(String fieldName, Reader reader) {
    return getWrappedAnalyzer(fieldName).initReaderForNormalization(fieldName, wrapReaderForNormalization(fieldName, reader));
  }

  @Override
  protected final AttributeFactory attributeFactory(String fieldName) {
    return getWrappedAnalyzer(fieldName).attributeFactory(fieldName);
  }
}
