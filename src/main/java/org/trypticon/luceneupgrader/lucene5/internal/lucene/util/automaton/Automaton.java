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


//import java.io.IOException;
//import java.io.PrintWriter;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.InPlaceMergeSorter;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Sorter;




// TODO
//   - could use packed int arrays instead
//   - could encode dest w/ delta from to?



public class Automaton implements Accountable {


  private int nextState;


  private int nextTransition;


  private int curState = -1;


  private int[] states;

  private final BitSet isAccept;
  
  private int[] transitions;

  private boolean deterministic = true;

  public Automaton() {
     this(2, 2);
  }

  public Automaton(int numStates, int numTransitions) {
     states = new int[numStates * 2];
     isAccept = new BitSet(numStates);
     transitions = new int[numTransitions * 3];
  }

  public int createState() {
    growStates();
    int state = nextState/2;
    states[nextState] = -1;
    nextState += 2;
    return state;
  }

  public void setAccept(int state, boolean accept) {
    if (state >= getNumStates()) {
      throw new IllegalArgumentException("state=" + state + " is out of bounds (numStates=" + getNumStates() + ")");
    }
    if (accept) {
      isAccept.set(state);
    } else {
      isAccept.clear(state);
    }
  }

  public Transition[][] getSortedTransitions() {
    int numStates = getNumStates();
    Transition[][] transitions = new Transition[numStates][];
    for(int s=0;s<numStates;s++) {
      int numTransitions = getNumTransitions(s);
      transitions[s] = new Transition[numTransitions];
      for(int t=0;t<numTransitions;t++) {
        Transition transition = new Transition();
        getTransition(s, t, transition);
        transitions[s][t] = transition;
      }
    }

    return transitions;
  }

  BitSet getAcceptStates() {
    return isAccept;
  }

  public boolean isAccept(int state) {
    return isAccept.get(state);
  }

  public void addTransition(int source, int dest, int label) {
    addTransition(source, dest, label, label);
  }

  public void addTransition(int source, int dest, int min, int max) {
    assert nextTransition%3 == 0;

    if (source >= nextState/2) {
      throw new IllegalArgumentException("source=" + source + " is out of bounds (maxState is " + (nextState/2-1) + ")");
    }
    if (dest >= nextState/2) {
      throw new IllegalArgumentException("dest=" + dest + " is out of bounds (max state is " + (nextState/2-1) + ")");
    }

    growTransitions();
    if (curState != source) {
      if (curState != -1) {
        finishCurrentState();
      }

      // Move to next source:
      curState = source;
      if (states[2*curState] != -1) {
        throw new IllegalStateException("from state (" + source + ") already had transitions added");
      }
      assert states[2*curState+1] == 0;
      states[2*curState] = nextTransition;
    }

    transitions[nextTransition++] = dest;
    transitions[nextTransition++] = min;
    transitions[nextTransition++] = max;

    // Increment transition count for this state
    states[2*curState+1]++;
  }


  public void addEpsilon(int source, int dest) {
    Transition t = new Transition();
    int count = initTransition(dest, t);
    for(int i=0;i<count;i++) {
      getNextTransition(t);
      addTransition(source, t.dest, t.min, t.max);
    }
    if (isAccept(dest)) {
      setAccept(source, true);
    }
  }

  public void copy(Automaton other) {

    // Bulk copy and then fixup the state pointers:
    int stateOffset = getNumStates();
    states = ArrayUtil.grow(states, nextState + other.nextState);
    System.arraycopy(other.states, 0, states, nextState, other.nextState);
    for(int i=0;i<other.nextState;i += 2) {
      if (states[nextState+i] != -1) {
        states[nextState+i] += nextTransition;
      }
    }
    nextState += other.nextState;
    int otherNumStates = other.getNumStates();
    BitSet otherAcceptStates = other.getAcceptStates();
    int state = 0;
    while (state < otherNumStates && (state = otherAcceptStates.nextSetBit(state)) != -1) {
      setAccept(stateOffset + state, true);
      state++;
    }

    // Bulk copy and then fixup dest for each transition:
    transitions = ArrayUtil.grow(transitions, nextTransition + other.nextTransition);
    System.arraycopy(other.transitions, 0, transitions, nextTransition, other.nextTransition);
    for(int i=0;i<other.nextTransition;i += 3) {
      transitions[nextTransition+i] += stateOffset;
    }
    nextTransition += other.nextTransition;

    if (other.deterministic == false) {
      deterministic = false;
    }
  }

