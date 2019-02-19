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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext.Context;

public final class RateLimitedDirectoryWrapper extends FilterDirectory {

  // we need to be volatile here to make sure we see all the values that are set
  // / modified concurrently
  private volatile RateLimiter[] contextRateLimiters = new RateLimiter[IOContext.Context
      .values().length];
  
  public RateLimitedDirectoryWrapper(Directory wrapped) {
    super(wrapped);
  }
  
  @Override
  public IndexOutput createOutput(String name, IOContext context)
      throws IOException {
    ensureOpen();
    final IndexOutput output = super.createOutput(name, context);
    final RateLimiter limiter = getRateLimiter(context.context);
    if (limiter != null) {
      return new RateLimitedIndexOutput(limiter, output);
    }
    return output;
  }

  @Override
  public void copy(Directory to, String src, String dest, IOContext context) throws IOException {
    ensureOpen();
    in.copy(to, src, dest, context);
  }
  
  private RateLimiter getRateLimiter(IOContext.Context context) {
    assert context != null;
    return contextRateLimiters[context.ordinal()];
  }
  
  public void setMaxWriteMBPerSec(Double mbPerSec, IOContext.Context context) {
    ensureOpen();
    if (context == null) {
      throw new IllegalArgumentException("Context must not be null");
    }
    final int ord = context.ordinal();
    final RateLimiter limiter = contextRateLimiters[ord];
    if (mbPerSec == null) {
      if (limiter != null) {
        limiter.setMbPerSec(Double.MAX_VALUE);
        contextRateLimiters[ord] = null;
      }
    } else if (limiter != null) {
      limiter.setMbPerSec(mbPerSec);
      contextRateLimiters[ord] = limiter; // cross the mem barrier again
    } else {
      contextRateLimiters[ord] = new RateLimiter.SimpleRateLimiter(mbPerSec);
    }
  }
  
  public void setRateLimiter(RateLimiter mergeWriteRateLimiter,
      Context context) {
    ensureOpen();
    if (context == null) {
      throw new IllegalArgumentException("Context must not be null");
    }
    contextRateLimiters[context.ordinal()] = mergeWriteRateLimiter;
  }
  
  public Double getMaxWriteMBPerSec(IOContext.Context context) {
    ensureOpen();
    if (context == null) {
      throw new IllegalArgumentException("Context must not be null");
    }
    RateLimiter limiter = getRateLimiter(context);
    return limiter == null ? null : limiter.getMbPerSec();
  }
  
}
