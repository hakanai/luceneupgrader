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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;


public class RefCount<T> {
  
  private final AtomicInteger refCount = new AtomicInteger(1);
  
  protected final T object;
  
  public RefCount(T object) {
    this.object = object;
  }

  protected void release() throws IOException {}
  
  public final void decRef() throws IOException {
    final int rc = refCount.decrementAndGet();
    if (rc == 0) {
      boolean success = false;
      try {
        release();
        success = true;
      } finally {
        if (!success) {
          // Put reference back on failure
          refCount.incrementAndGet();
        }
      }
    } else if (rc < 0) {
      throw new IllegalStateException("too many decRef calls: refCount is " + rc + " after decrement");
    }
  }
  
  public final T get() {
    return object;
  }
  
  public final int getRefCount() {
    return refCount.get();
  }
  
  public final void incRef() {
    refCount.incrementAndGet();
  }
  
}

