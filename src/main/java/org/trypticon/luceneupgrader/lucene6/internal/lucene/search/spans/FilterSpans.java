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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.search.spans;


import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.TwoPhaseIterator;

public abstract class FilterSpans extends Spans {
 
  protected final Spans in;
  
  private boolean atFirstInCurrentDoc = false;
  private int startPos = -1;
  
  protected FilterSpans(Spans in) {
    this.in = Objects.requireNonNull(in);
  }
  

  protected abstract AcceptStatus accept(Spans candidate) throws IOException;
  
  @Override
  public final int nextDoc() throws IOException {
    while (true) {
      int doc = in.nextDoc();
      if (doc == NO_MORE_DOCS) {
        return NO_MORE_DOCS;
      } else if (twoPhaseCurrentDocMatches()) {
        return doc;
      }
    }
  }

  @Override
  public final int advance(int target) throws IOException {
    int doc = in.advance(target);
    while (doc != NO_MORE_DOCS) {
      if (twoPhaseCurrentDocMatches()) {
        break;
      }
      doc = in.nextDoc();
    }

    return doc;
  }

  @Override
  public final int docID() {
    return in.docID();
  }

  @Override
  public final int nextStartPosition() throws IOException {
    if (atFirstInCurrentDoc) {
      atFirstInCurrentDoc = false;
      return startPos;
    }

    for (;;) {
      startPos = in.nextStartPosition();
      if (startPos == NO_MORE_POSITIONS) {
        return NO_MORE_POSITIONS;
      }
      switch(accept(in)) {
        case YES:
          return startPos;
        case NO:
          break;
        case NO_MORE_IN_CURRENT_DOC:
          return startPos = NO_MORE_POSITIONS; // startPos ahead for the current doc.
      }
    }
  }

  @Override
  public final int startPosition() {
    return atFirstInCurrentDoc ? -1 : startPos;
  }

  @Override
  public final int endPosition() {
    return atFirstInCurrentDoc ? -1
          : (startPos != NO_MORE_POSITIONS) ? in.endPosition() : NO_MORE_POSITIONS;
  }

  @Override
  public int width() {
    return in.width();
  }

  @Override
  public void collect(SpanCollector collector) throws IOException {
    in.collect(collector);
  }

  @Override
  public final long cost() {
    return in.cost();
  }
  
  @Override
  public String toString() {
    return "Filter(" + in.toString() + ")";
  }
  
  @Override
  public final TwoPhaseIterator asTwoPhaseIterator() {
    TwoPhaseIterator inner = in.asTwoPhaseIterator();
    if (inner != null) {
      // wrapped instance has an approximation
      return new TwoPhaseIterator(inner.approximation()) {
        @Override
        public boolean matches() throws IOException {
          return inner.matches() && twoPhaseCurrentDocMatches();
        }

        @Override
        public float matchCost() {
          return inner.matchCost(); // underestimate
        }

        @Override
        public String toString() {
          return "FilterSpans@asTwoPhaseIterator(inner=" + inner + ", in=" + in + ")";
        }
      };
    } else {
      // wrapped instance has no approximation, but 
      // we can still defer matching until absolutely needed.
      return new TwoPhaseIterator(in) {
        @Override
        public boolean matches() throws IOException {
          return twoPhaseCurrentDocMatches();
        }

        @Override
        public float matchCost() {
          return in.positionsCost(); // overestimate
        }

        @Override
        public String toString() {
          return "FilterSpans@asTwoPhaseIterator(in=" + in + ")";
        }
      };
    }
  }
  
  @Override
  public float positionsCost() {
    throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null
  }

  // return true if the current document matches
  @SuppressWarnings("fallthrough")
  private final boolean twoPhaseCurrentDocMatches() throws IOException {
    atFirstInCurrentDoc = false;
    startPos = in.nextStartPosition();
    assert startPos != NO_MORE_POSITIONS;
    for (;;) {
      switch(accept(in)) {
        case YES:
          atFirstInCurrentDoc = true;
          return true;
        case NO:
          startPos = in.nextStartPosition();
          if (startPos != NO_MORE_POSITIONS) {
            break;
          }
          // else fallthrough
        case NO_MORE_IN_CURRENT_DOC:
          startPos = -1;
          return false;
      }
    }
  }

  public static enum AcceptStatus {
    YES,

    NO,

    NO_MORE_IN_CURRENT_DOC
  };
}
