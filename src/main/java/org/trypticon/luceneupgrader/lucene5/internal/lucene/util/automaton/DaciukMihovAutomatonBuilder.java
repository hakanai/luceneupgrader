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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.util.automaton;


import java.util.*;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.CharsRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.UnicodeUtil;

final class DaciukMihovAutomatonBuilder {
  private final static class State {
    
    private final static int[] NO_LABELS = new int[0];
    
    private final static State[] NO_STATES = new State[0];
    
    int[] labels = NO_LABELS;
    
    State[] states = NO_STATES;
    
    boolean is_final;
    
    State getState(int label) {
      final int index = Arrays.binarySearch(labels, label);
      return index >= 0 ? states[index] : null;
    }
    
    @Override
    public boolean equals(Object obj) {
      final State other = (State) obj;
      return is_final == other.is_final
          && Arrays.equals(this.labels, other.labels)
          && referenceEquals(this.states, other.states);
    }
    
    @Override
    public int hashCode() {
      int hash = is_final ? 1 : 0;
      
      hash ^= hash * 31 + this.labels.length;
      for (int c : this.labels)
        hash ^= hash * 31 + c;
      
      /*
       * Compare the right-language of this state using reference-identity of
       * outgoing states. This is possible because states are interned (stored
       * in registry) and traversed in post-order, so any outgoing transitions
       * are already interned.
       */
      for (State s : this.states) {
        hash ^= System.identityHashCode(s);
      }
      
      return hash;
    }
    
    boolean hasChildren() {
      return labels.length > 0;
    }

    State newState(int label) {
      assert Arrays.binarySearch(labels, label) < 0 : "State already has transition labeled: "
          + label;
      
      labels = Arrays.copyOf(labels, labels.length + 1);
      states = Arrays.copyOf(states, states.length + 1);

      labels[labels.length - 1] = label;
      return states[states.length - 1] = new State();
    }
    
    State lastChild() {
      assert hasChildren() : "No outgoing transitions.";
      return states[states.length - 1];
    }
    
    State lastChild(int label) {
      final int index = labels.length - 1;
      State s = null;
      if (index >= 0 && labels[index] == label) {
        s = states[index];
      }
      assert s == getState(label);
      return s;
    }
    
    void replaceLastChild(State state) {
      assert hasChildren() : "No outgoing transitions.";
      states[states.length - 1] = state;
    }
    
    private static boolean referenceEquals(Object[] a1, Object[] a2) {
      if (a1.length != a2.length) { 
        return false;
      }

      for (int i = 0; i < a1.length; i++) {
        if (a1[i] != a2[i]) { 
          return false;
        }
      }

      return true;
    }
  }
  
  private HashMap<State,State> stateRegistry = new HashMap<>();
  
  private State root = new State();
  
  private CharsRef previous;

  @SuppressWarnings("deprecation")
  private static final Comparator<CharsRef> comparator = CharsRef.getUTF16SortedAsUTF8Comparator();

  public void add(CharsRef current) {
    assert stateRegistry != null : "Automaton already built.";
    assert previous == null
        || comparator.compare(previous, current) <= 0 : "Input must be in sorted UTF-8 order: "
        + previous + " >= " + current;
    assert setPrevious(current);

    // Descend in the automaton (find matching prefix).
    int pos = 0, max = current.length();
    State next, state = root;
    while (pos < max && (next = state.lastChild(Character.codePointAt(current, pos))) != null) {
      state = next;
      // todo, optimize me
      pos += Character.charCount(Character.codePointAt(current, pos));
    }
    
    if (state.hasChildren()) replaceOrRegister(state);
    
    addSuffix(state, current, pos);
  }
  
  public State complete() {
    if (this.stateRegistry == null) throw new IllegalStateException();
    
    if (root.hasChildren()) replaceOrRegister(root);
    
    stateRegistry = null;
    return root;
  }
  
  private static int convert(Automaton.Builder a, State s,
      IdentityHashMap<State,Integer> visited) {

    Integer converted = visited.get(s);
    if (converted != null) {
      return converted;
    }
    
    converted = a.createState();
    a.setAccept(converted, s.is_final);
    
    visited.put(s, converted);
    int i = 0;
    int[] labels = s.labels;
    for (DaciukMihovAutomatonBuilder.State target : s.states) {
      a.addTransition(converted, convert(a, target, visited), labels[i++]);
    }
    
    return converted;
  }

  public static Automaton build(Collection<BytesRef> input) {
    final DaciukMihovAutomatonBuilder builder = new DaciukMihovAutomatonBuilder();
    
    char[] chars = new char[0];
    CharsRef ref = new CharsRef();
    for (BytesRef b : input) {
      chars = ArrayUtil.grow(chars, b.length);
      final int len = UnicodeUtil.UTF8toUTF16(b, chars);
      ref.chars = chars;
      ref.length = len;
      builder.add(ref);
    }
    
    Automaton.Builder a = new Automaton.Builder();
    convert(a,
        builder.complete(), 
        new IdentityHashMap<State,Integer>());

    return a.finish();
  }

  private boolean setPrevious(CharsRef current) {
    // don't need to copy, once we fix https://issues.apache.org/jira/browse/LUCENE-3277
    // still, called only from assert
    previous = CharsRef.deepCopyOf(current);
    return true;
  }
  
  private void replaceOrRegister(State state) {
    final State child = state.lastChild();
    
    if (child.hasChildren()) replaceOrRegister(child);
    
    final State registered = stateRegistry.get(child);
    if (registered != null) {
      state.replaceLastChild(registered);
    } else {
      stateRegistry.put(child, child);
    }
  }

  private void addSuffix(State state, CharSequence current, int fromIndex) {
    final int len = current.length();
    while (fromIndex < len) {
      int cp = Character.codePointAt(current, fromIndex);
      state = state.newState(cp);
      fromIndex += Character.charCount(cp);
    }
    state.is_final = true;
  }
}
