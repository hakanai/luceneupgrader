/*
 * dk.brics.automaton
 *
 * Copyright (c) 2001-2009 Anders Moeller
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.hppc.BitMixer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.hppc.IntCursor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.hppc.IntHashSet;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.hppc.IntObjectHashMap;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRefBuilder;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.FixedBitSet;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IntsRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.IntsRefBuilder;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;

/**
 * Automata operations.
 *
 * @lucene.experimental
 */
public final class Operations {
  /**
   * Default maximum effort that {@link Operations#determinize} should spend before giving up and
   * throwing {@link TooComplexToDeterminizeException}.
   */
  public static final int DEFAULT_DETERMINIZE_WORK_LIMIT = 10000;

  /** Maximum level of recursion allowed in recursive operations. */
  public static final int MAX_RECURSION_LEVEL = 1000;

  private Operations() {}

  /**
   * Returns an automaton that accepts the concatenation of the languages of the given automata.
   *
   * <p>Complexity: linear in total number of states.
   */
  public static Automaton concatenate(Automaton a1, Automaton a2) {
    return concatenate(Arrays.asList(a1, a2));
  }

  /**
   * Returns an automaton that accepts the concatenation of the languages of the given automata.
   *
   * <p>Complexity: linear in total number of states.
   */
  public static Automaton concatenate(List<Automaton> l) {
    Automaton result = new Automaton();

    // First pass: create all states
    for (Automaton a : l) {
      if (a.getNumStates() == 0) {
        result.finishState();
        return result;
      }
      int numStates = a.getNumStates();
      for (int s = 0; s < numStates; s++) {
        result.createState();
      }
    }

    // Second pass: add transitions, carefully linking accept
    // states of A to init state of next A:
    int stateOffset = 0;
    Transition t = new Transition();
    for (int i = 0; i < l.size(); i++) {
      Automaton a = l.get(i);
      int numStates = a.getNumStates();

      Automaton nextA = (i == l.size() - 1) ? null : l.get(i + 1);

      for (int s = 0; s < numStates; s++) {
        int numTransitions = a.initTransition(s, t);
        for (int j = 0; j < numTransitions; j++) {
          a.getNextTransition(t);
          result.addTransition(stateOffset + s, stateOffset + t.dest, t.min, t.max);
        }

        if (a.isAccept(s)) {
          Automaton followA = nextA;
          int followOffset = stateOffset;
          int upto = i + 1;
          while (true) {
            if (followA != null) {
              // Adds a "virtual" epsilon transition:
              numTransitions = followA.initTransition(0, t);
              for (int j = 0; j < numTransitions; j++) {
                followA.getNextTransition(t);
                result.addTransition(
                    stateOffset + s, followOffset + numStates + t.dest, t.min, t.max);
              }
              if (followA.isAccept(0)) {
                // Keep chaining if followA accepts empty string
                followOffset += followA.getNumStates();
                followA = (upto == l.size() - 1) ? null : l.get(upto + 1);
                upto++;
              } else {
                break;
              }
            } else {
              result.setAccept(stateOffset + s, true);
              break;
            }
          }
        }
      }

      stateOffset += numStates;
    }

    if (result.getNumStates() == 0) {
      result.createState();
    }

    result.finishState();

    return result;
  }

  /**
   * Returns an automaton that accepts the union of the empty string and the language of the given
   * automaton. This may create a dead state.
   *
   * <p>Complexity: linear in number of states.
   */
  public static Automaton optional(Automaton a) {
    Automaton result = new Automaton();
    result.createState();
    result.setAccept(0, true);
    if (a.getNumStates() > 0) {
      result.copy(a);
      result.addEpsilon(0, 1);
    }
    result.finishState();
    return result;
  }

  /**
   * Returns an automaton that accepts the Kleene star (zero or more concatenated repetitions) of
   * the language of the given automaton. Never modifies the input automaton language.
   *
   * <p>Complexity: linear in number of states.
   */
  public static Automaton repeat(Automaton a) {
    if (a.getNumStates() == 0) {
      // Repeating the empty automata will still only accept the empty automata.
      return a;
    }
    Automaton.Builder builder = new Automaton.Builder();
    builder.createState();
    builder.setAccept(0, true);
    builder.copy(a);

    Transition t = new Transition();
    int count = a.initTransition(0, t);
    for (int i = 0; i < count; i++) {
      a.getNextTransition(t);
      builder.addTransition(0, t.dest + 1, t.min, t.max);
    }

    int numStates = a.getNumStates();
    for (int s = 0; s < numStates; s++) {
      if (a.isAccept(s)) {
        count = a.initTransition(0, t);
        for (int i = 0; i < count; i++) {
          a.getNextTransition(t);
          builder.addTransition(s + 1, t.dest + 1, t.min, t.max);
        }
      }
    }

    return builder.finish();
  }

  /**
   * Returns an automaton that accepts <code>min</code> or more concatenated repetitions of the
   * language of the given automaton.
   *
   * <p>Complexity: linear in number of states and in <code>min</code>.
   */
  public static Automaton repeat(Automaton a, int count) {
    if (count == 0) {
      return repeat(a);
    }
    List<Automaton> as = new ArrayList<>();
    while (count-- > 0) {
      as.add(a);
    }
    as.add(repeat(a));
    return concatenate(as);
  }

