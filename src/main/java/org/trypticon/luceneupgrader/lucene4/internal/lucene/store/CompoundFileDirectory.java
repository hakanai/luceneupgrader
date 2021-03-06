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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.store;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.Codec; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.LiveDocsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class CompoundFileDirectory extends BaseDirectory {
  
  public static final class FileEntry {
    long offset;
    long length;
  }
  
  private final Directory directory;
  private final String fileName;
  protected final int readBufferSize;  
  private final Map<String,FileEntry> entries;
  private final boolean openForWrite;
  private static final Map<String,FileEntry> SENTINEL = Collections.emptyMap();
  private final CompoundFileWriter writer;
  private final IndexInput handle;
  private int version;
  
  public CompoundFileDirectory(Directory directory, String fileName, IOContext context, boolean openForWrite) throws IOException {
    this.directory = directory;
    this.fileName = fileName;
    this.readBufferSize = BufferedIndexInput.bufferSize(context);
    this.isOpen = false;
    this.openForWrite = openForWrite;
    if (!openForWrite) {
      boolean success = false;
      handle = directory.openInput(fileName, context);
      try {
        this.entries = readEntries(handle, directory, fileName);
        if (version >= CompoundFileWriter.VERSION_CHECKSUM) {
          CodecUtil.checkHeader(handle, CompoundFileWriter.DATA_CODEC, version, version);
          // NOTE: data file is too costly to verify checksum against all the bytes on open,
          // but for now we at least verify proper structure of the checksum footer: which looks
          // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
          // such as file truncation.
          CodecUtil.retrieveChecksum(handle);
        }
        success = true;
      } finally {
        if (!success) {
          IOUtils.closeWhileHandlingException(handle);
        }
      }
      this.isOpen = true;
      writer = null;
    } else {
      assert !(directory instanceof CompoundFileDirectory) : "compound file inside of compound file: " + fileName;
      this.entries = SENTINEL;
      this.isOpen = true;
      writer = new CompoundFileWriter(directory, fileName);
      handle = null;
    }
  }

  private static final byte CODEC_MAGIC_BYTE1 = (byte) (CodecUtil.CODEC_MAGIC >>> 24);
  private static final byte CODEC_MAGIC_BYTE2 = (byte) (CodecUtil.CODEC_MAGIC >>> 16);
  private static final byte CODEC_MAGIC_BYTE3 = (byte) (CodecUtil.CODEC_MAGIC >>> 8);
  private static final byte CODEC_MAGIC_BYTE4 = (byte) CodecUtil.CODEC_MAGIC;

  private final Map<String, FileEntry> readEntries(
      IndexInput handle, Directory dir, String name) throws IOException {
    IndexInput stream = null; 
    ChecksumIndexInput entriesStream = null;
    Map<String,FileEntry> mapping = null;
    // read the first VInt. If it is negative, it's the version number
    // otherwise it's the count (pre-3.1 indexes)
    boolean success = false;
    try {
      stream = handle.clone();
      final int firstInt = stream.readVInt();
      // impossible for 3.0 to have 63 files in a .cfs, CFS writer was not visible
      // and separate norms/etc are outside of cfs.
      if (firstInt == CODEC_MAGIC_BYTE1) {
        byte secondByte = stream.readByte();
        byte thirdByte = stream.readByte();
        byte fourthByte = stream.readByte();
        if (secondByte != CODEC_MAGIC_BYTE2 || 
            thirdByte != CODEC_MAGIC_BYTE3 || 
            fourthByte != CODEC_MAGIC_BYTE4) {
          throw new CorruptIndexException("Illegal/impossible header for CFS file: " 
                                         + secondByte + "," + thirdByte + "," + fourthByte);
        }
        version = CodecUtil.checkHeaderNoMagic(stream, CompoundFileWriter.DATA_CODEC, 
            CompoundFileWriter.VERSION_START, CompoundFileWriter.VERSION_CURRENT);
        final String entriesFileName = IndexFileNames.segmentFileName(
                                              IndexFileNames.stripExtension(name), "",
                                              IndexFileNames.COMPOUND_FILE_ENTRIES_EXTENSION);
        entriesStream = dir.openChecksumInput(entriesFileName, IOContext.READONCE);
        CodecUtil.checkHeader(entriesStream, CompoundFileWriter.ENTRY_CODEC, CompoundFileWriter.VERSION_START, CompoundFileWriter.VERSION_CURRENT);
        final int numEntries = entriesStream.readVInt();
        mapping = new HashMap<>(numEntries);
        for (int i = 0; i < numEntries; i++) {
          final FileEntry fileEntry = new FileEntry();
          final String id = entriesStream.readString();
          FileEntry previous = mapping.put(id, fileEntry);
          if (previous != null) {
            throw new CorruptIndexException("Duplicate cfs entry id=" + id + " in CFS: " + entriesStream);
          }
          fileEntry.offset = entriesStream.readLong();
          fileEntry.length = entriesStream.readLong();
        }
        if (version >= CompoundFileWriter.VERSION_CHECKSUM) {
          CodecUtil.checkFooter(entriesStream);
        } else {
          CodecUtil.checkEOF(entriesStream);
        }
      } else {
        // TODO remove once 3.x is not supported anymore
        mapping = readLegacyEntries(stream, firstInt);
        version = -1; // version before versioning was added
      }
      success = true;
    } finally {
      if (success) {
        IOUtils.close(stream, entriesStream);
      } else {
        IOUtils.closeWhileHandlingException(stream, entriesStream);
      }
    }
    return mapping;
  }

  private static Map<String, FileEntry> readLegacyEntries(IndexInput stream,
      int firstInt) throws CorruptIndexException, IOException {
    final Map<String,FileEntry> entries = new HashMap<>();
    final int count;
    final boolean stripSegmentName;
    if (firstInt < CompoundFileWriter.FORMAT_PRE_VERSION) {
      if (firstInt < CompoundFileWriter.FORMAT_NO_SEGMENT_PREFIX) {
        throw new CorruptIndexException("Incompatible format version: "
            + firstInt + " expected >= " + CompoundFileWriter.FORMAT_NO_SEGMENT_PREFIX + " (resource: " + stream + ")");
      }
      // It's a post-3.1 index, read the count.
      count = stream.readVInt();
      stripSegmentName = false;
    } else {
      count = firstInt;
      stripSegmentName = true;
    }
    
    // read the directory and init files
    long streamLength = stream.length();
    FileEntry entry = null;
    for (int i=0; i<count; i++) {
      long offset = stream.readLong();
      if (offset < 0 || offset > streamLength) {
        throw new CorruptIndexException("Invalid CFS entry offset: " + offset + " (resource: " + stream + ")");
      }
      String id = stream.readString();
      
      if (stripSegmentName) {
        // Fix the id to not include the segment names. This is relevant for
        // pre-3.1 indexes.
        id = IndexFileNames.stripSegmentName(id);
      }
      
      if (entry != null) {
        // set length of the previous entry
        entry.length = offset - entry.offset;
      }
      
      entry = new FileEntry();
      entry.offset = offset;

      FileEntry previous = entries.put(id, entry);
      if (previous != null) {
        throw new CorruptIndexException("Duplicate cfs entry id=" + id + " in CFS: " + stream);
      }
    }
    
    // set the length of the final entry
    if (entry != null) {
      entry.length = streamLength - entry.offset;
    }
    
    return entries;
  }
  
  public Directory getDirectory() {
    return directory;
  }
  
  public String getName() {
    return fileName;
  }
  
  @Override
  public synchronized void close() throws IOException {
    if (!isOpen) {
      // allow double close - usually to be consistent with other closeables
      return; // already closed
     }
    isOpen = false;
    if (writer != null) {
      assert openForWrite;
      writer.close();
    } else {
      IOUtils.close(handle);
    }
  }
  
  @Override
  public synchronized IndexInput openInput(String name, IOContext context) throws IOException {
    ensureOpen();
    assert !openForWrite;
    final String id = IndexFileNames.stripSegmentName(name);
    final FileEntry entry = entries.get(id);
    if (entry == null) {
      throw new FileNotFoundException("No sub-file with id " + id + " found (fileName=" + name + " files: " + entries.keySet() + ")");
    }
    return handle.slice(name, entry.offset, entry.length);
  }
  
  @Override
  public String[] listAll() {
    ensureOpen();
    String[] res;
    if (writer != null) {
      res = writer.listAll(); 
    } else {
      res = entries.keySet().toArray(new String[entries.size()]);
      // Add the segment name
      String seg = IndexFileNames.parseSegmentName(fileName);
      for (int i = 0; i < res.length; i++) {
        res[i] = seg + res[i];
      }
    }
    return res;
  }
  
  @Override
  public boolean fileExists(String name) {
    ensureOpen();
    if (this.writer != null) {
      return writer.fileExists(name);
    }
    return entries.containsKey(IndexFileNames.stripSegmentName(name));
  }
  
  @Override
  public void deleteFile(String name) {
    throw new UnsupportedOperationException();
  }
  
  public void renameFile(String from, String to) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public long fileLength(String name) throws IOException {
    ensureOpen();
    if (this.writer != null) {
      return writer.fileLength(name);
    }
    FileEntry e = entries.get(IndexFileNames.stripSegmentName(name));
    if (e == null)
      throw new FileNotFoundException(name);
    return e.length;
  }
  
  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    ensureOpen();
    return writer.createOutput(name, context);
  }
  
  @Override
  public void sync(Collection<String> names) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Lock makeLock(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "CompoundFileDirectory(file=\"" + fileName + "\" in dir=" + directory + ")";
  }
}
