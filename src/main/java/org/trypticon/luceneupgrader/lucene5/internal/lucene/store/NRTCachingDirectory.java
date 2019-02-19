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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.store;


import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.RAMDirectory;      // javadocs
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountables;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;

// TODO
//   - let subclass dictate policy...?
//   - rename to MergeCacheingDir?  NRTCachingDir

public class NRTCachingDirectory extends FilterDirectory implements Accountable {

  private final RAMDirectory cache = new RAMDirectory();


  private final long maxMergeSizeBytes;
  private final long maxCachedBytes;

  private static final boolean VERBOSE = false;


  public NRTCachingDirectory(Directory delegate, double maxMergeSizeMB, double maxCachedMB) {
    super(delegate);
    maxMergeSizeBytes = (long) (maxMergeSizeMB*1024*1024);
    maxCachedBytes = (long) (maxCachedMB*1024*1024);
  }


  @Override
  public String toString() {
    return "NRTCachingDirectory(" + in + "; maxCacheMB=" + (maxCachedBytes/1024/1024.) + " maxMergeSizeMB=" + (maxMergeSizeBytes/1024/1024.) + ")";
  }

  @Override
  public synchronized String[] listAll() throws IOException {
    final Set<String> files = new HashSet<>();
    for(String f : cache.listAll()) {
      files.add(f);
    }
    for(String f : in.listAll()) {
      files.add(f);
    }
    return files.toArray(new String[files.size()]);
  }

  @Override
  public synchronized void deleteFile(String name) throws IOException {
    if (VERBOSE) {
      System.out.println("nrtdir.deleteFile name=" + name);
    }
    if (cache.fileNameExists(name)) {
      cache.deleteFile(name);
    } else {
      in.deleteFile(name);
    }
  }

  @Override
  public synchronized long fileLength(String name) throws IOException {
    if (cache.fileNameExists(name)) {
      return cache.fileLength(name);
    } else {
      return in.fileLength(name);
    }
  }

  public String[] listCachedFiles() {
    return cache.listAll();
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    if (VERBOSE) {
      System.out.println("nrtdir.createOutput name=" + name);
    }
    if (doCacheWrite(name, context)) {
      if (VERBOSE) {
        System.out.println("  to cache");
      }
      try {
        in.deleteFile(name);
      } catch (IOException ioe) {
        // This is fine: file may not exist
      }
      return cache.createOutput(name, context);
    } else {
      try {
        cache.deleteFile(name);
      } catch (IOException ioe) {
        // This is fine: file may not exist
      }
      return in.createOutput(name, context);
    }
  }

  @Override
  public void sync(Collection<String> fileNames) throws IOException {
    if (VERBOSE) {
      System.out.println("nrtdir.sync files=" + fileNames);
    }
    for(String fileName : fileNames) {
      unCache(fileName);
    }
    in.sync(fileNames);
  }

  @Override
  public void renameFile(String source, String dest) throws IOException {
    // NOTE: uncache is unnecessary for lucene's usage, as we always sync() before renaming.
    unCache(source);
    in.renameFile(source, dest);
  }


  @Override
  public synchronized IndexInput openInput(String name, IOContext context) throws IOException {
    if (VERBOSE) {
      System.out.println("nrtdir.openInput name=" + name);
    }
    if (cache.fileNameExists(name)) {
      if (VERBOSE) {
        System.out.println("  from cache");
      }
      return cache.openInput(name, context);
    } else {
      return in.openInput(name, context);
    }
  }
  
  @Override
  public void close() throws IOException {
    // NOTE: technically we shouldn't have to do this, ie,
    // IndexWriter should have sync'd all files, but we do
    // it for defensive reasons... or in case the app is
    // doing something custom (creating outputs directly w/o
    // using IndexWriter):
    boolean success = false;
    try {
      if (cache.isOpen) {
        for(String fileName : cache.listAll()) {
          unCache(fileName);
        }
      }
      success = true;
    } finally {
      if (success) {
        IOUtils.close(cache, in);
      } else {
        IOUtils.closeWhileHandlingException(cache, in);
      }
    }
  }

  protected boolean doCacheWrite(String name, IOContext context) {
    //System.out.println(Thread.currentThread().getName() + ": CACHE check merge=" + merge + " size=" + (merge==null ? 0 : merge.estimatedMergeBytes));

    long bytes = 0;
    if (context.mergeInfo != null) {
      bytes = context.mergeInfo.estimatedMergeBytes;
    } else if (context.flushInfo != null) {
      bytes = context.flushInfo.estimatedSegmentSize;
    }

    return (bytes <= maxMergeSizeBytes) && (bytes + cache.ramBytesUsed()) <= maxCachedBytes;
  }

  private final Object uncacheLock = new Object();

  private void unCache(String fileName) throws IOException {
    // Only let one thread uncache at a time; this only
    // happens during commit() or close():
    synchronized(uncacheLock) {
      if (VERBOSE) {
        System.out.println("nrtdir.unCache name=" + fileName);
      }
      if (!cache.fileNameExists(fileName)) {
        // Another thread beat us...
        return;
      }
      final IOContext context = IOContext.DEFAULT;
      final IndexOutput out = in.createOutput(fileName, context);
      IndexInput in = null;
      try {
        in = cache.openInput(fileName, context);
        out.copyBytes(in, in.length());
      } finally {
        IOUtils.close(in, out);
      }

      // Lock order: uncacheLock -> this
      synchronized(this) {
        // Must sync here because other sync methods have
        // if (cache.fileNameExists(name)) { ... } else { ... }:
        cache.deleteFile(fileName);
      }
    }
  }

  @Override
  public long ramBytesUsed() {
    return cache.ramBytesUsed();
  }
  
  @Override
  public Collection<Accountable> getChildResources() {
    return Collections.singleton(Accountables.namedAccountable("cache", cache));
  }
}
