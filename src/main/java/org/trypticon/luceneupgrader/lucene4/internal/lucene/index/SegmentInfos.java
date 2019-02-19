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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xCodec;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xSegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene3x.Lucene3xSegmentInfoReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.ChecksumIndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexOutput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.NoSuchDirectoryException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Version;

public final class SegmentInfos implements Cloneable, Iterable<SegmentCommitInfo> {

  public static final int VERSION_40 = 0;

  public static final int VERSION_46 = 1;
  
  public static final int VERSION_48 = 2;
  
  public static final int VERSION_49 = 3;

  // Used for the segments.gen file only!
  // Whenever you add a new format, make it 1 smaller (negative version logic)!
  private static final int FORMAT_SEGMENTS_GEN_47 = -2;
  private static final int FORMAT_SEGMENTS_GEN_CHECKSUM = -3;
  private static final int FORMAT_SEGMENTS_GEN_START = FORMAT_SEGMENTS_GEN_47;
  public static final int FORMAT_SEGMENTS_GEN_CURRENT = FORMAT_SEGMENTS_GEN_CHECKSUM;

  // TODO: should this be a long ...?
  public int counter;
  
  public long version;

  private long generation;     // generation of the "segments_N" for the next commit
  private long lastGeneration; // generation of the "segments_N" file we last successfully read
                               // or wrote; this is normally the same as generation except if
                               // there was an IOException that had interrupted a commit

  public Map<String,String> userData = Collections.<String,String>emptyMap();
  
  private List<SegmentCommitInfo> segments = new ArrayList<>();
  
  private static PrintStream infoStream = null;


  public SegmentInfos() {
  }

  public SegmentCommitInfo info(int i) {
    return segments.get(i);
  }

