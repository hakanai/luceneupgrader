package org.apache.lucene.store;

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

import org.apache.lucene.util.ThreadInterruptedException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A memory-resident {@code Directory} implementation.  Locking
 * implementation is by default the {@code SingleInstanceLockFactory}
 * but can be changed with {@code #setLockFactory}.
 * 
 * <p><b>Warning:</b> This class is not intended to work with huge
 * indexes. Everything beyond several hundred megabytes will waste
 * resources (GC cycles), because it uses an internal buffer size
 * of 1024 bytes, producing millions of {@code byte[1024]} arrays.
 * This class is optimized for small memory-resident indexes.
 * It also has bad concurrency on multithreaded environments.
 * 
 * <p>It is recommended to materialize large indexes on disk and use
 * {@code MMapDirectory}, which is a high-performance directory
 * implementation working directly on the file system cache of the
 * operating system, so copying data to Java heap space is not useful.
 */
public class RAMDirectory extends Directory implements Serializable {

  private static final long serialVersionUID = 1l;

  protected final Map<String,RAMFile> fileMap = new ConcurrentHashMap<String,RAMFile>();
  protected final AtomicLong sizeInBytes = new AtomicLong();
  
  // *****
  // Lock acquisition sequence:  RAMDirectory, then RAMFile
  // *****

  /** Constructs an empty {@code Directory}. */
  public RAMDirectory() {
    try {
      setLockFactory(new SingleInstanceLockFactory());
    } catch (IOException e) {
      // Cannot happen
    }
  }

  @Override
  public final String[] listAll() {
    ensureOpen();
    // NOTE: fileMap.keySet().toArray(new String[0]) is broken in non Sun JDKs,
    // and the code below is resilient to map changes during the array population.
    Set<String> fileNames = fileMap.keySet();
    List<String> names = new ArrayList<String>(fileNames.size());
    for (String name : fileNames) names.add(name);
    return names.toArray(new String[names.size()]);
  }

  /** Returns true iff the named file exists in this directory. */
  @Override
  public final boolean fileExists(String name) {
    ensureOpen();
    return fileMap.containsKey(name);
  }

  /** Set the modified time of an existing file to now.
   * @throws IOException if the file does not exist
   *  @deprecated Lucene never uses this API; it will be
   *  removed in 4.0. */
  @Override
  @Deprecated
  public void touchFile(String name) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(name);
    if (file == null) {
      throw new FileNotFoundException(name);
    }

    long ts2, ts1 = System.currentTimeMillis();
    do {
      try {
        Thread.sleep(0, 1);
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
      ts2 = System.currentTimeMillis();
    } while(ts1 == ts2);

    file.setLastModified(ts2);
  }

  /** Returns the length in bytes of a file in the directory.
   * @throws IOException if the file does not exist
   */
  @Override
  public final long fileLength(String name) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(name);
    if (file == null) {
      throw new FileNotFoundException(name);
    }
    return file.getLength();
  }
  
  /**
   * Return total size in bytes of all files in this directory. This is
   * currently quantized to RAMOutputStream.BUFFER_SIZE.
   */
  public final long sizeInBytes() {
    ensureOpen();
    return sizeInBytes.get();
  }
  
  /** Removes an existing file in the directory.
   * @throws IOException if the file does not exist
   */
  @Override
  public void deleteFile(String name) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.remove(name);
    if (file != null) {
      file.directory = null;
      sizeInBytes.addAndGet(-file.sizeInBytes);
    } else {
      throw new FileNotFoundException(name);
    }
  }

  /** Creates a new, empty file in the directory with the given name. Returns a stream writing this file. */
  @Override
  public IndexOutput createOutput(String name) throws IOException {
    ensureOpen();
    RAMFile file = newRAMFile();
    RAMFile existing = fileMap.remove(name);
    if (existing != null) {
      sizeInBytes.addAndGet(-existing.sizeInBytes);
      existing.directory = null;
    }
    fileMap.put(name, file);
    return new RAMOutputStream(file);
  }

  /**
   * Returns a new {@code RAMFile} for storing data. This method can be
   * overridden to return different {@code RAMFile} impls, that e.g. override
   * {@code RAMFile#newBuffer(int)}.
   */
  protected RAMFile newRAMFile() {
    return new RAMFile(this);
  }

  /** Returns a stream reading an existing file. */
  @Override
  public IndexInput openInput(String name) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(name);
    if (file == null) {
      throw new FileNotFoundException(name);
    }
    return new RAMInputStream(name, file);
  }

  /** Closes the store to future operations, releasing associated memory. */
  @Override
  public void close() {
    isOpen = false;
    fileMap.clear();
  }
}
