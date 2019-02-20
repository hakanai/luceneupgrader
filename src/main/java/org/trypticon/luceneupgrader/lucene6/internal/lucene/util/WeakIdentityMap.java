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


import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

public final class WeakIdentityMap<K,V> {
  private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
  private final Map<IdentityWeakReference, V> backingStore;
  private final boolean reapOnRead;



  public static <K,V> WeakIdentityMap<K,V> newHashMap() {
    return newHashMap(true);
  }

  public static <K,V> WeakIdentityMap<K,V> newHashMap(boolean reapOnRead) {
    return new WeakIdentityMap<>(new HashMap<IdentityWeakReference,V>(), reapOnRead);
  }

  public static <K,V> WeakIdentityMap<K,V> newConcurrentHashMap() {
    return newConcurrentHashMap(true);
  }

  public static <K,V> WeakIdentityMap<K,V> newConcurrentHashMap(boolean reapOnRead) {
    return new WeakIdentityMap<>(new ConcurrentHashMap<IdentityWeakReference,V>(), reapOnRead);
  }

  private WeakIdentityMap(Map<IdentityWeakReference, V> backingStore, boolean reapOnRead) {
    this.backingStore = backingStore;
    this.reapOnRead = reapOnRead;
  }

  public void clear() {
    backingStore.clear();
    reap();
  }

  public boolean containsKey(Object key) {
    if (reapOnRead) reap();
    return backingStore.containsKey(new IdentityWeakReference(key, null));
  }

  public V get(Object key) {
    if (reapOnRead) reap();
    return backingStore.get(new IdentityWeakReference(key, null));
  }


  public V put(K key, V value) {
    reap();
    return backingStore.put(new IdentityWeakReference(key, queue), value);
  }

  public boolean isEmpty() {
    return size() == 0;
  }


  public V remove(Object key) {
    reap();
    return backingStore.remove(new IdentityWeakReference(key, null));
  }


  public int size() {
    if (backingStore.isEmpty())
      return 0;
    if (reapOnRead) reap();
    return backingStore.size();
  }
  

  public Iterator<K> keyIterator() {
    reap();
    final Iterator<IdentityWeakReference> iterator = backingStore.keySet().iterator();
    // IMPORTANT: Don't use oal.util.FilterIterator here:
    // We need *strong* reference to current key after setNext()!!!
    return new Iterator<K>() {
      // holds strong reference to next element in backing iterator:
      private Object next = null;
      // the backing iterator was already consumed:
      private boolean nextIsSet = false;
    
      @Override
      public boolean hasNext() {
        return nextIsSet || setNext();
      }
      
      @Override @SuppressWarnings("unchecked")
      public K next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        assert nextIsSet;
        try {
          return (K) next;
        } finally {
           // release strong reference and invalidate current value:
          nextIsSet = false;
          next = null;
        }
      }
      
      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
      
      private boolean setNext() {
        assert !nextIsSet;
        while (iterator.hasNext()) {
          next = iterator.next().get();
          if (next == null) {
            // the key was already GCed, we can remove it from backing map:
            iterator.remove();
          } else {
            // unfold "null" special value:
            if (next == NULL) {
              next = null;
            }
            return nextIsSet = true;
          }
        }
        return false;
      }
    };
  }
  

  public Iterator<V> valueIterator() {
    if (reapOnRead) reap();
    return backingStore.values().iterator();
  }

  public void reap() {
    Reference<?> zombie;
    while ((zombie = queue.poll()) != null) {
      backingStore.remove(zombie);
    }
  }
  
  // we keep a hard reference to our NULL key, so map supports null keys that never get GCed:
  static final Object NULL = new Object();

  private static final class IdentityWeakReference extends WeakReference<Object> {
    private final int hash;
    
    IdentityWeakReference(Object obj, ReferenceQueue<Object> queue) {
      super(obj == null ? NULL : obj, queue);
      hash = System.identityHashCode(obj);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof IdentityWeakReference) {
        final IdentityWeakReference ref = (IdentityWeakReference)o;
        if (this.get() == ref.get()) {
          return true;
        }
      }
      return false;
    }
  }
}

