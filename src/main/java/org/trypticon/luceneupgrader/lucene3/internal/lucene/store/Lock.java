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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ThreadInterruptedException;
import java.io.IOException;

public abstract class Lock {

  public static long LOCK_POLL_INTERVAL = 1000;

  public static final long LOCK_OBTAIN_WAIT_FOREVER = -1;


  public abstract boolean obtain() throws IOException;

  protected Throwable failureReason;


  public boolean obtain(long lockWaitTimeout) throws LockObtainFailedException, IOException {
    failureReason = null;
    boolean locked = obtain();
    if (lockWaitTimeout < 0 && lockWaitTimeout != LOCK_OBTAIN_WAIT_FOREVER)
      throw new IllegalArgumentException("lockWaitTimeout should be LOCK_OBTAIN_WAIT_FOREVER or a non-negative number (got " + lockWaitTimeout + ")");

    long maxSleepCount = lockWaitTimeout / LOCK_POLL_INTERVAL;
    long sleepCount = 0;
    while (!locked) {
      if (lockWaitTimeout != LOCK_OBTAIN_WAIT_FOREVER && sleepCount++ >= maxSleepCount) {
        String reason = "Lock obtain timed out: " + this.toString();
        if (failureReason != null) {
          reason += ": " + failureReason;
        }
        LockObtainFailedException e = new LockObtainFailedException(reason);
        if (failureReason != null) {
          e.initCause(failureReason);
        }
        throw e;
      }
      try {
        Thread.sleep(LOCK_POLL_INTERVAL);
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
      locked = obtain();
    }
    return locked;
  }

  public abstract void release() throws IOException;

  public abstract boolean isLocked() throws IOException;


  public abstract static class With {
    private Lock lock;
    private long lockWaitTimeout;


    public With(Lock lock, long lockWaitTimeout) {
      this.lock = lock;
      this.lockWaitTimeout = lockWaitTimeout;
    }

    protected abstract Object doBody() throws IOException;

    public Object run() throws LockObtainFailedException, IOException {
      boolean locked = false;
      try {
         locked = lock.obtain(lockWaitTimeout);
         return doBody();
      } finally {
        if (locked)
	      lock.release();
      }
    }
  }

}
