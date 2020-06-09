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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.OffsetAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.RollingBuffer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.automaton.Automaton;

// TODO: maybe also toFST?  then we can translate atts into FST outputs/weights

public class TokenStreamToAutomaton {

  private boolean preservePositionIncrements;
  private boolean finalOffsetGapAsHole;
  private boolean unicodeArcs;

  public TokenStreamToAutomaton() {
    this.preservePositionIncrements = true;
  }

  public void setPreservePositionIncrements(boolean enablePositionIncrements) {
    this.preservePositionIncrements = enablePositionIncrements;
  }

  public void setFinalOffsetGapAsHole(boolean finalOffsetGapAsHole) {
    this.finalOffsetGapAsHole = finalOffsetGapAsHole;
  }

  public void setUnicodeArcs(boolean unicodeArcs) {
    this.unicodeArcs = unicodeArcs;
  }

  private static class Position implements RollingBuffer.Resettable {
    // Any tokens that ended at our position arrive to this state:
    int arriving = -1;

    // Any tokens that start at our position leave from this state:
    int leaving = -1;

    @Override
    public void reset() {
      arriving = -1;
      leaving = -1;
    }
  }

  private static class Positions extends RollingBuffer<Position> {
    @Override
    protected Position newInstance() {
      return new Position();
    }
  }

  protected BytesRef changeToken(BytesRef in) {
    return in;
  }

  public static final int POS_SEP = 0x001f;

  public static final int HOLE = 0x001e;

  public Automaton toAutomaton(TokenStream in) throws IOException {
    final Automaton.Builder builder = new Automaton.Builder();
    builder.createState();

    final TermToBytesRefAttribute termBytesAtt = in.addAttribute(TermToBytesRefAttribute.class);
    final PositionIncrementAttribute posIncAtt = in.addAttribute(PositionIncrementAttribute.class);
    final PositionLengthAttribute posLengthAtt = in.addAttribute(PositionLengthAttribute.class);
    final OffsetAttribute offsetAtt = in.addAttribute(OffsetAttribute.class);

    in.reset();

    // Only temporarily holds states ahead of our current
    // position:

    final RollingBuffer<Position> positions = new Positions();

    int pos = -1;
    int freedPos = 0;
    Position posData = null;
    int maxOffset = 0;
    while (in.incrementToken()) {
      int posInc = posIncAtt.getPositionIncrement();
      if (preservePositionIncrements == false && posInc > 1) {
        posInc = 1;
      }
      assert pos > -1 || posInc > 0;

      if (posInc > 0) {

        // New node:
        pos += posInc;

        posData = positions.get(pos);
        assert posData.leaving == -1;

        if (posData.arriving == -1) {
          // No token ever arrived to this position
          if (pos == 0) {
            // OK: this is the first token
            posData.leaving = 0;
          } else {
            // This means there's a hole (eg, StopFilter
            // does this):
            posData.leaving = builder.createState();
            addHoles(builder, positions, pos);
          }
        } else {
          posData.leaving = builder.createState();
          builder.addTransition(posData.arriving, posData.leaving, POS_SEP);
          if (posInc > 1) {
            // A token spanned over a hole; add holes
            // "under" it:
            addHoles(builder, positions, pos);
          }
        }
        while (freedPos <= pos) {
          Position freePosData = positions.get(freedPos);
          // don't free this position yet if we may still need to fill holes over it:
          if (freePosData.arriving == -1 || freePosData.leaving == -1) {
            break;
          }
          positions.freeBefore(freedPos);
          freedPos++;
        }
      }

      final int endPos = pos + posLengthAtt.getPositionLength();

      final BytesRef termUTF8 = changeToken(termBytesAtt.getBytesRef());
      int[] termUnicode = null;
      final Position endPosData = positions.get(endPos);
      if (endPosData.arriving == -1) {
        endPosData.arriving = builder.createState();
      }

      int termLen;
      if (unicodeArcs) {
        final String utf16 = termUTF8.utf8ToString();
        termUnicode = new int[utf16.codePointCount(0, utf16.length())];
        termLen = termUnicode.length;
        for (int cp, i = 0, j = 0; i < utf16.length(); i += Character.charCount(cp)) {
          termUnicode[j++] = cp = utf16.codePointAt(i);
        }
      } else {
        termLen = termUTF8.length;
      }

      int state = posData.leaving;

      for(int byteIDX=0;byteIDX<termLen;byteIDX++) {
        final int nextState = byteIDX == termLen-1 ? endPosData.arriving : builder.createState();
        int c;
        if (unicodeArcs) {
          c = termUnicode[byteIDX];
        } else {
          c = termUTF8.bytes[termUTF8.offset + byteIDX] & 0xff;
        }
        builder.addTransition(state, nextState, c);
        state = nextState;
      }

      maxOffset = Math.max(maxOffset, offsetAtt.endOffset());
    }

    in.end();

    int endPosInc = posIncAtt.getPositionIncrement();
    if (endPosInc == 0 && finalOffsetGapAsHole && offsetAtt.endOffset() > maxOffset) {
      endPosInc = 1;
    } else if (endPosInc > 0 && preservePositionIncrements==false) {
      endPosInc = 0;
    }

    int endState;
    if (endPosInc > 0) {
      // there were hole(s) after the last token
      endState = builder.createState();

      // add trailing holes now:
      int lastState = endState;
      while (true) {
        int state1 = builder.createState();
        builder.addTransition(lastState, state1, HOLE);
        endPosInc--;
        if (endPosInc == 0) {
          builder.setAccept(state1, true);
          break;
        }
        int state2 = builder.createState();
        builder.addTransition(state1, state2, POS_SEP);
        lastState = state2;
      }
    } else {
      endState = -1;
    }

    pos++;
    while (pos <= positions.getMaxPos()) {
      posData = positions.get(pos);
      if (posData.arriving != -1) {
        if (endState != -1) {
          builder.addTransition(posData.arriving, endState, POS_SEP);
        } else {
          builder.setAccept(posData.arriving, true);
        }
      }
      pos++;
    }
    
    return builder.finish();
  }

  // for debugging!
  /*
  private static void toDot(Automaton a) throws IOException {
    final String s = a.toDot();
    Writer w = new OutputStreamWriter(new FileOutputStream("/tmp/out.dot"));
    w.write(s);
    w.close();
    System.out.println("TEST: saved to /tmp/out.dot");
  }
  */

  private static void addHoles(Automaton.Builder builder, RollingBuffer<Position> positions, int pos) {
    Position posData = positions.get(pos);
    Position prevPosData = positions.get(pos-1);

    while(posData.arriving == -1 || prevPosData.leaving == -1) {
      if (posData.arriving == -1) {
        posData.arriving = builder.createState();
        builder.addTransition(posData.arriving, posData.leaving, POS_SEP);
      }
      if (prevPosData.leaving == -1) {
        if (pos == 1) {
          prevPosData.leaving = 0;
        } else {
          prevPosData.leaving = builder.createState();
        }
        if (prevPosData.arriving != -1) {
          builder.addTransition(prevPosData.arriving, prevPosData.leaving, POS_SEP);
        }
      }
      builder.addTransition(prevPosData.leaving, posData.arriving, HOLE);
      pos--;
      if (pos <= 0) {
        break;
      }
      posData = prevPosData;
      prevPosData = positions.get(pos-1);
    }
  }
}