  private void finishCurrentState() {
    int numTransitions = states[2*curState+1];
    assert numTransitions > 0;

    int offset = states[2*curState];
    int start = offset/3;
    destMinMaxSorter.sort(start, start+numTransitions);

    // Reduce any "adjacent" transitions:
    int upto = 0;
    int min = -1;
    int max = -1;
    int dest = -1;

    for(int i=0;i<numTransitions;i++) {
      int tDest = transitions[offset+3*i];
      int tMin = transitions[offset+3*i+1];
      int tMax = transitions[offset+3*i+2];

      if (dest == tDest) {
        if (tMin <= max+1) {
          if (tMax > max) {
            max = tMax;
          }
        } else {
          if (dest != -1) {
            transitions[offset+3*upto] = dest;
            transitions[offset+3*upto+1] = min;
            transitions[offset+3*upto+2] = max;
            upto++;
          }
          min = tMin;
          max = tMax;
        }
      } else {
        if (dest != -1) {
          transitions[offset+3*upto] = dest;
          transitions[offset+3*upto+1] = min;
          transitions[offset+3*upto+2] = max;
          upto++;
        }
        dest = tDest;
        min = tMin;
        max = tMax;
      }
    }

    if (dest != -1) {
      // Last transition
      transitions[offset+3*upto] = dest;
      transitions[offset+3*upto+1] = min;
      transitions[offset+3*upto+2] = max;
      upto++;
    }

    nextTransition -= (numTransitions-upto)*3;
    states[2*curState+1] = upto;

    // Sort transitions by min/max/dest:
    minMaxDestSorter.sort(start, start+upto);

    if (deterministic && upto > 1) {
      int lastMax = transitions[offset+2];
      for(int i=1;i<upto;i++) {
        min = transitions[offset + 3*i + 1];
        if (min <= lastMax) {
          deterministic = false;
          break;
        }
        lastMax = transitions[offset + 3*i + 2];
      }
    }
  }

  public boolean isDeterministic() {
    return deterministic;
  }


  public void finishState() {
    if (curState != -1) {
      finishCurrentState();
      curState = -1;
    }
  }

  // TODO: add finish() to shrink wrap the arrays?

  public int getNumStates() {
    return nextState/2;
  }

  public int getNumTransitions() {
    return nextTransition / 3;   
  }
  
  public int getNumTransitions(int state) {
    assert state >= 0;
    int count = states[2*state+1];
    if (count == -1) {
      return 0;
    } else {
      return count;
    }
  }

  private void growStates() {
    if (nextState+2 >= states.length) {
      states = ArrayUtil.grow(states, nextState+2);
    }
  }

  private void growTransitions() {
    if (nextTransition+3 >= transitions.length) {
      transitions = ArrayUtil.grow(transitions, nextTransition+3);
    }
  }

  private final Sorter destMinMaxSorter = new InPlaceMergeSorter() {

      private void swapOne(int i, int j) {
        int x = transitions[i];
        transitions[i] = transitions[j];
        transitions[j] = x;
      }

      @Override
      protected void swap(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;
        swapOne(iStart, jStart);
        swapOne(iStart+1, jStart+1);
        swapOne(iStart+2, jStart+2);
      };

      @Override
      protected int compare(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;

        // First dest:
        int iDest = transitions[iStart];
        int jDest = transitions[jStart];
        if (iDest < jDest) {
          return -1;
        } else if (iDest > jDest) {
          return 1;
        }

        // Then min:
        int iMin = transitions[iStart+1];
        int jMin = transitions[jStart+1];
        if (iMin < jMin) {
          return -1;
        } else if (iMin > jMin) {
          return 1;
        }

        // Then max:
        int iMax = transitions[iStart+2];
        int jMax = transitions[jStart+2];
        if (iMax < jMax) {
          return -1;
        } else if (iMax > jMax) {
          return 1;
        }

        return 0;
      }
    };

