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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CharacterUtils;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Version;

import java.util.*;


public class CharArrayMap<V> extends AbstractMap<Object,V> {
  // private only because missing generics
  private static final CharArrayMap<?> EMPTY_MAP = new EmptyCharArrayMap<Object>();

  private final static int INIT_SIZE = 8;
  private final CharacterUtils charUtils;
  private boolean ignoreCase;  
  private int count;
  final Version matchVersion; // package private because used in CharArraySet
  char[][] keys; // package private because used in CharArraySet's non Set-conform CharArraySetIterator
  V[] values; // package private because used in CharArraySet's non Set-conform CharArraySetIterator


  @SuppressWarnings("unchecked")
  public CharArrayMap(Version matchVersion, int startSize, boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
    int size = INIT_SIZE;
    while(startSize + (startSize>>2) > size)
      size <<= 1;
    keys = new char[size][];
    values = (V[]) new Object[size];
    this.charUtils = CharacterUtils.getInstance(matchVersion);
    this.matchVersion = matchVersion;
  }


  public CharArrayMap(Version matchVersion, Map<?,? extends V> c, boolean ignoreCase) {
    this(matchVersion, c.size(), ignoreCase);
    putAll(c);
  }
  
  private CharArrayMap(CharArrayMap<V> toCopy){
    this.keys = toCopy.keys;
    this.values = toCopy.values;
    this.ignoreCase = toCopy.ignoreCase;
    this.count = toCopy.count;
    this.charUtils = toCopy.charUtils;
    this.matchVersion = toCopy.matchVersion;
  }
  
  @Override
  public void clear() {
    count = 0;
    Arrays.fill(keys, null);
    Arrays.fill(values, null);
  }

  public boolean containsKey(char[] text, int off, int len) {
    return keys[getSlot(text, off, len)] != null;
  }

  public boolean containsKey(CharSequence cs) {
    return keys[getSlot(cs)] != null;
  }

  @Override
  public boolean containsKey(Object o) {
    if (o instanceof char[]) {
      final char[] text = (char[])o;
      return containsKey(text, 0, text.length);
    } 
    return containsKey(o.toString());
  }

  public V get(char[] text, int off, int len) {
    return values[getSlot(text, off, len)];
  }

  public V get(CharSequence cs) {
    return values[getSlot(cs)];
  }

  @Override
  public V get(Object o) {
    if (o instanceof char[]) {
      final char[] text = (char[])o;
      return get(text, 0, text.length);
    } 
    return get(o.toString());
  }

  private int getSlot(char[] text, int off, int len) {
    int code = getHashCode(text, off, len);
    int pos = code & (keys.length-1);
    char[] text2 = keys[pos];
    if (text2 != null && !equals(text, off, len, text2)) {
      final int inc = ((code>>8)+code)|1;
      do {
        code += inc;
        pos = code & (keys.length-1);
        text2 = keys[pos];
      } while (text2 != null && !equals(text, off, len, text2));
    }
    return pos;
  }

  private int getSlot(CharSequence text) {
    int code = getHashCode(text);
    int pos = code & (keys.length-1);
    char[] text2 = keys[pos];
    if (text2 != null && !equals(text, text2)) {
      final int inc = ((code>>8)+code)|1;
      do {
        code += inc;
        pos = code & (keys.length-1);
        text2 = keys[pos];
      } while (text2 != null && !equals(text, text2));
    }
    return pos;
  }

  public V put(CharSequence text, V value) {
    return put(text.toString(), value); // could be more efficient
  }

  @Override
  public V put(Object o, V value) {
    if (o instanceof char[]) {
      return put((char[])o, value);
    }
    return put(o.toString(), value);
  }
  
  public V put(String text, V value) {
    return put(text.toCharArray(), value);
  }


