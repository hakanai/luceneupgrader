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

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.HashSet;

public class NativeFSLockFactory extends FSLockFactory {

  public NativeFSLockFactory() throws IOException {
    this((File) null);
  }

  public NativeFSLockFactory(String lockDirName) throws IOException {
    this(new File(lockDirName));
  }

  public NativeFSLockFactory(File lockDir) throws IOException {
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
    // Note that this isn't strictly required anymore
    // because the existence of these files does not mean
    // they are locked, but, still do this in case people
    // really want to see the files go away:
    if (lockDir.exists()) {
      
      // Try to release the lock first - if it's held by another process, this
      // method should not silently fail.
      // NOTE: makeLock fixes the lock name by prefixing it w/ lockPrefix.
      // Therefore it should be called before the code block next which prefixes
      // the given name.
      makeLock(lockName).release();

      if (lockPrefix != null) {
        lockName = lockPrefix + "-" + lockName;
      }
      
      // As mentioned above, we don't care if the deletion of the file failed.
      new File(lockDir, lockName).delete();
    }
  }
}

class NativeFSLock extends Lock {

  private RandomAccessFile f;
  private FileChannel channel;
  private FileLock lock;
  private File path;
  private File lockDir;

  /*
   * The javadocs for FileChannel state that you should have
   * a single instance of a FileChannel (per JVM) for all
   * locking against a given file (locks are tracked per 
   * FileChannel instance in Java 1.4/1.5). Even using the same 
   * FileChannel instance is not completely thread-safe with Java 
   * 1.4/1.5 though. To work around this, we have a single (static) 
   * HashSet that contains the file paths of all currently 
   * locked locks.  This protects against possible cases 
   * where different Directory instances in one JVM (each 
   * with their own NativeFSLockFactory instance) have set 
   * the same lock dir and lock prefix. However, this will not 
   * work when LockFactorys are created by different 
   * classloaders (eg multiple webapps). 
   * 
   * TODO: Java 1.6 tracks system wide locks in a thread safe manner 
   * (same FileChannel instance or not), so we may want to 
   * change this when Lucene moves to Java 1.6.
   */
  private static HashSet<String> LOCK_HELD = new HashSet<String>();

  public NativeFSLock(File lockDir, String lockFileName) {
    this.lockDir = lockDir;
    path = new File(lockDir, lockFileName);
  }

  private synchronized boolean lockExists() {
    return lock != null;
  }

  @Override
  public synchronized boolean obtain() throws IOException {

    if (lockExists()) {
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

    String canonicalPath = path.getCanonicalPath();

    boolean markedHeld = false;

    try {

      // Make sure nobody else in-process has this lock held
      // already, and, mark it held if not:

      synchronized(LOCK_HELD) {
        if (LOCK_HELD.contains(canonicalPath)) {
          // Someone else in this JVM already has the lock:
          return false;
        } else {
          // This "reserves" the fact that we are the one
          // thread trying to obtain this lock, so we own
          // the only instance of a channel against this
          // file:
          LOCK_HELD.add(canonicalPath);
          markedHeld = true;
        }
      }

      try {
        f = new RandomAccessFile(path, "rw");
      } catch (IOException e) {
        // On Windows, we can get intermittent "Access
        // Denied" here.  So, we treat this as failure to
        // acquire the lock, but, store the reason in case
        // there is in fact a real error case.
        failureReason = e;
        f = null;
      }

      if (f != null) {
        try {
          channel = f.getChannel();
          try {
            lock = channel.tryLock();
          } catch (IOException e) {
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
          } finally {
            if (lock == null) {
              try {
                channel.close();
              } finally {
                channel = null;
              }
            }
          }
        } finally {
          if (channel == null) {
            try {
              f.close();
            } finally {
              f = null;
            }
          }
        }
      }

    } finally {
      if (markedHeld && !lockExists()) {
        synchronized(LOCK_HELD) {
          if (LOCK_HELD.contains(canonicalPath)) {
            LOCK_HELD.remove(canonicalPath);
          }
        }
      }
    }
    return lockExists();
  }

  @Override
  public synchronized void release() throws IOException {
    if (lockExists()) {
      try {
        lock.release();
      } finally {
        lock = null;
        try {
          channel.close();
        } finally {
          channel = null;
          try {
            f.close();
          } finally {
            f = null;
            synchronized(LOCK_HELD) {
              LOCK_HELD.remove(path.getCanonicalPath());
            }
          }
        }
      }
      // LUCENE-2421: we don't care anymore if the file cannot be deleted
      // because it's held up by another process (e.g. AntiVirus). NativeFSLock
      // does not depend on the existence/absence of the lock file
      path.delete();
    } else {
      // if we don't hold the lock, and somebody still called release(), for
      // example as a result of calling IndexWriter.unlock(), we should attempt
      // to obtain the lock and release it. If the obtain fails, it means the
      // lock cannot be released, and we should throw a proper exception rather
      // than silently failing/not doing anything.
      boolean obtained = false;
      try {
        if (!(obtained = obtain())) {
          throw new LockReleaseFailedException(
              "Cannot forcefully unlock a NativeFSLock which is held by another indexer component: "
                  + path);
        }
      } finally {
        if (obtained) {
          release();
        }
      }
    }
  }

  @Override
  public synchronized boolean isLocked() {
    // The test for is isLocked is not directly possible with native file locks:
    
    // First a shortcut, if a lock reference in this instance is available
    if (lockExists()) return true;
    
    // Look if lock file is present; if not, there can definitely be no lock!
    if (!path.exists()) return false;
    
    // Try to obtain and release (if was locked) the lock
    try {
      boolean obtained = obtain();
      if (obtained) release();
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
