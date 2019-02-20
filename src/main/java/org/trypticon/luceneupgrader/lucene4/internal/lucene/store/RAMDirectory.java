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

import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Accountable;


public class RAMDirectory extends BaseDirectory implements Accountable {
  protected final Map<String,RAMFile> fileMap = new ConcurrentHashMap<>();
  protected final AtomicLong sizeInBytes = new AtomicLong();
  
  // *****
  // Lock acquisition sequence:  RAMDirectory, then RAMFile
  // ***** 

  public RAMDirectory() {
    try {
      setLockFactory(new SingleInstanceLockFactory());
    } catch (IOException e) {
      // Cannot happen
    }
  }

  public RAMDirectory(Directory dir, IOContext context) throws IOException {
    this(dir, false, context);
  }
  
  private RAMDirectory(Directory dir, boolean closeDir, IOContext context) throws IOException {
    this();
    for (String file : dir.listAll()) {
      dir.copy(this, file, file, context);
    }
    if (closeDir) {
      dir.close();
    }
  }

  @Override
  public String getLockID() {
    return "lucene-" + Integer.toHexString(hashCode());
  }
  
  @Override
  public final String[] listAll() {
    ensureOpen();
    // NOTE: fileMap.keySet().toArray(new String[0]) is broken in non Sun JDKs,
    // and the code below is resilient to map changes during the array population.
    Set<String> fileNames = fileMap.keySet();
    List<String> names = new ArrayList<>(fileNames.size());
    for (String name : fileNames) names.add(name);
    return names.toArray(new String[names.size()]);
  }

  @Override
  public final boolean fileExists(String name) {
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
    RAMFile existing = fileMap.remove(name);
    if (existing != null) {
      sizeInBytes.addAndGet(-existing.sizeInBytes);
      existing.directory = null;
    }
    fileMap.put(name, file);
    return new RAMOutputStream(file, true);
  }

  protected RAMFile newRAMFile() {
    return new RAMFile(this);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
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