  public V put(char[] text, V value) {
    if (ignoreCase)
      for(int i=0;i<text.length;){
        i += Character.toChars(
              Character.toLowerCase(
                  charUtils.codePointAt(text, i)), text, i);
      }
    int slot = getSlot(text, 0, text.length);
    if (keys[slot] != null) {
      final V oldValue = values[slot];
      values[slot] = value;
      return oldValue;
    }
    keys[slot] = text;
    values[slot] = value;
    count++;

    if (count + (count>>2) > keys.length) {
      rehash();
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  private void rehash() {
    assert keys.length == values.length;
    final int newSize = 2*keys.length;
    final char[][] oldkeys = keys;
    final V[] oldvalues = values;
    keys = new char[newSize][];
    values = (V[]) new Object[newSize];

    for(int i=0; i<oldkeys.length; i++) {
      char[] text = oldkeys[i];
      if (text != null) {
        // todo: could be faster... no need to compare strings on collision
        final int slot = getSlot(text,0,text.length);
        keys[slot] = text;
        values[slot] = oldvalues[i];
      }
    }
  }
  
  private boolean equals(char[] text1, int off, int len, char[] text2) {
    if (len != text2.length)
      return false;
    final int limit = off+len;
    if (ignoreCase) {
      for(int i=0;i<len;) {
        final int codePointAt = charUtils.codePointAt(text1, off+i, limit);
        if (Character.toLowerCase(codePointAt) != charUtils.codePointAt(text2, i))
          return false;
        i += Character.charCount(codePointAt); 
      }
    } else {
      for(int i=0;i<len;i++) {
        if (text1[off+i] != text2[i])
          return false;
      }
    }
    return true;
  }

  private boolean equals(CharSequence text1, char[] text2) {
    int len = text1.length();
    if (len != text2.length)
      return false;
    if (ignoreCase) {
      for(int i=0;i<len;) {
        final int codePointAt = charUtils.codePointAt(text1, i);
        if (Character.toLowerCase(codePointAt) != charUtils.codePointAt(text2, i))
          return false;
        i += Character.charCount(codePointAt);
      }
    } else {
      for(int i=0;i<len;i++) {
        if (text1.charAt(i) != text2[i])
          return false;
      }
    }
    return true;
  }
  
  private int getHashCode(char[] text, int offset, int len) {
    if (text == null)
      throw new NullPointerException();
    int code = 0;
    final int stop = offset + len;
    if (ignoreCase) {
      for (int i=offset; i<stop;) {
        final int codePointAt = charUtils.codePointAt(text, i, stop);
        code = code*31 + Character.toLowerCase(codePointAt);
        i += Character.charCount(codePointAt);
      }
    } else {
      for (int i=offset; i<stop; i++) {
        code = code*31 + text[i];
      }
    }
    return code;
  }

  private int getHashCode(CharSequence text) {
    if (text == null)
      throw new NullPointerException();
    int code = 0;
    int len = text.length();
    if (ignoreCase) {
      for (int i=0; i<len;) {
        int codePointAt = charUtils.codePointAt(text, i);
        code = code*31 + Character.toLowerCase(codePointAt);
        i += Character.charCount(codePointAt);
      }
    } else {
      for (int i=0; i<len; i++) {
        code = code*31 + text.charAt(i);
      }
    }
    return code;
  }

  @Override
  public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return count;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("{");
    for (Map.Entry<Object,V> entry : entrySet()) {
      if (sb.length()>1) sb.append(", ");
      sb.append(entry);
    }
    return sb.append('}').toString();
  }

  private EntrySet entrySet = null;
  private CharArraySet keySet = null;
  
  EntrySet createEntrySet() {
    return new EntrySet(true);
  }
  
  @Override
  public final EntrySet entrySet() {
    if (entrySet == null) {
      entrySet = createEntrySet();
    }
    return entrySet;
  }
  
  // helper for CharArraySet to not produce endless recursion
  final Set<Object> originalKeySet() {
    return super.keySet();
  }

  @Override @SuppressWarnings({"unchecked","rawtypes"})
  public final CharArraySet keySet() {
    if (keySet == null) {
      // prevent adding of entries
      keySet = new CharArraySet((CharArrayMap<Object>) this) {
        @Override
        public boolean add(Object o) {
          throw new UnsupportedOperationException();
        }
        @Override
        public boolean add(CharSequence text) {
          throw new UnsupportedOperationException();
        }
        @Override
        public boolean add(String text) {
          throw new UnsupportedOperationException();
        }
        @Override
        public boolean add(char[] text) {
          throw new UnsupportedOperationException();
        }
      };
    }
    return keySet;
  }

  public class EntryIterator implements Iterator<Map.Entry<Object,V>> {
    private int pos=-1;
    private int lastPos;
    private final boolean allowModify;
    
    private EntryIterator(boolean allowModify) {
      this.allowModify = allowModify;
      goNext();
    }

    private void goNext() {
      lastPos = pos;
      pos++;
      while (pos < keys.length && keys[pos] == null) pos++;
    }

    public boolean hasNext() {
      return pos < keys.length;
    }

    public char[] nextKey() {
      goNext();
      return keys[lastPos];
    }

    public String nextKeyString() {
      return new String(nextKey());
    }

    public V currentValue() {
      return values[lastPos];
    }

    public V setValue(V value) {
      if (!allowModify)
        throw new UnsupportedOperationException();
      V old = values[lastPos];
      values[lastPos] = value;
      return old;      
    }

    public Map.Entry<Object,V> next() {
      goNext();
      return new MapEntry(lastPos, allowModify);
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private final class MapEntry implements Map.Entry<Object,V> {
    private final int pos;
    private final boolean allowModify;

    private MapEntry(int pos, boolean allowModify) {
      this.pos = pos;
      this.allowModify = allowModify;
    }

    public Object getKey() {
      // we must clone here, as putAll to another CharArrayMap
      // with other case sensitivity flag would corrupt the keys
      return keys[pos].clone();
    }

    public V getValue() {
      return values[pos];
    }

    public V setValue(V value) {
      if (!allowModify)
        throw new UnsupportedOperationException();
      final V old = values[pos];
      values[pos] = value;
      return old;
    }

    @Override
    public String toString() {
      return new StringBuilder().append(keys[pos]).append('=')
        .append((values[pos] == CharArrayMap.this) ? "(this Map)" : values[pos])
        .toString();
    }
  }

  public final class EntrySet extends AbstractSet<Map.Entry<Object,V>> {
    private final boolean allowModify;
    
    private EntrySet(boolean allowModify) {
      this.allowModify = allowModify;
    }
  
    @Override
    public EntryIterator iterator() {
      return new EntryIterator(allowModify);
    }
    
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry))
        return false;
      final Map.Entry<Object,V> e = (Map.Entry<Object,V>)o;
      final Object key = e.getKey();
      final Object val = e.getValue();
      final Object v = get(key);
      return v == null ? val == null : v.equals(val);
    }
    
    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public int size() {
      return count;
    }
    
    @Override
    public void clear() {
      if (!allowModify)
        throw new UnsupportedOperationException();
      CharArrayMap.this.clear();
    }
  }
  

