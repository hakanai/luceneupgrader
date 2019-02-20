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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis;

import java.io.Reader;

public abstract class DelegatingAnalyzerWrapper extends AnalyzerWrapper {
  
  protected DelegatingAnalyzerWrapper(ReuseStrategy fallbackStrategy) {
    super(new DelegatingReuseStrategy(fallbackStrategy));
    // häckidy-hick-hack, because we cannot call super() with a reference to "this":
    ((DelegatingReuseStrategy) getReuseStrategy()).wrapper = this;
  }
  
  @Override
  protected final TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
    return super.wrapComponents(fieldName, components);
  }
  
  @Override
  protected final Reader wrapReader(String fieldName, Reader reader) {
    return super.wrapReader(fieldName, reader);
  }
  
  private static final class DelegatingReuseStrategy extends ReuseStrategy {
    DelegatingAnalyzerWrapper wrapper;
    private final ReuseStrategy fallbackStrategy;
    
    DelegatingReuseStrategy(ReuseStrategy fallbackStrategy) {
      this.fallbackStrategy = fallbackStrategy;
    }
    
    @Override
    public TokenStreamComponents getReusableComponents(Analyzer analyzer, String fieldName) {
      if (analyzer == wrapper) {
        final Analyzer wrappedAnalyzer = wrapper.getWrappedAnalyzer(fieldName);
        return wrappedAnalyzer.getReuseStrategy().getReusableComponents(wrappedAnalyzer, fieldName);
      } else {
        return fallbackStrategy.getReusableComponents(analyzer, fieldName);
      }
    }

    @Override
    public void setReusableComponents(Analyzer analyzer, String fieldName,  TokenStreamComponents components) {
      if (analyzer == wrapper) {
        final Analyzer wrappedAnalyzer = wrapper.getWrappedAnalyzer(fieldName);
        wrappedAnalyzer.getReuseStrategy().setReusableComponents(wrappedAnalyzer, fieldName, components);
      } else {
        fallbackStrategy.setReusableComponents(analyzer, fieldName, components);
      }
    }
  };
  
}