  /**
   * Returns an automaton that accepts between <code>min</code> and <code>max</code> (including
   * both) concatenated repetitions of the language of the given automaton.
   *
   * <p>Complexity: linear in number of states and in <code>min</code> and <code>max</code>.
   */
  public static Automaton repeat(Automaton a, int min, int max) {
    if (min > max) {
      return Automata.makeEmpty();
    }

    Automaton b;
    if (min == 0) {
      b = Automata.makeEmptyString();
    } else if (min == 1) {
      b = new Automaton();
      b.copy(a);
    } else {
      List<Automaton> as = new ArrayList<>();
      for (int i = 0; i < min; i++) {
        as.add(a);
      }
      b = concatenate(as);
    }

    IntHashSet prevAcceptStates = toSet(b, 0);
    Automaton.Builder builder = new Automaton.Builder();
    builder.copy(b);
    for (int i = min; i < max; i++) {
      int numStates = builder.getNumStates();
      builder.copy(a);
      for (IntCursor s : prevAcceptStates) {
        builder.addEpsilon(s.value, numStates);
      }
      prevAcceptStates = toSet(a, numStates);
    }

    return builder.finish();
  }

  private static IntHashSet toSet(Automaton a, int offset) {
    int numStates = a.getNumStates();
    BitSet isAccept = a.getAcceptStates();
    IntHashSet result = new IntHashSet();
    int upto = 0;
    while (upto < numStates && (upto = isAccept.nextSetBit(upto)) != -1) {
      result.add(offset + upto);
      upto++;
    }
    return result;
  }

  /**
   * Returns a (deterministic) automaton that accepts the complement of the language of the given
   * automaton.
   *
   * <p>Complexity: linear in number of states if already deterministic and exponential otherwise.
   *
   * @param determinizeWorkLimit maximum effort to spend determinizing the automaton. Set higher to
   *     allow more complex queries and lower to prevent memory exhaustion. {@link
   *     #DEFAULT_DETERMINIZE_WORK_LIMIT} is a good starting default.
   */
  public static Automaton complement(Automaton a, int determinizeWorkLimit) {
    a = totalize(determinize(a, determinizeWorkLimit));
    int numStates = a.getNumStates();
    for (int p = 0; p < numStates; p++) {
      a.setAccept(p, !a.isAccept(p));
    }
    return removeDeadStates(a);
  }

  /**
   * Returns a (deterministic) automaton that accepts the intersection of the language of <code>a1
   * </code> and the complement of the language of <code>a2</code>. As a side-effect, the automata
   * may be determinized, if not already deterministic.
   *
   * <p>Complexity: quadratic in number of states if a2 already deterministic and exponential in
   * number of a2's states otherwise.
   *
   * @param a1 the initial automaton
   * @param a2 the automaton to subtract
   * @param determinizeWorkLimit maximum effort to spend determinizing the automaton. Set higher to
   *     allow more complex queries and lower to prevent memory exhaustion. {@link
   *     #DEFAULT_DETERMINIZE_WORK_LIMIT} is a good starting default.
   */
  public static Automaton minus(Automaton a1, Automaton a2, int determinizeWorkLimit) {
    if (Operations.isEmpty(a1) || a1 == a2) {
      return Automata.makeEmpty();
    }
    if (Operations.isEmpty(a2)) {
      return a1;
    }
    return intersection(a1, complement(a2, determinizeWorkLimit));
  }

  /**
   * Returns an automaton that accepts the intersection of the languages of the given automata.
   * Never modifies the input automata languages.
   *
   * <p>Complexity: quadratic in number of states.
   */
  public static Automaton intersection(Automaton a1, Automaton a2) {
    if (a1 == a2) {
      return a1;
    }
    if (a1.getNumStates() == 0) {
      return a1;
    }
    if (a2.getNumStates() == 0) {
      return a2;
    }
    Transition[][] transitions1 = a1.getSortedTransitions();
    Transition[][] transitions2 = a2.getSortedTransitions();
    Automaton c = new Automaton();
    c.createState();
    ArrayDeque<StatePair> worklist = new ArrayDeque<>();
    HashMap<StatePair, StatePair> newstates = new HashMap<>();
    StatePair p = new StatePair(0, 0, 0);
    worklist.add(p);
    newstates.put(p, p);
    while (worklist.size() > 0) {
      p = worklist.removeFirst();
      c.setAccept(p.s, a1.isAccept(p.s1) && a2.isAccept(p.s2));
      Transition[] t1 = transitions1[p.s1];
      Transition[] t2 = transitions2[p.s2];
      for (int n1 = 0, b2 = 0; n1 < t1.length; n1++) {
        while (b2 < t2.length && t2[b2].max < t1[n1].min) b2++;
        for (int n2 = b2; n2 < t2.length && t1[n1].max >= t2[n2].min; n2++)
          if (t2[n2].max >= t1[n1].min) {
            StatePair q = new StatePair(t1[n1].dest, t2[n2].dest);
            StatePair r = newstates.get(q);
            if (r == null) {
              q.s = c.createState();
              worklist.add(q);
              newstates.put(q, q);
              r = q;
            }
            int min = t1[n1].min > t2[n2].min ? t1[n1].min : t2[n2].min;
            int max = t1[n1].max < t2[n2].max ? t1[n1].max : t2[n2].max;
            c.addTransition(p.s, r.s, min, max);
          }
      }
    }
    c.finishState();

    return removeDeadStates(c);
  }

