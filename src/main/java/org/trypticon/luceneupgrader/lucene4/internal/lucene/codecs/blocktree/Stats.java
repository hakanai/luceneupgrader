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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.blocktree;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsBaseFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

public class Stats {
  public long indexNodeCount;

  public long indexArcCount;

  public long indexNumBytes;

  public long totalTermCount;

  public long totalTermBytes;

  public int nonFloorBlockCount;

  public int floorBlockCount;
    
  public int floorSubBlockCount;

  public int mixedBlockCount;

  public int termsOnlyBlockCount;

  public int subBlocksOnlyBlockCount;

  public int totalBlockCount;

  public int[] blockCountByPrefixLen = new int[10];
  private int startBlockCount;
  private int endBlockCount;

  public long totalBlockSuffixBytes;


  public long totalBlockStatsBytes;

  public long totalBlockOtherBytes;

  public final String segment;

  public final String field;

  Stats(String segment, String field) {
    this.segment = segment;
    this.field = field;
  }

  void startBlock(SegmentTermsEnumFrame frame, boolean isFloor) {
    totalBlockCount++;
    if (isFloor) {
      if (frame.fp == frame.fpOrig) {
        floorBlockCount++;
      }
      floorSubBlockCount++;
    } else {
      nonFloorBlockCount++;
    }

    if (blockCountByPrefixLen.length <= frame.prefix) {
      blockCountByPrefixLen = ArrayUtil.grow(blockCountByPrefixLen, 1+frame.prefix);
    }
    blockCountByPrefixLen[frame.prefix]++;
    startBlockCount++;
    totalBlockSuffixBytes += frame.suffixesReader.length();
    totalBlockStatsBytes += frame.statsReader.length();
  }

  void endBlock(SegmentTermsEnumFrame frame) {
    final int termCount = frame.isLeafBlock ? frame.entCount : frame.state.termBlockOrd;
    final int subBlockCount = frame.entCount - termCount;
    totalTermCount += termCount;
    if (termCount != 0 && subBlockCount != 0) {
      mixedBlockCount++;
    } else if (termCount != 0) {
      termsOnlyBlockCount++;
    } else if (subBlockCount != 0) {
      subBlocksOnlyBlockCount++;
    } else {
      throw new IllegalStateException();
    }
    endBlockCount++;
    final long otherBytes = frame.fpEnd - frame.fp - frame.suffixesReader.length() - frame.statsReader.length();
    assert otherBytes > 0 : "otherBytes=" + otherBytes + " frame.fp=" + frame.fp + " frame.fpEnd=" + frame.fpEnd;
    totalBlockOtherBytes += otherBytes;
  }

  void term(BytesRef term) {
    totalTermBytes += term.length;
  }

  void finish() {
    assert startBlockCount == endBlockCount: "startBlockCount=" + startBlockCount + " endBlockCount=" + endBlockCount;
    assert totalBlockCount == floorSubBlockCount + nonFloorBlockCount: "floorSubBlockCount=" + floorSubBlockCount + " nonFloorBlockCount=" + nonFloorBlockCount + " totalBlockCount=" + totalBlockCount;
    assert totalBlockCount == mixedBlockCount + termsOnlyBlockCount + subBlocksOnlyBlockCount: "totalBlockCount=" + totalBlockCount + " mixedBlockCount=" + mixedBlockCount + " subBlocksOnlyBlockCount=" + subBlocksOnlyBlockCount + " termsOnlyBlockCount=" + termsOnlyBlockCount;
  }

  @Override
  public String toString() {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
    PrintStream out;
    try {
      out = new PrintStream(bos, false, IOUtils.UTF_8);
    } catch (UnsupportedEncodingException bogus) {
      throw new RuntimeException(bogus);
    }
      
    out.println("  index FST:");
    out.println("    " + indexNodeCount + " nodes");
    out.println("    " + indexArcCount + " arcs");
    out.println("    " + indexNumBytes + " bytes");
    out.println("  terms:");
    out.println("    " + totalTermCount + " terms");
    out.println("    " + totalTermBytes + " bytes" + (totalTermCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalTermBytes)/totalTermCount) + " bytes/term)" : ""));
    out.println("  blocks:");
    out.println("    " + totalBlockCount + " blocks");
    out.println("    " + termsOnlyBlockCount + " terms-only blocks");
    out.println("    " + subBlocksOnlyBlockCount + " sub-block-only blocks");
    out.println("    " + mixedBlockCount + " mixed blocks");
    out.println("    " + floorBlockCount + " floor blocks");
    out.println("    " + (totalBlockCount-floorSubBlockCount) + " non-floor blocks");
    out.println("    " + floorSubBlockCount + " floor sub-blocks");
    out.println("    " + totalBlockSuffixBytes + " term suffix bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockSuffixBytes)/totalBlockCount) + " suffix-bytes/block)" : ""));
    out.println("    " + totalBlockStatsBytes + " term stats bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockStatsBytes)/totalBlockCount) + " stats-bytes/block)" : ""));
    out.println("    " + totalBlockOtherBytes + " other bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockOtherBytes)/totalBlockCount) + " other-bytes/block)" : ""));
    if (totalBlockCount != 0) {
      out.println("    by prefix length:");
      int total = 0;
      for(int prefix=0;prefix<blockCountByPrefixLen.length;prefix++) {
        final int blockCount = blockCountByPrefixLen[prefix];
        total += blockCount;
        if (blockCount != 0) {
          out.println("      " + String.format(Locale.ROOT, "%2d", prefix) + ": " + blockCount);
        }
      }
      assert totalBlockCount == total;
    }

    try {
      return bos.toString(IOUtils.UTF_8);
    } catch (UnsupportedEncodingException bogus) {
      throw new RuntimeException(bogus);
    }
  }
}
