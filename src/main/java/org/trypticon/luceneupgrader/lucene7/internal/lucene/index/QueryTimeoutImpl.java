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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.util.concurrent.TimeUnit;

import static java.lang.System.nanoTime;

public class QueryTimeoutImpl implements QueryTimeout {

  private Long timeoutAt;

  public QueryTimeoutImpl(long timeAllowed) {
    if (timeAllowed < 0L) {
      timeAllowed = Long.MAX_VALUE;
    }
    timeoutAt = nanoTime() + TimeUnit.NANOSECONDS.convert(timeAllowed, TimeUnit.MILLISECONDS);
  }

  public Long getTimeoutAt() {
    return timeoutAt;
  }

  @Override
  public boolean shouldExit() {
    return timeoutAt != null && nanoTime() - timeoutAt > 0;
  }

  public void reset() {
    timeoutAt = null;
  }
  
  @Override
  public String toString() {
    return "timeoutAt: " + timeoutAt + " (System.nanoTime(): " + nanoTime() + ")";
  }
}


