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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.CollectionUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.FileDeleter;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.InfoStream;

/**
 * This class keeps track of each SegmentInfos instance that is still "live", either because it
 * corresponds to a segments_N file in the Directory (a "commit", i.e. a committed SegmentInfos) or
 * because it's an in-memory SegmentInfos that a writer is actively updating but has not yet
 * committed. This class uses simple reference counting to map the live SegmentInfos instances to
 * individual files in the Directory.
 *
 * <p>The same directory file may be referenced by more than one IndexCommit, i.e. more than one
 * SegmentInfos. Therefore we count how many commits reference each file. When all the commits
 * referencing a certain file have been deleted, the refcount for that file becomes zero, and the
 * file is deleted.
 *
 * <p>A separate deletion policy interface (IndexDeletionPolicy) is consulted on creation (onInit)
 * and once per commit (onCommit), to decide when a commit should be removed.
 *
 * <p>It is the business of the IndexDeletionPolicy to choose when to delete commit points. The
 * actual mechanics of file deletion, retrying, etc, derived from the deletion of commit points is
 * the business of the IndexFileDeleter.
 *
 * <p>The current default deletion policy is {@link KeepOnlyLastCommitDeletionPolicy}, which removes
 * all prior commits when a new commit has completed. This matches the behavior before 2.2.
 *
 * <p>Note that you must hold the write.lock before instantiating this class. It opens segments_N
 * file(s) directly with no retry logic.
 */
final class IndexFileDeleter implements Closeable {

  /* Holds all commits (segments_N) currently in the index.
   * This will have just 1 commit if you are using the
   * default delete policy (KeepOnlyLastCommitDeletionPolicy).
   * Other policies may leave commit points live for longer
   * in which case this list would be longer than 1: */
  private List<CommitPoint> commits = new ArrayList<>();

  /* Holds files we had incref'd from the previous
   * non-commit checkpoint: */
  private final List<String> lastFiles = new ArrayList<>();

  /* Commits that the IndexDeletionPolicy have decided to delete: */
  private List<CommitPoint> commitsToDelete = new ArrayList<>();

  private final InfoStream infoStream;
  private final Directory directoryOrig; // for commit point metadata
  private final Directory directory;
  private final IndexDeletionPolicy policy;

  final boolean startingCommitDeleted;
  private SegmentInfos lastSegmentInfos;

  /** Change to true to see details of reference counts when infoStream is enabled */
  public static boolean VERBOSE_REF_COUNTS = false;

  private final FileDeleter fileDeleter;

  private final IndexWriter writer;

  // called only from assert
  private boolean locked() {
    return writer == null || Thread.holdsLock(writer);
  }

