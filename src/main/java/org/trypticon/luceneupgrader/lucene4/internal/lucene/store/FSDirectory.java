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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import static java.util.Collections.synchronizedSet;

public abstract class FSDirectory extends BaseDirectory {

  @Deprecated
  public static final int DEFAULT_READ_CHUNK_SIZE = 8192;

  protected final File directory; // The underlying filesystem directory
  protected final Set<String> staleFiles = synchronizedSet(new HashSet<String>()); // Files written, but not yet sync'ed
  private int chunkSize = DEFAULT_READ_CHUNK_SIZE;


  protected FSDirectory(File path, LockFactory lockFactory) throws IOException {
    // new ctors use always NativeFSLockFactory as default:
    if (lockFactory == null) {
      lockFactory = new NativeFSLockFactory();
    }
    directory = path.getCanonicalFile();

    if (directory.exists() && !directory.isDirectory())
      throw new NoSuchDirectoryException("file '" + directory + "' exists but is not a directory");

    setLockFactory(lockFactory);

  }


  public static FSDirectory open(File path) throws IOException {
    return open(path, null);
  }

  public static FSDirectory open(File path, LockFactory lockFactory) throws IOException {
    if (Constants.JRE_IS_64BIT && MMapDirectory.UNMAP_SUPPORTED) {
      return new MMapDirectory(path, lockFactory);
    } else if (Constants.WINDOWS) {
      return new SimpleFSDirectory(path, lockFactory);
    } else {
      return new NIOFSDirectory(path, lockFactory);
    }
  }

  @Override
  public void setLockFactory(LockFactory lockFactory) throws IOException {
    super.setLockFactory(lockFactory);

    // for filesystem based LockFactory, delete the lockPrefix, if the locks are placed
    // in index dir. If no index dir is given, set ourselves
    if (lockFactory instanceof FSLockFactory) {
      final FSLockFactory lf = (FSLockFactory) lockFactory;
      final File dir = lf.getLockDir();
      // if the lock factory has no lockDir set, use the this directory as lockDir
      if (dir == null) {
        lf.setLockDir(directory);
        lf.setLockPrefix(null);
      } else if (dir.getCanonicalPath().equals(directory.getCanonicalPath())) {
        lf.setLockPrefix(null);
      }
    }

  }
  

  public static String[] listAll(File dir) throws IOException {
    if (!dir.exists())
      throw new NoSuchDirectoryException("directory '" + dir + "' does not exist");
    else if (!dir.isDirectory())
      throw new NoSuchDirectoryException("file '" + dir + "' exists but is not a directory");

    // Exclude subdirs
    String[] result = dir.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String file) {
          return !new File(dir, file).isDirectory();
        }
      });

    if (result == null)
      throw new IOException("directory '" + dir + "' exists and is a directory, but cannot be listed: list() returned null");

    return result;
  }


  @Override
  public String[] listAll() throws IOException {
    ensureOpen();
    return listAll(directory);
  }

  @Override
  public boolean fileExists(String name) {
    ensureOpen();
    File file = new File(directory, name);
    return file.exists();
  }

  @Override
  public long fileLength(String name) throws IOException {
    ensureOpen();
    File file = new File(directory, name);
    final long len = file.length();
    if (len == 0 && !file.exists()) {
      throw new FileNotFoundException(name);
    } else {
      return len;
    }
  }

  @Override
  public void deleteFile(String name) throws IOException {
    ensureOpen();
    File file = new File(directory, name);
    if (!file.delete())
      throw new IOException("Cannot delete " + file);
    staleFiles.remove(name);
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    ensureOpen();

    ensureCanWrite(name);
    return new FSIndexOutput(name);
  }

  protected void ensureCanWrite(String name) throws IOException {
    if (!directory.exists())
      if (!directory.mkdirs())
        throw new IOException("Cannot create directory: " + directory);

    File file = new File(directory, name);
    if (file.exists() && !file.delete())          // delete existing, if any
      throw new IOException("Cannot overwrite: " + file);
  }

  protected void onIndexOutputClosed(String name) {
    staleFiles.add(name);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
    ensureOpen();
    Set<String> toSync = new HashSet<>(names);
    toSync.retainAll(staleFiles);

    for (String name : toSync) {
      fsync(name);
    }
    
    // fsync the directory itsself, but only if there was any file fsynced before
    // (otherwise it can happen that the directory does not yet exist)!
    if (!toSync.isEmpty()) {
      IOUtils.fsync(directory, true);
    }
    
    staleFiles.removeAll(toSync);
  }

  @Override
  public String getLockID() {
    ensureOpen();
    String dirName;                               // name to be hashed
    try {
      dirName = directory.getCanonicalPath();
    } catch (IOException e) {
      throw new RuntimeException(e.toString(), e);
    }

    int digest = 0;
    for(int charIDX=0;charIDX<dirName.length();charIDX++) {
      final char ch = dirName.charAt(charIDX);
      digest = 31 * digest + ch;
    }
    return "lucene-" + Integer.toHexString(digest);
  }

  @Override
  public synchronized void close() {
    isOpen = false;
  }

  public File getDirectory() {
    ensureOpen();
    return directory;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + directory + " lockFactory=" + getLockFactory();
  }

  @Deprecated
  public final void setReadChunkSize(int chunkSize) {
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("chunkSize must be positive");
    }
    this.chunkSize = chunkSize;
  }

  @Deprecated
  public final int getReadChunkSize() {
    return chunkSize;
  }

  final class FSIndexOutput extends OutputStreamIndexOutput {
    static final int CHUNK_SIZE = 8192;
    
    private final String name;

    public FSIndexOutput(String name) throws IOException {
      super(new FilterOutputStream(new FileOutputStream(new File(directory, name))) {
        // This implementation ensures, that we never write more than CHUNK_SIZE bytes:
        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
          while (length > 0) {
            final int chunk = Math.min(length, CHUNK_SIZE);
            out.write(b, offset, chunk);
            length -= chunk;
            offset += chunk;
          }
        }
      }, CHUNK_SIZE);
      this.name = name;
    }
    
    @Override
    public void close() throws IOException {
      try {
        onIndexOutputClosed(name);
      } finally {
        super.close();
      }
    }
  }

  protected void fsync(String name) throws IOException {
    IOUtils.fsync(new File(directory, name), false);
  }
}
