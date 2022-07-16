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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton;

import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.hppc.BitMixer;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.hppc.IntIntHashMap;

final class StateSet extends IntSet {

  private final IntIntHashMap inner;
  private long hashCode;
  private boolean hashUpdated = true;
  private boolean arrayUpdated = true;
  private int[] arrayCache = new int[0];

  StateSet(int capacity) {
    inner = new IntIntHashMap(capacity);
  }

  void incr(int state) {
    if (inner.addTo(state, 1) == 1) {
      keyChanged();
    }
  }

  void decr(int state) {
    assert inner.containsKey(state);
    int keyIndex = inner.indexOf(state);
    int count = inner.indexGet(keyIndex) - 1;
    if (count == 0) {
      inner.indexRemove(keyIndex);
      keyChanged();
    } else {
      inner.indexReplace(keyIndex, count);
    }
  }

  FrozenIntSet freeze(int state) {
    return new FrozenIntSet(getArray(), longHashCode(), state);
  }

  private void keyChanged() {
    hashUpdated = false;
    arrayUpdated = false;
  }

  @Override
  int[] getArray() {
    if (arrayUpdated) {
      return arrayCache;
    }
    arrayCache = new int[inner.size()];
    int i = 0;
    for (IntIntHashMap.IntCursor cursor : inner.keys()) {
      arrayCache[i++] = cursor.value;
    }
    // we need to sort this array since "equals" method depend on this
    Arrays.sort(arrayCache);
    arrayUpdated = true;
    return arrayCache;
  }

  @Override
  int size() {
    return inner.size();
  }

  @Override
  long longHashCode() {
    if (hashUpdated) {
      return hashCode;
    }
    hashCode = inner.size();
    for (IntIntHashMap.IntCursor cursor : inner.keys()) {
      hashCode += BitMixer.mix(cursor.value);
    }
    hashUpdated = true;
    return hashCode;
  }
}
