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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NativeFSLockFactory extends FSLockFactory {

  public NativeFSLockFactory() {
    this((File) null);
  }

  public NativeFSLockFactory(String lockDirName) {
    this(new File(lockDirName));
  }

  public NativeFSLockFactory(File lockDir) {
    setLockDir(lockDir);
  }

  @Override
  public synchronized Lock makeLock(String lockName) {
    if (lockPrefix != null)
      lockName = lockPrefix + "-" + lockName;
    return new NativeFSLock(lockDir, lockName);
  }

  @Override
  public void clearLock(String lockName) throws IOException {
    makeLock(lockName).close();
  }
}

class NativeFSLock extends Lock {

  private FileChannel channel;
  private FileLock lock;
  private File path;
  private File lockDir;
  private static final Set<String> LOCK_HELD = Collections.synchronizedSet(new HashSet<String>());


  public NativeFSLock(File lockDir, String lockFileName) {
    this.lockDir = lockDir;
    path = new File(lockDir, lockFileName);
  }


  @Override
  public synchronized boolean obtain() throws IOException {

    if (lock != null) {
      // Our instance is already locked:
      return false;
    }

    // Ensure that lockDir exists and is a directory.
    if (!lockDir.exists()) {
      if (!lockDir.mkdirs())
        throw new IOException("Cannot create directory: " +
            lockDir.getAbsolutePath());
    } else if (!lockDir.isDirectory()) {
      // TODO: NoSuchDirectoryException instead?
      throw new IOException("Found regular file where directory expected: " + 
          lockDir.getAbsolutePath());
    }
    final String canonicalPath = path.getCanonicalPath();
    // Make sure nobody else in-process has this lock held
    // already, and, mark it held if not:
    // This is a pretty crazy workaround for some documented
    // but yet awkward JVM behavior:
    //
    //   On some systems, closing a channel releases all locks held by the Java virtual machine on the underlying file
    //   regardless of whether the locks were acquired via that channel or via another channel open on the same file.
    //   It is strongly recommended that, within a program, a unique channel be used to acquire all locks on any given
    //   file.
    //
    // This essentially means if we close "A" channel for a given file all locks might be released... the odd part
    // is that we can't re-obtain the lock in the same JVM but from a different process if that happens. Nevertheless
    // this is super trappy. See LUCENE-5738
    boolean obtained = false;
    if (LOCK_HELD.add(canonicalPath)) {
      try {
        channel = FileChannel.open(path.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
          lock = channel.tryLock();
          obtained = lock != null;
        } catch (IOException | OverlappingFileLockException e) {
          // At least on OS X, we will sometimes get an
          // intermittent "Permission Denied" IOException,
          // which seems to simply mean "you failed to get
          // the lock".  But other IOExceptions could be
          // "permanent" (eg, locking is not supported via
          // the filesystem).  So, we record the failure
          // reason here; the timeout obtain (usually the
          // one calling us) will use this as "root cause"
          // if it fails to get the lock.
          failureReason = e;
        }
      } finally {
        if (obtained == false) { // not successful - clear up and move out
          clearLockHeld(path);
          final FileChannel toClose = channel;
          channel = null;
          IOUtils.closeWhileHandlingException(toClose);
        }
      }
    }
    return obtained;
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      if (lock != null) {
        try {
          lock.release();
          lock = null;
        } finally {
          clearLockHeld(path);
        }
      }
    } finally {
      IOUtils.close(channel);
      channel = null;
    }
  }

  private static final void clearLockHeld(File path) throws IOException {
    boolean remove = LOCK_HELD.remove(path.getCanonicalPath());
    assert remove : "Lock was cleared but never marked as held";
  }

  @Override
  public synchronized boolean isLocked() {
    // The test for is isLocked is not directly possible with native file locks:
    
    // First a shortcut, if a lock reference in this instance is available
    if (lock != null) return true;
    
    // Look if lock file is present; if not, there can definitely be no lock!
    if (!path.exists()) return false;
    
    // Try to obtain and release (if was locked) the lock
    try {
      boolean obtained = obtain();
      if (obtained) close();
      return !obtained;
    } catch (IOException ioe) {
      return false;
    }    
  }

  @Override
  public String toString() {
    return "NativeFSLock@" + path;
  }
}
