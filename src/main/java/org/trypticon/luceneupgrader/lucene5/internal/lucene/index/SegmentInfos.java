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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.index;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Version;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;

public final class SegmentInfos implements Cloneable, Iterable<SegmentCommitInfo> {

  public static final int VERSION_40 = 0;

  public static final int VERSION_46 = 1;
  
  public static final int VERSION_48 = 2;
  
  public static final int VERSION_49 = 3;

  public static final int VERSION_50 = 4;
  public static final int VERSION_51 = 5; // use safe maps
  public static final int VERSION_53 = 6;

  static final int VERSION_CURRENT = VERSION_53;

  // TODO: should this be a long ...?
  public int counter;
  
  public long version;

  private long generation;     // generation of the "segments_N" for the next commit
  private long lastGeneration; // generation of the "segments_N" file we last successfully read
                               // or wrote; this is normally the same as generation except if
                               // there was an IOException that had interrupted a commit

  public Map<String,String> userData = Collections.emptyMap();
  
  private List<SegmentCommitInfo> segments = new ArrayList<>();
  
  private static PrintStream infoStream = null;

  private byte[] id;

  private Version luceneVersion;

  private Version minSegmentLuceneVersion;


  public SegmentInfos() {
  }

  public SegmentCommitInfo info(int i) {
    return segments.get(i);
  }

  public static long getLastCommitGeneration(String[] files) {
    long max = -1;
    for (String file : files) {
      if (file.startsWith(IndexFileNames.SEGMENTS) && !file.equals(IndexFileNames.OLD_SEGMENTS_GEN)) {
        long gen = generationFromSegmentsFileName(file);
        if (gen > max) {
          max = gen;
        }
      }
    }
    return max;
  }

  public static long getLastCommitGeneration(Directory directory) throws IOException {
    return getLastCommitGeneration(directory.listAll());
  }

