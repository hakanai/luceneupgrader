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

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.ThreadInterruptedException;

public final class SleepingLockWrapper extends FilterDirectory {
 
  public static final long LOCK_OBTAIN_WAIT_FOREVER = -1;
  
  public static long DEFAULT_POLL_INTERVAL = 1000;
  
  private final long lockWaitTimeout;
  private final long pollInterval;
  
  public SleepingLockWrapper(Directory delegate, long lockWaitTimeout) {
    this(delegate, lockWaitTimeout, DEFAULT_POLL_INTERVAL);
  }
  
  public SleepingLockWrapper(Directory delegate, long lockWaitTimeout, long pollInterval) {
    super(delegate);
    this.lockWaitTimeout = lockWaitTimeout;
    this.pollInterval = pollInterval;
    if (lockWaitTimeout < 0 && lockWaitTimeout != LOCK_OBTAIN_WAIT_FOREVER) {
      throw new IllegalArgumentException("lockWaitTimeout should be LOCK_OBTAIN_WAIT_FOREVER or a non-negative number (got " + lockWaitTimeout + ")");
    }
    if (pollInterval < 0) {
      throw new IllegalArgumentException("pollInterval must be a non-negative number (got " + pollInterval + ")");
    }
  }

  @Override
  public Lock obtainLock(String lockName) throws IOException {
    LockObtainFailedException failureReason = null;
    long maxSleepCount = lockWaitTimeout / pollInterval;
    long sleepCount = 0;
    
    do {
      try {
        return in.obtainLock(lockName);
      } catch (LockObtainFailedException failed) {
        if (failureReason == null) {
          failureReason = failed;
        }
      }
      try {
        Thread.sleep(pollInterval);
      } catch (InterruptedException ie) {
        throw new ThreadInterruptedException(ie);
      }
    } while (sleepCount++ < maxSleepCount || lockWaitTimeout == LOCK_OBTAIN_WAIT_FOREVER);
    
    // we failed to obtain the lock in the required time
    String reason = "Lock obtain timed out: " + this.toString();
    if (failureReason != null) {
      reason += ": " + failureReason;
    }
    throw new LockObtainFailedException(reason, failureReason);
  }

  @Override
  public String toString() {
    return "SleepingLockWrapper(" + in + ")";
  }
}
