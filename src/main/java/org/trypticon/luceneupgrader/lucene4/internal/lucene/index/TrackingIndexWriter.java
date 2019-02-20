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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.ControlledRealTimeReopenThread; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;



public class TrackingIndexWriter {
  private final IndexWriter writer;
  private final AtomicLong indexingGen = new AtomicLong(1);

  public TrackingIndexWriter(IndexWriter writer) {
    this.writer = writer;
  }


  @Deprecated
  public long updateDocument(Term t, Iterable<? extends IndexableField> d, Analyzer a) throws IOException {
    writer.updateDocument(t, d, a);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }


  public long updateDocument(Term t, Iterable<? extends IndexableField> d) throws IOException {
    writer.updateDocument(t, d);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }


  @Deprecated
  public long updateDocuments(Term t, Iterable<? extends Iterable<? extends IndexableField>> docs, Analyzer a) throws IOException {
    writer.updateDocuments(t, docs, a);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }


  public long updateDocuments(Term t, Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    writer.updateDocuments(t, docs);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long deleteDocuments(Term t) throws IOException {
    writer.deleteDocuments(t);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long deleteDocuments(Term... terms) throws IOException {
    writer.deleteDocuments(terms);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long deleteDocuments(Query q) throws IOException {
    writer.deleteDocuments(q);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long deleteDocuments(Query... queries) throws IOException {
    writer.deleteDocuments(queries);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long deleteAll() throws IOException {
    writer.deleteAll();
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }


  @Deprecated
  public long addDocument(Iterable<? extends IndexableField> d, Analyzer a) throws IOException {
    writer.addDocument(d, a);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }


  @Deprecated
  public long addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs, Analyzer a) throws IOException {
    writer.addDocuments(docs, a);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long addDocument(Iterable<? extends IndexableField> d) throws IOException {
    writer.addDocument(d);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs) throws IOException {
    writer.addDocuments(docs);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long addIndexes(Directory... dirs) throws IOException {
    writer.addIndexes(dirs);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long addIndexes(IndexReader... readers) throws IOException {
    writer.addIndexes(readers);
    // Return gen as of when indexing finished:
    return indexingGen.get();
  }

  public long getGeneration() {
    return indexingGen.get();
  }

  public IndexWriter getIndexWriter() {
    return writer;
  }


  public long getAndIncrementGeneration() {
    return indexingGen.getAndIncrement();
  }


  public long tryDeleteDocument(IndexReader reader, int docID) throws IOException {
    if (writer.tryDeleteDocument(reader, docID)) {
      return indexingGen.get();
    } else {
      return -1;
    }
  }
}

