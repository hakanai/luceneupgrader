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
import java.io.InputStream;
import java.io.OutputStream;

public class VerifyingLockFactory extends LockFactory {

  final LockFactory lf;
  final InputStream in;
  final OutputStream out;

  private class CheckedLock extends Lock {
    private final Lock lock;

    public CheckedLock(Lock lock) {
      this.lock = lock;
    }

    private void verify(byte message) throws IOException {
      out.write(message);
      out.flush();
      final int ret = in.read();
      if (ret < 0) {
        throw new IllegalStateException("Lock server died because of locking error.");
      }
      if (ret != message) {
        throw new IOException("Protocol violation.");
      }
    }

    @Override
    public synchronized boolean obtain() throws IOException {
      boolean obtained = lock.obtain();
      if (obtained)
        verify((byte) 1);
      return obtained;
    }

    @Override
    public synchronized boolean isLocked() throws IOException {
      return lock.isLocked();
    }

    @Override
    public synchronized void close() throws IOException {
      if (isLocked()) {
        verify((byte) 0);
        lock.close();
      }
    }
  }

  public VerifyingLockFactory(LockFactory lf, InputStream in, OutputStream out) throws IOException {
    this.lf = lf;
    this.in = in;
    this.out = out;
  }

  @Override
  public synchronized Lock makeLock(String lockName) {
    return new CheckedLock(lf.makeLock(lockName));
  }

  @Override
  public synchronized void clearLock(String lockName)
    throws IOException {
    lf.clearLock(lockName);
  }
}
