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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.FSDirectory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CommandLineUtil;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

public final class IndexUpgrader {
  
  private static final String LOG_PREFIX = "IndexUpgrader";

  private static void printUsage() {
    System.err.println("Upgrades an index so all segments created with a previous Lucene version are rewritten.");
    System.err.println("Usage:");
    System.err.println("  java " + IndexUpgrader.class.getName() + " [-delete-prior-commits] [-verbose] [-dir-impl X] indexDir");
    System.err.println("This tool keeps only the last commit in an index; for this");
    System.err.println("reason, if the incoming index has more than one commit, the tool");
    System.err.println("refuses to run by default. Specify -delete-prior-commits to override");
    System.err.println("this, allowing the tool to delete all but the last commit.");
    System.err.println("Specify a " + FSDirectory.class.getSimpleName() + 
        " implementation through the -dir-impl option to force its use. If no package is specified the " 
        + FSDirectory.class.getPackage().getName() + " package will be used.");
    System.err.println("WARNING: This tool may reorder document IDs!");
    System.exit(1);
  }

  @SuppressWarnings("deprecation")
  public static void main(String[] args) throws IOException {
    String path = null;
    boolean deletePriorCommits = false;
    PrintStream out = null;
    String dirImpl = null;
    int i = 0;
    while (i<args.length) {
      String arg = args[i];
      if ("-delete-prior-commits".equals(arg)) {
        deletePriorCommits = true;
      } else if ("-verbose".equals(arg)) {
        out = System.out;
      } else if (path == null) {
        path = arg;
      } else if ("-dir-impl".equals(arg)) {
        if (i == args.length - 1) {
          System.out.println("ERROR: missing value for -dir-impl option");
          System.exit(1);
        }
        i++;
        dirImpl = args[i];
      }else {
        printUsage();
      }
      i++;
    }
    if (path == null) {
      printUsage();
    }
    
    Directory dir = null;
    if (dirImpl == null) {
      dir = FSDirectory.open(new File(path));
    } else {
      dir = CommandLineUtil.newFSDirectory(dirImpl, new File(path));
    }
    new IndexUpgrader(dir, Version.LUCENE_CURRENT, out, deletePriorCommits).upgrade();
  }
  
  private final Directory dir;
  private final PrintStream infoStream;
  private final IndexWriterConfig iwc;
  private final boolean deletePriorCommits;
  
  public IndexUpgrader(Directory dir, Version matchVersion) {
    this(dir, new IndexWriterConfig(matchVersion, null), null, false);
  }
  

  public IndexUpgrader(Directory dir, Version matchVersion, PrintStream infoStream, boolean deletePriorCommits) {
    this(dir, new IndexWriterConfig(matchVersion, null), infoStream, deletePriorCommits);
  }
  

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

    try (final IndexWriter w = new IndexWriter(dir, c)) {
      w.setInfoStream(infoStream);
      w.message("Upgrading all pre-" + Constants.LUCENE_MAIN_VERSION + " segments of index directory '" + dir + "' to version " + Constants.LUCENE_MAIN_VERSION + "...");
      w.forceMerge(1);
      w.message("All segments upgraded to version " + Constants.LUCENE_MAIN_VERSION);
      w.message("Enforcing commit to rewrite all index metadata...");
      // Workaround for LUCENE-6658
      w.forceCommit();
      w.message("Committed upgraded metadata to index.");
    }
  }
  
}
