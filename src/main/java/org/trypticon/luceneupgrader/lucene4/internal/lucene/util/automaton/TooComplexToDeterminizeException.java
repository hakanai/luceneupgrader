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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton;

public class TooComplexToDeterminizeException extends RuntimeException {
  private final Automaton automaton;
  private final RegExp regExp;
  private final int maxDeterminizedStates;

  public TooComplexToDeterminizeException(RegExp regExp, TooComplexToDeterminizeException cause) {
    super("Determinizing " + regExp.getOriginalString() + " would result in more than " +
      cause.maxDeterminizedStates + " states.", cause);
    this.regExp = regExp;
    this.automaton = cause.automaton;
    this.maxDeterminizedStates = cause.maxDeterminizedStates;
  }

  public TooComplexToDeterminizeException(Automaton automaton, int maxDeterminizedStates) {
    super("Determinizing automaton would result in more than " + maxDeterminizedStates + " states.");
    this.automaton = automaton;
    this.regExp = null;
    this.maxDeterminizedStates = maxDeterminizedStates;
  }

  public Automaton getAutomaton() {
    return automaton;
  }

  public RegExp getRegExp() {
    return regExp;
  }

  public int getMaxDeterminizedStates() {
    return maxDeterminizedStates;
  }
}
