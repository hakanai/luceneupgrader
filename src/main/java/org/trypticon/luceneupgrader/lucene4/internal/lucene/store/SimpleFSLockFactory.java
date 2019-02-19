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

import java.io.File;
import java.io.IOException;

public class SimpleFSLockFactory extends FSLockFactory {

  public SimpleFSLockFactory() {
    this((File) null);
  }

  public SimpleFSLockFactory(File lockDir) {
    setLockDir(lockDir);
  }

  public SimpleFSLockFactory(String lockDirName) {
    setLockDir(new File(lockDirName));
  }

  @Override
  public Lock makeLock(String lockName) {
    if (lockPrefix != null) {
      lockName = lockPrefix + "-" + lockName;
    }
    return new SimpleFSLock(lockDir, lockName);
  }

  @Override
  public void clearLock(String lockName) throws IOException {
    if (lockDir.exists()) {
      if (lockPrefix != null) {
        lockName = lockPrefix + "-" + lockName;
      }
      File lockFile = new File(lockDir, lockName);
      if (lockFile.exists() && !lockFile.delete()) {
        throw new IOException("Cannot delete " + lockFile);
      }
    }
  }
}

class SimpleFSLock extends Lock {

  File lockFile;
  File lockDir;

  public SimpleFSLock(File lockDir, String lockFileName) {
    this.lockDir = lockDir;
    lockFile = new File(lockDir, lockFileName);
  }

  @Override
  public boolean obtain() throws IOException {

    // Ensure that lockDir exists and is a directory:
    if (!lockDir.exists()) {
      if (!lockDir.mkdirs())
        throw new IOException("Cannot create directory: " +
                              lockDir.getAbsolutePath());
    } else if (!lockDir.isDirectory()) {
      // TODO: NoSuchDirectoryException instead?
      throw new IOException("Found regular file where directory expected: " + 
                            lockDir.getAbsolutePath());
    }
    
    try {
      return lockFile.createNewFile();
    } catch (IOException ioe) {
      // On Windows, on concurrent createNewFile, the 2nd process gets "access denied".
      // In that case, the lock was not aquired successfully, so return false.
      // We record the failure reason here; the obtain with timeout (usually the
      // one calling us) will use this as "root cause" if it fails to get the lock.
      failureReason = ioe;
      return false;
    }
  }

  @Override
  public void close() throws LockReleaseFailedException {
    if (lockFile.exists() && !lockFile.delete()) {
      throw new LockReleaseFailedException("failed to delete " + lockFile);
    }
  }

  @Override
  public boolean isLocked() {
    return lockFile.exists();
  }

  @Override
  public String toString() {
    return "SimpleFSLock@" + lockFile;
  }
}
