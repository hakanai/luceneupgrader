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


import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

public class MapOfSets<K, V> {

  private final Map<K, Set<V>> theMap;

  public MapOfSets(Map<K, Set<V>> m) {
    theMap = m;
  }

  public Map<K, Set<V>> getMap() {
    return theMap;
  }

  public int put(K key, V val) {
    final Set<V> theSet;
    if (theMap.containsKey(key)) {
      theSet = theMap.get(key);
    } else {
      theSet = new HashSet<V>(23);
      theMap.put(key, theSet);
    }
    theSet.add(val);
    return theSet.size();
  }
  public int putAll(K key, Collection<? extends V> vals) {
    final Set<V> theSet;
    if (theMap.containsKey(key)) {
      theSet = theMap.get(key);
    } else {
      theSet = new HashSet<V>(23);
      theMap.put(key, theSet);
    }
    theSet.addAll(vals);
    return theSet.size();
  }
 
}
