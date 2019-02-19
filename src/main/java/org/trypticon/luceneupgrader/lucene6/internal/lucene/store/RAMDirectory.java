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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.store;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountables;

public class RAMDirectory extends BaseDirectory implements Accountable {
  protected final Map<String,RAMFile> fileMap = new ConcurrentHashMap<>();
  protected final AtomicLong sizeInBytes = new AtomicLong();
  
  private final AtomicLong nextTempFileCounter = new AtomicLong();

  public RAMDirectory() {
    this(new SingleInstanceLockFactory());
  }

  public RAMDirectory(LockFactory lockFactory) {
    super(lockFactory);
  }

  public RAMDirectory(FSDirectory dir, IOContext context) throws IOException {
    this(dir, false, context);
  }
  
  private RAMDirectory(FSDirectory dir, boolean closeDir, IOContext context) throws IOException {
    this();
    for (String file : dir.listAll()) {
      if (!Files.isDirectory(dir.getDirectory().resolve(file))) {
        copyFrom(dir, file, file, context);
      }
    }
    if (closeDir) {
      dir.close();
    }
  }

  @Override
  public final String[] listAll() {
    ensureOpen();
    // NOTE: this returns a "weakly consistent view". Unless we change Dir API, keep this,
    // and do not synchronize or anything stronger. it's great for testing!
    // NOTE: fileMap.keySet().toArray(new String[0]) is broken in non Sun JDKs,
    // and the code below is resilient to map changes during the array population.
    // NOTE: don't replace this with return names.toArray(new String[names.size()]);
    // or some files could be null at the end of the array if files are being deleted
    // concurrently
    Set<String> fileNames = fileMap.keySet();
    List<String> names = new ArrayList<>(fileNames.size());
    for (String name : fileNames) {
      names.add(name);
    }
    String[] namesArray = names.toArray(new String[names.size()]);
    Arrays.sort(namesArray);
    return namesArray;
  }

  public final boolean fileNameExists(String name) {
    ensureOpen();
    return fileMap.containsKey(name);
  }


  @Override
  public final long fileLength(String name) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(name);
    if (file == null) {
      throw new FileNotFoundException(name);
    }
    return file.getLength();
  }
  
  @Override
  public final long ramBytesUsed() {
    ensureOpen();
    return sizeInBytes.get();
  }
  
  @Override
  public Collection<Accountable> getChildResources() {
    return Accountables.namedAccountables("file", fileMap);
  }
  
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

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    ensureOpen();
    RAMFile file = newRAMFile();
    if (fileMap.putIfAbsent(name, file) != null) {
      throw new FileAlreadyExistsException(name);
    }
    return new RAMOutputStream(name, file, true);
  }

  @Override
  public IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException {
    ensureOpen();

    // Make the file first...
    RAMFile file = newRAMFile();

    // ... then try to find a unique name for it:
    while (true) {
      String name = IndexFileNames.segmentFileName(prefix, suffix + "_" + Long.toString(nextTempFileCounter.getAndIncrement(), Character.MAX_RADIX), "tmp");
      if (fileMap.putIfAbsent(name, file) == null) {
        return new RAMOutputStream(name, file, true);
      }
    }
  }

  protected RAMFile newRAMFile() {
    return new RAMFile(this);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
  }

  @Override
  public void rename(String source, String dest) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(source);
    if (file == null) {
      throw new FileNotFoundException(source);
    }
    if (fileMap.putIfAbsent(dest, file) != null) {
      throw new FileAlreadyExistsException(dest);
    }
    if (!fileMap.remove(source, file)) {
      throw new IllegalStateException("file was unexpectedly replaced: " + source);
    }
    fileMap.remove(source);
  }

  @Override
  public void syncMetaData() throws IOException {
    // we are by definition not durable!
  }
  
  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    ensureOpen();
    RAMFile file = fileMap.get(name);
    if (file == null) {
      throw new FileNotFoundException(name);
    }
    return new RAMInputStream(name, file);
  }

  @Override
  public void close() {
    isOpen = false;
    fileMap.clear();
  }
}