  public static long getLastCommitGeneration(String[] files) {
    if (files == null) {
      return -1;
    }
    long max = -1;
    for (String file : files) {
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

  public static void writeSegmentsGen(Directory dir, long generation) {
    try {
      IndexOutput genOutput = dir.createOutput(IndexFileNames.SEGMENTS_GEN, IOContext.READONCE);
      try {
        genOutput.writeInt(FORMAT_SEGMENTS_GEN_CURRENT);
        genOutput.writeLong(generation);
        genOutput.writeLong(generation);
        CodecUtil.writeFooter(genOutput);
      } finally {
        genOutput.close();
        dir.sync(Collections.singleton(IndexFileNames.SEGMENTS_GEN));
      }
    } catch (Throwable t) {
      // It's OK if we fail to write this file since it's
      // used only as one of the retry fallbacks.
      IOUtils.deleteFilesIgnoringExceptions(dir, IndexFileNames.SEGMENTS_GEN);
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

  public final void read(Directory directory, String segmentFileName) throws IOException {
    boolean success = false;

    // Clear any previous segments:
    this.clear();

    generation = generationFromSegmentsFileName(segmentFileName);

    lastGeneration = generation;

    ChecksumIndexInput input = directory.openChecksumInput(segmentFileName, IOContext.READ);
    try {
      final int format = input.readInt();
      final int actualFormat;
      long totalDocs = 0;
      if (format == CodecUtil.CODEC_MAGIC) {
        // 4.0+
        actualFormat = CodecUtil.checkHeaderNoMagic(input, "segments", VERSION_40, VERSION_49);
        version = input.readLong();
        counter = input.readInt();
        int numSegments = input.readInt();
        if (numSegments < 0) {
          throw new CorruptIndexException("invalid segment count: " + numSegments + " (resource: " + input + ")");
        }
        for (int seg = 0; seg < numSegments; seg++) {
          String segName = input.readString();
          Codec codec = Codec.forName(input.readString());
          //System.out.println("SIS.read seg=" + seg + " codec=" + codec);
          SegmentInfo info = codec.segmentInfoFormat().getSegmentInfoReader().read(directory, segName, IOContext.READ);
          info.setCodec(codec);
          totalDocs += info.getDocCount();
          long delGen = input.readLong();
          int delCount = input.readInt();
          if (delCount < 0 || delCount > info.getDocCount()) {
            throw new CorruptIndexException("invalid deletion count: " + delCount + " vs docCount=" + info.getDocCount() + " (resource: " + input + ")");
          }
          long fieldInfosGen = -1;
          if (actualFormat >= VERSION_46) {
            fieldInfosGen = input.readLong();
          }
          long dvGen = -1;
          if (actualFormat >= VERSION_49) {
            dvGen = input.readLong();
          } else {
            dvGen = fieldInfosGen;
          }
          SegmentCommitInfo siPerCommit = new SegmentCommitInfo(info, delCount, delGen, fieldInfosGen, dvGen);
          if (actualFormat >= VERSION_46) {
            if (actualFormat < VERSION_49) {
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
              siPerCommit.setFieldInfosFiles(input.readStringSet());
              final Map<Integer,Set<String>> dvUpdateFiles;
              final int numDVFields = input.readInt();
              if (numDVFields == 0) {
                dvUpdateFiles = Collections.emptyMap();
              } else {
                dvUpdateFiles = new HashMap<>(numDVFields);
                for (int i = 0; i < numDVFields; i++) {
                  dvUpdateFiles.put(input.readInt(), input.readStringSet());
                }
              }
              siPerCommit.setDocValuesUpdatesFiles(dvUpdateFiles);
            }
          }
          add(siPerCommit);
        }
        userData = input.readStringStringMap();
      } else {
        actualFormat = -1;
        Lucene3xSegmentInfoReader.readLegacyInfos(this, directory, input, format);
        Codec codec = Codec.forName("Lucene3x");
        for (SegmentCommitInfo info : this) {
          info.info.setCodec(codec);
          totalDocs += info.info.getDocCount();
        }
      }

      if (actualFormat >= VERSION_48) {
        CodecUtil.checkFooter(input);
      } else {
        final long checksumNow = input.getChecksum();
        final long checksumThen = input.readLong();
        if (checksumNow != checksumThen) {
          throw new CorruptIndexException("checksum mismatch in segments file (resource: " + input + ")");
        }
        CodecUtil.checkEOF(input);
      }

      // LUCENE-6299: check we are in bounds
      if (totalDocs > IndexWriter.getActualMaxDocs()) {
        throw new CorruptIndexException("Too many documents: an index cannot exceed " + IndexWriter.getActualMaxDocs() + " but readers have total maxDoc=" + totalDocs + " (resource: " + input + ")");
      }

      success = true;
    } finally {
      if (!success) {
        // Clear any segment infos we had loaded so we
        // have a clean slate on retry:
        this.clear();
        IOUtils.closeWhileHandlingException(input);
      } else {
        input.close();
      }
    }
  }

  public final void read(Directory directory) throws IOException {
    generation = lastGeneration = -1;

    new FindSegmentsFile(directory) {

      @Override
      protected Object doBody(String segmentFileName) throws IOException {
        read(directory, segmentFileName);
        return null;
      }
    }.run();
  }

  // Only non-null after prepareCommit has been called and
  // before finishCommit is called
  IndexOutput pendingSegnOutput;

  private static final String SEGMENT_INFO_UPGRADE_CODEC = "SegmentInfo3xUpgrade";
  private static final int SEGMENT_INFO_UPGRADE_VERSION = 0;

  private void write(Directory directory) throws IOException {

    String segmentsFileName = getNextSegmentFileName();
    
    // Always advance the generation on write:
    if (generation == -1) {
      generation = 1;
    } else {
      generation++;
    }
    
    IndexOutput segnOutput = null;
    boolean success = false;

    final Set<String> upgradedSIFiles = new HashSet<>();

    try {
      segnOutput = directory.createOutput(segmentsFileName, IOContext.DEFAULT);
      CodecUtil.writeHeader(segnOutput, "segments", VERSION_49);
      segnOutput.writeLong(version); 
      segnOutput.writeInt(counter); // write counter
      segnOutput.writeInt(size()); // write infos
      for (SegmentCommitInfo siPerCommit : this) {
        SegmentInfo si = siPerCommit.info;
        segnOutput.writeString(si.name);
        segnOutput.writeString(si.getCodec().getName());
        segnOutput.writeLong(siPerCommit.getDelGen());
        int delCount = siPerCommit.getDelCount();
        if (delCount < 0 || delCount > si.getDocCount()) {
          throw new IllegalStateException("cannot write segment: invalid docCount segment=" + si.name + " docCount=" + si.getDocCount() + " delCount=" + delCount);
        }
        segnOutput.writeInt(delCount);
        segnOutput.writeLong(siPerCommit.getFieldInfosGen());
        segnOutput.writeLong(siPerCommit.getDocValuesGen());
        segnOutput.writeStringSet(siPerCommit.getFieldInfosFiles());
        final Map<Integer,Set<String>> dvUpdatesFiles = siPerCommit.getDocValuesUpdatesFiles();
        segnOutput.writeInt(dvUpdatesFiles.size());
        for (Entry<Integer,Set<String>> e : dvUpdatesFiles.entrySet()) {
          segnOutput.writeInt(e.getKey());
          segnOutput.writeStringSet(e.getValue());
        }
        assert si.dir == directory;

        // If this segment is pre-4.x, perform a one-time
        // "upgrade" to write the .si file for it:
        Version version = si.getVersion();
        if (version == null || version.onOrAfter(Version.LUCENE_4_0_0_ALPHA) == false) {

          // Defensive check: we are about to write this SI in 3.x format, dropping all codec information, etc.
          // so it had better be a 3.x segment or you will get very confusing errors later.
          if ((si.getCodec() instanceof Lucene3xCodec) == false) {
            throw new IllegalStateException("cannot write 3x SegmentInfo unless codec is Lucene3x (got: " + si.getCodec() + ")");
          }

          if (!segmentWasUpgraded(directory, si)) {

            String markerFileName = IndexFileNames.segmentFileName(si.name, "upgraded", Lucene3xSegmentInfoFormat.UPGRADED_SI_EXTENSION);
            si.addFile(markerFileName);

            final String segmentFileName = write3xInfo(directory, si, IOContext.DEFAULT);
            upgradedSIFiles.add(segmentFileName);
            directory.sync(Collections.singletonList(segmentFileName));

            // Write separate marker file indicating upgrade
            // is completed.  This way, if there is a JVM
            // kill/crash, OS crash, power loss, etc. while
            // writing the upgraded file, the marker file
            // will be missing:
            IndexOutput out = directory.createOutput(markerFileName, IOContext.DEFAULT);
            try {
              CodecUtil.writeHeader(out, SEGMENT_INFO_UPGRADE_CODEC, SEGMENT_INFO_UPGRADE_VERSION);
            } finally {
              out.close();
            }
            upgradedSIFiles.add(markerFileName);
            directory.sync(Collections.singletonList(markerFileName));
          }
        }
      }
      segnOutput.writeStringStringMap(userData);
      pendingSegnOutput = segnOutput;
      success = true;
    } finally {
      if (!success) {
        // We hit an exception above; try to close the file
        // but suppress any exception:
        IOUtils.closeWhileHandlingException(segnOutput);

        for (String fileName : upgradedSIFiles) {
          IOUtils.deleteFilesIgnoringExceptions(directory, fileName);
        }

        // Try not to leave a truncated segments_N file in
        // the index:
        IOUtils.deleteFilesIgnoringExceptions(directory, segmentsFileName);
      }
    }
  }

  private static boolean segmentWasUpgraded(Directory directory, SegmentInfo si) {
    // Check marker file:
    String markerFileName = IndexFileNames.segmentFileName(si.name, "upgraded", Lucene3xSegmentInfoFormat.UPGRADED_SI_EXTENSION);

    // LUCENE-6279: don't rely solely on existence of the marker file; also require that we see the marker
    // file in our si.files(), which means we did previously at least attempt to write it:
    if (si.files().contains(markerFileName) == false) {
      return false;
    }

    // Also verify the marker file exists and has the proper header:
    IndexInput in = null;
    try {
      in = directory.openInput(markerFileName, IOContext.READONCE);
      if (CodecUtil.checkHeader(in, SEGMENT_INFO_UPGRADE_CODEC, SEGMENT_INFO_UPGRADE_VERSION, SEGMENT_INFO_UPGRADE_VERSION) == 0) {
        return true;
      }
    } catch (IOException ioe) {
      // Ignore: if something is wrong w/ the marker file,
      // we will just upgrade again
    } finally {
      if (in != null) {
        IOUtils.closeWhileHandlingException(in);
      }
    }

    return false;
  }

  @Deprecated
  public static String write3xInfo(Directory dir, SegmentInfo si, IOContext context) throws IOException {

    // Defensive check: we are about to write this SI in 3.x format, dropping all codec information, etc.
    // so it had better be a 3.x segment or you will get very confusing errors later.
    if ((si.getCodec() instanceof Lucene3xCodec) == false) {
      throw new IllegalStateException("cannot write 3x SegmentInfo unless codec is Lucene3x (got: " + si.getCodec() + ")");
    }

    // NOTE: this is NOT how 3.x is really written...
    String fileName = IndexFileNames.segmentFileName(si.name, "", Lucene3xSegmentInfoFormat.UPGRADED_SI_EXTENSION);
    si.addFile(fileName);

    //System.out.println("UPGRADE write " + fileName);
    boolean success = false;
    IndexOutput output = dir.createOutput(fileName, context);
    try {
      CodecUtil.writeHeader(output, Lucene3xSegmentInfoFormat.UPGRADED_SI_CODEC_NAME, 
                                    Lucene3xSegmentInfoFormat.UPGRADED_SI_VERSION_CURRENT);
      // Write the Lucene version that created this segment, since 3.1
      output.writeString(si.getVersion().toString());
      output.writeInt(si.getDocCount());

      output.writeStringStringMap(si.attributes());

      output.writeByte((byte) (si.getUseCompoundFile() ? SegmentInfo.YES : SegmentInfo.NO));
      output.writeStringStringMap(si.getDiagnostics());
      output.writeStringSet(si.files());

      output.close();

      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(output);
        try {
          si.dir.deleteFile(fileName);
        } catch (Throwable t) {
          // Suppress so we keep throwing the original exception
        }
      }
    }

    return fileName;
  }

  @Override
  public SegmentInfos clone() {
    return clone(false);
  }

  SegmentInfos clone(boolean cloneSegmentInfo) {
    try {
      final SegmentInfos sis = (SegmentInfos) super.clone();
      // deep clone, first recreate all collections:
      sis.segments = new ArrayList<>(size());
      for(final SegmentCommitInfo info : this) {
        assert info.info.getCodec() != null;
        // dont directly access segments, use add method!!!
        sis.add(info.clone(cloneSegmentInfo));
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

    public Object run() throws IOException {
      return run(null);
    }
    
    public Object run(IndexCommit commit) throws IOException {
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
          ChecksumIndexInput genInput = null;
          try {
            genInput = directory.openChecksumInput(IndexFileNames.SEGMENTS_GEN, IOContext.READONCE);
          } catch (IOException e) {
            if (infoStream != null) {
              message("segments.gen open: IOException " + e);
            }
          }
  
          if (genInput != null) {
            try {
              int version = genInput.readInt();
              if (version == FORMAT_SEGMENTS_GEN_47 || version == FORMAT_SEGMENTS_GEN_CHECKSUM) {
                long gen0 = genInput.readLong();
                long gen1 = genInput.readLong();
                if (infoStream != null) {
                  message("fallback check: " + gen0 + "; " + gen1);
                }
                if (version == FORMAT_SEGMENTS_GEN_CHECKSUM) {
                  CodecUtil.checkFooter(genInput);
                } else {
                  CodecUtil.checkEOF(genInput);
                }
                if (gen0 == gen1) {
                  // The file is consistent.
                  genB = gen0;
                }
              } else {
                throw new IndexFormatTooNewException(genInput, version, FORMAT_SEGMENTS_GEN_START, FORMAT_SEGMENTS_GEN_CURRENT);
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
          gen = Math.max(genA, genB);

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

          // TODO: we should use the new IO apis in Java7 to get better exceptions on why the open failed.  E.g. we don't want to fall back
          // if the open failed for a "different" reason (too many open files, access denied) than "the commit was in progress"

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

            boolean prevExists;

            try {
              directory.openInput(prevSegmentFileName, IOContext.DEFAULT).close();
              prevExists = true;
            } catch (IOException ioe) {
              prevExists = false;
            }

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

    protected abstract Object doBody(String segmentFileName) throws IOException;
  }

  // Carry over generation numbers from another SegmentInfos
  void updateGeneration(SegmentInfos other) {
    lastGeneration = other.lastGeneration;
    generation = other.generation;
  }

  void setGeneration(long generation) {
    this.generation = generation;
    this.lastGeneration = generation;
  }

  final void rollbackCommit(Directory dir) {
    if (pendingSegnOutput != null) {
      // Suppress so we keep throwing the original exception
      // in our caller
      IOUtils.closeWhileHandlingException(pendingSegnOutput);
      pendingSegnOutput = null;

      // Must carefully compute fileName from "generation"
      // since lastGeneration isn't incremented:
      final String segmentFileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS,
                                                                            "",
                                                                           generation);
      // Suppress so we keep throwing the original exception
      // in our caller
      IOUtils.deleteFilesIgnoringExceptions(dir, segmentFileName);
    }
  }


  final void prepareCommit(Directory dir) throws IOException {
    if (pendingSegnOutput != null) {
      throw new IllegalStateException("prepareCommit was already called");
    }
    write(dir);
  }


  public Collection<String> files(Directory dir, boolean includeSegmentsFile) throws IOException {
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
      assert info.info.dir == dir;
      if (info.info.dir == dir) {
        files.addAll(info.files());
      }
    }
    
    return files;
  }

  final String finishCommit(Directory dir) throws IOException {
    if (pendingSegnOutput == null) {
      throw new IllegalStateException("prepareCommit was not called");
    }
    boolean success = false;
    final String dest;
    try {
      CodecUtil.writeFooter(pendingSegnOutput);
      success = true;
    } finally {
      if (!success) {
        // Closes pendingSegnOutput & deletes partial segments_N:
        rollbackCommit(dir);
      } else {
        success = false;
        try {
          pendingSegnOutput.close();
          success = true;
        } finally {
          if (!success) {
            // Closes pendingSegnOutput & deletes partial segments_N:
            rollbackCommit(dir);
          } else {
            pendingSegnOutput = null;
          }
        }
      }
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

    final String fileName = IndexFileNames.fileNameFromGeneration(IndexFileNames.SEGMENTS, "", generation);
    success = false;
    try {
      dir.sync(Collections.singleton(fileName));
      success = true;
    } finally {
      if (!success) {
        IOUtils.deleteFilesIgnoringExceptions(dir, fileName);
      }
    }

    lastGeneration = generation;
    writeSegmentsGen(dir, generation);

    return fileName;
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
      final SegmentCommitInfo info = info(i);
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
    long count = 0;
    for(SegmentCommitInfo info : this) {
      count += info.info.getDocCount();
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
}
