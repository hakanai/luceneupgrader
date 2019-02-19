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


import java.io.IOException;
import java.io.Reader;

// the way java.io.FilterReader should work!
public abstract class CharFilter extends Reader {

  protected final Reader input;

  public CharFilter(Reader input) {
    super(input);
    this.input = input;
  }
  

  @Override
  public void close() throws IOException {
    input.close();
  }

  protected abstract int correct(int currentOff);
  
  public final int correctOffset(int currentOff) {
    final int corrected = correct(currentOff);
    return (input instanceof CharFilter) ? ((CharFilter) input).correctOffset(corrected) : corrected;
  }
}