  public static String getLastCommitSegmentsFileName(String[] files) {
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
  
  private long getNextPendingGeneration() {
    if (generation == -1) {
      return 1;
    } else {
      return generation+1;
    }
  }

  public byte[] getId() {
    return id == null ? null : id.clone();
  }

  public static final SegmentInfos readCommit(Directory directory, String segmentFileName) throws IOException {

    long generation = generationFromSegmentsFileName(segmentFileName);
    try (ChecksumIndexInput input = directory.openChecksumInput(segmentFileName, IOContext.READ)) {
      // NOTE: as long as we want to throw indexformattooold (vs corruptindexexception), we need
      // to read the magic ourselves.
      int magic = input.readInt();
      if (magic != CodecUtil.CODEC_MAGIC) {
        throw new IndexFormatTooOldException(input, magic, CodecUtil.CODEC_MAGIC, CodecUtil.CODEC_MAGIC);
      }
      // 4.0+
      int format = CodecUtil.checkHeaderNoMagic(input, "segments", VERSION_40, VERSION_CURRENT);
      // 5.0+
      byte id[] = null;
      if (format >= VERSION_50) {
        id = new byte[StringHelper.ID_LENGTH];
        input.readBytes(id, 0, id.length);
        CodecUtil.checkIndexHeaderSuffix(input, Long.toString(generation, Character.MAX_RADIX));
      }
      
      SegmentInfos infos = new SegmentInfos();
      infos.id = id;
      infos.generation = generation;
      infos.lastGeneration = generation;
      if (format >= VERSION_53) {
        // TODO: in the future (7.0?  sigh) we can use this to throw IndexFormatTooOldException ... or just rely on the
        // minSegmentLuceneVersion check instead:
        infos.luceneVersion = Version.fromBits(input.readVInt(), input.readVInt(), input.readVInt());
      } else {
        // else compute the min version down below in the for loop
      }

      infos.version = input.readLong();
      infos.counter = input.readInt();
      int numSegments = input.readInt();
      if (numSegments < 0) {
        throw new CorruptIndexException("invalid segment count: " + numSegments, input);
      }

      if (format >= VERSION_53) {
        if (numSegments > 0) {
          infos.minSegmentLuceneVersion = Version.fromBits(input.readVInt(), input.readVInt(), input.readVInt());
          if (infos.minSegmentLuceneVersion.onOrAfter(Version.LUCENE_4_0_0_ALPHA) == false) {
            throw new IndexFormatTooOldException(input, "this index contains a too-old segment (version: " + infos.minSegmentLuceneVersion + ")");
          }
        } else {
          // else leave as null: no segments
        }
      } else {
        // else we recompute it below as we visit segments; it can't be used for throwing IndexFormatTooOldExc, but consumers of
        // SegmentInfos can maybe still use it for other reasons
      }

      long totalDocs = 0;
      for (int seg = 0; seg < numSegments; seg++) {
        String segName = input.readString();
        final byte segmentID[];
        if (format >= VERSION_50) {
          byte hasID = input.readByte();
          if (hasID == 1) {
            segmentID = new byte[StringHelper.ID_LENGTH];
            input.readBytes(segmentID, 0, segmentID.length);
          } else if (hasID == 0) {
            segmentID = null; // 4.x segment, doesn't have an ID
          } else {
            throw new CorruptIndexException("invalid hasID byte, got: " + hasID, input);
          }
        } else {
          segmentID = null;
        }
        Codec codec = readCodec(input, format < VERSION_53);
        SegmentInfo info = codec.segmentInfoFormat().read(directory, segName, segmentID, IOContext.READ);
        info.setCodec(codec);
        totalDocs += info.maxDoc();
        long delGen = input.readLong();
        int delCount = input.readInt();
        if (delCount < 0 || delCount > info.maxDoc()) {
          throw new CorruptIndexException("invalid deletion count: " + delCount + " vs maxDoc=" + info.maxDoc(), input);
        }
        long fieldInfosGen = -1;
        if (format >= VERSION_46) {
          fieldInfosGen = input.readLong();
        }
        long dvGen = -1;
        if (format >= VERSION_49) {
          dvGen = input.readLong();
        } else {
          dvGen = fieldInfosGen;
        }
        SegmentCommitInfo siPerCommit = new SegmentCommitInfo(info, delCount, delGen, fieldInfosGen, dvGen);
        if (format >= VERSION_46) {
          if (format < VERSION_49) {
            // Recorded per-generation files, which were buggy (see
            // LUCENE-5636). We need to read and keep them so we continue to
            // reference those files. Unfortunately it means that the files will
            // be referenced even if the fields are updated again, until the
            // segment is merged.
            final int numGensUpdatesFiles = input.readInt();
            final Map<Long,Set<String>> genUpdatesFiles;
            if (numGensUpdatesFiles == 0) {
              genUpdatesFiles = Collections.emptyMap();
            } else {
              genUpdatesFiles = new HashMap<>(numGensUpdatesFiles);
              for (int i = 0; i < numGensUpdatesFiles; i++) {
                genUpdatesFiles.put(input.readLong(), input.readStringSet());
              }
            }
            siPerCommit.setGenUpdatesFiles(genUpdatesFiles);
          } else {
            if (format >= VERSION_51) {
              siPerCommit.setFieldInfosFiles(input.readSetOfStrings());
            } else {
              siPerCommit.setFieldInfosFiles(Collections.unmodifiableSet(input.readStringSet()));
            }
            final Map<Integer,Set<String>> dvUpdateFiles;
            final int numDVFields = input.readInt();
            if (numDVFields == 0) {
              dvUpdateFiles = Collections.emptyMap();
            } else {
              Map<Integer,Set<String>> map = new HashMap<>(numDVFields);
              for (int i = 0; i < numDVFields; i++) {
                if (format >= VERSION_51) {
                  map.put(input.readInt(), input.readSetOfStrings());
                } else {
                  map.put(input.readInt(), Collections.unmodifiableSet(input.readStringSet()));
                }
              }
              dvUpdateFiles = Collections.unmodifiableMap(map);
            }
            siPerCommit.setDocValuesUpdatesFiles(dvUpdateFiles);
          }
        }
        infos.add(siPerCommit);

        Version segmentVersion = info.getVersion();
        if (format < VERSION_53) {
          if (infos.minSegmentLuceneVersion == null || segmentVersion.onOrAfter(infos.minSegmentLuceneVersion) == false) {
            infos.minSegmentLuceneVersion = segmentVersion;
          }
        } else if (segmentVersion.onOrAfter(infos.minSegmentLuceneVersion) == false) {
          throw new CorruptIndexException("segments file recorded minSegmentLuceneVersion=" + infos.minSegmentLuceneVersion + " but segment=" + info + " has older version=" + segmentVersion, input);
        }
      }

      if (format >= VERSION_51) {
        infos.userData = input.readMapOfStrings();
      } else {
        infos.userData = Collections.unmodifiableMap(input.readStringStringMap());
      }

      if (format >= VERSION_48) {
        CodecUtil.checkFooter(input);
      } else {
        final long checksumNow = input.getChecksum();
        final long checksumThen = input.readLong();
        if (checksumNow != checksumThen) {
          throw new CorruptIndexException("checksum failed (hardware problem?) : expected=" + Long.toHexString(checksumThen) +  
                                          " actual=" + Long.toHexString(checksumNow), input);
        }
        CodecUtil.checkEOF(input);
      }

      // LUCENE-6299: check we are in bounds
      if (totalDocs > IndexWriter.getActualMaxDocs()) {
        throw new CorruptIndexException("Too many documents: an index cannot exceed " + IndexWriter.getActualMaxDocs() + " but readers have total maxDoc=" + totalDocs, input);
      }
      
      return infos;
    }
  }

  private static final List<String> unsupportedCodecs = Arrays.asList(
      "Lucene3x"
  );

  private static Codec readCodec(DataInput input, boolean unsupportedAllowed) throws IOException {
    final String name = input.readString();
    try {
      return Codec.forName(name);
    } catch (IllegalArgumentException e) {
      // give better error messages if we can, first check if this is a legacy codec
      if (unsupportedCodecs.contains(name)) {
        // We should only get here on pre-5.3 indices, but we can't test this until 7.0 when 5.x indices become too old:
        assert unsupportedAllowed;
        IOException newExc = new IndexFormatTooOldException(input, "Codec '" + name + "' is too old");
        newExc.initCause(e);
        throw newExc;
      }
      // or maybe it's an old default codec that moved
      if (name.startsWith("Lucene")) {
        throw new IllegalArgumentException("Could not load codec '" + name + "'.  Did you forget to add lucene-backward-codecs.jar?", e);
      }
      throw e;
    }
  }

  public static final SegmentInfos readLatestCommit(Directory directory) throws IOException {
    return new FindSegmentsFile<SegmentInfos>(directory) {
      @Override
      protected SegmentInfos doBody(String segmentFileName) throws IOException {
        return readCommit(directory, segmentFileName);
      }
    }.run();
  }

  // Only true after prepareCommit has been called and
  // before finishCommit is called
  boolean pendingCommit;

  private void write(Directory directory) throws IOException {

    long nextGeneration = getNextPendingGeneration();
    String segmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS,
                                                                   "",
                                                                   nextGeneration);
    
    // Always advance the generation on write:
    generation = nextGeneration;
    
    IndexOutput segnOutput = null;
    boolean success = false;

    try {
      segnOutput = directory.createOutput(segmentFileName, IOContext.DEFAULT);
      CodecUtil.writeIndexHeader(segnOutput, "segments", VERSION_CURRENT, 
                                 StringHelper.randomId(), Long.toString(nextGeneration, Character.MAX_RADIX));
      segnOutput.writeVInt(Version.LATEST.major);
      segnOutput.writeVInt(Version.LATEST.minor);
      segnOutput.writeVInt(Version.LATEST.bugfix);

      segnOutput.writeLong(version); 
      segnOutput.writeInt(counter); // write counter
      segnOutput.writeInt(size());

      if (size() > 0) {

        Version minSegmentVersion = null;

        // We do a separate loop up front so we can write the minSegmentVersion before
        // any SegmentInfo; this makes it cleaner to throw IndexFormatTooOldExc at read time:
        for (SegmentCommitInfo siPerCommit : this) {
          Version segmentVersion = siPerCommit.info.getVersion();
          if (minSegmentVersion == null || segmentVersion.onOrAfter(minSegmentVersion) == false) {
            minSegmentVersion = segmentVersion;
          }
        }

        segnOutput.writeVInt(minSegmentVersion.major);
        segnOutput.writeVInt(minSegmentVersion.minor);
        segnOutput.writeVInt(minSegmentVersion.bugfix);
      }

      // write infos
      for (SegmentCommitInfo siPerCommit : this) {
        SegmentInfo si = siPerCommit.info;
        segnOutput.writeString(si.name);
        byte segmentID[] = si.getId();
        // TODO: remove this in lucene 6, we don't need to include 4.x segments in commits anymore
        if (segmentID == null) {
          segnOutput.writeByte((byte)0);
        } else {
          if (segmentID.length != StringHelper.ID_LENGTH) {
            throw new IllegalStateException("cannot write segment: invalid id segment=" + si.name + "id=" + StringHelper.idToString(segmentID));
          }
          segnOutput.writeByte((byte)1);
          segnOutput.writeBytes(segmentID, segmentID.length);
        }
        segnOutput.writeString(si.getCodec().getName());
        segnOutput.writeLong(siPerCommit.getDelGen());
        int delCount = siPerCommit.getDelCount();
        if (delCount < 0 || delCount > si.maxDoc()) {
          throw new IllegalStateException("cannot write segment: invalid maxDoc segment=" + si.name + " maxDoc=" + si.maxDoc() + " delCount=" + delCount);
        }
        segnOutput.writeInt(delCount);
        segnOutput.writeLong(siPerCommit.getFieldInfosGen());
        segnOutput.writeLong(siPerCommit.getDocValuesGen());
        segnOutput.writeSetOfStrings(siPerCommit.getFieldInfosFiles());
        final Map<Integer,Set<String>> dvUpdatesFiles = siPerCommit.getDocValuesUpdatesFiles();
        segnOutput.writeInt(dvUpdatesFiles.size());
        for (Entry<Integer,Set<String>> e : dvUpdatesFiles.entrySet()) {
          segnOutput.writeInt(e.getKey());
          segnOutput.writeSetOfStrings(e.getValue());
        }
      }
      segnOutput.writeMapOfStrings(userData);
      CodecUtil.writeFooter(segnOutput);
      segnOutput.close();
      directory.sync(Collections.singleton(segmentFileName));
      success = true;
    } finally {
      if (success) {
        pendingCommit = true;
      } else {
        // We hit an exception above; try to close the file
        // but suppress any exception:
        IOUtils.closeWhileHandlingException(segnOutput);
        // Try not to leave a truncated segments_N file in
        // the index:
        IOUtils.deleteFilesIgnoringExceptions(directory, segmentFileName);
      }
    }
  }