  private final Sorter minMaxDestSorter = new InPlaceMergeSorter() {

      private void swapOne(int i, int j) {
        int x = transitions[i];
        transitions[i] = transitions[j];
        transitions[j] = x;
      }

      @Override
      protected void swap(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;
        swapOne(iStart, jStart);
        swapOne(iStart+1, jStart+1);
        swapOne(iStart+2, jStart+2);
      };

      @Override
      protected int compare(int i, int j) {
        int iStart = 3*i;
        int jStart = 3*j;

        // First min:
        int iMin = transitions[iStart+1];
        int jMin = transitions[jStart+1];
        if (iMin < jMin) {
          return -1;
        } else if (iMin > jMin) {
          return 1;
        }

        // Then max:
        int iMax = transitions[iStart+2];
        int jMax = transitions[jStart+2];
        if (iMax < jMax) {
          return -1;
        } else if (iMax > jMax) {
          return 1;
        }

        // Then dest:
        int iDest = transitions[iStart];
        int jDest = transitions[jStart];
        if (iDest < jDest) {
          return -1;
        } else if (iDest > jDest) {
          return 1;
        }

        return 0;
      }
    };


  public int initTransition(int state, Transition t) {
    assert state < nextState/2: "state=" + state + " nextState=" + nextState;
    t.source = state;
    t.transitionUpto = states[2*state];
    return getNumTransitions(state);
  }

  public void getNextTransition(Transition t) {
    // Make sure there is still a transition left:
    assert (t.transitionUpto+3 - states[2*t.source]) <= 3*states[2*t.source+1];

    // Make sure transitions are in fact sorted:
    assert transitionSorted(t);

    t.dest = transitions[t.transitionUpto++];
    t.min = transitions[t.transitionUpto++];
    t.max = transitions[t.transitionUpto++];
  }

  private boolean transitionSorted(Transition t) {

    int upto = t.transitionUpto;
    if (upto == states[2*t.source]) {
      // Transition isn't initialzed yet (this is the first transition); don't check:
      return true;
    }

    int nextDest = transitions[upto];
    int nextMin = transitions[upto+1];
    int nextMax = transitions[upto+2];
    if (nextMin > t.min) {
      return true;
    } else if (nextMin < t.min) {
      return false;
    }

    // Min is equal, now test max:
    if (nextMax > t.max) {
      return true;
    } else if (nextMax < t.max) {
      return false;
    }

    // Max is also equal, now test dest:
    if (nextDest > t.dest) {
      return true;
    } else if (nextDest < t.dest) {
      return false;
    }

    // We should never see fully equal transitions here:
    return false;
  }

  public void getTransition(int state, int index, Transition t) {
    int i = states[2*state] + 3*index;
    t.source = state;
    t.dest = transitions[i++];
    t.min = transitions[i++];
    t.max = transitions[i++];
  }

  static void appendCharString(int c, StringBuilder b) {
    if (c >= 0x21 && c <= 0x7e && c != '\\' && c != '"') b.appendCodePoint(c);
    else {
      b.append("\\\\U");
      String s = Integer.toHexString(c);
      if (c < 0x10) b.append("0000000").append(s);
      else if (c < 0x100) b.append("000000").append(s);
      else if (c < 0x1000) b.append("00000").append(s);
      else if (c < 0x10000) b.append("0000").append(s);
      else if (c < 0x100000) b.append("000").append(s);
      else if (c < 0x1000000) b.append("00").append(s);
      else if (c < 0x10000000) b.append("0").append(s);
      else b.append(s);
    }
  }

  /*
  public void writeDot(String fileName) {
    if (fileName.indexOf('/') == -1) {
      fileName = "/l/la/lucene/core/" + fileName + ".dot";
    }
    try {
      PrintWriter pw = new PrintWriter(fileName);
      pw.println(toDot());
      pw.close();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
  */

