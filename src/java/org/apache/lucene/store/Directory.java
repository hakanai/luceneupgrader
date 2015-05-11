package org.apache.lucene.store;

/**
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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

/** A Directory is a flat list of files.  Files may be written once, when they
 * are created.  Once a file is created it may only be opened for read, or
 * deleted.  Random access is permitted both when reading and writing.
 *
 * <p> Java's i/o APIs not used directly, but rather all i/o is
 * through this API.  This permits things such as: <ul>
 * <li> implementation of RAM-based indices;
 * <li> implementation indices stored in a database, via JDBC;
 * <li> implementation of an index as a single file;
 * </ul>
 *
 * Directory locking is implemented by an instance of {@code
 * LockFactory}, and can be changed for each Directory
 * instance using {@code #setLockFactory}.
 *
 */
public abstract class Directory implements Closeable {

  volatile protected boolean isOpen = true;

  /** Holds the LockFactory instance (implements locking for
   * this Directory instance). */
  protected LockFactory lockFactory;

  /**
   * Returns an array of strings, one for each file in the directory.
   * 
   * @throws NoSuchDirectoryException if the directory is not prepared for any
   *         write operations (such as {@code #createOutput(String)}).
   * @throws IOException in case of other IO errors
   */
  public abstract String[] listAll() throws IOException;

  /** Returns true iff a file with the given name exists. */
  public abstract boolean fileExists(String name)
       throws IOException;

  /** Set the modified time of an existing file to now.
   *
   *  @deprecated Lucene never uses this API; it will be
   *  removed in 4.0. */
  @Deprecated
  public abstract void touchFile(String name)
       throws IOException;

  /** Removes an existing file in the directory. */
  public abstract void deleteFile(String name)
       throws IOException;

  /**
   * Returns the length of a file in the directory. This method follows the
   * following contract:
   * <ul>
   * <li>Throws {@code FileNotFoundException} if the file does not exist
   * <li>Returns a value &ge;0 if the file exists, which specifies its length.
   * </ul>
   * 
   * @param name the name of the file for which to return the length.
   * @throws FileNotFoundException if the file does not exist.
   * @throws IOException if there was an IO error while retrieving the file's
   *         length.
   */
  public abstract long fileLength(String name) throws IOException;


  /** Creates a new, empty file in the directory with the given name.
      Returns a stream writing this file. */
  public abstract IndexOutput createOutput(String name)
       throws IOException;

  /**
   * Ensure that any writes to this file are moved to
   * stable storage.  Lucene uses this to properly commit
   * changes to the index, to prevent a machine/OS crash
   * from corrupting the index.
   * @deprecated use {@code #sync(Collection)} instead.
   * For easy migration you can change your code to call
   * sync(Collections.singleton(name))
   */
  @Deprecated
  public void sync(String name) throws IOException { // TODO 4.0 kill me
  }

  /**
   * Ensure that any writes to these files are moved to
   * stable storage.  Lucene uses this to properly commit
   * changes to the index, to prevent a machine/OS crash
   * from corrupting the index.<br/>
   * <br/>
   * NOTE: Clients may call this method for same files over
   * and over again, so some impls might optimize for that.
   * For other impls the operation can be a noop, for various
   * reasons.
   */
  public void sync(Collection<String> names) throws IOException { // TODO 4.0 make me abstract
    for (String name : names)
      sync(name);
  }

  /** Returns a stream reading an existing file. */
  public abstract IndexInput openInput(String name)
    throws IOException;

  /** Returns a stream reading an existing file, with the
   * specified read buffer size.  The particular Directory
   * implementation may ignore the buffer size.  Currently
   * the only Directory implementations that respect this
   * parameter are {@code FSDirectory} and {@code
   * org.apache.lucene.index.CompoundFileReader}.
  */
  public IndexInput openInput(String name, int bufferSize) throws IOException {
    return openInput(name);
  }

  /** Construct a {@code Lock}.
   * @param name the name of the lock file
   */
  public Lock makeLock(String name) {
      return lockFactory.makeLock(name);
  }

  /** Closes the store. */
  public abstract void close()
       throws IOException;

  /**
   * Set the LockFactory that this Directory instance should
   * use for its locking implementation.  Each * instance of
   * LockFactory should only be used for one directory (ie,
   * do not share a single instance across multiple
   * Directories).
   *
   * @param lockFactory instance of {@code LockFactory}.
   */
  public void setLockFactory(LockFactory lockFactory) throws IOException {
    assert lockFactory != null;
    this.lockFactory = lockFactory;
    lockFactory.setLockPrefix(this.getLockID());
  }

  /**
   * Get the LockFactory that this Directory instance is
   * using for its locking implementation.  Note that this
   * may be null for Directory implementations that provide
   * their own locking implementation.
   */
  public LockFactory getLockFactory() {
      return this.lockFactory;
  }

  /**
   * Return a string identifier that uniquely differentiates
   * this Directory instance from other Directory instances.
   * This ID should be the same if two Directory instances
   * (even in different JVMs and/or on different machines)
   * are considered "the same index".  This is how locking
   * "scopes" to the right index.
   */
  public String getLockID() {
      return this.toString();
  }

  @Override
  public String toString() {
    return super.toString() + " lockFactory=" + getLockFactory();
  }

  /**
   * @throws AlreadyClosedException if this Directory is closed
   */
  protected final void ensureOpen() throws AlreadyClosedException {
    if (!isOpen)
      throw new AlreadyClosedException("this Directory is closed");
  }
}
