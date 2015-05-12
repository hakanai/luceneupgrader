package org.apache.lucene.index;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockObtainFailedException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/** 
 * An IndexReader which reads indexes with multiple segments.
 */
class DirectoryReader extends IndexReader implements Cloneable {
  protected Directory directory;
  protected boolean readOnly;

  IndexWriter writer;

  private IndexDeletionPolicy deletionPolicy;
  private Lock writeLock;
  private final SegmentInfos segmentInfos;
  private boolean stale;
  private final int termInfosIndexDivisor;

  private boolean rollbackHasChanges;

  private SegmentReader[] subReaders;
  private int[] starts;                           // 1st docno for each segment
  private Map<String,byte[]> normsCache = new HashMap<String,byte[]>();
  private int maxDoc = 0;
  private int numDocs = -1;
  private boolean hasDeletions = false;

  // Max version in index as of when we opened; this can be
  // > our current segmentInfos version in case we were
  // opened on a past IndexCommit:
  private long maxIndexVersion;

  /** This constructor is only used for {@code #doOpenIfChanged()} */
  DirectoryReader(Directory directory, SegmentInfos infos, SegmentReader[] oldReaders, int[] oldStarts,
                  Map<String,byte[]> oldNormsCache, boolean readOnly, boolean doClone, int termInfosIndexDivisor) throws IOException {
    this.directory = directory;
    this.readOnly = readOnly;
    this.segmentInfos = infos;
    this.termInfosIndexDivisor = termInfosIndexDivisor;

    // we put the old SegmentReaders in a map, that allows us
    // to lookup a reader using its segment name
    Map<String,Integer> segmentReaders = new HashMap<String,Integer>();

    if (oldReaders != null) {
      // create a Map SegmentName->SegmentReader
      for (int i = 0; i < oldReaders.length; i++) {
        segmentReaders.put(oldReaders[i].getSegmentName(), i);
      }
    }
    
    SegmentReader[] newReaders = new SegmentReader[infos.size()];
    
    // remember which readers are shared between the old and the re-opened
    // DirectoryReader - we have to incRef those readers
    boolean[] readerShared = new boolean[infos.size()];
    
    for (int i = infos.size() - 1; i>=0; i--) {
      // find SegmentReader for this segment
      Integer oldReaderIndex = segmentReaders.get(infos.info(i).name);
      if (oldReaderIndex == null) {
        // this is a new segment, no old SegmentReader can be reused
        newReaders[i] = null;
      } else {
        // there is an old reader for this segment - we'll try to reopen it
        newReaders[i] = oldReaders[oldReaderIndex.intValue()];
      }

      IOException prior = null;
      boolean success = false;
      try {
        SegmentReader newReader;
        if (newReaders[i] == null || infos.info(i).getUseCompoundFile() != newReaders[i].getSegmentInfo().getUseCompoundFile()) {

          // We should never see a totally new segment during cloning
          assert !doClone;

          // this is a new reader; in case we hit an exception we can close it safely
          newReader = SegmentReader.get(readOnly, infos.info(i), termInfosIndexDivisor);
          readerShared[i] = false;
          newReaders[i] = newReader;
        } else {
          newReader = newReaders[i].reopenSegment(infos.info(i), doClone, readOnly);
          if (newReader == null) {
            // this reader will be shared between the old and the new one,
            // so we must incRef it
            readerShared[i] = true;
            newReaders[i].incRef();
          } else {
            readerShared[i] = false;
            // Steal ref returned to us by reopenSegment:
            newReaders[i] = newReader;
          }
        }
        success = true;
      } catch (IOException ex) {
        prior = ex;
      } finally {
        if (!success) {
          for (i++; i < infos.size(); i++) {
            if (newReaders[i] != null) {
              try {
                if (!readerShared[i]) {
                  // this is a new subReader that is not used by the old one,
                  // we can close it
                  newReaders[i].close();
                } else {
                  // this subReader is also used by the old reader, so instead
                  // closing we must decRef it
                  newReaders[i].decRef();
                }
              } catch (IOException ex) {
                if (prior == null) prior = ex;
              }
            }
          }
        }
        // throw the first exception
        if (prior != null) throw prior;
      }
    }    
    
    // initialize the readers to calculate maxDoc before we try to reuse the old normsCache
    initialize(newReaders);
    
    // try to copy unchanged norms from the old normsCache to the new one
    if (oldNormsCache != null) {
      for (Map.Entry<String,byte[]> entry: oldNormsCache.entrySet()) {
        String field = entry.getKey();
        if (!hasNorms(field)) {
          continue;
        }

        byte[] oldBytes = entry.getValue();

        byte[] bytes = new byte[maxDoc()];

        for (int i = 0; i < subReaders.length; i++) {
          Integer oldReaderIndex = segmentReaders.get(subReaders[i].getSegmentName());

          // this SegmentReader was not re-opened, we can copy all of its norms 
          if (oldReaderIndex != null &&
               (oldReaders[oldReaderIndex.intValue()] == subReaders[i] 
                 || oldReaders[oldReaderIndex.intValue()].norms.get(field) == subReaders[i].norms.get(field))) {
            // we don't have to synchronize here: either this constructor is called from a SegmentReader,
            // in which case no old norms cache is present, or it is called from MultiReader.reopen(),
            // which is synchronized
            System.arraycopy(oldBytes, oldStarts[oldReaderIndex.intValue()], bytes, starts[i], starts[i+1] - starts[i]);
          } else {
            subReaders[i].norms(field, bytes, starts[i]);
          }
        }

        normsCache.put(field, bytes);      // update cache
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    if (hasChanges) {
      buffer.append("*");
    }
    buffer.append(getClass().getSimpleName());
    buffer.append('(');
    final String segmentsFile = segmentInfos.getSegmentsFileName();
    if (segmentsFile != null) {
      buffer.append(segmentsFile);
    }
    if (writer != null) {
      buffer.append(":nrt");
    }
    for (SegmentReader subReader : subReaders) {
      buffer.append(' ');
      buffer.append(subReader);
    }
    buffer.append(')');
    return buffer.toString();
  }

  @Override
  public FieldInfos getFieldInfos() {
    throw new UnsupportedOperationException("call getFieldInfos() on each sub reader, or use ReaderUtil.getMergedFieldInfos, instead");
  }

  private void initialize(SegmentReader[] subReaders) throws IOException {
    this.subReaders = subReaders;
    starts = new int[subReaders.length + 1];    // build starts array
    for (int i = 0; i < subReaders.length; i++) {
      final SegmentReader reader = subReaders[i];
      starts[i] = maxDoc;
      maxDoc += reader.maxDoc();      // compute maxDocs
      if (reader.hasDeletions()) {
        hasDeletions = true;
      }
    }
    starts[subReaders.length] = maxDoc;

    if (!readOnly) {
      maxIndexVersion = SegmentInfos.readCurrentVersion(directory);
    }
  }

  @SuppressWarnings("CloneDoesntCallSuperClone")
  @Override
  public final synchronized Object clone() {
    try {
      return clone(readOnly); // Preserve current readOnly
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /** {@inheritDoc} */
  @Override @Deprecated
  public final synchronized IndexReader clone(boolean openReadOnly) throws IOException {
    // doOpenIfChanged calls ensureOpen
    DirectoryReader newReader = doOpenIfChanged((SegmentInfos) segmentInfos.clone(), true, openReadOnly);

    if (this != newReader) {
      newReader.deletionPolicy = deletionPolicy;
    }
    newReader.writer = writer;
    // If we're cloning a non-readOnly reader, move the
    // writeLock (if there is one) to the new reader:
    if (!openReadOnly && writeLock != null) {
      // In near real-time search, reader is always readonly
      assert writer == null;
      newReader.writeLock = writeLock;
      newReader.hasChanges = hasChanges;
      newReader.hasDeletions = hasDeletions;
      writeLock = null;
      hasChanges = false;
    }

    return newReader;
  }

  private synchronized DirectoryReader doOpenIfChanged(SegmentInfos infos, boolean doClone, boolean openReadOnly) throws IOException {
    DirectoryReader reader;
    if (openReadOnly) {
      reader = new ReadOnlyDirectoryReader(directory, infos, subReaders, starts, normsCache, doClone, termInfosIndexDivisor);
    } else {
      reader = new DirectoryReader(directory, infos, subReaders, starts, normsCache, false, doClone, termInfosIndexDivisor);
    }
    return reader;
  }

  @Override
  public TermFreqVector[] getTermFreqVectors(int n) throws IOException {
    ensureOpen();
    int i = readerIndex(n);        // find segment num
    return subReaders[i].getTermFreqVectors(n - starts[i]); // dispatch to segment
  }

  @Override
  public TermFreqVector getTermFreqVector(int n, String field)
      throws IOException {
    ensureOpen();
    int i = readerIndex(n);        // find segment num
    return subReaders[i].getTermFreqVector(n - starts[i], field);
  }


  @Override
  public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException {
    ensureOpen();
    int i = readerIndex(docNumber);        // find segment num
    subReaders[i].getTermFreqVector(docNumber - starts[i], field, mapper);
  }

  @Override
  public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException {
    ensureOpen();
    int i = readerIndex(docNumber);        // find segment num
    subReaders[i].getTermFreqVector(docNumber - starts[i], mapper);
  }

  @Override
  public int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)

    // NOTE: multiple threads may wind up init'ing
    // numDocs... but that's harmless
    if (numDocs == -1) {        // check cache
      int n = 0;                // cache miss--recompute
      for (SegmentReader subReader : subReaders) n += subReader.numDocs();      // sum from readers
      numDocs = n;
    }
    return numDocs;
  }

  @Override
  public int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return maxDoc;
  }

  // inherit javadoc
  @Override
  public Document document(int n) throws IOException {
    ensureOpen();
    int i = readerIndex(n);                          // find segment num
    return subReaders[i].document(n - starts[i]);    // dispatch to segment reader
  }

  @Override
  public boolean isDeleted(int n) {
    // Don't call ensureOpen() here (it could affect performance)
    final int i = readerIndex(n);                           // find segment num
    return subReaders[i].isDeleted(n - starts[i]);    // dispatch to segment reader
  }

  @Override
  public boolean hasDeletions() {
    ensureOpen();
    return hasDeletions;
  }

  /** {@inheritDoc} */
  @Override @Deprecated
  protected void doDelete(int n) throws IOException {
    numDocs = -1;                             // invalidate cache
    int i = readerIndex(n);                   // find segment num
    subReaders[i].deleteDocument(n - starts[i]);      // dispatch to segment reader
    hasDeletions = true;
  }

  /** {@inheritDoc} */
  @Override @Deprecated
  protected void doUndeleteAll() throws IOException {
    for (SegmentReader subReader : subReaders) subReader.undeleteAll();

    hasDeletions = false;
    numDocs = -1;                                 // invalidate cache
  }

  private int readerIndex(int n) {    // find reader for doc n:
    return readerIndex(n, this.starts, this.subReaders.length);
  }
  
  static int readerIndex(int n, int[] starts, int numSubReaders) {    // find reader for doc n:
    int lo = 0;                                      // search starts array
    int hi = numSubReaders - 1;                  // for first element less

    while (hi >= lo) {
      int mid = (lo + hi) >>> 1;
      int midValue = starts[mid];
      if (n < midValue)
        hi = mid - 1;
      else if (n > midValue)
        lo = mid + 1;
      else {                                      // found a match
        while (mid+1 < numSubReaders && starts[mid+1] == midValue) {
          mid++;                                  // scan to last match
        }
        return mid;
      }
    }
    return hi;
  }

  @Override
  public boolean hasNorms(String field) throws IOException {
    ensureOpen();
    for (SegmentReader subReader : subReaders) {
      if (subReader.hasNorms(field)) return true;
    }
    return false;
  }

  @Override
  public synchronized byte[] norms(String field) throws IOException {
    ensureOpen();
    byte[] bytes = normsCache.get(field);
    if (bytes != null)
      return bytes;          // cache hit
    if (!hasNorms(field))
      return null;

    bytes = new byte[maxDoc()];
    for (int i = 0; i < subReaders.length; i++)
      subReaders[i].norms(field, bytes, starts[i]);
    normsCache.put(field, bytes);      // update cache
    return bytes;
  }

  @Override
  public synchronized void norms(String field, byte[] result, int offset)
    throws IOException {
    ensureOpen();
    byte[] bytes = normsCache.get(field);
    if (bytes==null && !hasNorms(field)) {
      Arrays.fill(result, offset, result.length, Similarity.getDefault().encodeNormValue(1.0f));
    } else if (bytes != null) {                           // cache hit
      System.arraycopy(bytes, 0, result, offset, maxDoc());
    } else {
      for (int i = 0; i < subReaders.length; i++) {      // read from segments
        subReaders[i].norms(field, result, offset + starts[i]);
      }
    }
  }

  /** {@inheritDoc} */
  @Override @Deprecated
  protected void doSetNorm(int n, String field, byte value)
    throws IOException {
    synchronized (normsCache) {
      normsCache.remove(field);                         // clear cache      
    }
    int i = readerIndex(n);                           // find segment num
    subReaders[i].setNorm(n-starts[i], field, value); // dispatch
  }

  @Override
  public TermEnum terms() throws IOException {
    ensureOpen();
    if (subReaders.length == 1) {
      // Optimize single segment case:
      return subReaders[0].terms();
    } else {
      return new MultiTermEnum(this, subReaders, starts, null);
    }
  }

  @Override
  public TermEnum terms(Term term) throws IOException {
    ensureOpen();
    if (subReaders.length == 1) {
      // Optimize single segment case:
      return subReaders[0].terms(term);
    } else {
      return new MultiTermEnum(this, subReaders, starts, term);
    }
  }

  @Override
  public int docFreq(Term t) throws IOException {
    ensureOpen();
    int total = 0;          // sum freqs in segments
    for (SegmentReader subReader : subReaders) total += subReader.docFreq(t);
    return total;
  }

  @Override
  public TermDocs termDocs() throws IOException {
    ensureOpen();
    if (subReaders.length == 1) {
      // Optimize single segment case:
      return subReaders[0].termDocs();
    } else {
      return new MultiTermDocs(this, subReaders, starts);
    }
  }

  @Override
  public TermDocs termDocs(Term term) throws IOException {
    ensureOpen();
    if (subReaders.length == 1) {
      // Optimize single segment case:
      return subReaders[0].termDocs(term);
    } else {
      return super.termDocs(term);
    }
  }

  @Override
  public TermPositions termPositions() throws IOException {
    ensureOpen();
    if (subReaders.length == 1) {
      // Optimize single segment case:
      return subReaders[0].termPositions();
    } else {
      return new MultiTermPositions(this, subReaders, starts);
    }
  }

  /**
   * Tries to acquire the WriteLock on this directory. this method is only valid if this IndexReader is directory
   * owner.
   *
   * @throws StaleReaderException  if the index has changed since this reader was opened
   * @throws CorruptIndexException if the index is corrupt
   * @throws org.apache.lucene.store.LockObtainFailedException
   *                               if another writer has this index open (<code>write.lock</code> could not be
   *                               obtained)
   * @throws IOException           if there is a low-level IO error
   * @deprecated
   */
  @Override @Deprecated
  protected void acquireWriteLock() throws IOException {

    if (readOnly) {
      // NOTE: we should not reach this code w/ the core
      // IndexReader classes; however, an external subclass
      // of IndexReader could reach this.
      ReadOnlySegmentReader.noWrite();
    }

    if (segmentInfos != null) {
      ensureOpen();
      if (stale)
        throw new StaleReaderException("IndexReader out of date and no longer valid for delete, undelete, or setNorm operations");

      if (writeLock == null) {
        Lock writeLock = directory.makeLock(IndexWriter.WRITE_LOCK_NAME);
        if (!writeLock.obtain(IndexWriterConfig.WRITE_LOCK_TIMEOUT)) // obtain write lock
          throw new LockObtainFailedException("Index locked for write: " + writeLock);
        this.writeLock = writeLock;

        // we have to check whether index has changed since this reader was opened.
        // if so, this reader is no longer valid for
        // deletion
        if (SegmentInfos.readCurrentVersion(directory) > maxIndexVersion) {
          stale = true;
          this.writeLock.release();
          this.writeLock = null;
          throw new StaleReaderException("IndexReader out of date and no longer valid for delete, undelete, or setNorm operations");
        }
      }
    }
  }

  /**
   * Commit changes resulting from delete, undeleteAll, or setNorm operations
   * <p/>
   * If an exception is hit, then either no changes or all changes will have been committed to the index (transactional
   * semantics).
   *
   * @throws IOException if there is a low-level IO error
   * @deprecated
   */
  @Override @Deprecated
  protected void doCommit(Map<String,String> commitUserData) throws IOException {
    if (hasChanges) {
      segmentInfos.setUserData(commitUserData);
      // Default deleter (for backwards compatibility) is
      // KeepOnlyLastCommitDeleter:
      IndexFileDeleter deleter = new IndexFileDeleter(directory,
                                                      deletionPolicy == null ? new KeepOnlyLastCommitDeletionPolicy() : deletionPolicy,
                                                      segmentInfos, null, null);
      segmentInfos.updateGeneration(deleter.getLastSegmentInfos());
      segmentInfos.changed();

      // Checkpoint the state we are about to change, in
      // case we have to roll back:
      startCommit();

      final List<SegmentInfo> rollbackSegments = segmentInfos.createBackupSegmentInfos(false);

      boolean success = false;
      try {
        for (SegmentReader subReader : subReaders) subReader.commit();

        // Remove segments that contain only 100% deleted
        // docs:
        segmentInfos.pruneDeletedSegments();

        // Sync all files we just wrote
        directory.sync(segmentInfos.files(directory, false));
        segmentInfos.commit(directory);
        success = true;
      } finally {

        if (!success) {

          // Rollback changes that were made to
          // SegmentInfos but failed to get [fully]
          // committed.  This way this reader instance
          // remains consistent (matched to what's
          // actually in the index):
          rollbackCommit();

          // Recompute deletable files & remove them (so
          // partially written .del files, etc, are
          // removed):
          deleter.refresh();

          // Restore all SegmentInfos (in case we pruned some)
          segmentInfos.rollbackSegmentInfos(rollbackSegments);
        }
      }

      // Have the deleter remove any now unreferenced
      // files due to this commit:
      deleter.checkpoint(segmentInfos, true);
      deleter.close();

      maxIndexVersion = segmentInfos.getVersion();

      if (writeLock != null) {
        writeLock.release();  // release write lock
        writeLock = null;
      }
    }
    hasChanges = false;
  }

  void startCommit() {
    rollbackHasChanges = hasChanges;
    for (SegmentReader subReader : subReaders) {
      subReader.startCommit();
    }
  }

  void rollbackCommit() {
    hasChanges = rollbackHasChanges;
    for (SegmentReader subReader : subReaders) {
      subReader.rollbackCommit();
    }
  }

  @Override
  protected synchronized void doClose() throws IOException {
    IOException ioe = null;
    normsCache = null;
    for (SegmentReader subReader : subReaders) {
      // try to close each reader, even if an exception is thrown
      try {
        subReader.decRef();
      } catch (IOException e) {
        if (ioe == null) ioe = e;
      }
    }

    if (writer != null) {
      // Since we just closed, writer may now be able to
      // delete unused files:
      writer.deletePendingFiles();
    }

    // throw the first exception
    if (ioe != null) throw ioe;
  }

  @Override
  public IndexReader[] getSequentialSubReaders() {
    return subReaders;
  }

  /** Returns the directory this index resides in. */
  @Override
  public Directory directory() {
    // Don't ensureOpen here -- in certain cases, when a
    // cloned/reopened reader needs to commit, it may call
    // this method on the closed original reader
    return directory;
  }

  /** */
  public static Collection<IndexCommit> listCommits(Directory dir) throws IOException {
    final String[] files = dir.listAll();

    List<IndexCommit> commits = new ArrayList<IndexCommit>();

    SegmentInfos latest = new SegmentInfos();
    latest.read(dir);
    final long currentGen = latest.getGeneration();

    commits.add(new ReaderCommit(latest, dir));

    for (final String fileName : files) {

      if (fileName.startsWith(IndexFileNames.SEGMENTS) &&
              !fileName.equals(IndexFileNames.SEGMENTS_GEN) &&
              SegmentInfos.generationFromSegmentsFileName(fileName) < currentGen) {

        SegmentInfos sis = new SegmentInfos();
        try {
          // IOException allowed to throw there, in case
          // segments_N is corrupt
          sis.read(dir, fileName);
        } catch (FileNotFoundException fnfe) {
          // LUCENE-948: on NFS (and maybe others), if
          // you have writers switching back and forth
          // between machines, it's very likely that the
          // dir listing will be stale and will claim a
          // file segments_X exists when in fact it
          // doesn't.  So, we catch this and handle it
          // as if the file does not exist
          sis = null;
        }

        if (sis != null)
          commits.add(new ReaderCommit(sis, dir));
      }
    }

    // Ensure that the commit points are sorted in ascending order.
    Collections.sort(commits);

    return commits;
  }

  private static final class ReaderCommit extends IndexCommit {
    private String segmentsFileName;
    Directory dir;
    long generation;
    long version;

    ReaderCommit(SegmentInfos infos, Directory dir) {
      segmentsFileName = infos.getSegmentsFileName();
      this.dir = dir;
      version = infos.getVersion();
      generation = infos.getGeneration();
    }

    @Override
    public String toString() {
      return "DirectoryReader.ReaderCommit(" + segmentsFileName + ")";
    }

    @Override
    public String getSegmentsFileName() {
      return segmentsFileName;
    }

    @Override
    public Directory getDirectory() {
      return dir;
    }

    @Override
    public long getVersion() {
      return version;
    }

    @Override
    public long getGeneration() {
      return generation;
    }

    @Override
    public boolean isDeleted() {
      return false;
    }

    @Override
    public void delete() {
      throw new UnsupportedOperationException("This IndexCommit does not support deletions");
    }
  }

  static class MultiTermEnum extends TermEnum {
    IndexReader topReader; // used for matching TermEnum to TermDocs
    private SegmentMergeQueue queue;
  
    private Term term;
    private int docFreq;
    final SegmentMergeInfo[] matchingSegments; // null terminated array of matching segments

    public MultiTermEnum(IndexReader topReader, IndexReader[] readers, int[] starts, Term t)
      throws IOException {
      this.topReader = topReader;
      queue = new SegmentMergeQueue(readers.length);
      matchingSegments = new SegmentMergeInfo[readers.length+1];
      for (int i = 0; i < readers.length; i++) {
        IndexReader reader = readers[i];
        TermEnum termEnum;
  
        if (t != null) {
          termEnum = reader.terms(t);
        } else
          termEnum = reader.terms();
  
        SegmentMergeInfo smi = new SegmentMergeInfo(starts[i], termEnum, reader);
        smi.ord = i;
        if (t == null ? smi.next() : termEnum.term() != null)
          queue.add(smi);          // initialize queue
        else
          smi.close();
      }
  
      if (t != null && queue.size() > 0) {
        next();
      }
    }
  
    @Override
    public boolean next() throws IOException {
      for (SegmentMergeInfo smi : matchingSegments) {
        if (smi == null) break;
        if (smi.next())
          queue.add(smi);
        else
          smi.close(); // done with segment
      }
      
      int numMatchingSegments = 0;
      matchingSegments[0] = null;

      SegmentMergeInfo top = queue.top();

      if (top == null) {
        term = null;
        return false;
      }
  
      term = top.term;
      docFreq = 0;
  
      while (top != null && term.compareTo(top.term) == 0) {
        matchingSegments[numMatchingSegments++] = top;
        queue.pop();
        docFreq += top.termEnum.docFreq();    // increment freq
        top = queue.top();
      }

      matchingSegments[numMatchingSegments] = null;
      return true;
    }
  
    @Override
    public Term term() {
      return term;
    }
  
    @Override
    public int docFreq() {
      return docFreq;
    }
  
    @Override
    public void close() throws IOException {
      queue.close();
    }
  }

  static class MultiTermDocs implements TermDocs {
    IndexReader topReader;  // used for matching TermEnum to TermDocs
    protected IndexReader[] readers;
    protected int[] starts;
    protected Term term;
  
    protected int base = 0;
    protected int pointer = 0;
  
    private TermDocs[] readerTermDocs;
    protected TermDocs current;              // == readerTermDocs[pointer]

    private MultiTermEnum tenum;  // the term enum used for seeking... can be null
    int matchingSegmentPos;  // position into the matching segments from tenum
    SegmentMergeInfo smi;     // current segment mere info... can be null

    public MultiTermDocs(IndexReader topReader, IndexReader[] r, int[] s) {
      this.topReader = topReader;
      readers = r;
      starts = s;
  
      readerTermDocs = new TermDocs[r.length];
    }

    public int doc() {
      return base + current.doc();
    }
    public int freq() {
      return current.freq();
    }
  
    public void seek(Term term) {
      this.term = term;
      this.base = 0;
      this.pointer = 0;
      this.current = null;
      this.tenum = null;
      this.smi = null;
      this.matchingSegmentPos = 0;
    }
  
    public void seek(TermEnum termEnum) throws IOException {
      seek(termEnum.term());
      if (termEnum instanceof MultiTermEnum) {
        tenum = (MultiTermEnum)termEnum;
        if (topReader != tenum.topReader)
          tenum = null;
      }
    }
  
    public boolean next() throws IOException {
      for(;;) {
        if (current!=null && current.next()) {
          return true;
        }
        else if (pointer < readers.length) {
          if (tenum != null) {
            smi = tenum.matchingSegments[matchingSegmentPos++];
            if (smi==null) {
              pointer = readers.length;
              return false;
            }
            pointer = smi.ord;
          }
          base = starts[pointer];
          current = termDocs(pointer++);
        } else {
          return false;
        }
      }
    }

    private TermDocs termDocs(int i) throws IOException {
      TermDocs result = readerTermDocs[i];
      if (result == null)
        result = readerTermDocs[i] = termDocs(readers[i]);
      if (smi != null) {
        assert(smi.ord == i);
        assert(smi.termEnum.term().equals(term));
        result.seek(smi.termEnum);
      } else {
        result.seek(term);
      }
      return result;
    }
  
    protected TermDocs termDocs(IndexReader reader)
      throws IOException {
      return term==null ? reader.termDocs(null) : reader.termDocs();
    }
  
    public void close() throws IOException {
      for (TermDocs readerTermDoc : readerTermDocs) {
        if (readerTermDoc != null)
          readerTermDoc.close();
      }
    }
  }

  static class MultiTermPositions extends MultiTermDocs implements TermPositions {
    public MultiTermPositions(IndexReader topReader, IndexReader[] r, int[] s) {
      super(topReader,r,s);
    }
  
    @Override
    protected TermDocs termDocs(IndexReader reader) throws IOException {
      return reader.termPositions();
    }
  
    public int nextPosition() throws IOException {
      return ((TermPositions)current).nextPosition();
    }
    
    public int getPayloadLength() {
      return ((TermPositions)current).getPayloadLength();
    }
     
    public byte[] getPayload(byte[] data, int offset) throws IOException {
      return ((TermPositions)current).getPayload(data, offset);
    }


  }
}
