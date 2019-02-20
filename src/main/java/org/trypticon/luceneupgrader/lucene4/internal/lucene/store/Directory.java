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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Closeable;
import java.nio.file.NoSuchFileException;
import java.util.Collection; // for javadocs

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

public abstract class Directory implements Closeable {

  public abstract String[] listAll() throws IOException;


  @Deprecated
  public abstract boolean fileExists(String name)
       throws IOException;

  public abstract void deleteFile(String name)
       throws IOException;

  public abstract long fileLength(String name) throws IOException;


  public abstract IndexOutput createOutput(String name, IOContext context)
       throws IOException;

  public abstract void sync(Collection<String> names) throws IOException;


  public abstract IndexInput openInput(String name, IOContext context) throws IOException;
  
  public ChecksumIndexInput openChecksumInput(String name, IOContext context) throws IOException {
    return new BufferedChecksumIndexInput(openInput(name, context));
  }
  

  public abstract Lock makeLock(String name);

  public abstract void clearLock(String name) throws IOException;

  @Override
  public abstract void close()
       throws IOException;

  public abstract void setLockFactory(LockFactory lockFactory) throws IOException;

  public abstract LockFactory getLockFactory();

  public String getLockID() {
    return this.toString();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(hashCode()) + " lockFactory=" + getLockFactory();
  }

  public void copy(Directory to, String src, String dest, IOContext context) throws IOException {
    IndexOutput os = null;
    IndexInput is = null;
    boolean success = false;
    try {
      os = to.createOutput(dest, context);
      is = openInput(src, context);
      os.copyBytes(is, is.length());
      success = true;
    } finally {
      if (success) {
        IOUtils.close(os, is);
      } else {
        IOUtils.closeWhileHandlingException(os, is);
        try {
          to.deleteFile(dest);
        } catch (Throwable t) {
        }
      }
    }
  }

  protected void ensureOpen() throws AlreadyClosedException {}

}