  public static <V> CharArrayMap<V> unmodifiableMap(CharArrayMap<V> map) {
    if (map == null)
      throw new NullPointerException("Given map is null");
    if (map == emptyMap() || map.isEmpty())
      return emptyMap();
    if (map instanceof UnmodifiableCharArrayMap)
      return map;
    return new UnmodifiableCharArrayMap<V>(map);
  }


  @SuppressWarnings("unchecked")
  public static <V> CharArrayMap<V> copy(final Version matchVersion, final Map<?,? extends V> map) {
    if(map == EMPTY_MAP)
      return emptyMap();
    if(map instanceof CharArrayMap) {
      CharArrayMap<V> m = (CharArrayMap<V>) map;
      // use fast path instead of iterating all values
      // this is even on very small sets ~10 times faster than iterating
      final char[][] keys = new char[m.keys.length][];
      System.arraycopy(m.keys, 0, keys, 0, keys.length);
      final V[] values = (V[]) new Object[m.values.length];
      System.arraycopy(m.values, 0, values, 0, values.length);
      m = new CharArrayMap<V>(m);
      m.keys = keys;
      m.values = values;
      return m;
    }
    return new CharArrayMap<V>(matchVersion, map, false);
  }
  
  @SuppressWarnings("unchecked")
  public static <V> CharArrayMap<V> emptyMap() {
    return (CharArrayMap<V>) EMPTY_MAP;
  }
  
  // package private CharArraySet instanceof check in CharArraySet
  static class UnmodifiableCharArrayMap<V> extends CharArrayMap<V> {

    UnmodifiableCharArrayMap(CharArrayMap<V> map) {
      super(map);
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public V put(Object o, V val){
      throw new UnsupportedOperationException();
    }
    
    @Override
    public V put(char[] text, V val) {
      throw new UnsupportedOperationException();
    }

    @Override
    public V put(CharSequence text, V val) {
      throw new UnsupportedOperationException();
    }

    @Override
    public V put(String text, V val) {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public V remove(Object key) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    EntrySet createEntrySet() {
      return new EntrySet(false);
    }
  }
  

  private static final class EmptyCharArrayMap<V> extends UnmodifiableCharArrayMap<V> {
    EmptyCharArrayMap() {
      super(new CharArrayMap<V>(Version.LUCENE_CURRENT, 0, false));
    }
    
    @Override
    public boolean containsKey(char[] text, int off, int len) {
      if(text == null)
        throw new NullPointerException();
      return false;
    }

    @Override
    public boolean containsKey(CharSequence cs) {
      if(cs == null)
        throw new NullPointerException();
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      if(o == null)
        throw new NullPointerException();
      return false;
    }
    
    @Override
    public V get(char[] text, int off, int len) {
      if(text == null)
        throw new NullPointerException();
      return null;
    }

    @Override
    public V get(CharSequence cs) {
      if(cs == null)
        throw new NullPointerException();
      return null;
    }

    @Override
    public V get(Object o) {
      if(o == null)
        throw new NullPointerException();
      return null;
    }
  }
}
