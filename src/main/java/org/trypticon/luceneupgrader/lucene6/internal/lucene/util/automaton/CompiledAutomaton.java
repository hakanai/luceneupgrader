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

  
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SingleTermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRefBuilder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.IntsRef;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.StringHelper;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.UnicodeUtil;

public class CompiledAutomaton {
  public enum AUTOMATON_TYPE {
    NONE,
    ALL,
    SINGLE,
    NORMAL
  };

  public final AUTOMATON_TYPE type;


  public final BytesRef term;


  public final ByteRunAutomaton runAutomaton;

  public final Automaton automaton;

  public final BytesRef commonSuffixRef;

  public final Boolean finite;

  public final int sinkState;

  public CompiledAutomaton(Automaton automaton) {
    this(automaton, null, true);
  }

  private static int findSinkState(Automaton automaton) {
    int numStates = automaton.getNumStates();
    Transition t = new Transition();
    int foundState = -1;
    for (int s=0;s<numStates;s++) {
      if (automaton.isAccept(s)) {
        int count = automaton.initTransition(s, t);
        boolean isSinkState = false;
        for(int i=0;i<count;i++) {
          automaton.getNextTransition(t);
          if (t.dest == s && t.min == 0 && t.max == 0xff) {
            isSinkState = true;
            break;
          }
        }
        if (isSinkState) {
          foundState = s;
          break;
        }
      }
    }

    return foundState;
  }


  public CompiledAutomaton(Automaton automaton, Boolean finite, boolean simplify) {
    this(automaton, finite, simplify, Operations.DEFAULT_MAX_DETERMINIZED_STATES, false);
  }



  public CompiledAutomaton(Automaton automaton, Boolean finite, boolean simplify,
                           int maxDeterminizedStates, boolean isBinary) {
    if (automaton.getNumStates() == 0) {
      automaton = new Automaton();
      automaton.createState();
    }

    if (simplify) {

      // Test whether the automaton is a "simple" form and
      // if so, don't create a runAutomaton.  Note that on a
      // large automaton these tests could be costly:

      if (Operations.isEmpty(automaton)) {
        // matches nothing
        type = AUTOMATON_TYPE.NONE;
        term = null;
        commonSuffixRef = null;
        runAutomaton = null;
        this.automaton = null;
        this.finite = null;
        sinkState = -1;
        return;
      }

      boolean isTotal;

      // NOTE: only approximate, because automaton may not be minimal:
      if (isBinary) {
        isTotal = Operations.isTotal(automaton, 0, 0xff);
      } else {
        isTotal = Operations.isTotal(automaton);
      }

      if (isTotal) {
        // matches all possible strings
        type = AUTOMATON_TYPE.ALL;
        term = null;
        commonSuffixRef = null;
        runAutomaton = null;
        this.automaton = null;
        this.finite = null;
        sinkState = -1;
        return;
      }

      automaton = Operations.determinize(automaton, maxDeterminizedStates);

      IntsRef singleton = Operations.getSingleton(automaton);

      if (singleton != null) {
        // matches a fixed string
        type = AUTOMATON_TYPE.SINGLE;
        commonSuffixRef = null;
        runAutomaton = null;
        this.automaton = null;
        this.finite = null;

        if (isBinary) {
          term = StringHelper.intsRefToBytesRef(singleton);
        } else {
          term = new BytesRef(UnicodeUtil.newString(singleton.ints, singleton.offset, singleton.length));
        }
        sinkState = -1;
        return;
      }
    }

    type = AUTOMATON_TYPE.NORMAL;
    term = null;

    if (finite == null) {
      this.finite = Operations.isFinite(automaton);
    } else {
      this.finite = finite;
    }

    Automaton binary;
    if (isBinary) {
      // Caller already built binary automaton themselves, e.g. PrefixQuery
      // does this since it can be provided with a binary (not necessarily
      // UTF8!) term:
      binary = automaton;
    } else {
      // Incoming automaton is unicode, and we must convert to UTF8 to match what's in the index:
      binary = new UTF32ToUTF8().convert(automaton);
    }

    if (this.finite) {
      commonSuffixRef = null;
    } else {
      // NOTE: this is a very costly operation!  We should test if it's really warranted in practice... we could do a fast match
      // by looking for a sink state (which means it has no common suffix).  Or maybe we shouldn't do it when simplify is false?:
      BytesRef suffix = Operations.getCommonSuffixBytesRef(binary, maxDeterminizedStates);
      if (suffix.length == 0) {
        commonSuffixRef = null;
      } else {
        commonSuffixRef = suffix;
      }
    }

    // This will determinize the binary automaton for us:
    runAutomaton = new ByteRunAutomaton(binary, true, maxDeterminizedStates);

    this.automaton = runAutomaton.automaton;

    // TODO: this is a bit fragile because if the automaton is not minimized there could be more than 1 sink state but auto-prefix will fail
    // to run for those:
    sinkState = findSinkState(this.automaton);
  }

  private Transition transition = new Transition();
  
  //private static final boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  private BytesRef addTail(int state, BytesRefBuilder term, int idx, int leadLabel) {
    //System.out.println("addTail state=" + state + " term=" + term.utf8ToString() + " idx=" + idx + " leadLabel=" + (char) leadLabel);
    //System.out.println(automaton.toDot());
    // Find biggest transition that's < label
    // TODO: use binary search here
    int maxIndex = -1;
    int numTransitions = automaton.initTransition(state, transition);
    for(int i=0;i<numTransitions;i++) {
      automaton.getNextTransition(transition);
      if (transition.min < leadLabel) {
        maxIndex = i;
      } else {
        // Transitions are alway sorted
        break;
      }
    }

