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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import java.io.IOException;

public abstract class DocIdSet {

  public static final DocIdSet EMPTY_DOCIDSET = new DocIdSet() {
    
    private final DocIdSetIterator iterator = new DocIdSetIterator() {
      @Override
      public int advance(int target) throws IOException { return NO_MORE_DOCS; }
      @Override
      public int docID() { return NO_MORE_DOCS; }
      @Override
      public int nextDoc() throws IOException { return NO_MORE_DOCS; }
    };
    
    @Override
    public DocIdSetIterator iterator() {
      return iterator;
    }
    
    @Override
    public boolean isCacheable() {
      return true;
    }
  };
    

  public abstract DocIdSetIterator iterator() throws IOException;

  public boolean isCacheable() {
    return false;
  }
}
