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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util;

import java.util.concurrent.atomic.AtomicReference;


public final class SetOnce<T> implements Cloneable {

  public static final class AlreadySetException extends IllegalStateException {
    public AlreadySetException() {
      super("The object cannot be set twice!");
    }
  }

  private static final class Wrapper<T> {
    private T object;

    private Wrapper(T object) {
      this.object = object;
    }
  }

  private final AtomicReference<Wrapper<T>> set;
  
  public SetOnce() {
    set = new AtomicReference<>();
  }

  public SetOnce(T obj) {
    set = new AtomicReference<>(new Wrapper<>(obj));
  }
  
  public final void set(T obj) {
    if (!trySet(obj)) {
      throw new AlreadySetException();
    }
  }

  public final boolean trySet(T obj) {
    return set.compareAndSet(null, new Wrapper<>(obj));
  }
  
  public final T get() {
    Wrapper<T> wrapper = set.get();
    return wrapper == null ? null : wrapper.object;
  }
}