  /**
   * Returns true if these two automata accept exactly the same language. This is a costly
   * computation! Both automata must be determinized and have no dead states!
   */
  public static boolean sameLanguage(Automaton a1, Automaton a2) {
    if (a1 == a2) {
      return true;
    }
    return subsetOf(a2, a1) && subsetOf(a1, a2);
  }

  // TODO: move to test-framework?
  /**
   * Returns true if this automaton has any states that cannot be reached from the initial state or
   * cannot reach an accept state. Cost is O(numTransitions+numStates).
   */
  public static boolean hasDeadStates(Automaton a) {
    BitSet liveStates = getLiveStates(a);
    int numLive = liveStates.cardinality();
    int numStates = a.getNumStates();
    assert numLive <= numStates
        : "numLive=" + numLive + " numStates=" + numStates + " " + liveStates;
    return numLive < numStates;
  }

  // TODO: move to test-framework?
  /** Returns true if there are dead states reachable from an initial state. */
  public static boolean hasDeadStatesFromInitial(Automaton a) {
    BitSet reachableFromInitial = getLiveStatesFromInitial(a);
    BitSet reachableFromAccept = getLiveStatesToAccept(a);
    reachableFromInitial.andNot(reachableFromAccept);
    return reachableFromInitial.isEmpty() == false;
  }

  // TODO: move to test-framework?
  /** Returns true if there are dead states that reach an accept state. */
  public static boolean hasDeadStatesToAccept(Automaton a) {
    BitSet reachableFromInitial = getLiveStatesFromInitial(a);
    BitSet reachableFromAccept = getLiveStatesToAccept(a);
    reachableFromAccept.andNot(reachableFromInitial);
    return reachableFromAccept.isEmpty() == false;
  }

