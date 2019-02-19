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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.ChecksumIndexOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.NoSuchDirectoryException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SegmentInfos implements Cloneable, Iterable<SegmentInfo> {

  /* Works since counter, the old 1st entry, is always >= 0 */
  public static final int FORMAT = -1;


  public static final int FORMAT_LOCKLESS = -2;


  public static final int FORMAT_SINGLE_NORM_FILE = -3;

  public static final int FORMAT_SHARED_DOC_STORE = -4;

  public static final int FORMAT_CHECKSUM = -5;

  public static final int FORMAT_DEL_COUNT = -6;


  public static final int FORMAT_HAS_PROX = -7;

  public static final int FORMAT_USER_DATA = -8;

  public static final int FORMAT_DIAGNOSTICS = -9;

  public static final int FORMAT_HAS_VECTORS = -10;

  public static final int FORMAT_3_1 = -11;
  
  /* This must always point to the most recent file format. */
  public static final int CURRENT_FORMAT = FORMAT_3_1;

  public static final int FORMAT_MINIMUM = FORMAT;
  public static final int FORMAT_MAXIMUM = CURRENT_FORMAT;
  
  public int counter = 0;    // used to name new segments
  long version = System.currentTimeMillis();

  private long generation = 0;     // generation of the "segments_N" for the next commit
  private long lastGeneration = 0; // generation of the "segments_N" file we last successfully read
                                   // or wrote; this is normally the same as generation except if
                                   // there was an IOException that had interrupted a commit

  private Map<String,String> userData = Collections.<String,String>emptyMap();       // Opaque Map<String, String> that user can specify during IndexWriter.commit

  private int format;
  
  private List<SegmentInfo> segments = new ArrayList<SegmentInfo>();
  private Set<SegmentInfo> segmentSet = new HashSet<SegmentInfo>();
  private transient List<SegmentInfo> cachedUnmodifiableList;
  private transient Set<SegmentInfo> cachedUnmodifiableSet;  
  
  private static PrintStream infoStream = null;

  public void setFormat(int format) {
    this.format = format;
  }

  public int getFormat() {
    return format;
  }

  public SegmentInfo info(int i) {
    return segments.get(i);
  }

  public static long getLastCommitGeneration(String[] files) {
    if (files == null) {
      return -1;
    }
    long max = -1;
    for (int i = 0; i < files.length; i++) {
      String file = files[i];
      if (file.startsWith(IndexFileNames.SEGMENTS) && !file.equals(IndexFileNames.SEGMENTS_GEN)) {
        long gen = generationFromSegmentsFileName(file);
        if (gen > max) {
          max = gen;
        }
      }
    }
    return max;
  }

  public static long getLastCommitGeneration(Directory directory) throws IOException {
    try {
      return getLastCommitGeneration(directory.listAll());
    } catch (NoSuchDirectoryException nsde) {
      return -1;
    }
  }

  public static String getLastCommitSegmentsFileName(String[] files) throws IOException {
    return IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                 "",
                                                 getLastCommitGeneration(files));
  }

  public static String getLastCommitSegmentsFileName(Directory directory) throws IOException {
    return IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                 "",
                                                 getLastCommitGeneration(directory));
  }

  public String getSegmentsFileName() {
    return IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                 "",
                                                 lastGeneration);
  }

  public static long generationFromSegmentsFileName(String fileName) {
    if (fileName.equals(IndexFileNames.SEGMENTS)) {
      return 0;
    } else if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
      return Long.parseLong(fileName.substring(1+IndexFileNames.SEGMENTS.length()),
                            Character.MAX_RADIX);
    } else {
      throw new IllegalArgumentException("fileName \"" + fileName + "\" is not a segments file");
    }
  }


  public String getNextSegmentFileName() {
    long nextGeneration;

    if (generation == -1) {
      nextGeneration = 1;
    } else {
      nextGeneration = generation+1;
    }
    return IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                 "",
                                                 nextGeneration);
  }

  public final void read(Directory directory, String segmentFileName) throws CorruptIndexException, IOException {
    boolean success = false;

    // Clear any previous segments:
    this.clear();

    ChecksumIndexInput input = new ChecksumIndexInput(directory.openInput(segmentFileName));

    generation = generationFromSegmentsFileName(segmentFileName);

    lastGeneration = generation;

    try {
      int format = input.readInt();
      // check that it is a format we can understand
      if (format > FORMAT_MINIMUM) {
        throw new IndexFormatTooOldException(input, format,
          FORMAT_MINIMUM, FORMAT_MAXIMUM);
      }
      if (format < FORMAT_MAXIMUM) {
        throw new IndexFormatTooNewException(input, format,
          FORMAT_MINIMUM, FORMAT_MAXIMUM);
      }
      version = input.readLong(); // read version
      counter = input.readInt(); // read counter
      
      for (int i = input.readInt(); i > 0; i--) { // read segmentInfos
        SegmentInfo si = new SegmentInfo(directory, format, input);
        if (si.getVersion() == null) {
          // It's a pre-3.1 segment, upgrade its version to either 3.0 or 2.x
          Directory dir = directory;
          if (si.getDocStoreOffset() != -1) {
            if (si.getDocStoreIsCompoundFile()) {
              dir = new CompoundFileReader(dir, IndexFileNames.segmentFileName(
                  si.getDocStoreSegment(),
                  IndexFileNames.COMPOUND_FILE_STORE_EXTENSION), 1024);
            }
          } else if (si.getUseCompoundFile()) {
            dir = new CompoundFileReader(dir, IndexFileNames.segmentFileName(
                si.name, IndexFileNames.COMPOUND_FILE_EXTENSION), 1024);
          }

          try {
            String store = si.getDocStoreOffset() != -1 ? si.getDocStoreSegment() : si.name;
            si.setVersion(FieldsReader.detectCodeVersion(dir, store));
          } finally {
            // If we opened the directory, close it
            if (dir != directory) dir.close();
          }
        }
        add(si);
      }
      
      if(format >= 0){    // in old format the version number may be at the end of the file
        if (input.getFilePointer() >= input.length())
          version = System.currentTimeMillis(); // old file format without version number
        else
          version = input.readLong(); // read version
      }

      if (format <= FORMAT_USER_DATA) {
        if (format <= FORMAT_DIAGNOSTICS) {
          userData = input.readStringStringMap();
        } else if (0 != input.readByte()) {
          userData = Collections.singletonMap("userData", input.readString());
        } else {
          userData = Collections.<String,String>emptyMap();
        }
      } else {
        userData = Collections.<String,String>emptyMap();
      }

      if (format <= FORMAT_CHECKSUM) {
        final long checksumNow = input.getChecksum();
        final long checksumThen = input.readLong();
        if (checksumNow != checksumThen)
          throw new CorruptIndexException("checksum mismatch in segments file (resource: " + input + ")");
      }
      success = true;
    }
    finally {
      input.close();
      if (!success) {
        // Clear any segment infos we had loaded so we
        // have a clean slate on retry:
        this.clear();
      }
    }
  }

  public final void read(Directory directory) throws CorruptIndexException, IOException {

    generation = lastGeneration = -1;

    new FindSegmentsFile(directory) {

      @Override
      protected Object doBody(String segmentFileName) throws CorruptIndexException, IOException {
        read(directory, segmentFileName);
        return null;
      }
    }.run();
  }

  // Only non-null after prepareCommit has been called and
  // before finishCommit is called
  ChecksumIndexOutput pendingSegnOutput;

  private final void write(Directory directory) throws IOException {

    String segmentFileName = getNextSegmentFileName();

    // Always advance the generation on write:
    if (generation == -1) {
      generation = 1;
    } else {
      generation++;
    }

    ChecksumIndexOutput segnOutput = null;
    boolean success = false;

    try {
      segnOutput = new ChecksumIndexOutput(directory.createOutput(segmentFileName));
      segnOutput.writeInt(CURRENT_FORMAT); // write FORMAT
      segnOutput.writeLong(version); 
      segnOutput.writeInt(counter); // write counter
      segnOutput.writeInt(size()); // write infos
      for (SegmentInfo si : this) {
        si.write(segnOutput);
      }
      segnOutput.writeStringStringMap(userData);
      segnOutput.prepareCommit();
      pendingSegnOutput = segnOutput;
      success = true;
    } finally {
      if (!success) {
        // We hit an exception above; try to close the file
        // but suppress any exception:
        IOUtils.closeWhileHandlingException(segnOutput);
        try {
          // Try not to leave a truncated segments_N file in
          // the index:
          directory.deleteFile(segmentFileName);
        } catch (Throwable t) {
          // Suppress so we keep throwing the original exception
        }
      }
    }
  }

  public void pruneDeletedSegments() throws IOException {
    for(final Iterator<SegmentInfo> it = segments.iterator(); it.hasNext();) {
      final SegmentInfo info = it.next();
      if (info.getDelCount() == info.docCount) {
        it.remove();
        segmentSet.remove(info);
      }
    }
    assert segmentSet.size() == segments.size();
  }

  @Override
  public Object clone() {
    try {
      final SegmentInfos sis = (SegmentInfos) super.clone();
      // deep clone, first recreate all collections:
      sis.segments = new ArrayList<SegmentInfo>(size());
      sis.segmentSet = new HashSet<SegmentInfo>(size());
      sis.cachedUnmodifiableList = null;
      sis.cachedUnmodifiableSet = null;
      for(final SegmentInfo info : this) {
        // dont directly access segments, use add method!!!
        sis.add((SegmentInfo) info.clone());
      }
      sis.userData = new HashMap<String,String>(userData);
      return sis;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("should not happen", e);
    }
  }

  public long getVersion() {
    return version;
  }
  public long getGeneration() {
    return generation;
  }
  public long getLastGeneration() {
    return lastGeneration;
  }

  @Deprecated
  public static long readCurrentVersion(Directory directory)
    throws CorruptIndexException, IOException {

    // Fully read the segments file: this ensures that it's
    // completely written so that if
    // IndexWriter.prepareCommit has been called (but not
    // yet commit), then the reader will still see itself as
    // current:
    SegmentInfos sis = new SegmentInfos();
    sis.read(directory);
    return sis.version;
  }


  public static void setInfoStream(PrintStream infoStream) {
    SegmentInfos.infoStream = infoStream;
  }

  /* Advanced configuration of retry logic in loading
     segments_N file */
  private static int defaultGenLookaheadCount = 10;

  public static void setDefaultGenLookaheadCount(int count) {
    defaultGenLookaheadCount = count;
  }
  public static int getDefaultGenLookahedCount() {
    return defaultGenLookaheadCount;
  }

  public static PrintStream getInfoStream() {
    return infoStream;
  }

  private static void message(String message) {
    infoStream.println("SIS [" + Thread.currentThread().getName() + "]: " + message);
  }

  public abstract static class FindSegmentsFile {
    
    final Directory directory;

    public FindSegmentsFile(Directory directory) {
      this.directory = directory;
    }

    public Object run() throws CorruptIndexException, IOException {
      return run(null);
    }
    
    public Object run(IndexCommit commit) throws CorruptIndexException, IOException {
      if (commit != null) {
        if (directory != commit.getDirectory())
          throw new IOException("the specified commit does not match the specified Directory");
        return doBody(commit.getSegmentsFileName());
      }

      String segmentFileName = null;
      long lastGen = -1;
      long gen = 0;
      int genLookaheadCount = 0;
      IOException exc = null;
      int retryCount = 0;

      boolean useFirstMethod = true;

      // Loop until we succeed in calling doBody() without
      // hitting an IOException.  An IOException most likely
      // means a commit was in process and has finished, in
      // the time it took us to load the now-old infos files
      // (and segments files).  It's also possible it's a
      // true error (corrupt index).  To distinguish these,
      // on each retry we must see "forward progress" on
      // which generation we are trying to load.  If we
      // don't, then the original error is real and we throw
      // it.
      
      // We have three methods for determining the current
      // generation.  We try the first two in parallel (when
      // useFirstMethod is true), and fall back to the third
      // when necessary.

      while(true) {

        if (useFirstMethod) {

          // List the directory and use the highest
          // segments_N file.  This method works well as long
          // as there is no stale caching on the directory
          // contents (NOTE: NFS clients often have such stale
          // caching):
          String[] files = null;

          long genA = -1;

          files = directory.listAll();
          
          if (files != null) {
            genA = getLastCommitGeneration(files);
          }
          
          if (infoStream != null) {
            message("directory listing genA=" + genA);
          }

          // Also open segments.gen and read its
          // contents.  Then we take the larger of the two
          // gens.  This way, if either approach is hitting
          // a stale cache (NFS) we have a better chance of
          // getting the right generation.
          long genB = -1;
          IndexInput genInput = null;
          try {
            genInput = directory.openInput(IndexFileNames.SEGMENTS_GEN);
          } catch (FileNotFoundException e) {
            if (infoStream != null) {
              message("segments.gen open: FileNotFoundException " + e);
            }
          } catch (IOException e) {
            if (infoStream != null) {
              message("segments.gen open: IOException " + e);
            }
          }
  
          if (genInput != null) {
            try {
              int version = genInput.readInt();
              if (version == FORMAT_LOCKLESS) {
                long gen0 = genInput.readLong();
                long gen1 = genInput.readLong();
                if (infoStream != null) {
                  message("fallback check: " + gen0 + "; " + gen1);
                }
                if (gen0 == gen1) {
                  // The file is consistent.
                  genB = gen0;
                }
              } else {
                throw new IndexFormatTooNewException(genInput, version, FORMAT_LOCKLESS, FORMAT_LOCKLESS);
              }
            } catch (IOException err2) {
              // rethrow any format exception
              if (err2 instanceof CorruptIndexException) throw err2;
            } finally {
              genInput.close();
            }
          }

          if (infoStream != null) {
            message(IndexFileNames.SEGMENTS_GEN + " check: genB=" + genB);
          }

          // Pick the larger of the two gen's:
          if (genA > genB)
            gen = genA;
          else
            gen = genB;

          if (gen == -1) {
            // Neither approach found a generation
            throw new IndexNotFoundException("no segments* file found in " + directory + ": files: " + Arrays.toString(files));
          }
        }

        if (useFirstMethod && lastGen == gen && retryCount >= 2) {
          // Give up on first method -- this is 3rd cycle on
          // listing directory and checking gen file to
          // attempt to locate the segments file.
          useFirstMethod = false;
        }

        // Second method: since both directory cache and
        // file contents cache seem to be stale, just
        // advance the generation.
        if (!useFirstMethod) {
          if (genLookaheadCount < defaultGenLookaheadCount) {
            gen++;
            genLookaheadCount++;
            if (infoStream != null) {
              message("look ahead increment gen to " + gen);
            }
          } else {
            // All attempts have failed -- throw first exc:
            throw exc;
          }
        } else if (lastGen == gen) {
          // This means we're about to try the same
          // segments_N last tried.
          retryCount++;
        } else {
          // Segment file has advanced since our last loop
          // (we made "progress"), so reset retryCount:
          retryCount = 0;
        }

        lastGen = gen;

        segmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                "",
                                                                gen);

        try {
          Object v = doBody(segmentFileName);
          if (infoStream != null) {
            message("success on " + segmentFileName);
          }
          return v;
        } catch (IOException err) {

          // Save the original root cause:
          if (exc == null) {
            exc = err;
          }

          if (infoStream != null) {
            message("primary Exception on '" + segmentFileName + "': " + err + "'; will retry: retryCount=" + retryCount + "; gen = " + gen);
          }

          if (gen > 1 && useFirstMethod && retryCount == 1) {

            // This is our second time trying this same segments
            // file (because retryCount is 1), and, there is
            // possibly a segments_(N-1) (because gen > 1).
            // So, check if the segments_(N-1) exists and
            // try it if so:
            String prevSegmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                               "",
                                                                               gen-1);

            final boolean prevExists;
            prevExists = directory.fileExists(prevSegmentFileName);

            if (prevExists) {
              if (infoStream != null) {
                message("fallback to prior segment file '" + prevSegmentFileName + "'");
              }
              try {
                Object v = doBody(prevSegmentFileName);
                if (infoStream != null) {
                  message("success on fallback " + prevSegmentFileName);
                }
                return v;
              } catch (IOException err2) {
                if (infoStream != null) {
                  message("secondary Exception on '" + prevSegmentFileName + "': " + err2 + "'; will retry");
                }
              }
            }
          }
        }
      }
    }

    protected abstract Object doBody(String segmentFileName) throws CorruptIndexException, IOException;
  }

  @Deprecated
  public SegmentInfos range(int first, int last) {
    SegmentInfos infos = new SegmentInfos();
    infos.addAll(segments.subList(first, last));
    return infos;
  }

  // Carry over generation numbers from another SegmentInfos
  void updateGeneration(SegmentInfos other) {
    lastGeneration = other.lastGeneration;
    generation = other.generation;
  }

  final void rollbackCommit(Directory dir) throws IOException {
    if (pendingSegnOutput != null) {
      try {
        pendingSegnOutput.close();
      } catch (Throwable t) {
        // Suppress so we keep throwing the original exception
        // in our caller
      }

      // Must carefully compute fileName from "generation"
      // since lastGeneration isn't incremented:
      try {
        final String segmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                              "",
                                                                             generation);
        dir.deleteFile(segmentFileName);
      } catch (Throwable t) {
        // Suppress so we keep throwing the original exception
        // in our caller
      }
      pendingSegnOutput = null;
    }
  }


  final void prepareCommit(Directory dir) throws IOException {
    if (pendingSegnOutput != null)
      throw new IllegalStateException("prepareCommit was already called");
    write(dir);
  }


  public Collection<String> files(Directory dir, boolean includeSegmentsFile) throws IOException {
    HashSet<String> files = new HashSet<String>();
    if (includeSegmentsFile) {
      final String segmentFileName = getSegmentsFileName();
      if (segmentFileName != null) {
        /*
         * TODO: if lastGen == -1 we get might get null here it seems wrong to
         * add null to the files set
         */
        files.add(segmentFileName);
      }
    }
    final int size = size();
    for(int i=0;i<size;i++) {
      final SegmentInfo info = info(i);
      if (info.dir == dir) {
        files.addAll(info(i).files());
      }
    }
    return files;
  }

  final void finishCommit(Directory dir) throws IOException {
    if (pendingSegnOutput == null)
      throw new IllegalStateException("prepareCommit was not called");
    boolean success = false;
    try {
      pendingSegnOutput.finishCommit();
      pendingSegnOutput.close();
      pendingSegnOutput = null;
      success = true;
    } finally {
      if (!success)
        rollbackCommit(dir);
    }

    // NOTE: if we crash here, we have left a segments_N
    // file in the directory in a possibly corrupt state (if
    // some bytes made it to stable storage and others
    // didn't).  But, the segments_N file includes checksum
    // at the end, which should catch this case.  So when a
    // reader tries to read it, it will throw a
    // CorruptIndexException, which should cause the retry
    // logic in SegmentInfos to kick in and load the last
    // good (previous) segments_N-1 file.

    final String fileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                  "",
                                                                  generation);
    success = false;
    try {
      dir.sync(Collections.singleton(fileName));
      success = true;
    } finally {
      if (!success) {
        try {
          dir.deleteFile(fileName);
        } catch (Throwable t) {
          // Suppress so we keep throwing the original exception
        }
      }
    }

    lastGeneration = generation;

    try {
      IndexOutput genOutput = dir.createOutput(IndexFileNames.SEGMENTS_GEN);
      try {
        genOutput.writeInt(FORMAT_LOCKLESS);
        genOutput.writeLong(generation);
        genOutput.writeLong(generation);
      } finally {
        genOutput.close();
        dir.sync(Collections.singleton(IndexFileNames.SEGMENTS_GEN));
      }
    } catch (Throwable t) {
      // It's OK if we fail to write this file since it's
      // used only as one of the retry fallbacks.
      try {
        dir.deleteFile(IndexFileNames.SEGMENTS_GEN);
      } catch (Throwable t2) {
        // Ignore; this file is only used in a retry
        // fallback on init.
      }
      if (t instanceof ThreadInterruptedException) {
        throw (ThreadInterruptedException) t;
      }
    }
  }


  final void commit(Directory dir) throws IOException {
    prepareCommit(dir);
    finishCommit(dir);
  }

  public String toString(Directory directory) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(getSegmentsFileName()).append(": ");
    final int count = size();
    for(int i = 0; i < count; i++) {
      if (i > 0) {
        buffer.append(' ');
      }
      final SegmentInfo info = info(i);
      buffer.append(info.toString(directory, 0));
    }
    return buffer.toString();
  }

  public Map<String,String> getUserData() {
    return userData;
  }

  void setUserData(Map<String,String> data) {
    if (data == null) {
      userData = Collections.<String,String>emptyMap();
    } else {
      userData = data;
    }
  }


  void replace(SegmentInfos other) {
    rollbackSegmentInfos(other.asList());
    lastGeneration = other.lastGeneration;
  }

  public int totalDocCount() {
    int count = 0;
    for(SegmentInfo info : this) {
      count += info.docCount;
    }
    return count;
  }

  public void changed() {
    version++;
  }
  
  void applyMergeChanges(MergePolicy.OneMerge merge, boolean dropSegment) {
    final Set<SegmentInfo> mergedAway = new HashSet<SegmentInfo>(merge.segments);
    boolean inserted = false;
    int newSegIdx = 0;
    for (int segIdx = 0, cnt = segments.size(); segIdx < cnt; segIdx++) {
      assert segIdx >= newSegIdx;
      final SegmentInfo info = segments.get(segIdx);
      if (mergedAway.contains(info)) {
        if (!inserted && !dropSegment) {
          segments.set(segIdx, merge.info);
          inserted = true;
          newSegIdx++;
        }
      } else {
        segments.set(newSegIdx, info);
        newSegIdx++;
      }
    }

    // Either we found place to insert segment, or, we did
    // not, but only because all segments we merged became
    // deleted while we are merging, in which case it should
    // be the case that the new segment is also all deleted,
    // we insert it at the beginning if it should not be dropped:
    if (!inserted && !dropSegment) {
      segments.add(0, merge.info);
    }

    // the rest of the segments in list are duplicates, so don't remove from map, only list!
    segments.subList(newSegIdx, segments.size()).clear();
    
    // update the Set
    if (!dropSegment) {
      segmentSet.add(merge.info);
    }
    segmentSet.removeAll(mergedAway);
    
    assert segmentSet.size() == segments.size();
  }

  List<SegmentInfo> createBackupSegmentInfos(boolean cloneChildren) {
    if (cloneChildren) {
      final List<SegmentInfo> list = new ArrayList<SegmentInfo>(size());
      for(final SegmentInfo info : this) {
        list.add((SegmentInfo) info.clone());
      }
      return list;
    } else {
      return new ArrayList<SegmentInfo>(segments);
    }
  }
  
  void rollbackSegmentInfos(List<SegmentInfo> infos) {
    this.clear();
    this.addAll(infos);
  }
  
  // @Override (comment out until Java 6)
  public Iterator<SegmentInfo> iterator() {
    return asList().iterator();
  }
  
  public List<SegmentInfo> asList() {
    if (cachedUnmodifiableList == null) {
      cachedUnmodifiableList = Collections.unmodifiableList(segments);
    }
    return cachedUnmodifiableList;
  }
  
  public Set<SegmentInfo> asSet() {
    if (cachedUnmodifiableSet == null) {
      cachedUnmodifiableSet = Collections.unmodifiableSet(segmentSet);
    }
    return cachedUnmodifiableSet;
  }
  
  public int size() {
    return segments.size();
  }

  public void add(SegmentInfo si) {
    if (segmentSet.contains(si)) {
      throw new IllegalStateException("Cannot add the same segment two times to this SegmentInfos instance");
    }
    segments.add(si);
    segmentSet.add(si);
    assert segmentSet.size() == segments.size();
  }
  
  public void addAll(Iterable<SegmentInfo> sis) {
    for (final SegmentInfo si : sis) {
      this.add(si);
    }
  }
  
  public void clear() {
    segments.clear();
    segmentSet.clear();
  }
  
  public void remove(SegmentInfo si) {
    final int index = this.indexOf(si);
    if (index >= 0) {
      this.remove(index);
    }
  }
  
  public void remove(int index) {
    segmentSet.remove(segments.remove(index));
    assert segmentSet.size() == segments.size();
  }
  
  public boolean contains(SegmentInfo si) {
    return segmentSet.contains(si);
  }

  public int indexOf(SegmentInfo si) {
    if (segmentSet.contains(si)) {
      return segments.indexOf(si);
    } else {
      return -1;
    }
  }

}