  @Override
  public SegmentInfos clone() {
    try {
      final SegmentInfos sis = (SegmentInfos) super.clone();
      // deep clone, first recreate all collections:
      sis.segments = new ArrayList<>(size());
      for(final SegmentCommitInfo info : this) {
        assert info.info.getCodec() != null;
        // dont directly access segments, use add method!!!
        sis.add(info.clone());
      }
      sis.userData = new HashMap<>(userData);
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


  public static void setInfoStream(PrintStream infoStream) {
    SegmentInfos.infoStream = infoStream;
  }

  public static PrintStream getInfoStream() {
    return infoStream;
  }

  private static void message(String message) {
    infoStream.println("SIS [" + Thread.currentThread().getName() + "]: " + message);
  }

  public abstract static class FindSegmentsFile<T> {

    final Directory directory;

    public FindSegmentsFile(Directory directory) {
      this.directory = directory;
    }

    public T run() throws IOException {
      return run(null);
    }
    
    public T run(IndexCommit commit) throws IOException {
      if (commit != null) {
        if (directory != commit.getDirectory())
          throw new IOException("the specified commit does not match the specified Directory");
        return doBody(commit.getSegmentsFileName());
      }

      long lastGen = -1;
      long gen = -1;
      IOException exc = null;

      // Loop until we succeed in calling doBody() without
      // hitting an IOException.  An IOException most likely
      // means an IW deleted our commit while opening
      // the time it took us to load the now-old infos files
      // (and segments files).  It's also possible it's a
      // true error (corrupt index).  To distinguish these,
      // on each retry we must see "forward progress" on
      // which generation we are trying to load.  If we
      // don't, then the original error is real and we throw
      // it.
      
      for (;;) {
        lastGen = gen;
        String files[] = directory.listAll();
        String files2[] = directory.listAll();
        Arrays.sort(files);
        Arrays.sort(files2);
        if (!Arrays.equals(files, files2)) {
          // listAll() is weakly consistent, this means we hit "concurrent modification exception"
          continue;
        }
        gen = getLastCommitGeneration(files);
        
        if (infoStream != null) {
          message("directory listing gen=" + gen);
        }
        
        if (gen == -1) {
          throw new IndexNotFoundException("no segments* file found in " + directory + ": files: " + Arrays.toString(files));
        } else if (gen > lastGen) {
          String segmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", gen);
        
          try {
            T t = doBody(segmentFileName);
            if (infoStream != null) {
              message("success on " + segmentFileName);
            }
            return t;
          } catch (IOException err) {
            // Save the original root cause:
            if (exc == null) {
              exc = err;
            }

            if (infoStream != null) {
              message("primary Exception on '" + segmentFileName + "': " + err + "'; will retry: gen = " + gen);
            }
          }
        } else {
          throw exc;
        }
      }
    }

    protected abstract T doBody(String segmentFileName) throws IOException;
  }

  // Carry over generation numbers from another SegmentInfos
  void updateGeneration(SegmentInfos other) {
    lastGeneration = other.lastGeneration;
    generation = other.generation;
  }

  // Carry over generation numbers, and version/counter, from another SegmentInfos
  void updateGenerationVersionAndCounter(SegmentInfos other) {
    updateGeneration(other);
    this.version = other.version;
    this.counter = other.counter;
  }

  void setNextWriteGeneration(long generation) {
    assert generation >= this.generation;
    this.generation = generation;
  }

  final void rollbackCommit(Directory dir) {
    if (pendingCommit) {
      pendingCommit = false;
      
      // we try to clean up our pending_segments_N

      // Must carefully compute fileName from "generation"
      // since lastGeneration isn't incremented:
      final String pending = IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS, "", generation);

      // Suppress so we keep throwing the original exception
      // in our caller
      IOUtils.deleteFilesIgnoringExceptions(dir, pending);
    }
  }


  final void prepareCommit(Directory dir) throws IOException {
    if (pendingCommit) {
      throw new IllegalStateException("prepareCommit was already called");
    }
    write(dir);
  }
  
  @Deprecated
  public final Collection<String> files(Directory dir, boolean includeSegmentsFile) throws IOException {
    return files(includeSegmentsFile);
  }


  public Collection<String> files(boolean includeSegmentsFile) throws IOException {
    HashSet<String> files = new HashSet<>();
    if (includeSegmentsFile) {
      final String segmentFileName = getSegmentsFileName();
      if (segmentFileName != null) {
        files.add(segmentFileName);
      }
    }
    final int size = size();
    for(int i=0;i<size;i++) {
      final SegmentCommitInfo info = info(i);
      files.addAll(info.files());
    }
    
    return files;
  }

  final String finishCommit(Directory dir) throws IOException {
    if (pendingCommit == false) {
      throw new IllegalStateException("prepareCommit was not called");
    }
    boolean success = false;
    final String dest;
    try {
      final String src =  IndexFileNames.fileNameFromGeneration(IndexFileNames.PENDING_SEGMENTS, "", generation);
      dest = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", generation);
      dir.renameFile(src, dest);
      success = true;
    } finally {
      if (!success) {
        // deletes pending_segments_N:
        rollbackCommit(dir);
      }
    }

    pendingCommit = false;
    lastGeneration = generation;
    return dest;
  }


  final void commit(Directory dir) throws IOException {
    prepareCommit(dir);
    finishCommit(dir);
  }
  

  @Deprecated
  public String toString(Directory dir) {
    return toString();
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append(getSegmentsFileName()).append(": ");
    final int count = size();
    for(int i = 0; i < count; i++) {
      if (i > 0) {
        buffer.append(' ');
      }
      final SegmentCommitInfo info = info(i);
      buffer.append(info.toString(0));
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

    changed();
  }


  void replace(SegmentInfos other) {
    rollbackSegmentInfos(other.asList());
    lastGeneration = other.lastGeneration;
  }

  public int totalMaxDoc() {
    long count = 0;
    for(SegmentCommitInfo info : this) {
      count += info.info.maxDoc();
    }
    // we should never hit this, checks should happen elsewhere...
    assert count <= IndexWriter.getActualMaxDocs();
    return (int) count;
  }

  public void changed() {
    version++;
  }
  
  void applyMergeChanges(MergePolicy.OneMerge merge, boolean dropSegment) {
    final Set<SegmentCommitInfo> mergedAway = new HashSet<>(merge.segments);
    boolean inserted = false;
    int newSegIdx = 0;
    for (int segIdx = 0, cnt = segments.size(); segIdx < cnt; segIdx++) {
      assert segIdx >= newSegIdx;
      final SegmentCommitInfo info = segments.get(segIdx);
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

    // the rest of the segments in list are duplicates, so don't remove from map, only list!
    segments.subList(newSegIdx, segments.size()).clear();
    
    // Either we found place to insert segment, or, we did
    // not, but only because all segments we merged becamee
    // deleted while we are merging, in which case it should
    // be the case that the new segment is also all deleted,
    // we insert it at the beginning if it should not be dropped:
    if (!inserted && !dropSegment) {
      segments.add(0, merge.info);
    }
  }

  List<SegmentCommitInfo> createBackupSegmentInfos() {
    final List<SegmentCommitInfo> list = new ArrayList<>(size());
    for(final SegmentCommitInfo info : this) {
      assert info.info.getCodec() != null;
      list.add(info.clone());
    }
    return list;
  }
  
  void rollbackSegmentInfos(List<SegmentCommitInfo> infos) {
    this.clear();
    this.addAll(infos);
  }
  
  // @Override (comment out until Java 6)
  @Override
  public Iterator<SegmentCommitInfo> iterator() {
    return asList().iterator();
  }
  
  public List<SegmentCommitInfo> asList() {
    return Collections.unmodifiableList(segments);
  }

  public int size() {
    return segments.size();
  }

  public void add(SegmentCommitInfo si) {
    segments.add(si);
  }
  
  public void addAll(Iterable<SegmentCommitInfo> sis) {
    for (final SegmentCommitInfo si : sis) {
      this.add(si);
    }
  }
  
  public void clear() {
    segments.clear();
  }


  public void remove(SegmentCommitInfo si) {
    segments.remove(si);
  }
  

  void remove(int index) {
    segments.remove(index);
  }


  boolean contains(SegmentCommitInfo si) {
    return segments.contains(si);
  }


  int indexOf(SegmentCommitInfo si) {
    return segments.indexOf(si);
  }

  public Version getCommitLuceneVersion() {
    return luceneVersion;
  }

  public Version getMinSegmentLuceneVersion() {
    return minSegmentLuceneVersion;
  }
}