    //System.out.println("  maxIndex=" + maxIndex);

    assert maxIndex != -1;
    automaton.getTransition(state, maxIndex, transition);

    // Append floorLabel
    final int floorLabel;
    if (transition.max > leadLabel-1) {
      floorLabel = leadLabel-1;
    } else {
      floorLabel = transition.max;
    }
    //System.out.println("  floorLabel=" + (char) floorLabel);
    term.grow(1+idx);
    //if (DEBUG) System.out.println("  add floorLabel=" + (char) floorLabel + " idx=" + idx);
    term.setByteAt(idx, (byte) floorLabel);

    state = transition.dest;
    //System.out.println("  dest: " + state);
    idx++;

    // Push down to last accept state
    while (true) {
      numTransitions = automaton.getNumTransitions(state);
      if (numTransitions == 0) {
        //System.out.println("state=" + state + " 0 trans");
        assert runAutomaton.isAccept(state);
        term.setLength(idx);
        //if (DEBUG) System.out.println("  return " + term.utf8ToString());
        return term.get();
      } else {
        // We are pushing "top" -- so get last label of
        // last transition:
        //System.out.println("get state=" + state + " numTrans=" + numTransitions);
        automaton.getTransition(state, numTransitions-1, transition);
        term.grow(1+idx);
        //if (DEBUG) System.out.println("  push maxLabel=" + (char) lastTransition.max + " idx=" + idx);
        //System.out.println("  add trans dest=" + scratch.dest + " label=" + (char) scratch.max);
        term.setByteAt(idx, (byte) transition.max);
        state = transition.dest;
        idx++;
      }
    }
  }

  // TODO: should this take startTerm too?  This way
  // Terms.intersect could forward to this method if type !=
  // NORMAL:
  public TermsEnum getTermsEnum(Terms terms) throws IOException {
    switch(type) {
    case NONE:
      return TermsEnum.EMPTY;
    case ALL:
      return terms.iterator();
    case SINGLE:
      return new SingleTermsEnum(terms.iterator(), term);
    case NORMAL:
      return terms.intersect(this, null);
    default:
      // unreachable
      throw new RuntimeException("unhandled case");
    }
  }


  public BytesRef floor(BytesRef input, BytesRefBuilder output) {

    //if (DEBUG) System.out.println("CA.floor input=" + input.utf8ToString());

    int state = 0;

    // Special case empty string:
    if (input.length == 0) {
      if (runAutomaton.isAccept(state)) {
        output.clear();
        return output.get();
      } else {
        return null;
      }
    }

    final List<Integer> stack = new ArrayList<>();

    int idx = 0;
    while (true) {
      int label = input.bytes[input.offset + idx] & 0xff;
      int nextState = runAutomaton.step(state, label);
      //if (DEBUG) System.out.println("  cycle label=" + (char) label + " nextState=" + nextState);

      if (idx == input.length-1) {
        if (nextState != -1 && runAutomaton.isAccept(nextState)) {
          // Input string is accepted
          output.grow(1+idx);
          output.setByteAt(idx, (byte) label);
          output.setLength(input.length);
          //if (DEBUG) System.out.println("  input is accepted; return term=" + output.utf8ToString());
          return output.get();
        } else {
          nextState = -1;
        }
      }

      if (nextState == -1) {

        // Pop back to a state that has a transition
        // <= our label:
        while (true) {
          int numTransitions = automaton.getNumTransitions(state);
          if (numTransitions == 0) {
            assert runAutomaton.isAccept(state);
            output.setLength(idx);
            //if (DEBUG) System.out.println("  return " + output.utf8ToString());
            return output.get();
          } else {
            automaton.getTransition(state, 0, transition);

            if (label-1 < transition.min) {

              if (runAutomaton.isAccept(state)) {
                output.setLength(idx);
                //if (DEBUG) System.out.println("  return " + output.utf8ToString());
                return output.get();
              }
              // pop
              if (stack.size() == 0) {
                //if (DEBUG) System.out.println("  pop ord=" + idx + " return null");
                return null;
              } else {
                state = stack.remove(stack.size()-1);
                idx--;
                //if (DEBUG) System.out.println("  pop ord=" + (idx+1) + " label=" + (char) label + " first trans.min=" + (char) transitions[0].min);
                label = input.bytes[input.offset + idx] & 0xff;
              }
            } else {
              //if (DEBUG) System.out.println("  stop pop ord=" + idx + " first trans.min=" + (char) transitions[0].min);
              break;
            }
          }
        }

        //if (DEBUG) System.out.println("  label=" + (char) label + " idx=" + idx);

        return addTail(state, output, idx, label);
        
      } else {
        output.grow(1+idx);
        output.setByteAt(idx, (byte) label);
        stack.add(state);
        state = nextState;
        idx++;
      }
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((runAutomaton == null) ? 0 : runAutomaton.hashCode());
    result = prime * result + ((term == null) ? 0 : term.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    CompiledAutomaton other = (CompiledAutomaton) obj;
    if (type != other.type) return false;
    if (type == AUTOMATON_TYPE.SINGLE) {
      if (!term.equals(other.term)) return false;
    } else if (type == AUTOMATON_TYPE.NORMAL) {
      if (!runAutomaton.equals(other.runAutomaton)) return false;
    }

    return true;
  }
}