  /**
   * Returns true if the language of <code>a1</code> is a subset of the language of <code>a2</code>.
   * Both automata must be determinized and must have no dead states.
   *
   * <p>Complexity: quadratic in number of states.
   */
  public static boolean subsetOf(Automaton a1, Automaton a2) {
    if (a1.isDeterministic() == false) {
      throw new IllegalArgumentException("a1 must be deterministic");
    }
    if (a2.isDeterministic() == false) {
      throw new IllegalArgumentException("a2 must be deterministic");
    }
    assert hasDeadStatesFromInitial(a1) == false;
    assert hasDeadStatesFromInitial(a2) == false;
    if (a1.getNumStates() == 0) {
      // Empty language is alwyas a subset of any other language
      return true;
    } else if (a2.getNumStates() == 0) {
      return isEmpty(a1);
    }

    // TODO: cutover to iterators instead
    Transition[][] transitions1 = a1.getSortedTransitions();
    Transition[][] transitions2 = a2.getSortedTransitions();
    ArrayDeque<StatePair> worklist = new ArrayDeque<>();
    HashSet<StatePair> visited = new HashSet<>();
    StatePair p = new StatePair(0, 0);
    worklist.add(p);
    visited.add(p);
    while (worklist.size() > 0) {
      p = worklist.removeFirst();
      if (a1.isAccept(p.s1) && a2.isAccept(p.s2) == false) {
        return false;
      }
      Transition[] t1 = transitions1[p.s1];
      Transition[] t2 = transitions2[p.s2];
      for (int n1 = 0, b2 = 0; n1 < t1.length; n1++) {
        while (b2 < t2.length && t2[b2].max < t1[n1].min) {
          b2++;
        }
        int min1 = t1[n1].min, max1 = t1[n1].max;

        for (int n2 = b2; n2 < t2.length && t1[n1].max >= t2[n2].min; n2++) {
          if (t2[n2].min > min1) {
            return false;
          }
          if (t2[n2].max < Character.MAX_CODE_POINT) {
            min1 = t2[n2].max + 1;
          } else {
            min1 = Character.MAX_CODE_POINT;
            max1 = Character.MIN_CODE_POINT;
          }
          StatePair q = new StatePair(t1[n1].dest, t2[n2].dest);
          if (!visited.contains(q)) {
            worklist.add(q);
            visited.add(q);
          }
        }
        if (min1 <= max1) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns an automaton that accepts the union of the languages of the given automata.
   *
   * <p>Complexity: linear in number of states.
   */
  public static Automaton union(Automaton a1, Automaton a2) {
    return union(Arrays.asList(a1, a2));
  }

  /**
   * Returns an automaton that accepts the union of the languages of the given automata.
   *
   * <p>Complexity: linear in number of states.
   */
  public static Automaton union(Collection<Automaton> l) {
    Automaton result = new Automaton();

    // Create initial state:
    result.createState();

    // Copy over all automata
    for (Automaton a : l) {
      result.copy(a);
    }

    // Add epsilon transition from new initial state
    int stateOffset = 1;
    for (Automaton a : l) {
      if (a.getNumStates() == 0) {
        continue;
      }
      result.addEpsilon(0, stateOffset);
      stateOffset += a.getNumStates();
    }

    result.finishState();

    return removeDeadStates(result);
  }

  // Simple custom ArrayList<Transition>
  private static final class TransitionList {
    // dest, min, max
    int[] transitions = new int[3];
    int next;

    public void add(Transition t) {
      if (transitions.length < next + 3) {
        transitions = ArrayUtil.grow(transitions, next + 3);
      }
      transitions[next] = t.dest;
      transitions[next + 1] = t.min;
      transitions[next + 2] = t.max;
      next += 3;
    }
  }

  // Holds all transitions that start on this int point, or
  // end at this point-1
  private static final class PointTransitions implements Comparable<PointTransitions> {
    int point;
    final TransitionList ends = new TransitionList();
    final TransitionList starts = new TransitionList();

    @Override
    public int compareTo(PointTransitions other) {
      return point - other.point;
    }

    public void reset(int point) {
      this.point = point;
      ends.next = 0;
      starts.next = 0;
    }

    @Override
    public boolean equals(Object other) {
      return ((PointTransitions) other).point == point;
    }

    @Override
    public int hashCode() {
      return point;
    }
  }

  private static final class PointTransitionSet {
    int count;
    PointTransitions[] points = new PointTransitions[5];

    private static final int HASHMAP_CUTOVER = 30;
    private final IntObjectHashMap<PointTransitions> map = new IntObjectHashMap<>();
    private boolean useHash = false;

    private PointTransitions next(int point) {
      // 1st time we are seeing this point
      if (count == points.length) {
        final PointTransitions[] newArray =
            new PointTransitions
                [ArrayUtil.oversize(1 + count, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
        System.arraycopy(points, 0, newArray, 0, count);
        points = newArray;
      }
      PointTransitions points0 = points[count];
      if (points0 == null) {
        points0 = points[count] = new PointTransitions();
      }
      points0.reset(point);
      count++;
      return points0;
    }

    private PointTransitions find(int point) {
      if (useHash) {
        final Integer pi = point;
        PointTransitions p = map.get(pi);
        if (p == null) {
          p = next(point);
          map.put(pi, p);
        }
        return p;
      } else {
        for (int i = 0; i < count; i++) {
          if (points[i].point == point) {
            return points[i];
          }
        }

        final PointTransitions p = next(point);
        if (count == HASHMAP_CUTOVER) {
          // switch to HashMap on the fly
          assert map.size() == 0;
          for (int i = 0; i < count; i++) {
            map.put(points[i].point, points[i]);
          }
          useHash = true;
        }
        return p;
      }
    }

    public void reset() {
      if (useHash) {
        map.clear();
        useHash = false;
      }
      count = 0;
    }

    public void sort() {
      // Tim sort performs well on already sorted arrays:
      if (count > 1) ArrayUtil.timSort(points, 0, count);
    }

    public void add(Transition t) {
      find(t.min).starts.add(t);
      find(1 + t.max).ends.add(t);
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < count; i++) {
        if (i > 0) {
          s.append(' ');
        }
        s.append(points[i].point)
            .append(':')
            .append(points[i].starts.next / 3)
            .append(',')
            .append(points[i].ends.next / 3);
      }
      return s.toString();
    }
  }

  /**
   * Determinizes the given automaton.
   *
   * <p>Worst case complexity: exponential in number of states.
   *
   * @param workLimit Maximum amount of "work" that the powerset construction will spend before
   *     throwing {@link TooComplexToDeterminizeException}. Higher numbers allow this operation to
   *     consume more memory and CPU but allow more complex automatons. Use {@link
   *     #DEFAULT_DETERMINIZE_WORK_LIMIT} as a decent default if you don't otherwise know what to
   *     specify.
   * @throws TooComplexToDeterminizeException if determinizing requires more than {@code workLimit}
   *     "effort"
   */
  public static Automaton determinize(Automaton a, int workLimit) {
    if (a.isDeterministic()) {
      // Already determinized
      return a;
    }
    if (a.getNumStates() <= 1) {
      // Already determinized
      return a;
    }

    // subset construction
    Automaton.Builder b = new Automaton.Builder();

    // System.out.println("DET:");
    // a.writeDot("/l/la/lucene/core/detin.dot");

    // Same initial values and state will always have the same hashCode
    FrozenIntSet initialset = new FrozenIntSet(new int[] {0}, BitMixer.mix(0) + 1, 0);

    // Create state 0:
    b.createState();

    ArrayDeque<FrozenIntSet> worklist = new ArrayDeque<>();
    Map<IntSet, Integer> newstate = new HashMap<>();

    worklist.add(initialset);

    b.setAccept(0, a.isAccept(0));
    newstate.put(initialset, 0);

    // like Set<Integer,PointTransitions>
    final PointTransitionSet points = new PointTransitionSet();

    // like HashMap<Integer,Integer>, maps state to its count
    final StateSet statesSet = new StateSet(5);

    Transition t = new Transition();

    long effortSpent = 0;

    // LUCENE-9981: approximate conversion from what used to be a limit on number of states, to
    // maximum "effort":
    long effortLimit = workLimit * (long) 10;

    while (worklist.size() > 0) {
      // TODO (LUCENE-9983): these int sets really do not need to be sorted, and we are paying
      // a high (unecessary) price for that!  really we just need a low-overhead Map<int,int>
      // that implements equals/hash based only on the keys (ignores the values).  fixing this
      // might be a bigspeedup for determinizing complex automata
      FrozenIntSet s = worklist.removeFirst();

      // LUCENE-9981: we more carefully aggregate the net work this automaton is costing us, instead
      // of (overly simplistically) counting number
      // of determinized states:
      effortSpent += s.values.length;
      if (effortSpent >= effortLimit) {
        throw new TooComplexToDeterminizeException(a, workLimit);
      }

      // Collate all outgoing transitions by min/1+max:
      for (int i = 0; i < s.values.length; i++) {
        final int s0 = s.values[i];
        int numTransitions = a.getNumTransitions(s0);
        a.initTransition(s0, t);
        for (int j = 0; j < numTransitions; j++) {
          a.getNextTransition(t);
          points.add(t);
        }
      }

      if (points.count == 0) {
        // No outgoing transitions -- skip it
        continue;
      }

      points.sort();

      int lastPoint = -1;
      int accCount = 0;

      final int r = s.state;

      for (int i = 0; i < points.count; i++) {

        final int point = points.points[i].point;

        if (statesSet.size() > 0) {
          assert lastPoint != -1;

          Integer q = newstate.get(statesSet);
          if (q == null) {
            q = b.createState();
            final FrozenIntSet p = statesSet.freeze(q);
            // System.out.println("  make new state=" + q + " -> " + p + " accCount=" + accCount);
            worklist.add(p);
            b.setAccept(q, accCount > 0);
            newstate.put(p, q);
          } else {
            assert (accCount > 0 ? true : false) == b.isAccept(q)
                : "accCount="
                    + accCount
                    + " vs existing accept="
                    + b.isAccept(q)
                    + " states="
                    + statesSet;
          }

          // System.out.println("  add trans src=" + r + " dest=" + q + " min=" + lastPoint + "
          // max=" + (point-1));

          b.addTransition(r, q, lastPoint, point - 1);
        }

        // process transitions that end on this point
        // (closes an overlapping interval)
        int[] transitions = points.points[i].ends.transitions;
        int limit = points.points[i].ends.next;
        for (int j = 0; j < limit; j += 3) {
          int dest = transitions[j];
          statesSet.decr(dest);
          accCount -= a.isAccept(dest) ? 1 : 0;
        }
        points.points[i].ends.next = 0;

        // process transitions that start on this point
        // (opens a new interval)
        transitions = points.points[i].starts.transitions;
        limit = points.points[i].starts.next;
        for (int j = 0; j < limit; j += 3) {
          int dest = transitions[j];
          statesSet.incr(dest);
          accCount += a.isAccept(dest) ? 1 : 0;
        }
        lastPoint = point;
        points.points[i].starts.next = 0;
      }
      points.reset();
      assert statesSet.size() == 0 : "size=" + statesSet.size();
    }

    Automaton result = b.finish();
    assert result.isDeterministic();
    return result;
  }

  /** Returns true if the given automaton accepts no strings. */
  public static boolean isEmpty(Automaton a) {
    if (a.getNumStates() == 0) {
      // Common case: no states
      return true;
    }
    if (a.isAccept(0) == false && a.getNumTransitions(0) == 0) {
      // Common case: just one initial state
      return true;
    }
    if (a.isAccept(0) == true) {
      // Apparently common case: it accepts the damned empty string
      return false;
    }

    ArrayDeque<Integer> workList = new ArrayDeque<>();
    BitSet seen = new BitSet(a.getNumStates());
    workList.add(0);
    seen.set(0);

    Transition t = new Transition();
    while (workList.isEmpty() == false) {
      int state = workList.removeFirst();
      if (a.isAccept(state)) {
        return false;
      }
      int count = a.initTransition(state, t);
      for (int i = 0; i < count; i++) {
        a.getNextTransition(t);
        if (seen.get(t.dest) == false) {
          workList.add(t.dest);
          seen.set(t.dest);
        }
      }
    }

    return true;
  }

  /** Returns true if the given automaton accepts all strings. The automaton must be minimized. */
  public static boolean isTotal(Automaton a) {
    return isTotal(a, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
  }

  /**
   * Returns true if the given automaton accepts all strings for the specified min/max range of the
   * alphabet. The automaton must be minimized.
   */
  public static boolean isTotal(Automaton a, int minAlphabet, int maxAlphabet) {
    if (a.isAccept(0) && a.getNumTransitions(0) == 1) {
      Transition t = new Transition();
      a.getTransition(0, 0, t);
      return t.dest == 0 && t.min == minAlphabet && t.max == maxAlphabet;
    }
    return false;
  }

  /**
   * Returns true if the given string is accepted by the automaton. The input must be deterministic.
   *
   * <p>Complexity: linear in the length of the string.
   *
   * <p><b>Note:</b> for full performance, use the {@link RunAutomaton} class.
   */
  public static boolean run(Automaton a, String s) {
    assert a.isDeterministic();
    int state = 0;
    for (int i = 0, cp = 0; i < s.length(); i += Character.charCount(cp)) {
      int nextState = a.step(state, cp = s.codePointAt(i));
      if (nextState == -1) {
        return false;
      }
      state = nextState;
    }
    return a.isAccept(state);
  }

  /**
   * Returns true if the given string (expressed as unicode codepoints) is accepted by the
   * automaton. The input must be deterministic.
   *
   * <p>Complexity: linear in the length of the string.
   *
   * <p><b>Note:</b> for full performance, use the {@link RunAutomaton} class.
   */
  public static boolean run(Automaton a, IntsRef s) {
    assert a.isDeterministic();
    int state = 0;
    for (int i = 0; i < s.length; i++) {
      int nextState = a.step(state, s.ints[s.offset + i]);
      if (nextState == -1) {
        return false;
      }
      state = nextState;
    }
    return a.isAccept(state);
  }

  /**
   * Returns the set of live states. A state is "live" if an accept state is reachable from it and
   * if it is reachable from the initial state.
   */
  private static BitSet getLiveStates(Automaton a) {
    BitSet live = getLiveStatesFromInitial(a);
    live.and(getLiveStatesToAccept(a));
    return live;
  }

  /** Returns bitset marking states reachable from the initial state. */
  private static BitSet getLiveStatesFromInitial(Automaton a) {
    int numStates = a.getNumStates();
    BitSet live = new BitSet(numStates);
    if (numStates == 0) {
      return live;
    }
    ArrayDeque<Integer> workList = new ArrayDeque<>();
    live.set(0);
    workList.add(0);

    Transition t = new Transition();
    while (workList.isEmpty() == false) {
      int s = workList.removeFirst();
      int count = a.initTransition(s, t);
      for (int i = 0; i < count; i++) {
        a.getNextTransition(t);
        if (live.get(t.dest) == false) {
          live.set(t.dest);
          workList.add(t.dest);
        }
      }
    }

    return live;
  }

  /** Returns bitset marking states that can reach an accept state. */
  private static BitSet getLiveStatesToAccept(Automaton a) {
    Automaton.Builder builder = new Automaton.Builder();

    // NOTE: not quite the same thing as what SpecialOperations.reverse does:
    Transition t = new Transition();
    int numStates = a.getNumStates();
    for (int s = 0; s < numStates; s++) {
      builder.createState();
    }
    for (int s = 0; s < numStates; s++) {
      int count = a.initTransition(s, t);
      for (int i = 0; i < count; i++) {
        a.getNextTransition(t);
        builder.addTransition(t.dest, s, t.min, t.max);
      }
    }
    Automaton a2 = builder.finish();

    ArrayDeque<Integer> workList = new ArrayDeque<>();
    BitSet live = new BitSet(numStates);
    BitSet acceptBits = a.getAcceptStates();
    int s = 0;
    while (s < numStates && (s = acceptBits.nextSetBit(s)) != -1) {
      live.set(s);
      workList.add(s);
      s++;
    }

    while (workList.isEmpty() == false) {
      s = workList.removeFirst();
      int count = a2.initTransition(s, t);
      for (int i = 0; i < count; i++) {
        a2.getNextTransition(t);
        if (live.get(t.dest) == false) {
          live.set(t.dest);
          workList.add(t.dest);
        }
      }
    }

    return live;
  }

  /**
   * Removes transitions to dead states (a state is "dead" if it is not reachable from the initial
   * state or no accept state is reachable from it.)
   */
  public static Automaton removeDeadStates(Automaton a) {
    int numStates = a.getNumStates();
    BitSet liveSet = getLiveStates(a);

    int[] map = new int[numStates];

    Automaton result = new Automaton();
    // System.out.println("liveSet: " + liveSet + " numStates=" + numStates);
    for (int i = 0; i < numStates; i++) {
      if (liveSet.get(i)) {
        map[i] = result.createState();
        result.setAccept(map[i], a.isAccept(i));
      }
    }

    Transition t = new Transition();

    for (int i = 0; i < numStates; i++) {
      if (liveSet.get(i)) {
        int numTransitions = a.initTransition(i, t);
        // filter out transitions to dead states:
        for (int j = 0; j < numTransitions; j++) {
          a.getNextTransition(t);
          if (liveSet.get(t.dest)) {
            result.addTransition(map[i], map[t.dest], t.min, t.max);
          }
        }
      }
    }

    result.finishState();
    assert hasDeadStates(result) == false;
    return result;
  }

  /**
   * Returns true if the language of this automaton is finite. The automaton must not have any dead
   * states.
   */
  public static boolean isFinite(Automaton a) {
    if (a.getNumStates() == 0) {
      return true;
    }
    return isFinite(
        new Transition(), a, 0, new BitSet(a.getNumStates()), new BitSet(a.getNumStates()), 0);
  }

  /**
   * Checks whether there is a loop containing state. (This is sufficient since there are never
   * transitions to dead states.)
   */
  // TODO: not great that this is recursive... in theory a
  // large automata could exceed java's stack so the maximum level of recursion is bounded to 1000
  private static boolean isFinite(
      Transition scratch, Automaton a, int state, BitSet path, BitSet visited, int level) {
    if (level > MAX_RECURSION_LEVEL) {
      throw new IllegalArgumentException("input automaton is too large: " + level);
    }
    path.set(state);
    int numTransitions = a.initTransition(state, scratch);
    for (int t = 0; t < numTransitions; t++) {
      a.getTransition(state, t, scratch);
      if (path.get(scratch.dest)
          || (!visited.get(scratch.dest)
              && !isFinite(scratch, a, scratch.dest, path, visited, level + 1))) {
        return false;
      }
    }
    path.clear(state);
    visited.set(state);
    return true;
  }

  /**
   * Returns the longest string that is a prefix of all accepted strings and visits each state at
   * most once. The automaton must not have dead states. If this automaton has already been
   * converted to UTF-8 (e.g. using {@link UTF32ToUTF8}) then you should use {@link
   * #getCommonPrefixBytesRef} instead.
   *
   * @throws IllegalArgumentException if the automaton has dead states reachable from the initial
   *     state.
   * @return common prefix, which can be an empty (length 0) String (never null)
   */
  public static String getCommonPrefix(Automaton a) {
    if (hasDeadStatesFromInitial(a)) {
      throw new IllegalArgumentException("input automaton has dead states");
    }
    if (isEmpty(a)) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    Transition scratch = new Transition();
    FixedBitSet visited = new FixedBitSet(a.getNumStates());
    FixedBitSet current = new FixedBitSet(a.getNumStates());
    FixedBitSet next = new FixedBitSet(a.getNumStates());
    current.set(0); // start with initial state
    algorithm:
    while (true) {
      int label = -1;
      // do a pass, stepping all current paths forward once
      for (int state = current.nextSetBit(0);
          state != DocIdSetIterator.NO_MORE_DOCS;
          state =
              state + 1 >= current.length()
                  ? DocIdSetIterator.NO_MORE_DOCS
                  : current.nextSetBit(state + 1)) {
        visited.set(state);
        // if it is an accept state, we are done
        if (a.isAccept(state)) {
          break algorithm;
        }
        for (int transition = 0; transition < a.getNumTransitions(state); transition++) {
          a.getTransition(state, transition, scratch);
          if (label == -1) {
            label = scratch.min;
          }
          // either a range of labels, or label that doesn't match all the other paths this round
          if (scratch.min != scratch.max || scratch.min != label) {
            break algorithm;
          }
          // mark target state for next iteration
          next.set(scratch.dest);
        }
      }

      assert label != -1 : "we should not get here since we checked no dead-end states up front!?";

      // add the label to the prefix
      builder.appendCodePoint(label);
      // swap "current" with "next", clear "next"
      FixedBitSet tmp = current;
      current = next;
      next = tmp;
      next.clear();
    }
    return builder.toString();
  }

  /**
   * Returns the longest BytesRef that is a prefix of all accepted strings and visits each state at
   * most once.
   *
   * @return common prefix, which can be an empty (length 0) BytesRef (never null), and might
   *     possibly include a UTF-8 fragment of a full Unicode character
   */
  public static BytesRef getCommonPrefixBytesRef(Automaton a) {
    String prefix = getCommonPrefix(a);
    BytesRefBuilder builder = new BytesRefBuilder();
    for (int i = 0; i < prefix.length(); i++) {
      char ch = prefix.charAt(i);
      if (ch > 255) {
        throw new IllegalStateException("automaton is not binary");
      }
      builder.append((byte) ch);
    }

    return builder.get();
  }

  /**
   * If this automaton accepts a single input, return it. Else, return null. The automaton must be
   * deterministic.
   */
  public static IntsRef getSingleton(Automaton a) {
    if (a.isDeterministic() == false) {
      throw new IllegalArgumentException("input automaton must be deterministic");
    }
    IntsRefBuilder builder = new IntsRefBuilder();
    IntHashSet visited = new IntHashSet();
    int s = 0;
    Transition t = new Transition();
    while (true) {
      visited.add(s);
      if (a.isAccept(s) == false) {
        if (a.getNumTransitions(s) == 1) {
          a.getTransition(s, 0, t);
          if (t.min == t.max && !visited.contains(t.dest)) {
            builder.append(t.min);
            s = t.dest;
            continue;
          }
        }
      } else if (a.getNumTransitions(s) == 0) {
        return builder.get();
      }

      // Automaton accepts more than one string:
      return null;
    }
  }

  /**
   * Returns the longest BytesRef that is a suffix of all accepted strings. Worst case complexity:
   * quadratic with number of states+transitions.
   *
   * @return common suffix, which can be an empty (length 0) BytesRef (never null)
   */
  public static BytesRef getCommonSuffixBytesRef(Automaton a) {
    // reverse the language of the automaton, then reverse its common prefix.
    Automaton r = removeDeadStates(reverse(a));
    BytesRef ref = getCommonPrefixBytesRef(r);
    reverseBytes(ref);
    return ref;
  }

  private static void reverseBytes(BytesRef ref) {
    if (ref.length <= 1) return;
    int num = ref.length >> 1;
    for (int i = ref.offset; i < (ref.offset + num); i++) {
      byte b = ref.bytes[i];
      ref.bytes[i] = ref.bytes[ref.offset * 2 + ref.length - i - 1];
      ref.bytes[ref.offset * 2 + ref.length - i - 1] = b;
    }
  }

  /** Returns an automaton accepting the reverse language. */
  public static Automaton reverse(Automaton a) {
    return reverse(a, null);
  }

  /** Reverses the automaton, returning the new initial states. */
  public static Automaton reverse(Automaton a, Set<Integer> initialStates) {

    if (Operations.isEmpty(a)) {
      return new Automaton();
    }

    int numStates = a.getNumStates();

    // Build a new automaton with all edges reversed
    Automaton.Builder builder = new Automaton.Builder();

    // Initial node; we'll add epsilon transitions in the end:
    builder.createState();

    for (int s = 0; s < numStates; s++) {
      builder.createState();
    }

    // Old initial state becomes new accept state:
    builder.setAccept(1, true);

    Transition t = new Transition();
    for (int s = 0; s < numStates; s++) {
      int numTransitions = a.getNumTransitions(s);
      a.initTransition(s, t);
      for (int i = 0; i < numTransitions; i++) {
        a.getNextTransition(t);
        builder.addTransition(t.dest + 1, s + 1, t.min, t.max);
      }
    }

    Automaton result = builder.finish();

    int s = 0;
    BitSet acceptStates = a.getAcceptStates();
    while (s < numStates && (s = acceptStates.nextSetBit(s)) != -1) {
      result.addEpsilon(0, s + 1);
      if (initialStates != null) {
        initialStates.add(s + 1);
      }
      s++;
    }

    result.finishState();

    return result;
  }

  /**
   * Returns a new automaton accepting the same language with added transitions to a dead state so
   * that from every state and every label there is a transition.
   */
  static Automaton totalize(Automaton a) {
    Automaton result = new Automaton();
    int numStates = a.getNumStates();
    for (int i = 0; i < numStates; i++) {
      result.createState();
      result.setAccept(i, a.isAccept(i));
    }

    int deadState = result.createState();
    result.addTransition(deadState, deadState, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);

    Transition t = new Transition();
    for (int i = 0; i < numStates; i++) {
      int maxi = Character.MIN_CODE_POINT;
      int count = a.initTransition(i, t);
      for (int j = 0; j < count; j++) {
        a.getNextTransition(t);
        result.addTransition(i, t.dest, t.min, t.max);
        if (t.min > maxi) {
          result.addTransition(i, deadState, maxi, t.min - 1);
        }
        if (t.max + 1 > maxi) {
          maxi = t.max + 1;
        }
      }

      if (maxi <= Character.MAX_CODE_POINT) {
        result.addTransition(i, deadState, maxi, Character.MAX_CODE_POINT);
      }
    }

    result.finishState();
    return result;
  }

  /**
   * Returns the topological sort of all states reachable from the initial state. This method
   * assumes that the automaton does not contain cycles, and will throw an IllegalArgumentException
   * if a cycle is detected. The CPU cost is O(numTransitions), and the implementation is
   * non-recursive, so it will not exhaust the java stack for automaton matching long strings. If
   * there are dead states in the automaton, they will be removed from the returned array.
   *
   * <p>Note: This method uses a deque to iterative the states, which could potentially consume a
   * lot of heap space for some automatons. Specifically, automatons with a deep level of states
   * (i.e., a large number of transitions from the initial state to the final state) may
   * particularly contribute to high memory usage. The memory consumption of this method can be
   * considered as O(N), where N is the depth of the automaton (the maximum number of transitions
   * from the initial state to any state). However, as this method detects cycles, it will never
   * attempt to use infinite RAM.
   *
   * @param a the Automaton to be sorted
   * @return the topologically sorted array of state ids
   */
  public static int[] topoSortStates(Automaton a) {
    if (a.getNumStates() == 0) {
      return new int[0];
    }
    int numStates = a.getNumStates();
    int[] states = new int[numStates];
    int upto = topoSortStates(a, states);

    if (upto < states.length) {
      // There were dead states
      int[] newStates = new int[upto];
      System.arraycopy(states, 0, newStates, 0, upto);
      states = newStates;
    }

    // Reverse the order:
    for (int i = 0; i < states.length / 2; i++) {
      int s = states[i];
      states[i] = states[states.length - 1 - i];
      states[states.length - 1 - i] = s;
    }

    return states;
  }

  /**
   * Performs a topological sort on the states of the given Automaton.
   *
   * @param a The automaton whose states are to be topologically sorted.
   * @param states An int array which stores the states.
   * @return the number of states in the final sorted list.
   * @throws IllegalArgumentException if the input automaton has a cycle.
   */
  private static int topoSortStates(Automaton a, int[] states) {
    BitSet onStack = new BitSet(a.getNumStates());
    BitSet visited = new BitSet(a.getNumStates());
    var stack = new ArrayDeque<Integer>();
    stack.push(0); // Assuming that the initial state is 0.
    int upto = 0;
    Transition t = new Transition();

    while (!stack.isEmpty()) {
      int state = stack.peek(); // Just peek, don't remove the state yet

      int count = a.initTransition(state, t);
      boolean pushed = false;
      for (int i = 0; i < count; i++) {
        a.getNextTransition(t);
        if (!visited.get(t.dest)) {
          visited.set(t.dest);
          stack.push(t.dest); // Push the next unvisited state onto the stack
          onStack.set(state);
          pushed = true;
          break; // Exit the loop, we'll continue from here in the next iteration
        } else if (onStack.get(t.dest)) {
          // If the state is on the current recursion stack, we have detected a cycle
          throw new IllegalArgumentException("Input automaton has a cycle.");
        }
      }

      // If we haven't pushed any new state onto the stack, we're done with this state
      if (!pushed) {
        onStack.clear(state); // remove the node from the current recursion stack
        stack.pop();
        states[upto] = state;
        upto++;
      }
    }
    return upto;
  }
}
