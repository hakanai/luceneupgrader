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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.automaton;

import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.internal.hppc.IntArrayList;

// TODO
//   - do we really need the .bits...?  if not we can make util in UnicodeUtil to convert 1 char
// into a BytesRef

/**
 * Converts UTF-32 automata to the equivalent UTF-8 representation.
 *
 * @lucene.internal
 */
public final class UTF32ToUTF8 {

  // Unicode boundaries for UTF8 bytes 1,2,3,4
  private static final int[] startCodes = new int[] {0, 128, 2048, 65536};
  private static final int[] endCodes = new int[] {127, 2047, 65535, 1114111};

  static byte[] MASKS = new byte[8];

  static {
    for (int i = 0; i < 7; i++) {
      MASKS[i + 1] = (byte) ((2 << i) - 1);
    }
  }

  // Represents one of the N utf8 bytes that (in sequence)
  // define a code point.  value is the byte value; bits is
  // how many bits are "used" by utf8 at that byte
  private static class UTF8Byte {
    byte value;
    byte bits;
  }

  // Holds a single code point, as a sequence of 1-4 utf8 bytes:
  // TODO: maybe move to UnicodeUtil?
  private static class UTF8Sequence {
    private final UTF8Byte[] bytes;
    private int len;

    public UTF8Sequence() {
      bytes = new UTF8Byte[4];
      for (int i = 0; i < 4; i++) {
        bytes[i] = new UTF8Byte();
      }
    }

    public int byteAt(int idx) {
      return bytes[idx].value & 0xFF;
    }

    public int numBits(int idx) {
      return bytes[idx].bits;
    }

    private void set(int code) {
      if (code < 128) {
        // 0xxxxxxx
        bytes[0].value = (byte) code;
        bytes[0].bits = 7;
        len = 1;
      } else if (code < 2048) {
        // 110yyyxx 10xxxxxx
        bytes[0].value = (byte) ((6 << 5) | (code >> 6));
        bytes[0].bits = 5;
        setRest(code, 1);
        len = 2;
      } else if (code < 65536) {
        // 1110yyyy 10yyyyxx 10xxxxxx
        bytes[0].value = (byte) ((14 << 4) | (code >> 12));
        bytes[0].bits = 4;
        setRest(code, 2);
        len = 3;
      } else {
        // 11110zzz 10zzyyyy 10yyyyxx 10xxxxxx
        bytes[0].value = (byte) ((30 << 3) | (code >> 18));
        bytes[0].bits = 3;
        setRest(code, 3);
        len = 4;
      }
    }

    // Only set first byte value for tmp utf8.
    private void setFirstByte(int code) {
      if (code < 128) {
        // 0xxxxxxx
        bytes[0].value = (byte) code;
        len = 1;
      } else if (code < 2048) {
        // 110yyyxx 10xxxxxx
        bytes[0].value = (byte) ((6 << 5) | (code >> 6));
        len = 2;
      } else if (code < 65536) {
        // 1110yyyy 10yyyyxx 10xxxxxx
        bytes[0].value = (byte) ((14 << 4) | (code >> 12));
        len = 3;
      } else {
        // 11110zzz 10zzyyyy 10yyyyxx 10xxxxxx
        bytes[0].value = (byte) ((30 << 3) | (code >> 18));
        len = 4;
      }
    }

