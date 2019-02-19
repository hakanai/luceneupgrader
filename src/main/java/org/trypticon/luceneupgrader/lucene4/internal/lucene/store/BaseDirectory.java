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

import java.io.IOException;

public abstract class BaseDirectory extends Directory {

  volatile protected boolean isOpen = true;

  protected LockFactory lockFactory;

  protected BaseDirectory() {
    super();
  }

  @Override
  public Lock makeLock(String name) {
      return lockFactory.makeLock(name);
  }

  @Override
  public void clearLock(String name) throws IOException {
    if (lockFactory != null) {
      lockFactory.clearLock(name);
    }
  }

  @Override
  public void setLockFactory(LockFactory lockFactory) throws IOException {
    assert lockFactory != null;
    this.lockFactory = lockFactory;
    lockFactory.setLockPrefix(this.getLockID());
  }

  @Override
  public LockFactory getLockFactory() {
    return this.lockFactory;
  }

  @Override
  protected final void ensureOpen() throws AlreadyClosedException {
    if (!isOpen)
      throw new AlreadyClosedException("this Directory is closed");
  }

}
