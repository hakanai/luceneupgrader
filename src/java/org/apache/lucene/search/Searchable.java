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

  /** Frees resources associated with this Searcher.
   * Be careful not to call this method while you are still using objects
   * that reference this Searchable.
   */
  void close() throws IOException;

  /** Expert: called to re-write queries into primitive queries.
   */
  Query rewrite(Query query) throws IOException;

}