  /**
   * Initialize the deleter: find all previous commits in the Directory, incref the files they
   * reference, call the policy to let it delete commits. This will remove any files not referenced
   * by any of the commits.
   *
   * @throws IOException if there is a low-level IO error
   */
  public IndexFileDeleter(
      String[] files,
      Directory directoryOrig,
      Directory directory,
      IndexDeletionPolicy policy,
      SegmentInfos segmentInfos,
      InfoStream infoStream,
      IndexWriter writer,
      boolean initialIndexExists,
      boolean isReaderInit)
      throws IOException {
    Objects.requireNonNull(writer);
    this.infoStream = infoStream;
    this.writer = writer;

    final String currentSegmentsFile = segmentInfos.getSegmentsFileName();

    if (infoStream.isEnabled("IFD")) {
      infoStream.message(
          "IFD",
          "init: current segments file is \""
              + currentSegmentsFile
              + "\"; deletionPolicy="
              + policy);
    }

    this.policy = policy;
    this.directoryOrig = directoryOrig;
    this.directory = directory;

    this.fileDeleter = new FileDeleter(directory, this::logInfo);

    // First pass: walk the files and initialize our ref
    // counts:
    CommitPoint currentCommitPoint = null;

    if (currentSegmentsFile != null) {
      Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");
      for (String fileName : files) {
        m.reset(fileName);
        if (!fileName.endsWith("write.lock")
            && (m.matches()
                || fileName.startsWith(IndexFileNames.SEGMENTS)
                || fileName.startsWith(IndexFileNames.PENDING_SEGMENTS))) {

          // Add this file to refCounts with initial count 0:
          fileDeleter.initRefCount(fileName);

          if (fileName.startsWith(IndexFileNames.SEGMENTS)) {

            // This is a commit (segments or segments_N), and
            // it's valid (<= the max gen).  Load it, then
            // incref all files it refers to:
            if (infoStream.isEnabled("IFD")) {
              infoStream.message("IFD", "init: load commit \"" + fileName + "\"");
            }
            SegmentInfos sis = SegmentInfos.readCommit(directoryOrig, fileName);

            final CommitPoint commitPoint = new CommitPoint(commitsToDelete, directoryOrig, sis);
            if (sis.getGeneration() == segmentInfos.getGeneration()) {
              currentCommitPoint = commitPoint;
            }
            commits.add(commitPoint);
            incRef(sis, true);

            if (lastSegmentInfos == null
                || sis.getGeneration() > lastSegmentInfos.getGeneration()) {
              lastSegmentInfos = sis;
            }
          }
        }
      }
    }

    if (currentCommitPoint == null && currentSegmentsFile != null && initialIndexExists) {
      // We did not in fact see the segments_N file
      // corresponding to the segmentInfos that was passed
      // in.  Yet, it must exist, because our caller holds
      // the write lock.  This can happen when the directory
      // listing was stale (eg when index accessed via NFS
      // client with stale directory listing cache).  So we
      // try now to explicitly open this commit point:
      SegmentInfos sis = null;
      try {
        sis = SegmentInfos.readCommit(directoryOrig, currentSegmentsFile);
      } catch (IOException e) {
        throw new CorruptIndexException(
            "unable to read current segments_N file", currentSegmentsFile, e);
      }
      if (infoStream.isEnabled("IFD")) {
        infoStream.message(
            "IFD", "forced open of current segments file " + segmentInfos.getSegmentsFileName());
      }
      currentCommitPoint = new CommitPoint(commitsToDelete, directoryOrig, sis);
      commits.add(currentCommitPoint);
      incRef(sis, true);
    }

    if (isReaderInit) {
      // Incoming SegmentInfos may have NRT changes not yet visible in the latest commit, so we have
      // to protect its files from deletion too:
      checkpoint(segmentInfos, false);
    }

    // We keep commits list in sorted order (oldest to newest):
    CollectionUtil.timSort(commits);
    Collection<String> relevantFiles = new HashSet<>(fileDeleter.getAllFiles());
    Set<String> pendingDeletions = directoryOrig.getPendingDeletions();
    if (pendingDeletions.isEmpty() == false) {
      relevantFiles.addAll(pendingDeletions);
    }
    // refCounts only includes "normal" filenames (does not include write.lock)
    inflateGens(segmentInfos, relevantFiles, infoStream);

    // Now delete anything with ref count at 0.  These are
    // presumably abandoned files eg due to crash of
    // IndexWriter.
    Set<String> toDelete = fileDeleter.getUnrefedFiles();
    for (String fileName : toDelete) {
      if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
        throw new IllegalStateException(
            "file \"" + fileName + "\" has refCount=0, which should never happen on init");
      }
      if (infoStream.isEnabled("IFD")) {
        infoStream.message("IFD", "init: removing unreferenced file \"" + fileName + "\"");
      }
    }

    fileDeleter.deleteFilesIfNoRef(toDelete);

    // Finally, give policy a chance to remove things on
    // startup:
    policy.onInit(commits);

    // Always protect the incoming segmentInfos since
    // sometime it may not be the most recent commit
    checkpoint(segmentInfos, false);

    if (currentCommitPoint == null) {
      startingCommitDeleted = false;
    } else {
      startingCommitDeleted = currentCommitPoint.isDeleted();
    }

