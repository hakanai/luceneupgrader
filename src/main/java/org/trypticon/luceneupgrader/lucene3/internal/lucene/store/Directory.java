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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.store;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Closeable;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexFileNameFilter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.IOUtils;

public abstract class Directory implements Closeable {

  volatile protected boolean isOpen = true;

  protected LockFactory lockFactory;

  public abstract String[] listAll() throws IOException;

  public abstract boolean fileExists(String name)
       throws IOException;

  @Deprecated
  public abstract long fileModified(String name)
       throws IOException;


  @Deprecated
  public abstract void touchFile(String name)
       throws IOException;

  public abstract void deleteFile(String name)
       throws IOException;

  public abstract long fileLength(String name) throws IOException;


  public abstract IndexOutput createOutput(String name)
       throws IOException;

  @Deprecated
  public void sync(String name) throws IOException { // TODO 4.0 kill me
  }

  public void sync(Collection<String> names) throws IOException { // TODO 4.0 make me abstract
    for (String name : names)
      sync(name);
  }

  public abstract IndexInput openInput(String name)
    throws IOException;

  public IndexInput openInput(String name, int bufferSize) throws IOException {
    return openInput(name);
  }


  public Lock makeLock(String name) {
      return lockFactory.makeLock(name);
  }
  public void clearLock(String name) throws IOException {
    if (lockFactory != null) {
      lockFactory.clearLock(name);
    }
  }

  public abstract void close()
       throws IOException;

  public void setLockFactory(LockFactory lockFactory) throws IOException {
    assert lockFactory != null;
    this.lockFactory = lockFactory;
    lockFactory.setLockPrefix(this.getLockID());
  }

  public LockFactory getLockFactory() {
      return this.lockFactory;
  }

  public String getLockID() {
      return this.toString();
  }

  @Override
  public String toString() {
    return super.toString() + " lockFactory=" + getLockFactory();
  }

  public void copy(Directory to, String src, String dest) throws IOException {
    IndexOutput os = null;
    IndexInput is = null;
    IOException priorException = null;
    try {
      os = to.createOutput(dest);
      is = openInput(src);
      is.copyBytes(os, is.length());
    } catch (IOException ioe) {
      priorException = ioe;
    } finally {
      IOUtils.closeWhileHandlingException(priorException, os, is);
    }
  }

  @Deprecated
  public static void copy(Directory src, Directory dest, boolean closeDirSrc) throws IOException {
    IndexFileNameFilter filter = IndexFileNameFilter.getFilter();
    for (String file : src.listAll()) {
      if (filter.accept(null, file)) {
        src.copy(dest, file, file);
      }
    }
    if (closeDirSrc) {
      src.close();
    }
  }

  protected final void ensureOpen() throws AlreadyClosedException {
    if (!isOpen)
      throw new AlreadyClosedException("this Directory is closed");
  }
}
