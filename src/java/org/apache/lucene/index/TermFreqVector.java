package org.apache.lucene.index;

/**
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

/** Provides access to stored term vector of 
 *  a document field.  The vector consists of the name of the field, an array of the terms that occur in the field of the
 * {@code org.apache.lucene.document.Document} and a parallel array of frequencies.  Thus, getTermFrequencies()[5] corresponds with the
 * frequency of getTerms()[5], assuming there are at least 5 terms in the Document.
 */
public interface TermFreqVector {
  /**
   * The {@code org.apache.lucene.document.Fieldable} name.
   * @return The name of the field this vector is associated with.
   * 
   */
  String getField();
  
  /** 
   * @return The number of terms in the term vector.
   */
  int size();

  /** 
   * @return An Array of term texts in ascending order.
   */
  String[] getTerms();


  /** Array of term frequencies. Locations of the array correspond one to one
   *  to the terms in the array obtained from <code>getTerms</code>
   *  method. Each location in the array contains the number of times this
   *  term occurs in the document or the document field.
   */
  int[] getTermFrequencies();


}
