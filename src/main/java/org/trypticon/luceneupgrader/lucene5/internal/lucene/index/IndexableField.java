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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import java.io.Reader;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.DefaultSimilarity;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.similarities.Similarity;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;

// TODO: how to handle versioning here...?



public interface IndexableField {

  public String name();

  public IndexableFieldType fieldType();


  public float boost();

  public BytesRef binaryValue();

  public String stringValue();

  public Reader readerValue();

  public Number numericValue();

  public TokenStream tokenStream(Analyzer analyzer, TokenStream reuse);
}
