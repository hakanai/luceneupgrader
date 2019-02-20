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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SetOnce<T> {

  public static final class AlreadySetException extends RuntimeException {
    public AlreadySetException() {
      super("The object cannot be set twice!");
    }
  }
  
  private volatile T obj = null;
  private final AtomicBoolean set;
  
  public SetOnce() {
    set = new AtomicBoolean(false);
  }

  public SetOnce(T obj) {
    this.obj = obj;
    set = new AtomicBoolean(true);
  }
  
  public final void set(T obj) {
    if (set.compareAndSet(false, true)) {
      this.obj = obj;
    } else {
      throw new AlreadySetException();
    }
  }
  
  public final T get() {
    return obj;
  }
}