  public String toDot() {
    // TODO: breadth first search so we can see get layered output...

    StringBuilder b = new StringBuilder();
    b.append("digraph Automaton {\n");
    b.append("  rankdir = LR\n");
    final int numStates = getNumStates();
    if (numStates > 0) {
      b.append("  initial [shape=plaintext,label=\"0\"]\n");
      b.append("  initial -> 0\n");
    }

    Transition t = new Transition();

    for(int state=0;state<numStates;state++) {
      b.append("  ");
      b.append(state);
      if (isAccept(state)) {
        b.append(" [shape=doublecircle,label=\"" + state + "\"]\n");
      } else {
        b.append(" [shape=circle,label=\"" + state + "\"]\n");
      }
      int numTransitions = initTransition(state, t);
      //System.out.println("toDot: state " + state + " has " + numTransitions + " transitions; t.nextTrans=" + t.transitionUpto);
      for(int i=0;i<numTransitions;i++) {
        getNextTransition(t);
        //System.out.println("  t.nextTrans=" + t.transitionUpto + " t=" + t);
        assert t.max >= t.min;
        b.append("  ");
        b.append(state);
        b.append(" -> ");
        b.append(t.dest);
        b.append(" [label=\"");
        appendCharString(t.min, b);
        if (t.max != t.min) {
          b.append('-');
          appendCharString(t.max, b);
        }
        b.append("\"]\n");
        //System.out.println("  t=" + t);
      }
    }
    b.append('}');
    return b.toString();
  }

  int[] getStartPoints() {
    Set<Integer> pointset = new HashSet<>();
    pointset.add(Character.MIN_CODE_POINT);
    //System.out.println("getStartPoints");
    for (int s=0;s<nextState;s+=2) {
      int trans = states[s];
      int limit = trans+3*states[s+1];
      //System.out.println("  state=" + (s/2) + " trans=" + trans + " limit=" + limit);
      while (trans < limit) {
        int min = transitions[trans+1];
        int max = transitions[trans+2];
        //System.out.println("    min=" + min);
        pointset.add(min);
        if (max < Character.MAX_CODE_POINT) {
          pointset.add(max + 1);
        }
        trans += 3;
      }
    }
    int[] points = new int[pointset.size()];
    int n = 0;
    for (Integer m : pointset) {
      points[n++] = m;
    }
    Arrays.sort(points);
    return points;
  }

  public int step(int state, int label) {
    assert state >= 0;
    assert label >= 0;
    int trans = states[2*state];
    int limit = trans + 3*states[2*state+1];
    // TODO: we could do bin search; transitions are sorted
    while (trans < limit) {
      int dest = transitions[trans];
      int min = transitions[trans+1];
      int max = transitions[trans+2];
      if (min <= label && label <= max) {
        return dest;
      }
      trans += 3;
    }

    return -1;
  }


  public static class Builder {
    private int nextState = 0;
    private final BitSet isAccept;
    private int[] transitions;
    private int nextTransition = 0;

    public Builder() {
       this(16, 16);
    }

    public Builder(int numStates, int numTransitions) {
       isAccept = new BitSet(numStates);
       transitions = new int[numTransitions * 4];
    }

    public void addTransition(int source, int dest, int label) {
      addTransition(source, dest, label, label);
    }

    public void addTransition(int source, int dest, int min, int max) {
      if (transitions.length < nextTransition+4) {
        transitions = ArrayUtil.grow(transitions, nextTransition+4);
      }
      transitions[nextTransition++] = source;
      transitions[nextTransition++] = dest;
      transitions[nextTransition++] = min;
      transitions[nextTransition++] = max;
    }


    public void addEpsilon(int source, int dest) {
      for (int upto = 0; upto < nextTransition; upto += 4) {
         if (transitions[upto] == dest) {
            addTransition(source, transitions[upto + 1], transitions[upto + 2], transitions[upto + 3]);
         }
      }
      if (isAccept(dest)) {
        setAccept(source, true);
      }
    }

