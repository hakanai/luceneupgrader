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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;

public class SnapshotDeletionPolicy extends IndexDeletionPolicy {

  protected final Map<Long,Integer> refCounts = new HashMap<>();

  protected final Map<Long,IndexCommit> indexCommits = new HashMap<>();

  private final IndexDeletionPolicy primary;

  protected IndexCommit lastCommit;

  private boolean initCalled;

  public SnapshotDeletionPolicy(IndexDeletionPolicy primary) {
    this.primary = primary;
  }

  @Override
  public synchronized void onCommit(List<? extends IndexCommit> commits)
      throws IOException {
    primary.onCommit(wrapCommits(commits));
    lastCommit = commits.get(commits.size() - 1);
  }

  @Override
  public synchronized void onInit(List<? extends IndexCommit> commits)
      throws IOException {
    initCalled = true;
    primary.onInit(wrapCommits(commits));
    for(IndexCommit commit : commits) {
      if (refCounts.containsKey(commit.getGeneration())) {
        indexCommits.put(commit.getGeneration(), commit);
      }
    }
    if (!commits.isEmpty()) {
      lastCommit = commits.get(commits.size() - 1);
    }
  }

  public synchronized void release(IndexCommit commit) throws IOException {
    long gen = commit.getGeneration();
    releaseGen(gen);
  }

  protected void releaseGen(long gen) throws IOException {
    if (!initCalled) {
      throw new IllegalStateException("this instance is not being used by IndexWriter; be sure to use the instance returned from writer.getConfig().getIndexDeletionPolicy()");
    }
    Integer refCount = refCounts.get(gen);
    if (refCount == null) {
      throw new IllegalArgumentException("commit gen=" + gen + " is not currently snapshotted");
    }
    int refCountInt = refCount.intValue();
    assert refCountInt > 0;
    refCountInt--;
    if (refCountInt == 0) {
      refCounts.remove(gen);
      indexCommits.remove(gen);
    } else {
      refCounts.put(gen, refCountInt);
    }
  }

  protected synchronized void incRef(IndexCommit ic) {
    long gen = ic.getGeneration();
    Integer refCount = refCounts.get(gen);
    int refCountInt;
    if (refCount == null) {
      indexCommits.put(gen, lastCommit);
      refCountInt = 0;
    } else {
      refCountInt = refCount.intValue();
    }
    refCounts.put(gen, refCountInt+1);
  }

  public synchronized IndexCommit snapshot() throws IOException {
    if (!initCalled) {
      throw new IllegalStateException("this instance is not being used by IndexWriter; be sure to use the instance returned from writer.getConfig().getIndexDeletionPolicy()");
    }
    if (lastCommit == null) {
      // No commit yet, eg this is a new IndexWriter:
      throw new IllegalStateException("No index commit to snapshot");
    }

    incRef(lastCommit);

    return lastCommit;
  }

  public synchronized List<IndexCommit> getSnapshots() {
    return new ArrayList<>(indexCommits.values());
  }

  public synchronized int getSnapshotCount() {
    int total = 0;
    for(Integer refCount : refCounts.values()) {
      total += refCount.intValue();
    }

    return total;
  }


  public synchronized IndexCommit getIndexCommit(long gen) {
    return indexCommits.get(gen);
  }

  private List<IndexCommit> wrapCommits(List<? extends IndexCommit> commits) {
    List<IndexCommit> wrappedCommits = new ArrayList<>(commits.size());
    for (IndexCommit ic : commits) {
      wrappedCommits.add(new SnapshotCommitPoint(ic));
    }
    return wrappedCommits;
  }

  private class SnapshotCommitPoint extends IndexCommit {

    protected IndexCommit cp;

    protected SnapshotCommitPoint(IndexCommit cp) {
      this.cp = cp;
    }

    @Override
    public String toString() {
      return "SnapshotDeletionPolicy.SnapshotCommitPoint(" + cp + ")";
    }

    @Override
    public void delete() {
      synchronized (SnapshotDeletionPolicy.this) {
        // Suppress the delete request if this commit point is
        // currently snapshotted.
        if (!refCounts.containsKey(cp.getGeneration())) {
          cp.delete();
        }
      }
    }

    @Override
    public Directory getDirectory() {
      return cp.getDirectory();
    }

    @Override
    public Collection<String> getFileNames() throws IOException {
      return cp.getFileNames();
    }

    @Override
    public long getGeneration() {
      return cp.getGeneration();
    }

    @Override
    public String getSegmentsFileName() {
      return cp.getSegmentsFileName();
    }

    @Override
    public Map<String, String> getUserData() throws IOException {
      return cp.getUserData();
    }

    @Override
    public boolean isDeleted() {
      return cp.isDeleted();
    }

    @Override
    public int getSegmentCount() {
      return cp.getSegmentCount();
    }
  }
}
