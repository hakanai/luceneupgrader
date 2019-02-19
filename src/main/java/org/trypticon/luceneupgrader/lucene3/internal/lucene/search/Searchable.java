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
import java.io.Closeable;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.FieldSelector;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;

@Deprecated
public interface Searchable extends Closeable {
  
  void search(Weight weight, Filter filter, Collector collector) throws IOException;


  void close() throws IOException;


  int docFreq(Term term) throws IOException;


  int[] docFreqs(Term[] terms) throws IOException;


  int maxDoc() throws IOException;


  TopDocs search(Weight weight, Filter filter, int n) throws IOException;

  Document doc(int i) throws CorruptIndexException, IOException;

  Document doc(int n, FieldSelector fieldSelector) throws CorruptIndexException, IOException;
  

  Query rewrite(Query query) throws IOException;


  Explanation explain(Weight weight, int doc) throws IOException;


  TopFieldDocs search(Weight weight, Filter filter, int n, Sort sort)
  throws IOException;

}
