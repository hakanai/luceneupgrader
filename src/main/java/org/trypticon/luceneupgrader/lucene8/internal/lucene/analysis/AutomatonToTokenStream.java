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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.CharTermAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.Automaton;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.Operations;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.automaton.Transition;

public class AutomatonToTokenStream {

  private AutomatonToTokenStream() {}

  public static TokenStream toTokenStream(Automaton automaton) {
    if (Operations.isFinite(automaton) == false) {
      throw new IllegalArgumentException("Automaton must be finite");
    }

    List<List<Integer>> positionNodes = new ArrayList<>();

    Transition[][] transitions = automaton.getSortedTransitions();

    int[] indegree = new int[transitions.length];

    for (int i = 0; i < transitions.length; i++) {
      for (int edge = 0; edge < transitions[i].length; edge++) {
        indegree[transitions[i][edge].dest] += 1;
      }
    }
    if (indegree[0] != 0) {
      throw new IllegalArgumentException("Start node has incoming edges, creating cycle");
    }

    LinkedList<RemapNode> noIncomingEdges = new LinkedList<>();
    Map<Integer, Integer> idToPos = new HashMap<>();
    noIncomingEdges.addLast(new RemapNode(0, 0));
    while (noIncomingEdges.isEmpty() == false) {
      RemapNode currState = noIncomingEdges.removeFirst();
      for (int i = 0; i < transitions[currState.id].length; i++) {
        indegree[transitions[currState.id][i].dest] -= 1;
        if (indegree[transitions[currState.id][i].dest] == 0) {
          noIncomingEdges.addLast(
              new RemapNode(transitions[currState.id][i].dest, currState.pos + 1));
        }
      }
      if (positionNodes.size() == currState.pos) {
        List<Integer> posIncs = new ArrayList<>();
        posIncs.add(currState.id);
        positionNodes.add(posIncs);
      } else {
        positionNodes.get(currState.pos).add(currState.id);
      }
      idToPos.put(currState.id, currState.pos);
    }

    for (int i = 0; i < indegree.length; i++) {
      if (indegree[i] != 0) {
        throw new IllegalArgumentException("Cycle found in automaton");
      }
    }

    List<List<EdgeToken>> edgesByLayer = new ArrayList<>();
    for (List<Integer> layer : positionNodes) {
      List<EdgeToken> edges = new ArrayList<>();
      for (int state : layer) {
        for (Transition t : transitions[state]) {
          // each edge in the token stream can only be on value, though a transition takes a range.
          for (int val = t.min; val <= t.max; val++) {
            int destLayer = idToPos.get(t.dest);
            edges.add(new EdgeToken(destLayer, val));
            // If there's an intermediate accept state, add an edge to the terminal state.
            if (automaton.isAccept(t.dest) && destLayer != positionNodes.size() - 1) {
              edges.add(new EdgeToken(positionNodes.size() - 1, val));
            }
          }
        }
      }
      edgesByLayer.add(edges);
    }

    return new TopoTokenStream(edgesByLayer);
  }

  private static class TopoTokenStream extends TokenStream {

    private final List<List<EdgeToken>> edgesByPos;
    private int currentPos;
    private int currentEdgeIndex;
    private CharTermAttribute charAttr = addAttribute(CharTermAttribute.class);
    private PositionIncrementAttribute incAttr = addAttribute(PositionIncrementAttribute.class);
    private PositionLengthAttribute lenAttr = addAttribute(PositionLengthAttribute.class);
    private OffsetAttribute offAttr = addAttribute(OffsetAttribute.class);

    public TopoTokenStream(List<List<EdgeToken>> edgesByPos) {
      this.edgesByPos = edgesByPos;
    }

    @Override
    public boolean incrementToken() throws IOException {
      clearAttributes();
      while (currentPos < edgesByPos.size()
          && currentEdgeIndex == edgesByPos.get(currentPos).size()) {
        currentEdgeIndex = 0;
        currentPos += 1;
      }
      if (currentPos == edgesByPos.size()) {
        return false;
      }
      EdgeToken currentEdge = edgesByPos.get(currentPos).get(currentEdgeIndex);

      charAttr.append((char) currentEdge.value);

      incAttr.setPositionIncrement(currentEdgeIndex == 0 ? 1 : 0);

      lenAttr.setPositionLength(currentEdge.destination - currentPos);

      offAttr.setOffset(currentPos, currentEdge.destination);

      currentEdgeIndex++;

      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      clearAttributes();
      currentPos = 0;
      currentEdgeIndex = 0;
    }

    @Override
    public void end() throws IOException {
      clearAttributes();
      incAttr.setPositionIncrement(0);
      // -1 because we don't count the terminal state as a position in the TokenStream
      offAttr.setOffset(edgesByPos.size() - 1, edgesByPos.size() - 1);
    }
  }

  private static class EdgeToken {
    public final int destination;
    public final int value;

    public EdgeToken(int destination, int value) {
      this.destination = destination;
      this.value = value;
    }
  }

  private static class RemapNode {
    public final int id;
    public final int pos;

    public RemapNode(int id, int pos) {
      this.id = id;
      this.pos = pos;
    }
  }
}
