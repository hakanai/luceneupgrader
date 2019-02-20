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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class OfflineSorter {
  public final static long MB = 1024 * 1024;
  public final static long GB = MB * 1024;
  
  public final static long MIN_BUFFER_SIZE_MB = 32;

  public static final long ABSOLUTE_MIN_SORT_BUFFER_SIZE = MB / 2;
  private static final String MIN_BUFFER_SIZE_MSG = "At least 0.5MB RAM buffer is needed";

  public final static int MAX_TEMPFILES = 128;


  public static final class BufferSize {
    final int bytes;
  
    private BufferSize(long bytes) {
      if (bytes > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Buffer too large for Java ("
            + (Integer.MAX_VALUE / MB) + "mb max): " + bytes);
      }
      
      if (bytes < ABSOLUTE_MIN_SORT_BUFFER_SIZE) {
        throw new IllegalArgumentException(MIN_BUFFER_SIZE_MSG + ": " + bytes);
      }
  
      this.bytes = (int) bytes;
    }
    
    public static BufferSize megabytes(long mb) {
      return new BufferSize(mb * MB);
    }
  
    public static BufferSize automatic() {
      Runtime rt = Runtime.getRuntime();
      
      // take sizes in "conservative" order
      final long max = rt.maxMemory(); // max allocated
      final long total = rt.totalMemory(); // currently allocated
      final long free = rt.freeMemory(); // unused portion of currently allocated
      final long totalAvailableBytes = max - total + free;
      
      // by free mem (attempting to not grow the heap for this)
      long sortBufferByteSize = free/2;
      final long minBufferSizeBytes = MIN_BUFFER_SIZE_MB*MB;
      if (sortBufferByteSize <  minBufferSizeBytes
          || totalAvailableBytes > 10 * minBufferSizeBytes) { // lets see if we need/should to grow the heap 
        if (totalAvailableBytes/2 > minBufferSizeBytes){ // there is enough mem for a reasonable buffer
          sortBufferByteSize = totalAvailableBytes/2; // grow the heap
        } else {
          //heap seems smallish lets be conservative fall back to the free/2 
          sortBufferByteSize = Math.max(ABSOLUTE_MIN_SORT_BUFFER_SIZE, sortBufferByteSize);
        }
      }
      return new BufferSize(Math.min((long)Integer.MAX_VALUE, sortBufferByteSize));
    }
  }
  
  public class SortInfo {
    public int tempMergeFiles;
    public int mergeRounds;
    public int lines;
    public long mergeTime;
    public long sortTime;
    public long totalTime;
    public long readTime;
    public final long bufferSize = ramBufferSize.bytes;
    
    public SortInfo() {}
    
    @Override
    public String toString() {
      return String.format(Locale.ROOT,
          "time=%.2f sec. total (%.2f reading, %.2f sorting, %.2f merging), lines=%d, temp files=%d, merges=%d, soft ram limit=%.2f MB",
          totalTime / 1000.0d, readTime / 1000.0d, sortTime / 1000.0d, mergeTime / 1000.0d,
          lines, tempMergeFiles, mergeRounds,
          (double) bufferSize / MB);
    }
  }

  private final BufferSize ramBufferSize;
  private final File tempDirectory;
  
  private final Counter bufferBytesUsed = Counter.newCounter();
  private final BytesRefArray buffer = new BytesRefArray(bufferBytesUsed);
  private SortInfo sortInfo;
  private int maxTempFiles;
  private final Comparator<BytesRef> comparator;
  
  public static final Comparator<BytesRef> DEFAULT_COMPARATOR = BytesRef.getUTF8SortedAsUnicodeComparator();

  public OfflineSorter() throws IOException {
    this(DEFAULT_COMPARATOR, BufferSize.automatic(), defaultTempDir(), MAX_TEMPFILES);
  }
  
  public OfflineSorter(Comparator<BytesRef> comparator) throws IOException {
    this(comparator, BufferSize.automatic(), defaultTempDir(), MAX_TEMPFILES);
  }

  public OfflineSorter(Comparator<BytesRef> comparator, BufferSize ramBufferSize, File tempDirectory, int maxTempfiles) {
    if (ramBufferSize.bytes < ABSOLUTE_MIN_SORT_BUFFER_SIZE) {
      throw new IllegalArgumentException(MIN_BUFFER_SIZE_MSG + ": " + ramBufferSize.bytes);
    }
    
    if (maxTempfiles < 2) {
      throw new IllegalArgumentException("maxTempFiles must be >= 2");
    }

    this.ramBufferSize = ramBufferSize;
    this.tempDirectory = tempDirectory;
    this.maxTempFiles = maxTempfiles;
    this.comparator = comparator;
  }


  public SortInfo sort(File input, File output) throws IOException {
    sortInfo = new SortInfo();
    sortInfo.totalTime = System.currentTimeMillis();

    output.delete();

    ArrayList<File> merges = new ArrayList<>();
    boolean success2 = false;
    try {
      ByteSequencesReader is = new ByteSequencesReader(input);
      boolean success = false;
      try {
        int lines = 0;
        while ((lines = readPartition(is)) > 0) {
          merges.add(sortPartition(lines));
          sortInfo.tempMergeFiles++;
          sortInfo.lines += lines;

          // Handle intermediate merges.
          if (merges.size() == maxTempFiles) {
            File intermediate = File.createTempFile("sort", "intermediate", tempDirectory);
            try {
              mergePartitions(merges, intermediate);
            } finally {
              for (File file : merges) {
                file.delete();
              }
              merges.clear();
              merges.add(intermediate);
            }
            sortInfo.tempMergeFiles++;
          }
        }
        success = true;
      } finally {
        if (success)
          IOUtils.close(is);
        else
          IOUtils.closeWhileHandlingException(is);
      }

      // One partition, try to rename or copy if unsuccessful.
      if (merges.size() == 1) {     
        File single = merges.get(0);
        // If simple rename doesn't work this means the output is
        // on a different volume or something. Copy the input then.
        if (!single.renameTo(output)) {
          copy(single, output);
        }
      } else { 
        // otherwise merge the partitions with a priority queue.
        mergePartitions(merges, output);
      }
      success2 = true;
    } finally {
      for (File file : merges) {
        file.delete();
      }
      if (!success2) {
        output.delete();
      }
    }

    sortInfo.totalTime = (System.currentTimeMillis() - sortInfo.totalTime); 
    return sortInfo;
  }

  public static File defaultTempDir() throws IOException {
    String tempDirPath = System.getProperty("java.io.tmpdir");
    if (tempDirPath == null) 
      throw new IOException("Java has no temporary folder property (java.io.tmpdir)?");

    File tempDirectory = new File(tempDirPath);
    if (!tempDirectory.exists() || !tempDirectory.canWrite()) {
      throw new IOException("Java's temporary folder not present or writeable?: " 
          + tempDirectory.getAbsolutePath());
    }
    return tempDirectory;
  }

  private static void copy(File file, File output) throws IOException {
    // 64kb copy buffer (empirical pick).
    byte [] buffer = new byte [16 * 1024];
    InputStream is = null;
    OutputStream os = null;
    try {
      is = new FileInputStream(file);
      os = new FileOutputStream(output);
      int length;
      while ((length = is.read(buffer)) > 0) {
        os.write(buffer, 0, length);
      }
    } finally {
      IOUtils.close(is, os);
    }
  }

  protected File sortPartition(int len) throws IOException {
    BytesRefArray data = this.buffer;
    File tempFile = File.createTempFile("sort", "partition", tempDirectory);

    long start = System.currentTimeMillis();
    sortInfo.sortTime += (System.currentTimeMillis() - start);
    
    final ByteSequencesWriter out = new ByteSequencesWriter(tempFile);
    BytesRef spare;
    try {
      BytesRefIterator iter = buffer.iterator(comparator);
      while((spare = iter.next()) != null) {
        assert spare.length <= Short.MAX_VALUE;
        out.write(spare);
      }
      
      out.close();

      // Clean up the buffer for the next partition.
      data.clear();
      return tempFile;
    } finally {
      IOUtils.close(out);
    }
  }

  void mergePartitions(List<File> merges, File outputFile) throws IOException {
    long start = System.currentTimeMillis();

    ByteSequencesWriter out = new ByteSequencesWriter(outputFile);

    PriorityQueue<FileAndTop> queue = new PriorityQueue<FileAndTop>(merges.size()) {
      @Override
      protected boolean lessThan(FileAndTop a, FileAndTop b) {
        return comparator.compare(a.current.get(), b.current.get()) < 0;
      }
    };

    ByteSequencesReader [] streams = new ByteSequencesReader [merges.size()];
    try {
      // Open streams and read the top for each file
      for (int i = 0; i < merges.size(); i++) {
        streams[i] = new ByteSequencesReader(merges.get(i));
        byte line[] = streams[i].read();
        if (line != null) {
          queue.insertWithOverflow(new FileAndTop(i, line));
        }
      }
  
      // Unix utility sort() uses ordered array of files to pick the next line from, updating
      // it as it reads new lines. The PQ used here is a more elegant solution and has 
      // a nicer theoretical complexity bound :) The entire sorting process is I/O bound anyway
      // so it shouldn't make much of a difference (didn't check).
      FileAndTop top;
      while ((top = queue.top()) != null) {
        out.write(top.current.bytes(), 0, top.current.length());
        if (!streams[top.fd].read(top.current)) {
          queue.pop();
        } else {
          queue.updateTop();
        }
      }
  
      sortInfo.mergeTime += System.currentTimeMillis() - start;
      sortInfo.mergeRounds++;
    } finally {
      // The logic below is: if an exception occurs in closing out, it has a priority over exceptions
      // happening in closing streams.
      try {
        IOUtils.close(streams);
      } finally {
        IOUtils.close(out);
      }
    }
  }

  int readPartition(ByteSequencesReader reader) throws IOException {
    long start = System.currentTimeMillis();
    final BytesRef scratch = new BytesRef();
    while ((scratch.bytes = reader.read()) != null) {
      scratch.length = scratch.bytes.length; 
      buffer.append(scratch);
      // Account for the created objects.
      // (buffer slots do not account to buffer size.) 
      if (ramBufferSize.bytes < bufferBytesUsed.get()) {
        break;
      }
    }
    sortInfo.readTime += (System.currentTimeMillis() - start);
    return buffer.size();
  }

  static class FileAndTop {
    final int fd;
    final BytesRefBuilder current;

    FileAndTop(int fd, byte[] firstLine) {
      this.fd = fd;
      this.current = new BytesRefBuilder();
      this.current.copyBytes(firstLine, 0, firstLine.length);
    }
  }

  public static class ByteSequencesWriter implements Closeable {
    private final DataOutput os;

    public ByteSequencesWriter(File file) throws IOException {
      this(new DataOutputStream(
          new BufferedOutputStream(
              new FileOutputStream(file))));
    }

    public ByteSequencesWriter(DataOutput os) {
      this.os = os;
    }

    public void write(BytesRef ref) throws IOException {
      assert ref != null;
      write(ref.bytes, ref.offset, ref.length);
    }

    public void write(byte [] bytes) throws IOException {
      write(bytes, 0, bytes.length);
    }

    public void write(byte [] bytes, int off, int len) throws IOException {
      assert bytes != null;
      assert off >= 0 && off + len <= bytes.length;
      assert len >= 0;
      if (len > Short.MAX_VALUE) {
        throw new IllegalArgumentException("len must be <= " + Short.MAX_VALUE + "; got " + len);
      }
      os.writeShort(len);
      os.write(bytes, off, len);
    }
    
    @Override
    public void close() throws IOException {
      if (os instanceof Closeable) {
        ((Closeable) os).close();
      }
    }    
  }

  public static class ByteSequencesReader implements Closeable {
    private final DataInput is;

    public ByteSequencesReader(File file) throws IOException {
      this(new DataInputStream(
          new BufferedInputStream(
              new FileInputStream(file))));
    }

    public ByteSequencesReader(DataInput is) {
      this.is = is;
    }

    public boolean read(BytesRefBuilder ref) throws IOException {
      short length;
      try {
        length = is.readShort();
      } catch (EOFException e) {
        return false;
      }

      ref.grow(length);
      ref.setLength(length);
      is.readFully(ref.bytes(), 0, length);
      return true;
    }

    public byte[] read() throws IOException {
      short length;
      try {
        length = is.readShort();
      } catch (EOFException e) {
        return null;
      }

      assert length >= 0 : "Sanity: sequence length < 0: " + length;
      byte [] result = new byte [length];
      is.readFully(result);
      return result;
    }

    @Override
    public void close() throws IOException {
      if (is instanceof Closeable) {
        ((Closeable) is).close();
      }
    }
  }

  public Comparator<BytesRef> getComparator() {
    return comparator;
  }  
}
