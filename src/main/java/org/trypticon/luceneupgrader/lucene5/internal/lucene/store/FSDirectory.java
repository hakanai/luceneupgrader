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


import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException; // javadoc @link
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;

public abstract class FSDirectory extends BaseDirectory {

  protected final Path directory; // The underlying filesystem directory


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
    List<String> entries = new ArrayList<>();
    
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path path : stream) {
        entries.add(path.getFileName().toString());
      }
    }
    
    return entries.toArray(new String[entries.size()]);
  }

  @Override
  public String[] listAll() throws IOException {
    ensureOpen();
    return listAll(directory);
  }

  @Override
  public long fileLength(String name) throws IOException {
    ensureOpen();
    return Files.size(directory.resolve(name));
  }

  @Override
  public void deleteFile(String name) throws IOException {
    ensureOpen();
    Files.delete(directory.resolve(name));
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    ensureOpen();
    return new FSIndexOutput(name);
  }

  @Override
  public void sync(Collection<String> names) throws IOException {
    ensureOpen();

    for (String name : names) {
      fsync(name);
    }
  }
  
  @Override
  public void renameFile(String source, String dest) throws IOException {
    ensureOpen();
    Files.move(directory.resolve(source), directory.resolve(dest), StandardCopyOption.ATOMIC_MOVE);
    // TODO: should we move directory fsync to a separate 'syncMetadata' method?
    // for example, to improve listCommits(), IndexFileDeleter could also call that after deleting segments_Ns
    IOUtils.fsync(directory, true);
  }

  @Override
  public synchronized void close() {
    isOpen = false;
  }

  public Path getDirectory() {
    ensureOpen();
    return directory;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + directory + " lockFactory=" + lockFactory;
  }

  final class FSIndexOutput extends OutputStreamIndexOutput {
    static final int CHUNK_SIZE = 8192;
    
    public FSIndexOutput(String name) throws IOException {
      super("FSIndexOutput(path=\"" + directory.resolve(name) + "\")", new FilterOutputStream(Files.newOutputStream(directory.resolve(name),
                                                                                                                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
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

  protected void fsync(String name) throws IOException {
    IOUtils.fsync(directory.resolve(name), false);
  }
}
