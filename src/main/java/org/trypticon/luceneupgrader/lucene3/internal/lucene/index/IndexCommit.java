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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public abstract class IndexCommit implements Comparable<IndexCommit> {

  public abstract String getSegmentsFileName();

  public abstract Collection<String> getFileNames() throws IOException;

  public abstract Directory getDirectory();
  
  public abstract void delete();

  public abstract boolean isDeleted();

  public abstract int getSegmentCount();

  @Override
  public boolean equals(Object other) {
    if (other instanceof IndexCommit) {
      IndexCommit otherCommit = (IndexCommit) other;
      return otherCommit.getDirectory().equals(getDirectory()) && otherCommit.getVersion() == getVersion();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return (int) (getDirectory().hashCode() + getVersion());
  }


  @Deprecated
  public abstract long getVersion();

  public abstract long getGeneration();


  @Deprecated
  public long getTimestamp() throws IOException {
    return getDirectory().fileModified(getSegmentsFileName());
  }


  public abstract Map<String,String> getUserData() throws IOException;
  
  public int compareTo(IndexCommit commit) {
    if (getDirectory() != commit.getDirectory()) {
      throw new UnsupportedOperationException("cannot compare IndexCommits from different Directory instances");
    }

    long gen = getGeneration();
    long comgen = commit.getGeneration();
    if (gen < comgen) {
      return -1;
    } else if (gen > comgen) {
      return 1;
    } else {
      return 0;
    }
  }
}
