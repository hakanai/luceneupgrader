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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.ReaderUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Implements search over a single IndexReader.
 *
 * <p>Applications usually need only call the inherited
 * {@code #search(Query,int)}
 * or {@code #search(Query,Filter,int)} methods. For
 * performance reasons, if your index is unchanging, you
 * should share a single IndexSearcher instance across
 * multiple searches instead of creating a new one
 * per-search.  If your index has changed and you wish to
 * see the changes reflected in searching, you should
 * use {@code IndexReader#openIfChanged} to obtain a new reader and
 * then create a new IndexSearcher from that.  Also, for
 * low-latency turnaround it's best to use a near-real-time
 * reader ({@code IndexReader#open(IndexWriter,boolean)}).
 * Once you have a new {@code IndexReader}, it's relatively
 * cheap to create a new IndexSearcher from it.
 * 
 * <a name="thread-safety"></a><p><b>NOTE</b>: <code>{@code
 * IndexSearcher}</code> instances are completely
 * thread safe, meaning multiple threads can call any of its
 * methods, concurrently.  If your application requires
 * external synchronization, you should <b>not</b>
 * synchronize on the <code>IndexSearcher</code> instance;
 * use your own (non-Lucene) objects instead.</p>
 */
public class IndexSearcher extends Searcher {
  IndexReader reader;
  private boolean closeReader;
  
  // NOTE: these members might change in incompatible ways
  // in the next release
  protected final IndexReader[] subReaders;
  protected final int[] docStarts;

  protected final IndexSearcher[] subSearchers;

  /** Creates a searcher searching the provided index. */
  public IndexSearcher(IndexReader r) {
    this(r, false, null);
  }

  // Used only when we are an atomic sub-searcher in a parent
  // IndexSearcher that has an ExecutorService, to record
  // our docBase in the parent IndexSearcher:
  private IndexSearcher(IndexReader r,
                        @SuppressWarnings("UnusedParameters") int docBase) // keeping to keep the methods unique. :(
  {
    reader = r;
    closeReader = false;
    subReaders = new IndexReader[] {r};
    docStarts = new int[] {0};
    subSearchers = null;
  }

  private IndexSearcher(IndexReader r, boolean closeReader, ExecutorService executor) {
    reader = r;
    this.closeReader = closeReader;

    List<IndexReader> subReadersList = new ArrayList<IndexReader>();
    gatherSubReaders(subReadersList, reader);
    subReaders = subReadersList.toArray(new IndexReader[subReadersList.size()]);
    docStarts = new int[subReaders.length];
    int maxDoc = 0;
    for (int i = 0; i < subReaders.length; i++) {
      docStarts[i] = maxDoc;
      maxDoc += subReaders[i].maxDoc();
    }
    if (executor == null) {
      subSearchers = null;
    } else {
      subSearchers = new IndexSearcher[subReaders.length];
      for (int i = 0; i < subReaders.length; i++) {
        subSearchers[i] = new IndexSearcher(subReaders[i], docStarts[i]);
      }
    }
  }

  protected void gatherSubReaders(List<IndexReader> allSubReaders, IndexReader r) {
    ReaderUtil.gatherSubReaders(allSubReaders, r);
  }

  /** Expert: Set the Similarity implementation used by this Searcher.
   *
   *
   */
  @Override
  public void setSimilarity(Similarity similarity) {
    super.setSimilarity(similarity);
  }

  @Override
  public Similarity getSimilarity() {
    return super.getSimilarity();
  }

  /**
   * Note that the underlying IndexReader is not closed, if
   * IndexSearcher was constructed with IndexSearcher(IndexReader r).
   * If the IndexReader was supplied implicitly by specifying a directory, then
   * the IndexReader is closed.
   */
  @Override
  public void close() throws IOException {
    if (closeReader) {
      reader.close();
    }
  }


  /** Expert: called to re-write queries into primitive queries.
   */
  @Override
  public Query rewrite(Query original) throws IOException {
    Query query = original;
    for (Query rewrittenQuery = query.rewrite(reader); rewrittenQuery != query;
         rewrittenQuery = query.rewrite(reader)) {
      query = rewrittenQuery;
    }
    return query;
  }

  /**
   * Creates a normalized weight for a top-level {@code Query}.
   * The query is rewritten by this method and {@code Query#createWeight} called,
   * afterwards the {@code Weight} is normalized. The returned {@code Weight}
   * can then directly be used to get a {@code Scorer}.
   * @lucene.internal
   */
  public Weight createNormalizedWeight(Query query) throws IOException {
    return super.createNormalizedWeight(query);
  }


  @Override
  public String toString() {
    return "IndexSearcher(" + reader + ")";
  }
}
