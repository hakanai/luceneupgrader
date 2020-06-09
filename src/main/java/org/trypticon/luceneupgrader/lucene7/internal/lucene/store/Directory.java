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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.store;


import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection; // for javadocs
import java.util.Collections;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.IOUtils;

public abstract class Directory implements Closeable {
  public abstract String[] listAll() throws IOException;

  public abstract void deleteFile(String name) throws IOException;

  public abstract long fileLength(String name) throws IOException;

  public abstract IndexOutput createOutput(String name, IOContext context) throws IOException;

  public abstract IndexOutput createTempOutput(String prefix, String suffix, IOContext context) throws IOException;

  public abstract void sync(Collection<String> names) throws IOException;

  public abstract void syncMetaData() throws IOException;

  public abstract void rename(String source, String dest) throws IOException;

  public abstract IndexInput openInput(String name, IOContext context) throws IOException;
  
  public ChecksumIndexInput openChecksumInput(String name, IOContext context) throws IOException {
    return new BufferedChecksumIndexInput(openInput(name, context));
  }
  
  public abstract Lock obtainLock(String name) throws IOException;

  @Override
  public abstract void close() throws IOException;

  public void copyFrom(Directory from, String src, String dest, IOContext context) throws IOException {
    boolean success = false;
    try (IndexInput is = from.openInput(src, context);
         IndexOutput os = createOutput(dest, context)) {
      os.copyBytes(is, is.length());
      success = true;
    } finally {
      if (!success) {
        IOUtils.deleteFilesIgnoringExceptions(this, dest);
      }
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(hashCode());
  }

  protected void ensureOpen() throws AlreadyClosedException {}

  public Set<String> getPendingDeletions() throws IOException {
    return Collections.emptySet();
  }
}
