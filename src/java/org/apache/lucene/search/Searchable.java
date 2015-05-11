package org.apache.lucene.search;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;

import java.io.Closeable;
import java.io.IOException;

/**
 * The interface for search implementations.
 * 
 * <p>
 * Searchable is the abstract network protocol for searching. Implementations
 * provide search over a single index, over multiple indices, and over indices
 * on remote servers.
 * 
 * <p>
 * Queries, filters and sort criteria are designed to be compact so that they
 * may be efficiently passed to a remote index, with only the top-scoring hits
 * being returned, rather than every matching hit.
 * 
 * <b>NOTE:</b> this interface is kept public for convenience. Since it is not
 * expected to be implemented directly, it may be changed unexpectedly between
 * releases.
 *
 * @deprecated In 4.0 this interface is removed/absorbed
 * into IndexSearcher
 */
@Deprecated
public interface Searchable extends Closeable {
  
  /**
   * Lower-level search API.
   * 
   * <p>
   * {@code Collector#collect(int)} is called for every document. <br>
   * Collector-based access to remote indexes is discouraged.
   * 
   * <p>
   * Applications should only use this if they need <i>all</i> of the matching
   * documents. The high-level search API ({@code Searcher#search(Query,int)}) is
   * usually more efficient, as it skips non-high-scoring hits.
   * 
   * @param weight
   *          to match documents
   * @param filter
   *          if non-null, used to permit documents to be collected.
   * @param collector
   *          to receive hits
   */
  void search(Weight weight, Filter filter, Collector collector) throws IOException;

  /** Frees resources associated with this Searcher.
   * Be careful not to call this method while you are still using objects
   * that reference this Searchable.
   */
  void close() throws IOException;

  /** Expert: Returns the number of documents containing <code>term</code>.
   * 
   *
   */
  int docFreq(Term term) throws IOException;

  /** Expert: Returns one greater than the largest possible document number.
   * 
   *
   */
  int maxDoc() throws IOException;

  /**
   * Returns the stored fields of document <code>i</code>.
   * 
   *
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  Document doc(int i) throws IOException;

  /**
   * Get the {@code org.apache.lucene.document.Document} at the <code>n</code><sup>th</sup> position. The {@code org.apache.lucene.document.FieldSelector}
   * may be used to determine what {@code org.apache.lucene.document.Field}s to load and how they should be loaded.
   * 
   * <b>NOTE:</b> If the underlying Reader (more specifically, the underlying <code>FieldsReader</code>) is closed before the lazy {@code org.apache.lucene.document.Field} is
   * loaded an exception may be thrown.  If you want the value of a lazy {@code org.apache.lucene.document.Field} to be available after closing you must
   * explicitly load it or fetch the Document again with a new loader.
   * 
   *  
   * @param n Get the document at the <code>n</code><sup>th</sup> position
   * @param fieldSelector The {@code org.apache.lucene.document.FieldSelector} to use to determine what Fields should be loaded on the Document.  May be null, in which case all Fields will be loaded.
   * @return The stored fields of the {@code org.apache.lucene.document.Document} at the nth position
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   * 
   *
   *
   *
   *
   *
   */
  Document doc(int n, FieldSelector fieldSelector) throws IOException;
  
  /** Expert: called to re-write queries into primitive queries.
   */
  Query rewrite(Query query) throws IOException;

}