    private void setRest(int code, int numBytes) {
      for (int i = 0; i < numBytes; i++) {
        bytes[numBytes - i].value = (byte) (128 | (code & MASKS[6]));
        bytes[numBytes - i].bits = 6;
        code = code >> 6;
      }
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < len; i++) {
        if (i > 0) {
          b.append(' ');
        }
        b.append(Integer.toBinaryString(byteAt(i)));
      }
      return b.toString();
    }
  }

  /** Sole constructor. */
  public UTF32ToUTF8() {}

  private final UTF8Sequence startUTF8 = new UTF8Sequence();
  private final UTF8Sequence endUTF8 = new UTF8Sequence();

  private final UTF8Sequence tmpUTF8a = new UTF8Sequence();
  private final UTF8Sequence tmpUTF8b = new UTF8Sequence();

  // Builds necessary utf8 edges between start & end
  void convertOneEdge(int start, int end, int startCodePoint, int endCodePoint) {
    startUTF8.set(startCodePoint);
    endUTF8.set(endCodePoint);
    build(start, end, startUTF8, endUTF8, 0);
  }

  private void build(int start, int end, UTF8Sequence startUTF8, UTF8Sequence endUTF8, int upto) {

    // Break into start, middle, end:
    if (startUTF8.byteAt(upto) == endUTF8.byteAt(upto)) {
      // Degen case: lead with the same byte:
      if (upto == startUTF8.len - 1 && upto == endUTF8.len - 1) {
        // Super degen: just single edge, one UTF8 byte:
        utf8.addTransition(start, end, startUTF8.byteAt(upto), endUTF8.byteAt(upto));
        return;
      } else {
        assert startUTF8.len > upto + 1;
        assert endUTF8.len > upto + 1;
        int n = utf8.createState();

        // Single value leading edge
        utf8.addTransition(start, n, startUTF8.byteAt(upto));

        // Recurse for the rest
        build(n, end, startUTF8, endUTF8, 1 + upto);
      }
    } else if (startUTF8.len == endUTF8.len) {
      if (upto == startUTF8.len - 1) {
        utf8.addTransition(start, end, startUTF8.byteAt(upto), endUTF8.byteAt(upto));
      } else {
        start(start, end, startUTF8, upto, false);
        if (endUTF8.byteAt(upto) - startUTF8.byteAt(upto) > 1) {
          // There is a middle
          all(
              start,
              end,
              startUTF8.byteAt(upto) + 1,
              endUTF8.byteAt(upto) - 1,
              startUTF8.len - upto - 1);
        }
        end(start, end, endUTF8, upto, false);
      }
    } else {

      // start
      start(start, end, startUTF8, upto, true);

      // possibly middle, spanning multiple num bytes
      int byteCount = 1 + startUTF8.len - upto;
      final int limit = endUTF8.len - upto;
      while (byteCount < limit) {
        tmpUTF8a.setFirstByte(startCodes[byteCount - 1]);
        tmpUTF8b.setFirstByte(endCodes[byteCount - 1]);
        all(start, end, tmpUTF8a.byteAt(0), tmpUTF8b.byteAt(0), tmpUTF8a.len - 1);
        byteCount++;
      }

      // end
      end(start, end, endUTF8, upto, true);
    }
  }

  private void start(int start, int end, UTF8Sequence startUTF8, int upto, boolean doAll) {
    if (upto == startUTF8.len - 1) {
      // Done recursing
      utf8.addTransition(
          start,
          end,
          startUTF8.byteAt(upto),
          startUTF8.byteAt(upto) | MASKS[startUTF8.numBits(upto)]); // type=start
    } else {
      int n = utf8.createState();
      utf8.addTransition(start, n, startUTF8.byteAt(upto));
      start(n, end, startUTF8, 1 + upto, true);
      int endCode = startUTF8.byteAt(upto) | MASKS[startUTF8.numBits(upto)];
      if (doAll && startUTF8.byteAt(upto) != endCode) {
        all(start, end, startUTF8.byteAt(upto) + 1, endCode, startUTF8.len - upto - 1);
      }
    }
  }

  private void end(int start, int end, UTF8Sequence endUTF8, int upto, boolean doAll) {
    if (upto == endUTF8.len - 1) {
      // Done recursing
      utf8.addTransition(
          start, end, endUTF8.byteAt(upto) & (~MASKS[endUTF8.numBits(upto)]), endUTF8.byteAt(upto));
    } else {
      final int startCode;
      // GH-ISSUE#12472: UTF-8 special case for the different start byte of the different
      // length=2,3,4
      if (endUTF8.len == 2) {
        assert upto == 0; // the upto==1 case will be handled by the first if above
        // the first length=2 UTF8 Unicode character is C2 80,
        // so we must special case 0xC2 as the 1st byte.
        startCode = 0xC2;
      } else if (endUTF8.len == 3 && upto == 1 && endUTF8.byteAt(0) == 0xE0) {
        // the first length=3 UTF8 Unicode character is E0 A0 80,
        // so we must special case 0xA0 as the 2nd byte when E0 was the first byte of endUTF8.
        startCode = 0xA0;
      } else if (endUTF8.len == 4 && upto == 1 && endUTF8.byteAt(0) == 0xF0) {
        // the first length=4 UTF8 Unicode character is F0 90 80 80,
        // so we must special case 0x90 as the 2nd byte when F0 was the first byte of endUTF8.
        startCode = 0x90;
      } else {
        startCode = endUTF8.byteAt(upto) & (~MASKS[endUTF8.numBits(upto)]);
      }
      if (doAll && endUTF8.byteAt(upto) != startCode) {
        all(start, end, startCode, endUTF8.byteAt(upto) - 1, endUTF8.len - upto - 1);
      }
      int n = utf8.createState();
      utf8.addTransition(start, n, endUTF8.byteAt(upto));
      end(n, end, endUTF8, 1 + upto, true);
    }
  }

  private void all(int start, int end, int startCode, int endCode, int left) {
    if (left == 0) {
      utf8.addTransition(start, end, startCode, endCode);
    } else {
      int lastN = utf8.createState();
      utf8.addTransition(start, lastN, startCode, endCode);
      while (left > 1) {
        int n = utf8.createState();
        utf8.addTransition(lastN, n, 128, 191); // type=all*
        left--;
        lastN = n;
      }
      utf8.addTransition(lastN, end, 128, 191); // type = all*
    }
  }

  Automaton.Builder utf8;

  /**
   * Converts an incoming utf32 automaton to an equivalent utf8 one. The incoming automaton need not
   * be deterministic. Note that the returned automaton will not in general be deterministic, so you
   * must determinize it if that's needed.
   */
  public Automaton convert(Automaton utf32) {
    if (utf32.getNumStates() == 0) {
      return utf32;
    }

    int[] map = new int[utf32.getNumStates()];
    Arrays.fill(map, -1);

    IntArrayList pending = new IntArrayList();
    int utf32State = 0;
    pending.add(utf32State);
    utf8 = new Automaton.Builder();

    int utf8State = utf8.createState();

    utf8.setAccept(utf8State, utf32.isAccept(utf32State));

    map[utf32State] = utf8State;

    Transition scratch = new Transition();

    while (pending.size() != 0) {
      utf32State = pending.removeLast();
      utf8State = map[utf32State];
      assert utf8State != -1;

      int numTransitions = utf32.getNumTransitions(utf32State);
      utf32.initTransition(utf32State, scratch);
      for (int i = 0; i < numTransitions; i++) {
        utf32.getNextTransition(scratch);
        int destUTF32 = scratch.dest;
        int destUTF8 = map[destUTF32];
        if (destUTF8 == -1) {
          destUTF8 = utf8.createState();
          utf8.setAccept(destUTF8, utf32.isAccept(destUTF32));
          map[destUTF32] = destUTF8;
          pending.add(destUTF32);
        }

        // Writes new transitions into pendingTransitions:
        convertOneEdge(utf8State, destUTF8, scratch.min, scratch.max);
      }
    }

    return utf8.finish();
  }
}
