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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;

import java.util.Collection;
import java.util.Map;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Directory;


// TODO: this is now a poor name, because this class also represents a
// point-in-time view from an NRT reader
public abstract class IndexCommit implements Comparable<IndexCommit> {

  public abstract String getSegmentsFileName();

  public abstract Collection<String> getFileNames() throws IOException;

  public abstract Directory getDirectory();
  
  public abstract void delete();

  public abstract boolean isDeleted();

  public abstract int getSegmentCount();

  protected IndexCommit() {
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof IndexCommit) {
      IndexCommit otherCommit = (IndexCommit) other;
      return otherCommit.getDirectory() == getDirectory() && otherCommit.getGeneration() == getGeneration();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getDirectory().hashCode() + Long.valueOf(getGeneration()).hashCode();
  }

  public abstract long getGeneration();

  public abstract Map<String,String> getUserData() throws IOException;
  
  @Override
  public int compareTo(IndexCommit commit) {
    if (getDirectory() != commit.getDirectory()) {
      throw new UnsupportedOperationException("cannot compare IndexCommits from different Directory instances");
    }

    long gen = getGeneration();
    long comgen = commit.getGeneration();
    return Long.compare(gen, comgen);
  }

  StandardDirectoryReader getReader() {
    return null;
  }
}
