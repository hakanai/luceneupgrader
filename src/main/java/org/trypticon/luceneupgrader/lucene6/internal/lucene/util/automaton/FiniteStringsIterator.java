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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util.automaton;


import java.util.BitSet;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.IntsRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.IntsRefBuilder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.RamUsageEstimator;

public class FiniteStringsIterator {
  private static final IntsRef EMPTY = new IntsRef();

  private final Automaton a;

  private final int endState;

  private final BitSet pathStates;

  private final IntsRefBuilder string;

  private PathNode[] nodes;

  private boolean emitEmptyString;

  public FiniteStringsIterator(Automaton a) {
    this(a, 0, -1);
  }


  public FiniteStringsIterator(Automaton a, int startState, int endState) {
    this.a = a;
    this.endState = endState;
    this.nodes = new PathNode[16];
    for (int i = 0, end = nodes.length; i < end; i++) {
      nodes[i] = new PathNode();
    }
    this.string = new IntsRefBuilder();
    this.pathStates = new BitSet(a.getNumStates());
    this.string.setLength(0);
    this.emitEmptyString = a.isAccept(0);

    // Start iteration with node startState.
    if (a.getNumTransitions(startState) > 0) {
      pathStates.set(startState);
      nodes[0].resetState(a, startState);
      string.append(startState);
    }
  }

  public IntsRef next() {
    // Special case the empty string, as usual:
    if (emitEmptyString) {
      emitEmptyString = false;
      return EMPTY;
    }

    for (int depth = string.length(); depth > 0;) {
      PathNode node = nodes[depth-1];

      // Get next label leaving the current node:
      int label = node.nextLabel(a);
      if (label != -1) {
        string.setIntAt(depth - 1, label);

        int to = node.to;
        if (a.getNumTransitions(to) != 0 && to != endState) {
          // Now recurse: the destination of this transition has outgoing transitions:
          if (pathStates.get(to)) {
            throw new IllegalArgumentException("automaton has cycles");
          }
          pathStates.set(to);

          // Push node onto stack:
          growStack(depth);
          nodes[depth].resetState(a, to);
          depth++;
          string.setLength(depth);
          string.grow(depth);
        } else if (endState == to || a.isAccept(to)) {
          // This transition leads to an accept state, so we save the current string:
          return string.get();
        }
      } else {
        // No more transitions leaving this state, pop/return back to previous state:
        int state = node.state;
        assert pathStates.get(state);
        pathStates.clear(state);
        depth--;
        string.setLength(depth);

        if (a.isAccept(state)) {
          // This transition leads to an accept state, so we save the current string:
          return string.get();
        }
      }
    }

    // Finished iteration.
    return null;
  }

  private void growStack(int depth) {
    if (nodes.length == depth) {
      PathNode[] newNodes = new PathNode[ArrayUtil.oversize(nodes.length + 1, RamUsageEstimator.NUM_BYTES_OBJECT_REF)];
      System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
      for (int i = depth, end = newNodes.length; i < end; i++) {
        newNodes[i] = new PathNode();
      }
      nodes = newNodes;
    }
  }

  private static class PathNode {

    public int state;

    public int to;

    public int transition;

    public int label;

    private final Transition t = new Transition();

    public void resetState(Automaton a, int state) {
      assert a.getNumTransitions(state) != 0;
      this.state = state;
      transition = 0;
      a.getTransition(state, 0, t);
      label = t.min;
      to = t.dest;
    }


    public int nextLabel(Automaton a) {
      if (label > t.max) {
        // We've exhaused the current transition's labels;
        // move to next transitions:
        transition++;
        if (transition >= a.getNumTransitions(state)) {
          // We're done iterating transitions leaving this state
          label = -1;
          return -1;
        }
        a.getTransition(state, transition, t);
        label = t.min;
        to = t.dest;
      }
      return label++;
    }
  }
}
