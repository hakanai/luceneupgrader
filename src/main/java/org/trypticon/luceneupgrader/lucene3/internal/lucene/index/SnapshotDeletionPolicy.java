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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;

public class SnapshotDeletionPolicy implements IndexDeletionPolicy {

  private static class SnapshotInfo {
    String id;
    String segmentsFileName;
    IndexCommit commit;
    
    public SnapshotInfo(String id, String segmentsFileName, IndexCommit commit) {
      this.id = id;
      this.segmentsFileName = segmentsFileName;
      this.commit = commit;
    }
    
    @Override
    public String toString() {
      return id + " : " + segmentsFileName;
    }
  }
  
  protected class SnapshotCommitPoint extends IndexCommit {
    protected IndexCommit cp;

    protected SnapshotCommitPoint(IndexCommit cp) {
      this.cp = cp;
    }

    @Override
    public String toString() {
      return "SnapshotDeletionPolicy.SnapshotCommitPoint(" + cp + ")";
    }

    protected boolean shouldDelete(String segmentsFileName) {
      return !segmentsFileToIDs.containsKey(segmentsFileName);
    }

    @Override
    public void delete() {
      synchronized (SnapshotDeletionPolicy.this) {
        // Suppress the delete request if this commit point is
        // currently snapshotted.
        if (shouldDelete(getSegmentsFileName())) {
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
    public long getVersion() {
      return cp.getVersion();
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

  private Map<String, SnapshotInfo> idToSnapshot = new HashMap<String, SnapshotInfo>();

  // multiple IDs could point to the same commit point (segments file name)
  private Map<String, Set<String>> segmentsFileToIDs = new HashMap<String, Set<String>>();

  private IndexDeletionPolicy primary;
  protected IndexCommit lastCommit;

  public SnapshotDeletionPolicy(IndexDeletionPolicy primary) {
    this.primary = primary;
  }

  public SnapshotDeletionPolicy(IndexDeletionPolicy primary,
      Map<String, String> snapshotsInfo) {
    this(primary);

    if (snapshotsInfo != null) {
      // Add the ID->segmentIDs here - the actual IndexCommits will be
      // reconciled on the call to onInit()
      for (Entry<String, String> e : snapshotsInfo.entrySet()) {
        registerSnapshotInfo(e.getKey(), e.getValue(), null);
      }
    }
  }

  protected void checkSnapshotted(String id) {
    if (isSnapshotted(id)) {
      throw new IllegalStateException("Snapshot ID " + id
          + " is already used - must be unique");
    }
  }

  protected void registerSnapshotInfo(String id, String segment, IndexCommit commit) {
    idToSnapshot.put(id, new SnapshotInfo(id, segment, commit));
    Set<String> ids = segmentsFileToIDs.get(segment);
    if (ids == null) {
      ids = new HashSet<String>();
      segmentsFileToIDs.put(segment, ids);
    }
    ids.add(id);
  }

  protected List<IndexCommit> wrapCommits(List<? extends IndexCommit> commits) {
    List<IndexCommit> wrappedCommits = new ArrayList<IndexCommit>(commits.size());
    for (IndexCommit ic : commits) {
      wrappedCommits.add(new SnapshotCommitPoint(ic));
    }
    return wrappedCommits;
  }

  public synchronized IndexCommit getSnapshot(String id) {
    SnapshotInfo snapshotInfo = idToSnapshot.get(id);
    if (snapshotInfo == null) {
      throw new IllegalStateException("No snapshot exists by ID: " + id);
    }
    return snapshotInfo.commit;
  }

  public synchronized Map<String, String> getSnapshots() {
    Map<String, String> snapshots = new HashMap<String, String>();
    for (Entry<String, SnapshotInfo> e : idToSnapshot.entrySet()) {
      snapshots.put(e.getKey(), e.getValue().segmentsFileName);
    }
    return snapshots;
  }

  public boolean isSnapshotted(String id) {
    return idToSnapshot.containsKey(id);
  }

  public synchronized void onCommit(List<? extends IndexCommit> commits)
      throws IOException {
    primary.onCommit(wrapCommits(commits));
    lastCommit = commits.get(commits.size() - 1);
  }

  public synchronized void onInit(List<? extends IndexCommit> commits)
      throws IOException {
    primary.onInit(wrapCommits(commits));
    lastCommit = commits.get(commits.size() - 1);

    /*
     * Assign snapshotted IndexCommits to their correct snapshot IDs as
     * specified in the constructor.
     */
    for (IndexCommit commit : commits) {
      Set<String> ids = segmentsFileToIDs.get(commit.getSegmentsFileName());
      if (ids != null) {
        for (String id : ids) {
          idToSnapshot.get(id).commit = commit;
        }
      }
    }

    /*
     * Second, see if there are any instances where a snapshot ID was specified
     * in the constructor but an IndexCommit doesn't exist. In this case, the ID
     * should be removed.
     * 
     * Note: This code is protective for extreme cases where IDs point to
     * non-existent segments. As the constructor should have received its
     * information via a call to getSnapshots(), the data should be well-formed.
     */
    // Find lost snapshots
    ArrayList<String> idsToRemove = null;
    for (Entry<String, SnapshotInfo> e : idToSnapshot.entrySet()) {
      if (e.getValue().commit == null) {
        if (idsToRemove == null) {
          idsToRemove = new ArrayList<String>();
        }
        idsToRemove.add(e.getKey());
      }
    }
    // Finally, remove those 'lost' snapshots.
    if (idsToRemove != null) {
      for (String id : idsToRemove) {
        SnapshotInfo info = idToSnapshot.remove(id);
        segmentsFileToIDs.remove(info.segmentsFileName);
      }
    }
  }

  public synchronized void release(String id) throws IOException {
    SnapshotInfo info = idToSnapshot.remove(id);
    if (info == null) {
      throw new IllegalStateException("Snapshot doesn't exist: " + id);
    }
    Set<String> ids = segmentsFileToIDs.get(info.segmentsFileName);
    if (ids != null) {
      ids.remove(id);
      if (ids.size() == 0) {
        segmentsFileToIDs.remove(info.segmentsFileName);
      }
    }
  }

  public synchronized IndexCommit snapshot(String id) throws IOException {
    if (lastCommit == null) {
      // no commit exists. Really shouldn't happen, but might be if SDP is
      // accessed before onInit or onCommit were called.
      throw new IllegalStateException("No index commit to snapshot");
    }

    // Can't use the same snapshot ID twice...
    checkSnapshotted(id);

    registerSnapshotInfo(id, lastCommit.getSegmentsFileName(), lastCommit);
    return lastCommit;
  }

}
