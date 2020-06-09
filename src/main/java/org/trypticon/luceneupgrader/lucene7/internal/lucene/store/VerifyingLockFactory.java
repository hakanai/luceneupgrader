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


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public final class VerifyingLockFactory extends LockFactory {

  final LockFactory lf;
  final InputStream in;
  final OutputStream out;

  private class CheckedLock extends Lock {
    private final Lock lock;

    public CheckedLock(Lock lock) throws IOException {
      this.lock = lock;
      verify((byte) 1);
    }

    @Override
    public void ensureValid() throws IOException {
      lock.ensureValid();
    }

    @Override
    public void close() throws IOException {
      try (Lock l = lock) {
        l.ensureValid();
        verify((byte) 0);
      }
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
  }

  public VerifyingLockFactory(LockFactory lf, InputStream in, OutputStream out) throws IOException {
    this.lf = lf;
    this.in = in;
    this.out = out;
  }

  @Override
  public Lock obtainLock(Directory dir, String lockName) throws IOException {
    return new CheckedLock(lf.obtainLock(dir, lockName));
  }
}
