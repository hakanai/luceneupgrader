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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SearcherManager; // javadocs
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.Directory;

public abstract class DirectoryReader extends BaseCompositeReader<LeafReader> {

  protected final Directory directory;
  
  public static DirectoryReader open(final Directory directory) throws IOException {
    return StandardDirectoryReader.open(directory, null);
  }
  
  public static DirectoryReader open(final IndexWriter writer) throws IOException {
    return open(writer, true, false);
  }

  public static DirectoryReader open(final IndexWriter writer, boolean applyAllDeletes, boolean writeAllDeletes) throws IOException {
    return writer.getReader(applyAllDeletes, writeAllDeletes);
  }

  public static DirectoryReader open(final IndexCommit commit) throws IOException {
    return StandardDirectoryReader.open(commit.getDirectory(), commit);
  }

  public static DirectoryReader openIfChanged(DirectoryReader oldReader) throws IOException {
    final DirectoryReader newReader = oldReader.doOpenIfChanged();
    assert newReader != oldReader;
    return newReader;
  }

  public static DirectoryReader openIfChanged(DirectoryReader oldReader, IndexCommit commit) throws IOException {
    final DirectoryReader newReader = oldReader.doOpenIfChanged(commit);
    assert newReader != oldReader;
    return newReader;
  }

  public static DirectoryReader openIfChanged(DirectoryReader oldReader, IndexWriter writer) throws IOException {
    return openIfChanged(oldReader, writer, true);
  }

  public static DirectoryReader openIfChanged(DirectoryReader oldReader, IndexWriter writer, boolean applyAllDeletes) throws IOException {
    final DirectoryReader newReader = oldReader.doOpenIfChanged(writer, applyAllDeletes);
    assert newReader != oldReader;
    return newReader;
  }

  public static List<IndexCommit> listCommits(Directory dir) throws IOException {
    final String[] files = dir.listAll();

    List<IndexCommit> commits = new ArrayList<>();

    SegmentInfos latest = SegmentInfos.readLatestCommit(dir);
    final long currentGen = latest.getGeneration();

    commits.add(new StandardDirectoryReader.ReaderCommit(null, latest, dir));

    for(int i=0;i<files.length;i++) {

      final String fileName = files[i];

      if (fileName.startsWith(IndexFileNames.SEGMENTS) &&
          !fileName.equals(IndexFileNames.OLD_SEGMENTS_GEN) &&
          SegmentInfos.generationFromSegmentsFileName(fileName) < currentGen) {

        SegmentInfos sis = null;
        try {
          // IOException allowed to throw there, in case
          // segments_N is corrupt
          sis = SegmentInfos.readCommit(dir, fileName);
        } catch (FileNotFoundException | NoSuchFileException fnfe) {
          // LUCENE-948: on NFS (and maybe others), if
          // you have writers switching back and forth
          // between machines, it's very likely that the
          // dir listing will be stale and will claim a
          // file segments_X exists when in fact it
          // doesn't.  So, we catch this and handle it
          // as if the file does not exist
        }

        if (sis != null) {
          commits.add(new StandardDirectoryReader.ReaderCommit(null, sis, dir));
        }
      }
    }

    // Ensure that the commit points are sorted in ascending order.
    Collections.sort(commits);

    return commits;
  }
  
  public static boolean indexExists(Directory directory) throws IOException {
    // LUCENE-2812, LUCENE-2727, LUCENE-4738: this logic will
    // return true in cases that should arguably be false,
    // such as only IW.prepareCommit has been called, or a
    // corrupt first commit, but it's too deadly to make
    // this logic "smarter" and risk accidentally returning
    // false due to various cases like file description
    // exhaustion, access denied, etc., because in that
    // case IndexWriter may delete the entire index.  It's
    // safer to err towards "index exists" than try to be
    // smart about detecting not-yet-fully-committed or
    // corrupt indices.  This means that IndexWriter will
    // throw an exception on such indices and the app must
    // resolve the situation manually:
    String[] files = directory.listAll();

    String prefix = IndexFileNames.SEGMENTS + "_";
    for(String file : files) {
      if (file.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  protected DirectoryReader(Directory directory, LeafReader[] segmentReaders) throws IOException {
    super(segmentReaders);
    this.directory = directory;
  }

  public final Directory directory() {
    // Don't ensureOpen here -- in certain cases, when a
    // cloned/reopened reader needs to commit, it may call
    // this method on the closed original reader
    return directory;
  }

  protected abstract DirectoryReader doOpenIfChanged() throws IOException;

  protected abstract DirectoryReader doOpenIfChanged(final IndexCommit commit) throws IOException;

  protected abstract DirectoryReader doOpenIfChanged(IndexWriter writer, boolean applyAllDeletes) throws IOException;

  public abstract long getVersion();

  public abstract boolean isCurrent() throws IOException;

  public abstract IndexCommit getIndexCommit() throws IOException;

}
