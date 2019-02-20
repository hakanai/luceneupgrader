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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException; // javadoc @link
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.IOUtils;

public abstract class FSDirectory extends BaseDirectory {

  protected final Path directory; // The underlying filesystem directory

  private final Set<String> pendingDeletes = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());

  private final AtomicInteger opsSinceLastDelete = new AtomicInteger();

  private final AtomicLong nextTempFileCounter = new AtomicLong();


  protected FSDirectory(Path path, LockFactory lockFactory) throws IOException {
    super(lockFactory);
    // If only read access is permitted, createDirectories fails even if the directory already exists.
    if (!Files.isDirectory(path)) {
      Files.createDirectories(path);  // create directory, if it doesn't exist
    }
    directory = path.toRealPath();
  }


  public static FSDirectory open(Path path) throws IOException {
    return open(path, FSLockFactory.getDefault());
  }

  public static FSDirectory open(Path path, LockFactory lockFactory) throws IOException {
    if (Constants.JRE_IS_64BIT && MMapDirectory.UNMAP_SUPPORTED) {
      return new MMapDirectory(path, lockFactory);
    } else if (Constants.WINDOWS) {
      return new SimpleFSDirectory(path, lockFactory);
    } else {
      return new NIOFSDirectory(path, lockFactory);
    }
  }


  public static String[] listAll(Path dir) throws IOException {
    return listAll(dir, null);
  }

  private static String[] listAll(Path dir, Set<String> skipNames) throws IOException {
    List<String> entries = new ArrayList<>();
    
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path path : stream) {
        String name = path.getFileName().toString();
        if (skipNames == null || skipNames.contains(name) == false) {
          entries.add(name);
        }
      }
    }
    
    String[] array = entries.toArray(new String[entries.size()]);
    // Directory.listAll javadocs state that we sort the results here, so we don't let filesystem
    // specifics leak out of this abstraction:
    Arrays.sort(array);
    return array;
  }

  @Override
  public String[] listAll() throws IOException {
    ensureOpen();
    return listAll(directory, pendingDeletes);
  }

  @Override
  public long fileLength(String name) throws IOException {
    ensureOpen();
    if (pendingDeletes.contains(name)) {
      throw new NoSuchFileException("file \"" + name + "\" is pending delete");
    }
    return Files.size(directory.resolve(name));
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    ensureOpen();

    // If this file was pending delete, we are now bringing it back to life:
    pendingDeletes.remove(name);
    maybeDeletePendingFiles();
    return new FSIndexOutput(name);
  }

  @Override
  public IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException {
    ensureOpen();
    maybeDeletePendingFiles();
    while (true) {
      try {
        String name = IndexFileNames.segmentFileName(prefix, suffix + "_" + Long.toString(nextTempFileCounter.getAndIncrement(), Character.MAX_RADIX), "tmp");
        if (pendingDeletes.contains(name)) {
          continue;
        }
        return new FSIndexOutput(name,
                                 StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
      } catch (FileAlreadyExistsException faee) {
        // Retry with next incremented name
      }
    }
  }

  protected void ensureCanRead(String name) throws IOException {
    if (pendingDeletes.contains(name)) {
      throw new NoSuchFileException("file \"" + name + "\" is pending delete and cannot be opened for read");
    }
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
    ensureOpen();

    for (String name : names) {
      fsync(name);
    }
    maybeDeletePendingFiles();
  }

  @Override
  public void rename(String source, String dest) throws IOException {
    ensureOpen();
    if (pendingDeletes.contains(source)) {
      throw new NoSuchFileException("file \"" + source + "\" is pending delete and cannot be moved");
    }
    pendingDeletes.remove(dest);
    Files.move(directory.resolve(source), directory.resolve(dest), StandardCopyOption.ATOMIC_MOVE);
    maybeDeletePendingFiles();
  }

  @Override
  public void syncMetaData() throws IOException {
    // TODO: to improve listCommits(), IndexFileDeleter could call this after deleting segments_Ns
    ensureOpen();
    IOUtils.fsync(directory, true);
    maybeDeletePendingFiles();
  }

  @Override
  public synchronized void close() throws IOException {
    isOpen = false;
    deletePendingFiles();
  }

  public Path getDirectory() {
    ensureOpen();
    return directory;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + directory + " lockFactory=" + lockFactory;
  }

  protected void fsync(String name) throws IOException {
    IOUtils.fsync(directory.resolve(name), false);
  }

  @Override
  public void deleteFile(String name) throws IOException {  
    if (pendingDeletes.contains(name)) {
      throw new NoSuchFileException("file \"" + name + "\" is already pending delete");
    }
    privateDeleteFile(name, false);
    maybeDeletePendingFiles();
  }

  public boolean checkPendingDeletions() throws IOException {
    deletePendingFiles();
    return pendingDeletes.isEmpty() == false;
  }

  public synchronized void deletePendingFiles() throws IOException {
    if (pendingDeletes.isEmpty() == false) {

      // TODO: we could fix IndexInputs from FSDirectory subclasses to call this when they are closed?

      // Clone the set since we mutate it in privateDeleteFile:
      for(String name : new HashSet<>(pendingDeletes)) {
        privateDeleteFile(name, true);
      }
    }
  }

  private void maybeDeletePendingFiles() throws IOException {
    if (pendingDeletes.isEmpty() == false) {
      // This is a silly heuristic to try to avoid O(N^2), where N = number of files pending deletion, behaviour on Windows:
      int count = opsSinceLastDelete.incrementAndGet();
      if (count >= pendingDeletes.size()) {
        opsSinceLastDelete.addAndGet(-count);
        deletePendingFiles();
      }
    }
  }

  private void privateDeleteFile(String name, boolean isPendingDelete) throws IOException {
    try {
      Files.delete(directory.resolve(name));
      pendingDeletes.remove(name);
    } catch (NoSuchFileException | FileNotFoundException e) {
      // We were asked to delete a non-existent file:
      pendingDeletes.remove(name);
      if (isPendingDelete && Constants.WINDOWS) {
        // TODO: can we remove this OS-specific hacky logic?  If windows deleteFile is buggy, we should instead contain this workaround in
        // a WindowsFSDirectory ...
        // LUCENE-6684: we suppress this check for Windows, since a file could be in a confusing "pending delete" state, failing the first
        // delete attempt with access denied and then apparently falsely failing here when we try ot delete it again, with NSFE/FNFE
      } else {
        throw e;
      }
    } catch (IOException ioe) {
      // On windows, a file delete can fail because there's still an open
      // file handle against it.  We record this in pendingDeletes and
      // try again later.

      // TODO: this is hacky/lenient (we don't know which IOException this is), and
      // it should only happen on filesystems that can do this, so really we should
      // move this logic to WindowsDirectory or something

      // TODO: can/should we do if (Constants.WINDOWS) here, else throw the exc?
      // but what about a Linux box with a CIFS mount?
      pendingDeletes.add(name);
    }
  }

  final class FSIndexOutput extends OutputStreamIndexOutput {
    static final int CHUNK_SIZE = 8192;
    
    public FSIndexOutput(String name) throws IOException {
      this(name, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
    }

    FSIndexOutput(String name, OpenOption... options) throws IOException {
      super("FSIndexOutput(path=\"" + directory.resolve(name) + "\")", name, new FilterOutputStream(Files.newOutputStream(directory.resolve(name), options)) {
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
    }
  }
}