    private final Sorter sorter = new InPlaceMergeSorter() {

        private void swapOne(int i, int j) {
          int x = transitions[i];
          transitions[i] = transitions[j];
          transitions[j] = x;
        }

        @Override
        protected void swap(int i, int j) {
          int iStart = 4*i;
          int jStart = 4*j;
          swapOne(iStart, jStart);
          swapOne(iStart+1, jStart+1);
          swapOne(iStart+2, jStart+2);
          swapOne(iStart+3, jStart+3);
        };

        @Override
        protected int compare(int i, int j) {
          int iStart = 4*i;
          int jStart = 4*j;

          // First src:
          int iSrc = transitions[iStart];
          int jSrc = transitions[jStart];
          if (iSrc < jSrc) {
            return -1;
          } else if (iSrc > jSrc) {
            return 1;
          }

          // Then min:
          int iMin = transitions[iStart+2];
          int jMin = transitions[jStart+2];
          if (iMin < jMin) {
            return -1;
          } else if (iMin > jMin) {
            return 1;
          }

          // Then max:
          int iMax = transitions[iStart+3];
          int jMax = transitions[jStart+3];
          if (iMax < jMax) {
            return -1;
          } else if (iMax > jMax) {
            return 1;
          }

          // First dest:
          int iDest = transitions[iStart+1];
          int jDest = transitions[jStart+1];
          if (iDest < jDest) {
            return -1;
          } else if (iDest > jDest) {
            return 1;
          }

          return 0;
        }
      };

    public Automaton finish() {
      // Create automaton with the correct size.
      int numStates = nextState;
      int numTransitions = nextTransition / 4;
      Automaton a = new Automaton(numStates, numTransitions);
      
      // Create all states.
      for (int state = 0; state < numStates; state++) {
         a.createState();
         a.setAccept(state, isAccept(state));
      }
      
      // Create all transitions
      sorter.sort(0, numTransitions);
      for (int upto = 0; upto < nextTransition; upto += 4) {
        a.addTransition(transitions[upto],
                        transitions[upto+1],
                        transitions[upto+2],
                        transitions[upto+3]);
      }

      a.finishState();
      
      return a;
    }

    public int createState() {
      return nextState++;
    }

    public void setAccept(int state, boolean accept) {
      if (state >= getNumStates()) {
        throw new IllegalArgumentException("state=" + state + " is out of bounds (numStates=" + getNumStates() + ")");
      }
      
      this.isAccept.set(state, accept);
    }

    public boolean isAccept(int state) {
      return this.isAccept.get(state);
    }

    public int getNumStates() {
      return nextState;
    }

    public void copy(Automaton other) {
      int offset = getNumStates();
      int otherNumStates = other.getNumStates();

      // Copy all states
      copyStates(other);
      
      // Copy all transitions
      Transition t = new Transition();
      for(int s=0;s<otherNumStates;s++) {
        int count = other.initTransition(s, t);
        for(int i=0;i<count;i++) {
          other.getNextTransition(t);
          addTransition(offset + s, offset + t.dest, t.min, t.max);
        }
      }
    }

    public void copyStates(Automaton other) {
      int otherNumStates = other.getNumStates();
      for (int s = 0; s < otherNumStates; s++) {
        int newState = createState();
        setAccept(newState, other.isAccept(s));
      }
    }
  }

  @Override
  public long ramBytesUsed() {
    // TODO: BitSet RAM usage (isAccept.size()/8) isn't fully accurate...
    return RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + RamUsageEstimator.sizeOf(states) + RamUsageEstimator.sizeOf(transitions) +
      RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + (isAccept.size() / 8) + RamUsageEstimator.NUM_BYTES_OBJECT_REF +
      2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF +
      3 * RamUsageEstimator.NUM_BYTES_INT +
      RamUsageEstimator.NUM_BYTES_BOOLEAN;
  }

  @Override
  public Collection<Accountable> getChildResources() {
    return Collections.emptyList();
  }
}