    deleteCommits();
  }

  /**
   * Set all gens beyond what we currently see in the directory, to avoid double-write in cases
   * where the previous IndexWriter did not gracefully close/rollback (e.g. os/machine crashed or
   * lost power).
   */
  static void inflateGens(SegmentInfos infos, Collection<String> files, InfoStream infoStream) {

    long maxSegmentGen = Long.MIN_VALUE;
    long maxSegmentName = Long.MIN_VALUE;

    // Confusingly, this is the union of liveDocs, field infos, doc values
    // (and maybe others, in the future) gens.  This is somewhat messy,
    // since it means DV updates will suddenly write to the next gen after
    // live docs' gen, for example, but we don't have the APIs to ask the
    // codec which file is which:
    Map<String, Long> maxPerSegmentGen = new HashMap<>();

    for (String fileName : files) {
      if (fileName.equals(IndexWriter.WRITE_LOCK_NAME)) {
        // do nothing
      } else if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
        try {
          maxSegmentGen =
              Math.max(SegmentInfos.generationFromSegmentsFileName(fileName), maxSegmentGen);
        } catch (
            @SuppressWarnings("unused")
            NumberFormatException ignore) {
          // trash file: we have to handle this since we allow anything starting with 'segments'
          // here
        }
      } else if (fileName.startsWith(IndexFileNames.PENDING_SEGMENTS)) {
        try {
          maxSegmentGen =
              Math.max(
                  SegmentInfos.generationFromSegmentsFileName(fileName.substring(8)),
                  maxSegmentGen);
        } catch (
            @SuppressWarnings("unused")
            NumberFormatException ignore) {
          // trash file: we have to handle this since we allow anything starting with
          // 'pending_segments' here
        }
      } else {
        String segmentName = IndexFileNames.parseSegmentName(fileName);
        assert segmentName.startsWith("_") : "wtf? file=" + fileName;

        if (fileName.toLowerCase(Locale.ROOT).endsWith(".tmp")) {
          // A temp file: don't try to look at its gen
          continue;
        }

        maxSegmentName =
            Math.max(maxSegmentName, Long.parseLong(segmentName.substring(1), Character.MAX_RADIX));

        Long curGen = maxPerSegmentGen.get(segmentName);
        if (curGen == null) {
          curGen = 0L;
        }

        try {
          curGen = Math.max(curGen, IndexFileNames.parseGeneration(fileName));
        } catch (
            @SuppressWarnings("unused")
            NumberFormatException ignore) {
          // trash file: we have to handle this since codec regex is only so good
        }
        maxPerSegmentGen.put(segmentName, curGen);
      }
    }

    // Generation is advanced before write:
    infos.setNextWriteGeneration(Math.max(infos.getGeneration(), maxSegmentGen));
    if (infos.counter < 1 + maxSegmentName) {
      if (infoStream.isEnabled("IFD")) {
        infoStream.message(
            "IFD",
            "init: inflate infos.counter to "
                + (1 + maxSegmentName)
                + " vs current="
                + infos.counter);
      }
      infos.counter = 1 + maxSegmentName;
    }

    for (SegmentCommitInfo info : infos) {
      Long gen = maxPerSegmentGen.get(info.info.name);
      assert gen != null;
      long genLong = gen;
      if (info.getNextWriteDelGen() < genLong + 1) {
        if (infoStream.isEnabled("IFD")) {
          infoStream.message(
              "IFD",
              "init: seg="
                  + info.info.name
                  + " set nextWriteDelGen="
                  + (genLong + 1)
                  + " vs current="
                  + info.getNextWriteDelGen());
        }
        info.setNextWriteDelGen(genLong + 1);
      }
      if (info.getNextWriteFieldInfosGen() < genLong + 1) {
        if (infoStream.isEnabled("IFD")) {
          infoStream.message(
              "IFD",
              "init: seg="
                  + info.info.name
                  + " set nextWriteFieldInfosGen="
                  + (genLong + 1)
                  + " vs current="
                  + info.getNextWriteFieldInfosGen());
        }
        info.setNextWriteFieldInfosGen(genLong + 1);
      }
      if (info.getNextWriteDocValuesGen() < genLong + 1) {
        if (infoStream.isEnabled("IFD")) {
          infoStream.message(
              "IFD",
              "init: seg="
                  + info.info.name
                  + " set nextWriteDocValuesGen="
                  + (genLong + 1)
                  + " vs current="
                  + info.getNextWriteDocValuesGen());
        }
        info.setNextWriteDocValuesGen(genLong + 1);
      }
    }
  }

  void ensureOpen() throws AlreadyClosedException {
    writer.ensureOpen(false);
    // since we allow 'closing' state, we must still check this, we could be closing because we hit
    // e.g. OOM
    if (writer.getTragicException() != null) {
      throw new AlreadyClosedException(
          "refusing to delete any files: this IndexWriter hit an unrecoverable exception",
          writer.getTragicException());
    }
  }

  // for testing
  boolean isClosed() {
    try {
      ensureOpen();
      return false;
    } catch (
        @SuppressWarnings("unused")
        AlreadyClosedException ace) {
      return true;
    }
  }

  /**
   * Remove the CommitPoints in the commitsToDelete List by DecRef'ing all files from each
   * SegmentInfos.
   */
  private void deleteCommits() throws IOException {

    int size = commitsToDelete.size();

    if (size > 0) {

      // First decref all files that had been referred to by
      // the now-deleted commits:
      Throwable firstThrowable = null;
      for (int i = 0; i < size; i++) {
        CommitPoint commit = commitsToDelete.get(i);
        if (infoStream.isEnabled("IFD")) {
          infoStream.message(
              "IFD", "deleteCommits: now decRef commit \"" + commit.getSegmentsFileName() + "\"");
        }
        try {
          decRef(commit.files);
        } catch (Throwable t) {
          firstThrowable = IOUtils.useOrSuppress(firstThrowable, t);
        }
      }
      commitsToDelete.clear();

      // Now compact commits to remove deleted ones (preserving the sort):
      size = commits.size();
      int readFrom = 0;
      int writeTo = 0;
      while (readFrom < size) {
        CommitPoint commit = commits.get(readFrom);
        if (!commit.deleted) {
          if (writeTo != readFrom) {
            commits.set(writeTo, commits.get(readFrom));
          }
          writeTo++;
        }
        readFrom++;
      }

      while (size > writeTo) {
        commits.remove(size - 1);
        size--;
      }

      if (firstThrowable != null) {
        throw IOUtils.rethrowAlways(firstThrowable);
      }
    }
  }

  /**
   * Writer calls this when it has hit an error and had to roll back, to tell us that there may now
   * be unreferenced files in the filesystem. So we re-list the filesystem and delete such files. If
   * segmentName is non-null, we will only delete files corresponding to that segment.
   */
  void refresh() throws IOException {
    assert locked();
    Set<String> toDelete = new HashSet<>();

    String[] files = directory.listAll();

    Matcher m = IndexFileNames.CODEC_FILE_PATTERN.matcher("");

    for (int i = 0; i < files.length; i++) {
      String fileName = files[i];
      m.reset(fileName);
      if (!fileName.endsWith("write.lock")
          && fileDeleter.exists(fileName) == false
          && (m.matches()
              || fileName.startsWith(IndexFileNames.SEGMENTS)
              // we only try to clear out pending_segments_N during rollback(), because we don't
              // ref-count it
              // TODO: this is sneaky, should we do this, or change TestIWExceptions? rollback
              // closes anyway, and
              // any leftover file will be deleted/retried on next IW bootup anyway...
              || fileName.startsWith(IndexFileNames.PENDING_SEGMENTS))) {
        // Unreferenced file, so remove it
        if (infoStream.isEnabled("IFD")) {
          infoStream.message(
              "IFD", "refresh: removing newly created unreferenced file \"" + fileName + "\"");
        }
        toDelete.add(fileName);
      }
    }

    fileDeleter.deleteFilesIfNoRef(toDelete);
  }

  @Override
  public void close() throws IOException {
    // DecRef old files from the last checkpoint, if any:
    assert locked();

    if (!lastFiles.isEmpty()) {
      try {
        decRef(lastFiles);
      } finally {
        lastFiles.clear();
      }
    }
  }

  boolean assertCommitsAreNotDeleted(List<CommitPoint> commits) {
    for (CommitPoint commit : commits) {
      assert commit.isDeleted() == false : "Commit [" + commit + "] was deleted already";
    }
    return true;
  }

  /**
   * Revisits the {@link IndexDeletionPolicy} by calling its {@link
   * IndexDeletionPolicy#onCommit(List)} again with the known commits. This is useful in cases where
   * a deletion policy which holds onto index commits is used. The application may know that some
   * commits are not held by the deletion policy anymore and call {@link
   * IndexWriter#deleteUnusedFiles()}, which will attempt to delete the unused commits again.
   */
  void revisitPolicy() throws IOException {
    assert locked();
    if (infoStream.isEnabled("IFD")) {
      infoStream.message("IFD", "now revisitPolicy");
    }

    if (commits.size() > 0) {
      assert assertCommitsAreNotDeleted(commits);
      policy.onCommit(commits);
      deleteCommits();
    }
  }

  /**
   * For definition of "check point" see IndexWriter comments: "Clarification: Check Points (and
   * commits)".
   *
   * <p>Writer calls this when it has made a "consistent change" to the index, meaning new files are
   * written to the index and the in-memory SegmentInfos have been modified to point to those files.
   *
   * <p>This may or may not be a commit (segments_N may or may not have been written).
   *
   * <p>We simply incref the files referenced by the new SegmentInfos and decref the files we had
   * previously seen (if any).
   *
   * <p>If this is a commit, we also call the policy to give it a chance to remove other commits. If
   * any commits are removed, we decref their files as well.
   */
  public void checkpoint(SegmentInfos segmentInfos, boolean isCommit) throws IOException {
    assert locked();

    assert Thread.holdsLock(writer);
    long t0 = System.nanoTime();
    if (infoStream.isEnabled("IFD")) {
      infoStream.message(
          "IFD",
          "now checkpoint \""
              + writer.segString(writer.toLiveInfos(segmentInfos))
              + "\" ["
              + segmentInfos.size()
              + " segments "
              + "; isCommit = "
              + isCommit
              + "]");
    }

    // Incref the files:
    incRef(segmentInfos, isCommit);

    if (isCommit) {
      // Append to our commits list:
      commits.add(new CommitPoint(commitsToDelete, directoryOrig, segmentInfos));

      // Tell policy so it can remove commits:
      assert assertCommitsAreNotDeleted(commits);
      policy.onCommit(commits);

      // Decref files for commits that were deleted by the policy:
      deleteCommits();
    } else {
      // DecRef old files from the last checkpoint, if any:
      try {
        decRef(lastFiles);
      } finally {
        lastFiles.clear();
      }

      // Save files so we can decr on next checkpoint/commit:
      lastFiles.addAll(segmentInfos.files(false));
    }

    if (infoStream.isEnabled("IFD")) {
      long t1 = System.nanoTime();
      infoStream.message("IFD", TimeUnit.NANOSECONDS.toMillis(t1 - t0) + " ms to checkpoint");
    }
  }

  private void logInfo(FileDeleter.MsgType msgType, String msg) {
    if (msgType == FileDeleter.MsgType.REF && VERBOSE_REF_COUNTS == false) {
      // do not log anything
    } else {
      if (infoStream.isEnabled("IFD")) {
        infoStream.message("IFD", msg);
      }
    }
  }

  void incRef(SegmentInfos segmentInfos, boolean isCommit) throws IOException {
    assert locked();
    // If this is a commit point, also incRef the
    // segments_N file:
    for (final String fileName : segmentInfos.files(isCommit)) {
      fileDeleter.incRef(fileName);
    }
  }

  void incRef(Collection<String> files) {
    assert locked();
    fileDeleter.incRef(files);
  }

  /** Decrefs all provided files, even on exception; throws first exception hit, if any. */
  void decRef(Collection<String> files) throws IOException {
    assert locked();
    fileDeleter.decRef(files);
  }

  void decRef(SegmentInfos segmentInfos) throws IOException {
    assert locked();
    decRef(segmentInfos.files(false));
  }

  public boolean exists(String fileName) {
    assert locked();
    return fileDeleter.exists(fileName);
  }

  /** Deletes the specified files, but only if they are new (have not yet been incref'd). */
  void deleteNewFiles(Collection<String> files) throws IOException {
    assert locked();
    fileDeleter.deleteFilesIfNoRef(files);
  }

  /**
   * Holds details for each commit point. This class is also passed to the deletion policy. Note:
   * this class has a natural ordering that is inconsistent with equals.
   */
  private static final class CommitPoint extends IndexCommit {

    Collection<String> files;
    String segmentsFileName;
    boolean deleted;
    Directory directoryOrig;
    Collection<CommitPoint> commitsToDelete;
    long generation;
    final Map<String, String> userData;
    private final int segmentCount;

    public CommitPoint(
        Collection<CommitPoint> commitsToDelete, Directory directoryOrig, SegmentInfos segmentInfos)
        throws IOException {
      this.directoryOrig = directoryOrig;
      this.commitsToDelete = commitsToDelete;
      userData = segmentInfos.getUserData();
      segmentsFileName = segmentInfos.getSegmentsFileName();
      generation = segmentInfos.getGeneration();
      files = Collections.unmodifiableCollection(segmentInfos.files(true));
      segmentCount = segmentInfos.size();
    }

    @Override
    public String toString() {
      return "IndexFileDeleter.CommitPoint(" + segmentsFileName + ")";
    }

    @Override
    public int getSegmentCount() {
      return segmentCount;
    }

    @Override
    public String getSegmentsFileName() {
      return segmentsFileName;
    }

    @Override
    public Collection<String> getFileNames() {
      return files;
    }

    @Override
    public Directory getDirectory() {
      return directoryOrig;
    }

    @Override
    public long getGeneration() {
      return generation;
    }

    @Override
    public Map<String, String> getUserData() {
      return userData;
    }

    /** Called only be the deletion policy, to remove this commit point from the index. */
    @Override
    public void delete() {
      if (!deleted) {
        deleted = true;
        commitsToDelete.add(this);
      }
    }

    @Override
    public boolean isDeleted() {
      return deleted;
    }
  }
}
