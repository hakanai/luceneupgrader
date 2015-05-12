package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

/**
  * This is an easy-to-use tool that upgrades all segments of an index from previous Lucene versions
  * to the current segment file format. It can be used from command line:
  * <pre>
  *  java -cp lucene-core.jar org.apache.lucene.index.IndexUpgrader [-delete-prior-commits] [-verbose] indexDir
  * </pre>
  * Alternatively this class can be instantiated and {@code #upgrade} invoked. It uses {@code UpgradeIndexMergePolicy}
  * and triggers the upgrade via an forceMerge request to {@code IndexWriter}.
  * <p>This tool keeps only the last commit in an index; for this
  * reason, if the incoming index has more than one commit, the tool
  * refuses to run by default. Specify {@code -delete-prior-commits}
  * to override this, allowing the tool to delete all but the last commit.
  * From Java code this can be enabled by passing {@code true} to
  * {@code #IndexUpgrader(Directory,Version,PrintStream,boolean)}.
  * <p><b>Warning:</b> This tool may reorder documents if the index was partially
  * upgraded before execution (e.g., documents were added). If your application relies
  * on &quot;monotonicity&quot; of doc IDs (which means that the order in which the documents
  * were added to the index is preserved), do a full forceMerge instead.
  * The {@code MergePolicy} set by {@code IndexWriterConfig} may also reorder
  * documents.
  */
public final class IndexUpgrader {
  private final Directory dir;
  private final PrintStream infoStream;
  private final IndexWriterConfig iwc;
  private final boolean deletePriorCommits;
  
  /** Creates index upgrader on the given directory, using an {@code IndexWriter} using the given
   * {@code matchVersion}. The tool refuses to upgrade indexes with multiple commit points. */
  public IndexUpgrader(Directory dir) {
    this(dir, new IndexWriterConfig(), null, false);
  }

  /** Creates index upgrader on the given directory, using an {@code IndexWriter} using the given
   * config. You have the possibility to upgrade indexes with multiple commit points by removing
   * all older ones. If {@code infoStream} is not {@code null}, all logging output will be sent to this stream. */
  public IndexUpgrader(Directory dir, IndexWriterConfig iwc, PrintStream infoStream, boolean deletePriorCommits) {
    this.dir = dir;
    this.iwc = iwc;
    this.infoStream = infoStream;
    this.deletePriorCommits = deletePriorCommits;
  }

  public void upgrade() throws IOException {
    if (!IndexReader.indexExists(dir)) {
      throw new IndexNotFoundException(dir.toString());
    }

    if (!deletePriorCommits) {
      final Collection<IndexCommit> commits = IndexReader.listCommits(dir);
      if (commits.size() > 1) {
        throw new IllegalArgumentException("This tool was invoked to not delete prior commit points, but the following commits were found: " + commits);
      }
    }

    final IndexWriterConfig c = (IndexWriterConfig) iwc.clone();
    c.setMergePolicy(new UpgradeIndexMergePolicy(c.getMergePolicy()));
    c.setIndexDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());

    try (IndexWriter w = new IndexWriter(dir, c)) {
      w.setInfoStream(infoStream);
      w.message("Upgrading all pre-" + Constants.LUCENE_MAIN_VERSION + " segments of index directory '" + dir + "' to version " + Constants.LUCENE_MAIN_VERSION + "...");
      w.forceMerge(1);
      w.message("All segments upgraded to version " + Constants.LUCENE_MAIN_VERSION);
    }
  }

}
