package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Field;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Fieldable;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Field.Index;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Field.Store;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexWriterConfig.OpenMode;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.LockObtainFailedException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

public class PersistentSnapshotDeletionPolicy extends SnapshotDeletionPolicy {

  // Used to validate that the given directory includes just one document w/ the
  // given ID field. Otherwise, it's not a valid Directory for snapshotting.
  private static final String SNAPSHOTS_ID = "$SNAPSHOTS_DOC$";

  // The index writer which maintains the snapshots metadata
  private final IndexWriter writer;

  public static Map<String, String> readSnapshotsInfo(Directory dir) throws IOException {
    IndexReader r = IndexReader.open(dir, true);
    Map<String, String> snapshots = new HashMap<String, String>();
    try {
      int numDocs = r.numDocs();
      // index is allowed to have exactly one document or 0.
      if (numDocs == 1) {
        Document doc = r.document(r.maxDoc() - 1);
        Field sid = doc.getField(SNAPSHOTS_ID);
        if (sid == null) {
          throw new IllegalStateException("directory is not a valid snapshots store!");
        }
        doc.removeField(SNAPSHOTS_ID);
        for (Fieldable f : doc.getFields()) {
          snapshots.put(f.name(), f.stringValue());
        }
      } else if (numDocs != 0) {
        throw new IllegalStateException(
            "should be at most 1 document in the snapshots directory: " + numDocs);
      }
    } finally {
      r.close();
    }
    return snapshots;
  }
  
  public PersistentSnapshotDeletionPolicy(IndexDeletionPolicy primary,
      Directory dir, OpenMode mode, Version matchVersion)
      throws CorruptIndexException, LockObtainFailedException, IOException {
    super(primary, null);

    // Initialize the index writer over the snapshot directory.
    writer = new IndexWriter(dir, new IndexWriterConfig(matchVersion, null).setOpenMode(mode));
    if (mode != OpenMode.APPEND) {
      // IndexWriter no longer creates a first commit on an empty Directory. So
      // if we were asked to CREATE*, call commit() just to be sure. If the
      // index contains information and mode is CREATE_OR_APPEND, it's a no-op.
      writer.commit();
    }

    try {
      // Initializes the snapshots information. This code should basically run
      // only if mode != CREATE, but if it is, it's no harm as we only open the
      // reader once and immediately close it.
      for (Entry<String, String> e : readSnapshotsInfo(dir).entrySet()) {
        registerSnapshotInfo(e.getKey(), e.getValue(), null);
      }
    } catch (RuntimeException e) {
      writer.close(); // don't leave any open file handles
      throw e;
    } catch (IOException e) {
      writer.close(); // don't leave any open file handles
      throw e;
    }
  }

  @Override
  public synchronized void onInit(List<? extends IndexCommit> commits)
  throws IOException {
    // super.onInit() needs to be called first to ensure that initialization
    // behaves as expected. The superclass, SnapshotDeletionPolicy, ensures
    // that any snapshot IDs with empty IndexCommits are released. Since this 
    // happens, this class needs to persist these changes.
    super.onInit(commits);
    persistSnapshotInfos(null, null);
  }

  @Override
  public synchronized IndexCommit snapshot(String id) throws IOException {
    checkSnapshotted(id);
    if (SNAPSHOTS_ID.equals(id)) {
      throw new IllegalArgumentException(id + " is reserved and cannot be used as a snapshot id");
    }
    persistSnapshotInfos(id, lastCommit.getSegmentsFileName());
    return super.snapshot(id);
  }

  @Override
  public synchronized void release(String id) throws IOException {
    super.release(id);
    persistSnapshotInfos(null, null);
  }

  public void close() throws CorruptIndexException, IOException {
    writer.close();
  }

  private void persistSnapshotInfos(String id, String segment) throws IOException {
    writer.deleteAll();
    Document d = new Document();
    d.add(new Field(SNAPSHOTS_ID, "", Store.YES, Index.NO));
    for (Entry<String, String> e : super.getSnapshots().entrySet()) {
      d.add(new Field(e.getKey(), e.getValue(), Store.YES, Index.NO));
    }
    if (id != null) {
      d.add(new Field(id, segment, Store.YES, Index.NO));
    }
    writer.addDocument(d);
    writer.commit();
  }

}
