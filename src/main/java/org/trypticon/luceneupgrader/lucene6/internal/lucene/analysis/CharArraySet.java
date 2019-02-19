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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis;


import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class CharArraySet extends AbstractSet<Object> {

  public static final CharArraySet EMPTY_SET = new CharArraySet(CharArrayMap.<Object>emptyMap());
  
  private static final Object PLACEHOLDER = new Object();
  
  private final CharArrayMap<Object> map;
  
  public CharArraySet(int startSize, boolean ignoreCase) {
    this(new CharArrayMap<>(startSize, ignoreCase));
  }

  public CharArraySet(Collection<?> c, boolean ignoreCase) {
    this(c.size(), ignoreCase);
    addAll(c);
  }

  CharArraySet(final CharArrayMap<Object> map){
    this.map = map;
  }
  
  @Override
  public void clear() {
    map.clear();
  }

  public boolean contains(char[] text, int off, int len) {
    return map.containsKey(text, off, len);
  }

  public boolean contains(CharSequence cs) {
    return map.containsKey(cs);
  }

  @Override
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  @Override
  public boolean add(Object o) {
    return map.put(o, PLACEHOLDER) == null;
  }

  public boolean add(CharSequence text) {
    return map.put(text, PLACEHOLDER) == null;
  }
  
  public boolean add(String text) {
    return map.put(text, PLACEHOLDER) == null;
  }


  public boolean add(char[] text) {
    return map.put(text, PLACEHOLDER) == null;
  }

  @Override
  public int size() {
    return map.size();
  }
  
  public static CharArraySet unmodifiableSet(CharArraySet set) {
    if (set == null)
      throw new NullPointerException("Given set is null");
    if (set == EMPTY_SET)
      return EMPTY_SET;
    if (set.map instanceof CharArrayMap.UnmodifiableCharArrayMap)
      return set;
    return new CharArraySet(CharArrayMap.unmodifiableMap(set.map));
  }

  public static CharArraySet copy(final Set<?> set) {
    if(set == EMPTY_SET)
      return EMPTY_SET;
    if(set instanceof CharArraySet) {
      final CharArraySet source = (CharArraySet) set;
      return new CharArraySet(CharArrayMap.copy(source.map));
    }
    return new CharArraySet(set, false);
  }
  
  @Override @SuppressWarnings("unchecked")
  public Iterator<Object> iterator() {
    // use the AbstractSet#keySet()'s iterator (to not produce endless recursion)
    return map.originalKeySet().iterator();
  }
  
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("[");
    for (Object item : this) {
      if (sb.length()>1) sb.append(", ");
      if (item instanceof char[]) {
        sb.append((char[]) item);
      } else {
        sb.append(item);
      }
    }
    return sb.append(']').toString();
  }
}
