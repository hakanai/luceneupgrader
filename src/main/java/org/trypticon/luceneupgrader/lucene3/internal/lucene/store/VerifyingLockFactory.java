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

import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VerifyingLockFactory extends LockFactory {

  LockFactory lf;
  byte id;
  String host;
  int port;

  private class CheckedLock extends Lock {
    private Lock lock;

    public CheckedLock(Lock lock) {
      this.lock = lock;
    }

    private void verify(byte message) {
      try {
        Socket s = new Socket(host, port);
        OutputStream out = s.getOutputStream();
        out.write(id);
        out.write(message);
        InputStream in = s.getInputStream();
        int result = in.read();
        in.close();
        out.close();
        s.close();
        if (result != 0)
          throw new RuntimeException("lock was double acquired");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public synchronized boolean obtain(long lockWaitTimeout)
      throws LockObtainFailedException, IOException {
      boolean obtained = lock.obtain(lockWaitTimeout);
      if (obtained)
        verify((byte) 1);
      return obtained;
    }

    @Override
    public synchronized boolean obtain()
      throws LockObtainFailedException, IOException {
      return lock.obtain();
    }

    @Override
    public synchronized boolean isLocked() throws IOException {
      return lock.isLocked();
    }

    @Override
    public synchronized void release() throws IOException {
      if (isLocked()) {
        verify((byte) 0);
        lock.release();
      }
    }
  }

  public VerifyingLockFactory(byte id, LockFactory lf, String host, int port) throws IOException {
    this.id = id;
    this.lf = lf;
    this.host = host;
    this.port = port;
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
