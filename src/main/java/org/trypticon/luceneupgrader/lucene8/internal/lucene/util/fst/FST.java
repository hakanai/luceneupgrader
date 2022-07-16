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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.util.fst;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.InputStreamDataInput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.OutputStreamDataOutput;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.store.RAMOutputStream;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.RamUsageEstimator;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.util.fst.FST.Arc.BitTable;

// TODO: break this into WritableFST and ReadOnlyFST.. then
// we can have subclasses of ReadOnlyFST to handle the
// different byte[] level encodings (packed or
// not)... and things like nodeCount, arcCount are read only

// TODO: if FST is pure prefix trie we can do a more compact
// job, ie, once we are at a 'suffix only', just store the
// completion labels as a string not as a series of arcs.

// NOTE: while the FST is able to represent a non-final
// dead-end state (NON_FINAL_END_NODE=0), the layers above
// (FSTEnum, Util) have problems with this!!

public final class FST<T> implements Accountable {

  public enum INPUT_TYPE {BYTE1, BYTE2, BYTE4}

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(FST.class);
  private static final long ARC_SHALLOW_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(Arc.class);

  private static final int BIT_FINAL_ARC = 1 << 0;
  static final int BIT_LAST_ARC = 1 << 1;
  static final int BIT_TARGET_NEXT = 1 << 2;

  // TODO: we can free up a bit if we can nuke this:
  private static final int BIT_STOP_NODE = 1 << 3;

  public static final int BIT_ARC_HAS_OUTPUT = 1 << 4;

  private static final int BIT_ARC_HAS_FINAL_OUTPUT = 1 << 5;

  // We use this as a marker because this one flag is illegal by itself.
  public static final byte ARCS_FOR_BINARY_SEARCH = BIT_ARC_HAS_FINAL_OUTPUT;

  static final byte ARCS_FOR_DIRECT_ADDRESSING = 1 << 6;

  static final int FIXED_LENGTH_ARC_SHALLOW_DEPTH = 3; // 0 => only root node.

  static final int FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS = 5;

  static final int FIXED_LENGTH_ARC_DEEP_NUM_ARCS = 10;

  private static final float DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR = 1.66f;

  // Increment version to change it
  private static final String FILE_FORMAT_NAME = "FST";
  private static final int VERSION_START = 6;
  private static final int VERSION_CURRENT = 7;

  // Never serialized; just used to represent the virtual
  // final node w/ no arcs:
  private static final long FINAL_END_NODE = -1;

  // Never serialized; just used to represent the virtual
  // non-final node w/ no arcs:
  private static final long NON_FINAL_END_NODE = 0;

  public static final int END_LABEL = -1;

  final INPUT_TYPE inputType;

  // if non-null, this FST accepts the empty string and
  // produces this output
  T emptyOutput;

  final BytesStore bytes;

  private final FSTStore fstStore;

  private long startNode = -1;

  public final Outputs<T> outputs;

  public static final class Arc<T> {

    //*** Arc fields.

    private int label;

    private T output;

    private long target;

    private byte flags;

    private T nextFinalOutput;

    private long nextArc;

    private byte nodeFlags;

    //*** Fields for arcs belonging to a node with fixed length arcs.
    // So only valid when bytesPerArc != 0.
    // nodeFlags == ARCS_FOR_BINARY_SEARCH || nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.

    private int bytesPerArc;

    private long posArcsStart;

    private int arcIdx;

    private int numArcs;

    //*** Fields for a direct addressing node. nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.

    private long bitTableStart;

    private int firstLabel;

    private int presenceIndex;

    public Arc<T> copyFrom(Arc<T> other) {
      label = other.label();
      target = other.target();
      flags = other.flags();
      output = other.output();
      nextFinalOutput = other.nextFinalOutput();
      nextArc = other.nextArc();
      nodeFlags = other.nodeFlags();
      bytesPerArc = other.bytesPerArc();

      // Fields for arcs belonging to a node with fixed length arcs.
      // We could avoid copying them if bytesPerArc() == 0 (this was the case with previous code, and the current code
      // still supports that), but it may actually help external uses of FST to have consistent arc state, and debugging
      // is easier.
      posArcsStart = other.posArcsStart();
      arcIdx = other.arcIdx();
      numArcs = other.numArcs();
      bitTableStart = other.bitTableStart;
      firstLabel = other.firstLabel();
      presenceIndex = other.presenceIndex;

      return this;
    }
    
    boolean flag(int flag) {
      return FST.flag(flags, flag);
    }

    public boolean isLast() {
      return flag(BIT_LAST_ARC);
    }

    public boolean isFinal() {
      return flag(BIT_FINAL_ARC);
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(" target=").append(target());
      b.append(" label=0x").append(Integer.toHexString(label()));
      if (flag(BIT_FINAL_ARC)) {
        b.append(" final");
      }
      if (flag(BIT_LAST_ARC)) {
        b.append(" last");
      }
      if (flag(BIT_TARGET_NEXT)) {
        b.append(" targetNext");
      }
      if (flag(BIT_STOP_NODE)) {
        b.append(" stop");
      }
      if (flag(BIT_ARC_HAS_OUTPUT)) {
        b.append(" output=").append(output());
      }
      if (flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
        b.append(" nextFinalOutput=").append(nextFinalOutput());
      }
      if (bytesPerArc() != 0) {
        b.append(" arcArray(idx=").append(arcIdx()).append(" of ").append(numArcs()).append(")")
            .append("(").append(nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING ? "da" : "bs").append(")");
      }
      return b.toString();
    }

    public int label() {
      return label;
    }

    public T output() {
      return output;
    }

    public long target() {
      return target;
    }

    public byte flags() {
      return flags;
    }

    public T nextFinalOutput() {
      return nextFinalOutput;
    }

     long nextArc() {
      return nextArc;
    }

    public int arcIdx() {
      return arcIdx;
    }

    public byte nodeFlags() {
      return nodeFlags;
    }

    public long posArcsStart() {
      return posArcsStart;
    }

    public int bytesPerArc() {
      return bytesPerArc;
    }

    public int numArcs() {
      return numArcs;
    }

    int firstLabel() {
      return firstLabel;
    }

    static class BitTable {

      static boolean isBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.isBitSet(bitIndex, in);
      }

      static int countBits(Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.countBits(getNumPresenceBytes(arc.numArcs()), in);
      }

      static int countBitsUpTo(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.countBitsUpTo(bitIndex, in);
      }

      static int nextBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.nextBitSet(bitIndex, getNumPresenceBytes(arc.numArcs()), in);
      }

      static int previousBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.previousBitSet(bitIndex, in);
      }

      static boolean assertIsValid(Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.bytesPerArc() > 0;
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        // First bit must be set.
        assert isBitSet(0, arc, in);
        // Last bit must be set.
        assert isBitSet(arc.numArcs() - 1, arc, in);
        // No bit set after the last arc.
        assert nextBitSet(arc.numArcs() - 1, arc, in) == -1;
        return true;
      }
    }
  }

  private static boolean flag(int flags, int bit) {
    return (flags & bit) != 0;
  }

  // make a new empty FST, for building; Builder invokes this
  FST(INPUT_TYPE inputType, Outputs<T> outputs, int bytesPageBits) {
    this.inputType = inputType;
    this.outputs = outputs;
    fstStore = null;
    bytes = new BytesStore(bytesPageBits);
    // pad: ensure no node gets address 0 which is reserved to mean
    // the stop state w/ no arcs
    bytes.writeByte((byte) 0);
    emptyOutput = null;
  }

  private static final int DEFAULT_MAX_BLOCK_BITS = Constants.JRE_IS_64BIT ? 30 : 28;

  public FST(DataInput metaIn, DataInput in, Outputs<T> outputs) throws IOException {
    this(metaIn, in, outputs, new OnHeapFSTStore(DEFAULT_MAX_BLOCK_BITS));
  }

  public FST(DataInput metaIn, DataInput in, Outputs<T> outputs, FSTStore fstStore) throws IOException {
    bytes = null;
    this.fstStore = fstStore;
    this.outputs = outputs;

    // NOTE: only reads formats VERSION_START up to VERSION_CURRENT; we don't have
    // back-compat promise for FSTs (they are experimental), but we are sometimes able to offer it
    CodecUtil.checkHeader(metaIn, FILE_FORMAT_NAME, VERSION_START, VERSION_CURRENT);
    if (metaIn.readByte() == 1) {
      // accepts empty string
      // 1 KB blocks:
      BytesStore emptyBytes = new BytesStore(10);
      int numBytes = metaIn.readVInt();
      emptyBytes.copyBytes(metaIn, numBytes);

      // De-serialize empty-string output:
      BytesReader reader = emptyBytes.getReverseReader();
      // NoOutputs uses 0 bytes when writing its output,
      // so we have to check here else BytesStore gets
      // angry:
      if (numBytes > 0) {
        reader.setPosition(numBytes-1);
      }
      emptyOutput = outputs.readFinalOutput(reader);
    } else {
      emptyOutput = null;
    }
    final byte t = metaIn.readByte();
    switch(t) {
      case 0:
        inputType = INPUT_TYPE.BYTE1;
        break;
      case 1:
        inputType = INPUT_TYPE.BYTE2;
        break;
      case 2:
        inputType = INPUT_TYPE.BYTE4;
        break;
    default:
      throw new CorruptIndexException("invalid input type " + t, in);
    }
    startNode = metaIn.readVLong();

    long numBytes = metaIn.readVLong();
    this.fstStore.init(in, numBytes);
  }

  @Override
  public long ramBytesUsed() {
    long size = BASE_RAM_BYTES_USED;
    if (this.fstStore != null) {
      size += this.fstStore.ramBytesUsed();
    } else {
      size += bytes.ramBytesUsed();
    }

    return size;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(input=" + inputType + ",output=" + outputs;
  }

  void finish(long newStartNode) throws IOException {
    assert newStartNode <= bytes.getPosition();
    if (startNode != -1) {
      throw new IllegalStateException("already finished");
    }
    if (newStartNode == FINAL_END_NODE && emptyOutput != null) {
      newStartNode = 0;
    }
    startNode = newStartNode;
    bytes.finish();
  }
  
  public T getEmptyOutput() {
    return emptyOutput;
  }

  void setEmptyOutput(T v) {
    if (emptyOutput != null) {
      emptyOutput = outputs.merge(emptyOutput, v);
    } else {
      emptyOutput = v;
    }
  }

  public void save(DataOutput metaOut, DataOutput out) throws IOException {
    if (startNode == -1) {
      throw new IllegalStateException("call finish first");
    }
    CodecUtil.writeHeader(metaOut, FILE_FORMAT_NAME, VERSION_CURRENT);
    // TODO: really we should encode this as an arc, arriving
    // to the root node, instead of special casing here:
    if (emptyOutput != null) {
      // Accepts empty string
      metaOut.writeByte((byte) 1);

      // Serialize empty-string output:
      RAMOutputStream ros = new RAMOutputStream();
      outputs.writeFinalOutput(emptyOutput, ros);
      
      byte[] emptyOutputBytes = new byte[(int) ros.getFilePointer()];
      ros.writeTo(emptyOutputBytes, 0);
      int emptyLen = emptyOutputBytes.length;

      // reverse
      final int stopAt = emptyLen / 2;
      int upto = 0;
      while(upto < stopAt) {
        final byte b = emptyOutputBytes[upto];
        emptyOutputBytes[upto] = emptyOutputBytes[emptyLen - upto - 1];
        emptyOutputBytes[emptyLen - upto - 1] = b;
        upto++;
      }
      metaOut.writeVInt(emptyLen);
      metaOut.writeBytes(emptyOutputBytes, 0, emptyLen);
    } else {
      metaOut.writeByte((byte) 0);
    }
    final byte t;
    if (inputType == INPUT_TYPE.BYTE1) {
      t = 0;
    } else if (inputType == INPUT_TYPE.BYTE2) {
      t = 1;
    } else {
      t = 2;
    }
    metaOut.writeByte(t);
    metaOut.writeVLong(startNode);
    if (bytes != null) {
      long numBytes = bytes.getPosition();
      metaOut.writeVLong(numBytes);
      bytes.writeTo(out);
    } else {
      assert fstStore != null;
      fstStore.writeTo(out);
    }
  }
  
  public void save(final Path path) throws IOException {
    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(path))) {
      DataOutput out = new OutputStreamDataOutput(os);
      save(out, out);
    }
  }

  public static <T> FST<T> read(Path path, Outputs<T> outputs) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      DataInput in = new InputStreamDataInput(new BufferedInputStream(is));
      return new FST<>(in, in, outputs);
    }
  }

  private void writeLabel(DataOutput out, int v) throws IOException {
    assert v >= 0: "v=" + v;
    if (inputType == INPUT_TYPE.BYTE1) {
      assert v <= 255: "v=" + v;
      out.writeByte((byte) v);
    } else if (inputType == INPUT_TYPE.BYTE2) {
      assert v <= 65535: "v=" + v;
      out.writeShort((short) v);
    } else {
      out.writeVInt(v);
    }
  }

  public int readLabel(DataInput in) throws IOException {
    final int v;
    if (inputType == INPUT_TYPE.BYTE1) {
      // Unsigned byte:
      v = in.readByte() & 0xFF;
    } else if (inputType == INPUT_TYPE.BYTE2) {
      // Unsigned short:
      v = in.readShort() & 0xFFFF;
    } else { 
      v = in.readVInt();
    }
    return v;
  }

  public static<T> boolean targetHasArcs(Arc<T> arc) {
    return arc.target() > 0;
  }

  // serializes new node by appending its bytes to the end
  // of the current byte[]
  long addNode(Builder<T> builder, Builder.UnCompiledNode<T> nodeIn) throws IOException {
    T NO_OUTPUT = outputs.getNoOutput();

    //System.out.println("FST.addNode pos=" + bytes.getPosition() + " numArcs=" + nodeIn.numArcs);
    if (nodeIn.numArcs == 0) {
      if (nodeIn.isFinal) {
        return FINAL_END_NODE;
      } else {
        return NON_FINAL_END_NODE;
      }
    }
    final long startAddress = builder.bytes.getPosition();
    //System.out.println("  startAddr=" + startAddress);

    final boolean doFixedLengthArcs = shouldExpandNodeWithFixedLengthArcs(builder, nodeIn);
    if (doFixedLengthArcs) {
      //System.out.println("  fixed length arcs");
      if (builder.numBytesPerArc.length < nodeIn.numArcs) {
        builder.numBytesPerArc = new int[ArrayUtil.oversize(nodeIn.numArcs, Integer.BYTES)];
        builder.numLabelBytesPerArc = new int[builder.numBytesPerArc.length];
      }
    }

    builder.arcCount += nodeIn.numArcs;
    
    final int lastArc = nodeIn.numArcs-1;

    long lastArcStart = builder.bytes.getPosition();
    int maxBytesPerArc = 0;
    int maxBytesPerArcWithoutLabel = 0;
    for(int arcIdx=0; arcIdx < nodeIn.numArcs; arcIdx++) {
      final Builder.Arc<T> arc = nodeIn.arcs[arcIdx];
      final Builder.CompiledNode target = (Builder.CompiledNode) arc.target;
      int flags = 0;
      //System.out.println("  arc " + arcIdx + " label=" + arc.label + " -> target=" + target.node);

      if (arcIdx == lastArc) {
        flags += BIT_LAST_ARC;
      }

      if (builder.lastFrozenNode == target.node && !doFixedLengthArcs) {
        // TODO: for better perf (but more RAM used) we
        // could avoid this except when arc is "near" the
        // last arc:
        flags += BIT_TARGET_NEXT;
      }

      if (arc.isFinal) {
        flags += BIT_FINAL_ARC;
        if (arc.nextFinalOutput != NO_OUTPUT) {
          flags += BIT_ARC_HAS_FINAL_OUTPUT;
        }
      } else {
        assert arc.nextFinalOutput == NO_OUTPUT;
      }

      boolean targetHasArcs = target.node > 0;

      if (!targetHasArcs) {
        flags += BIT_STOP_NODE;
      }

      if (arc.output != NO_OUTPUT) {
        flags += BIT_ARC_HAS_OUTPUT;
      }

      builder.bytes.writeByte((byte) flags);
      long labelStart = builder.bytes.getPosition();
      writeLabel(builder.bytes, arc.label);
      int numLabelBytes = (int) (builder.bytes.getPosition() - labelStart);

      // System.out.println("  write arc: label=" + (char) arc.label + " flags=" + flags + " target=" + target.node + " pos=" + bytes.getPosition() + " output=" + outputs.outputToString(arc.output));

      if (arc.output != NO_OUTPUT) {
        outputs.write(arc.output, builder.bytes);
        //System.out.println("    write output");
      }

      if (arc.nextFinalOutput != NO_OUTPUT) {
        //System.out.println("    write final output");
        outputs.writeFinalOutput(arc.nextFinalOutput, builder.bytes);
      }

      if (targetHasArcs && (flags & BIT_TARGET_NEXT) == 0) {
        assert target.node > 0;
        //System.out.println("    write target");
        builder.bytes.writeVLong(target.node);
      }

      // just write the arcs "like normal" on first pass, but record how many bytes each one took
      // and max byte size:
      if (doFixedLengthArcs) {
        int numArcBytes = (int) (builder.bytes.getPosition() - lastArcStart);
        builder.numBytesPerArc[arcIdx] = numArcBytes;
        builder.numLabelBytesPerArc[arcIdx] = numLabelBytes;
        lastArcStart = builder.bytes.getPosition();
        maxBytesPerArc = Math.max(maxBytesPerArc, numArcBytes);
        maxBytesPerArcWithoutLabel = Math.max(maxBytesPerArcWithoutLabel, numArcBytes - numLabelBytes);
        //System.out.println("    arcBytes=" + numArcBytes + " labelBytes=" + numLabelBytes);
      }
    }

    // TODO: try to avoid wasteful cases: disable doFixedLengthArcs in that case
    /* 
     * 
     * LUCENE-4682: what is a fair heuristic here?
     * It could involve some of these:
     * 1. how "busy" the node is: nodeIn.inputCount relative to frontier[0].inputCount?
     * 2. how much binSearch saves over scan: nodeIn.numArcs
     * 3. waste: numBytes vs numBytesExpanded
     * 
     * the one below just looks at #3
    if (doFixedLengthArcs) {
      // rough heuristic: make this 1.25 "waste factor" a parameter to the phd ctor????
      int numBytes = lastArcStart - startAddress;
      int numBytesExpanded = maxBytesPerArc * nodeIn.numArcs;
      if (numBytesExpanded > numBytes*1.25) {
        doFixedLengthArcs = false;
      }
    }
    */

    if (doFixedLengthArcs) {
      assert maxBytesPerArc > 0;
      // 2nd pass just "expands" all arcs to take up a fixed byte size

      int labelRange = nodeIn.arcs[nodeIn.numArcs - 1].label - nodeIn.arcs[0].label + 1;
      assert labelRange > 0;
      if (shouldExpandNodeWithDirectAddressing(builder, nodeIn, maxBytesPerArc, maxBytesPerArcWithoutLabel, labelRange)) {
        writeNodeForDirectAddressing(builder, nodeIn, startAddress, maxBytesPerArcWithoutLabel, labelRange);
        builder.directAddressingNodeCount++;
      } else {
        writeNodeForBinarySearch(builder, nodeIn, startAddress, maxBytesPerArc);
        builder.binarySearchNodeCount++;
      }
    }

    final long thisNodeAddress = builder.bytes.getPosition()-1;
    builder.bytes.reverse(startAddress, thisNodeAddress);
    builder.nodeCount++;
    return thisNodeAddress;
  }

  private boolean shouldExpandNodeWithFixedLengthArcs(Builder<T> builder, Builder.UnCompiledNode<T> node) {
    return builder.allowFixedLengthArcs &&
        ((node.depth <= FIXED_LENGTH_ARC_SHALLOW_DEPTH && node.numArcs >= FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS) ||
            node.numArcs >= FIXED_LENGTH_ARC_DEEP_NUM_ARCS);
  }

  private boolean shouldExpandNodeWithDirectAddressing(Builder<T> builder, Builder.UnCompiledNode<T> nodeIn,
                                                       int numBytesPerArc, int maxBytesPerArcWithoutLabel, int labelRange) {
    // Anticipate precisely the size of the encodings.
    int sizeForBinarySearch = numBytesPerArc * nodeIn.numArcs;
    int sizeForDirectAddressing = getNumPresenceBytes(labelRange) + builder.numLabelBytesPerArc[0]
        + maxBytesPerArcWithoutLabel * nodeIn.numArcs;

    // Determine the allowed oversize compared to binary search.
    // This is defined by a parameter of FST Builder (default 1: no oversize).
    int allowedOversize = (int) (sizeForBinarySearch * builder.getDirectAddressingMaxOversizingFactor());
    int expansionCost = sizeForDirectAddressing - allowedOversize;

    // Select direct addressing if either:
    // - Direct addressing size is smaller than binary search.
    //   In this case, increment the credit by the reduced size (to use it later).
    // - Direct addressing size is larger than binary search, but the positive credit allows the oversizing.
    //   In this case, decrement the credit by the oversize.
    // In addition, do not try to oversize to a clearly too large node size
    // (this is the DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR parameter).
    if (expansionCost <= 0 || (builder.directAddressingExpansionCredit >= expansionCost
        && sizeForDirectAddressing <= allowedOversize * DIRECT_ADDRESSING_MAX_OVERSIZE_WITH_CREDIT_FACTOR)) {
      builder.directAddressingExpansionCredit -= expansionCost;
      return true;
    }
    return false;
  }

  private void writeNodeForBinarySearch(Builder<T> builder, Builder.UnCompiledNode<T> nodeIn, long startAddress, int maxBytesPerArc) {
    // Build the header in a buffer.
    // It is a false/special arc which is in fact a node header with node flags followed by node metadata.
    builder.fixedLengthArcsBuffer
        .resetPosition()
        .writeByte(ARCS_FOR_BINARY_SEARCH)
        .writeVInt(nodeIn.numArcs)
        .writeVInt(maxBytesPerArc);
    int headerLen = builder.fixedLengthArcsBuffer.getPosition();

    // Expand the arcs in place, backwards.
    long srcPos = builder.bytes.getPosition();
    long destPos = startAddress + headerLen + nodeIn.numArcs * maxBytesPerArc;
    assert destPos >= srcPos;
    if (destPos > srcPos) {
      builder.bytes.skipBytes((int) (destPos - srcPos));
      for (int arcIdx = nodeIn.numArcs - 1; arcIdx >= 0; arcIdx--) {
        destPos -= maxBytesPerArc;
        int arcLen = builder.numBytesPerArc[arcIdx];
        srcPos -= arcLen;
        if (srcPos != destPos) {
          assert destPos > srcPos: "destPos=" + destPos + " srcPos=" + srcPos + " arcIdx=" + arcIdx + " maxBytesPerArc=" + maxBytesPerArc + " arcLen=" + arcLen + " nodeIn.numArcs=" + nodeIn.numArcs;
          builder.bytes.copyBytes(srcPos, destPos, arcLen);
        }
      }
    }

    // Write the header.
    builder.bytes.writeBytes(startAddress, builder.fixedLengthArcsBuffer.getBytes(), 0, headerLen);
  }

  private void writeNodeForDirectAddressing(Builder<T> builder, Builder.UnCompiledNode<T> nodeIn, long startAddress, int maxBytesPerArcWithoutLabel, int labelRange) {
    // Expand the arcs backwards in a buffer because we remove the labels.
    // So the obtained arcs might occupy less space. This is the reason why this
    // whole method is more complex.
    // Drop the label bytes since we can infer the label based on the arc index,
    // the presence bits, and the first label. Keep the first label.
    int headerMaxLen = 11;
    int numPresenceBytes = getNumPresenceBytes(labelRange);
    long srcPos = builder.bytes.getPosition();
    int totalArcBytes = builder.numLabelBytesPerArc[0] + nodeIn.numArcs * maxBytesPerArcWithoutLabel;
    int bufferOffset = headerMaxLen + numPresenceBytes + totalArcBytes;
    byte[] buffer = builder.fixedLengthArcsBuffer.ensureCapacity(bufferOffset).getBytes();
    // Copy the arcs to the buffer, dropping all labels except first one.
    for (int arcIdx = nodeIn.numArcs - 1; arcIdx >= 0; arcIdx--) {
      bufferOffset -= maxBytesPerArcWithoutLabel;
      int srcArcLen = builder.numBytesPerArc[arcIdx];
      srcPos -= srcArcLen;
      int labelLen = builder.numLabelBytesPerArc[arcIdx];
      // Copy the flags.
      builder.bytes.copyBytes(srcPos, buffer, bufferOffset, 1);
      // Skip the label, copy the remaining.
      int remainingArcLen = srcArcLen - 1 - labelLen;
      if (remainingArcLen != 0) {
        builder.bytes.copyBytes(srcPos + 1 + labelLen, buffer, bufferOffset + 1, remainingArcLen);
      }
      if (arcIdx == 0) {
        // Copy the label of the first arc only.
        bufferOffset -= labelLen;
        builder.bytes.copyBytes(srcPos + 1, buffer, bufferOffset, labelLen);
      }
    }
    assert bufferOffset == headerMaxLen + numPresenceBytes;

    // Build the header in the buffer.
    // It is a false/special arc which is in fact a node header with node flags followed by node metadata.
    builder.fixedLengthArcsBuffer
        .resetPosition()
        .writeByte(ARCS_FOR_DIRECT_ADDRESSING)
        .writeVInt(labelRange) // labelRange instead of numArcs.
        .writeVInt(maxBytesPerArcWithoutLabel); // maxBytesPerArcWithoutLabel instead of maxBytesPerArc.
    int headerLen = builder.fixedLengthArcsBuffer.getPosition();

    // Prepare the builder byte store. Enlarge or truncate if needed.
    long nodeEnd = startAddress + headerLen + numPresenceBytes + totalArcBytes;
    long currentPosition = builder.bytes.getPosition();
    if (nodeEnd >= currentPosition) {
      builder.bytes.skipBytes((int) (nodeEnd - currentPosition));
    } else {
      builder.bytes.truncate(nodeEnd);
    }
    assert builder.bytes.getPosition() == nodeEnd;

    // Write the header.
    long writeOffset = startAddress;
    builder.bytes.writeBytes(writeOffset, builder.fixedLengthArcsBuffer.getBytes(), 0, headerLen);
    writeOffset += headerLen;

    // Write the presence bits
    writePresenceBits(builder, nodeIn, writeOffset, numPresenceBytes);
    writeOffset += numPresenceBytes;

    // Write the first label and the arcs.
    builder.bytes.writeBytes(writeOffset, builder.fixedLengthArcsBuffer.getBytes(), bufferOffset, totalArcBytes);
  }

  private void writePresenceBits(Builder<T> builder, Builder.UnCompiledNode<T> nodeIn, long dest, int numPresenceBytes) {
    long bytePos = dest;
    byte presenceBits = 1; // The first arc is always present.
    int presenceIndex = 0;
    int previousLabel = nodeIn.arcs[0].label;
    for (int arcIdx = 1; arcIdx < nodeIn.numArcs; arcIdx++) {
      int label = nodeIn.arcs[arcIdx].label;
      assert label > previousLabel;
      presenceIndex += label - previousLabel;
      while (presenceIndex >= Byte.SIZE) {
        builder.bytes.writeByte(bytePos++, presenceBits);
        presenceBits = 0;
        presenceIndex -= Byte.SIZE;
      }
      // Set the bit at presenceIndex to flag that the corresponding arc is present.
      presenceBits |= 1 << presenceIndex;
      previousLabel = label;
    }
    assert presenceIndex == (nodeIn.arcs[nodeIn.numArcs - 1].label - nodeIn.arcs[0].label) % 8;
    assert presenceBits != 0; // The last byte is not 0.
    assert (presenceBits & (1 << presenceIndex)) != 0; // The last arc is always present.
    builder.bytes.writeByte(bytePos++, presenceBits);
    assert bytePos - dest == numPresenceBytes;
  }

  private static int getNumPresenceBytes(int labelRange) {
    assert labelRange >= 0;
    return (labelRange + 7) >> 3;
  }

  private void readPresenceBytes(Arc<T> arc, BytesReader in) throws IOException {
    assert arc.bytesPerArc() > 0;
    assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
    arc.bitTableStart = in.getPosition();
    in.skipBytes(getNumPresenceBytes(arc.numArcs()));
  }

  public Arc<T> getFirstArc(Arc<T> arc) {
    T NO_OUTPUT = outputs.getNoOutput();

    if (emptyOutput != null) {
      arc.flags = BIT_FINAL_ARC | BIT_LAST_ARC;
      arc.nextFinalOutput = emptyOutput;
      if (emptyOutput != NO_OUTPUT) {
        arc.flags = (byte) (arc.flags() | BIT_ARC_HAS_FINAL_OUTPUT);
      }
    } else {
      arc.flags = BIT_LAST_ARC;
      arc.nextFinalOutput = NO_OUTPUT;
    }
    arc.output = NO_OUTPUT;

    // If there are no nodes, ie, the FST only accepts the
    // empty string, then startNode is 0
    arc.target = startNode;
    return arc;
  }

  Arc<T> readLastTargetArc(Arc<T> follow, Arc<T> arc, BytesReader in) throws IOException {
    //System.out.println("readLast");
    if (!targetHasArcs(follow)) {
      //System.out.println("  end node");
      assert follow.isFinal();
      arc.label = END_LABEL;
      arc.target = FINAL_END_NODE;
      arc.output = follow.nextFinalOutput();
      arc.flags = BIT_LAST_ARC;
      arc.nodeFlags = arc.flags;
      return arc;
    } else {
      in.setPosition(follow.target());
      byte flags = arc.nodeFlags = in.readByte();
      if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING) {
        // Special arc which is actually a node header for fixed length arcs.
        // Jump straight to end to find the last arc.
        arc.numArcs = in.readVInt();
        arc.bytesPerArc = in.readVInt();
        //System.out.println("  array numArcs=" + arc.numArcs + " bpa=" + arc.bytesPerArc);
        if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
          readPresenceBytes(arc, in);
          arc.firstLabel = readLabel(in);
          arc.posArcsStart = in.getPosition();
          readLastArcByDirectAddressing(arc, in);
        } else {
          arc.arcIdx = arc.numArcs() - 2;
          arc.posArcsStart = in.getPosition();
          readNextRealArc(arc, in);
        }
      } else {
        arc.flags = flags;
        // non-array: linear scan
        arc.bytesPerArc = 0;
        //System.out.println("  scan");
        while(!arc.isLast()) {
          // skip this arc:
          readLabel(in);
          if (arc.flag(BIT_ARC_HAS_OUTPUT)) {
            outputs.skipOutput(in);
          }
          if (arc.flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
            outputs.skipFinalOutput(in);
          }
          if (arc.flag(BIT_STOP_NODE)) {
          } else if (arc.flag(BIT_TARGET_NEXT)) {
          } else {
            readUnpackedNodeTarget(in);
          }
          arc.flags = in.readByte();
        }
        // Undo the byte flags we read:
        in.skipBytes(-1);
        arc.nextArc = in.getPosition();
        readNextRealArc(arc, in);
      }
      assert arc.isLast();
      return arc;
    }
  }

  private long readUnpackedNodeTarget(BytesReader in) throws IOException {
    return in.readVLong();
  }

  public Arc<T> readFirstTargetArc(Arc<T> follow, Arc<T> arc, BytesReader in) throws IOException {
    //int pos = address;
    //System.out.println("    readFirstTarget follow.target=" + follow.target + " isFinal=" + follow.isFinal());
    if (follow.isFinal()) {
      // Insert "fake" final first arc:
      arc.label = END_LABEL;
      arc.output = follow.nextFinalOutput();
      arc.flags = BIT_FINAL_ARC;
      if (follow.target() <= 0) {
        arc.flags |= BIT_LAST_ARC;
      } else {
        // NOTE: nextArc is a node (not an address!) in this case:
        arc.nextArc = follow.target();
      }
      arc.target = FINAL_END_NODE;
      arc.nodeFlags = arc.flags;
      //System.out.println("    insert isFinal; nextArc=" + follow.target + " isLast=" + arc.isLast() + " output=" + outputs.outputToString(arc.output));
      return arc;
    } else {
      return readFirstRealTargetArc(follow.target(), arc, in);
    }
  }

  public Arc<T> readFirstRealTargetArc(long nodeAddress, Arc<T> arc, final BytesReader in) throws IOException {
    in.setPosition(nodeAddress);
    //System.out.println("   flags=" + arc.flags);

    byte flags = arc.nodeFlags = in.readByte();
    if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING) {
      //System.out.println("  fixed length arc");
      // Special arc which is actually a node header for fixed length arcs.
      arc.numArcs = in.readVInt();
      arc.bytesPerArc = in.readVInt();
      arc.arcIdx = -1;
      if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
        readPresenceBytes(arc, in);
        arc.firstLabel = readLabel(in);
        arc.presenceIndex = -1;
      }
      arc.posArcsStart = in.getPosition();
      //System.out.println("  bytesPer=" + arc.bytesPerArc + " numArcs=" + arc.numArcs + " arcsStart=" + pos);
    } else {
      arc.nextArc = nodeAddress;
      arc.bytesPerArc = 0;
    }

    return readNextRealArc(arc, in);
  }

  boolean isExpandedTarget(Arc<T> follow, BytesReader in) throws IOException {
    if (!targetHasArcs(follow)) {
      return false;
    } else {
      in.setPosition(follow.target());
      byte flags = in.readByte();
      return flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING;
    }
  }

  public Arc<T> readNextArc(Arc<T> arc, BytesReader in) throws IOException {
    if (arc.label() == END_LABEL) {
      // This was a fake inserted "final" arc
      if (arc.nextArc() <= 0) {
        throw new IllegalArgumentException("cannot readNextArc when arc.isLast()=true");
      }
      return readFirstRealTargetArc(arc.nextArc(), arc, in);
    } else {
      return readNextRealArc(arc, in);
    }
  }

  int readNextArcLabel(Arc<T> arc, BytesReader in) throws IOException {
    assert !arc.isLast();

    if (arc.label() == END_LABEL) {
      //System.out.println("    nextArc fake " + arc.nextArc);
      // Next arc is the first arc of a node.
      // Position to read the first arc label.

      in.setPosition(arc.nextArc());
      byte flags = in.readByte();
      if (flags == ARCS_FOR_BINARY_SEARCH || flags == ARCS_FOR_DIRECT_ADDRESSING) {
        //System.out.println("    nextArc fixed length arc");
        // Special arc which is actually a node header for fixed length arcs.
        int numArcs = in.readVInt();
        in.readVInt(); // Skip bytesPerArc.
        if (flags == ARCS_FOR_BINARY_SEARCH) {
          in.readByte(); // Skip arc flags.
        } else {
          in.skipBytes(getNumPresenceBytes(numArcs));
        }
      }
    } else {
      if (arc.bytesPerArc() != 0) {
        //System.out.println("    nextArc real array");
        // Arcs have fixed length.
        if (arc.nodeFlags() == ARCS_FOR_BINARY_SEARCH) {
          // Point to next arc, -1 to skip arc flags.
          in.setPosition(arc.posArcsStart() - (1 + arc.arcIdx()) * arc.bytesPerArc() - 1);
        } else {
          assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
          // Direct addressing node. The label is not stored but rather inferred
          // based on first label and arc index in the range.
          assert BitTable.assertIsValid(arc, in);
          assert BitTable.isBitSet(arc.arcIdx(), arc, in);
          int nextIndex = BitTable.nextBitSet(arc.arcIdx(), arc, in);
          assert nextIndex != -1;
          return arc.firstLabel() + nextIndex;
        }
      } else {
        // Arcs have variable length.
        //System.out.println("    nextArc real list");
        // Position to next arc, -1 to skip flags.
        in.setPosition(arc.nextArc() - 1);
      }
    }
    return readLabel(in);
  }

  public Arc<T> readArcByIndex(Arc<T> arc, final BytesReader in, int idx) throws IOException {
    assert arc.bytesPerArc() > 0;
    assert arc.nodeFlags() == ARCS_FOR_BINARY_SEARCH;
    assert idx >= 0 && idx < arc.numArcs();
    in.setPosition(arc.posArcsStart() - idx * arc.bytesPerArc());
    arc.arcIdx = idx;
    arc.flags = in.readByte();
    return readArc(arc, in);
  }

  public Arc<T> readArcByDirectAddressing(Arc<T> arc, final BytesReader in, int rangeIndex) throws IOException {
    assert BitTable.assertIsValid(arc, in);
    assert rangeIndex >= 0 && rangeIndex < arc.numArcs();
    assert BitTable.isBitSet(rangeIndex, arc, in);
    int presenceIndex = BitTable.countBitsUpTo(rangeIndex, arc, in);
    return readArcByDirectAddressing(arc, in, rangeIndex, presenceIndex);
  }

  private Arc<T> readArcByDirectAddressing(Arc<T> arc, final BytesReader in, int rangeIndex, int presenceIndex)  throws IOException {
    in.setPosition(arc.posArcsStart() - presenceIndex * arc.bytesPerArc());
    arc.arcIdx = rangeIndex;
    arc.presenceIndex = presenceIndex;
    arc.flags = in.readByte();
    return readArc(arc, in);
  }

  public Arc<T> readLastArcByDirectAddressing(Arc<T> arc, final BytesReader in) throws IOException {
    assert BitTable.assertIsValid(arc, in);
    int presenceIndex = BitTable.countBits(arc, in) - 1;
    return readArcByDirectAddressing(arc, in, arc.numArcs() - 1, presenceIndex);
  }

  public Arc<T> readNextRealArc(Arc<T> arc, final BytesReader in) throws IOException {

    // TODO: can't assert this because we call from readFirstArc
    // assert !flag(arc.flags, BIT_LAST_ARC);

    switch (arc.nodeFlags()) {

      case ARCS_FOR_BINARY_SEARCH:
        assert arc.bytesPerArc() > 0;
        arc.arcIdx++;
        assert arc.arcIdx() >= 0 && arc.arcIdx() < arc.numArcs();
        in.setPosition(arc.posArcsStart() - arc.arcIdx() * arc.bytesPerArc());
        arc.flags = in.readByte();
        break;

      case ARCS_FOR_DIRECT_ADDRESSING:
        assert BitTable.assertIsValid(arc, in);
        assert arc.arcIdx() == -1 || BitTable.isBitSet(arc.arcIdx(), arc, in);
        int nextIndex = BitTable.nextBitSet(arc.arcIdx(), arc, in);
        return readArcByDirectAddressing(arc, in, nextIndex, arc.presenceIndex + 1);

      default:
        // Variable length arcs - linear search.
        assert arc.bytesPerArc() == 0;
        in.setPosition(arc.nextArc());
        arc.flags = in.readByte();
    }
    return readArc(arc, in);
  }

  private Arc<T> readArc(Arc<T> arc, BytesReader in) throws IOException {
    if (arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING) {
      arc.label = arc.firstLabel() + arc.arcIdx();
    } else {
      arc.label = readLabel(in);
    }

    if (arc.flag(BIT_ARC_HAS_OUTPUT)) {
      arc.output = outputs.read(in);
    } else {
      arc.output = outputs.getNoOutput();
    }

    if (arc.flag(BIT_ARC_HAS_FINAL_OUTPUT)) {
      arc.nextFinalOutput = outputs.readFinalOutput(in);
    } else {
      arc.nextFinalOutput = outputs.getNoOutput();
    }

    if (arc.flag(BIT_STOP_NODE)) {
      if (arc.flag(BIT_FINAL_ARC)) {
        arc.target = FINAL_END_NODE;
      } else {
        arc.target = NON_FINAL_END_NODE;
      }
      arc.nextArc = in.getPosition(); // Only useful for list.
    } else if (arc.flag(BIT_TARGET_NEXT)) {
      arc.nextArc = in.getPosition(); // Only useful for list.
      // TODO: would be nice to make this lazy -- maybe
      // caller doesn't need the target and is scanning arcs...
      if (!arc.flag(BIT_LAST_ARC)) {
        if (arc.bytesPerArc() == 0) {
          // must scan
          seekToNextNode(in);
        } else {
          int numArcs = arc.nodeFlags == ARCS_FOR_DIRECT_ADDRESSING ? BitTable.countBits(arc, in) : arc.numArcs();
          in.setPosition(arc.posArcsStart() - arc.bytesPerArc() * numArcs);
        }
      }
      arc.target = in.getPosition();
    } else {
      arc.target = readUnpackedNodeTarget(in);
      arc.nextArc = in.getPosition(); // Only useful for list.
    }
    return arc;
  }

  static <T> Arc<T> readEndArc(Arc<T> follow, Arc<T> arc) {
    if (follow.isFinal()) {
      if (follow.target() <= 0) {
        arc.flags = FST.BIT_LAST_ARC;
      } else {
        arc.flags = 0;
        // NOTE: nextArc is a node (not an address!) in this case:
        arc.nextArc = follow.target();
      }
      arc.output = follow.nextFinalOutput();
      arc.label = FST.END_LABEL;
      return arc;
    } else {
      return null;
    }
  }

  // TODO: could we somehow [partially] tableize arc lookups
  // like automaton?

  public Arc<T> findTargetArc(int labelToMatch, Arc<T> follow, Arc<T> arc, BytesReader in) throws IOException {

    if (labelToMatch == END_LABEL) {
      if (follow.isFinal()) {
        if (follow.target() <= 0) {
          arc.flags = BIT_LAST_ARC;
        } else {
          arc.flags = 0;
          // NOTE: nextArc is a node (not an address!) in this case:
          arc.nextArc = follow.target();
        }
        arc.output = follow.nextFinalOutput();
        arc.label = END_LABEL;
        arc.nodeFlags = arc.flags;
        return arc;
      } else {
        return null;
      }
    }

    if (!targetHasArcs(follow)) {
      return null;
    }

    in.setPosition(follow.target());

    // System.out.println("fta label=" + (char) labelToMatch);

    byte flags = arc.nodeFlags = in.readByte();
    if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
      arc.numArcs = in.readVInt(); // This is in fact the label range.
      arc.bytesPerArc = in.readVInt();
      readPresenceBytes(arc, in);
      arc.firstLabel = readLabel(in);
      arc.posArcsStart = in.getPosition();

      int arcIndex = labelToMatch - arc.firstLabel();
      if (arcIndex < 0 || arcIndex >= arc.numArcs()) {
        return null; // Before or after label range.
      } else if (!BitTable.isBitSet(arcIndex, arc, in)) {
        return null; // Arc missing in the range.
      }
      return readArcByDirectAddressing(arc, in, arcIndex);
    } else if (flags == ARCS_FOR_BINARY_SEARCH) {
      arc.numArcs = in.readVInt();
      arc.bytesPerArc = in.readVInt();
      arc.posArcsStart = in.getPosition();

      // Array is sparse; do binary search:
      int low = 0;
      int high = arc.numArcs() - 1;
      while (low <= high) {
        //System.out.println("    cycle");
        int mid = (low + high) >>> 1;
        // +1 to skip over flags
        in.setPosition(arc.posArcsStart() - (arc.bytesPerArc() * mid + 1));
        int midLabel = readLabel(in);
        final int cmp = midLabel - labelToMatch;
        if (cmp < 0) {
          low = mid + 1;
        } else if (cmp > 0) {
          high = mid - 1;
        } else {
          arc.arcIdx = mid - 1;
          //System.out.println("    found!");
          return readNextRealArc(arc, in);
        }
      }
      return null;
    }

    // Linear scan
    readFirstRealTargetArc(follow.target(), arc, in);

    while(true) {
      //System.out.println("  non-bs cycle");
      // TODO: we should fix this code to not have to create
      // object for the output of every arc we scan... only
      // for the matching arc, if found
      if (arc.label() == labelToMatch) {
        //System.out.println("    found!");
        return arc;
      } else if (arc.label() > labelToMatch) {
        return null;
      } else if (arc.isLast()) {
        return null;
      } else {
        readNextRealArc(arc, in);
      }
    }
  }

  private void seekToNextNode(BytesReader in) throws IOException {

    while(true) {

      final int flags = in.readByte();
      readLabel(in);

      if (flag(flags, BIT_ARC_HAS_OUTPUT)) {
        outputs.skipOutput(in);
      }

      if (flag(flags, BIT_ARC_HAS_FINAL_OUTPUT)) {
        outputs.skipFinalOutput(in);
      }

      if (!flag(flags, BIT_STOP_NODE) && !flag(flags, BIT_TARGET_NEXT)) {
        readUnpackedNodeTarget(in);
      }

      if (flag(flags, BIT_LAST_ARC)) {
        return;
      }
    }
  }

  public BytesReader getBytesReader() {
    if (this.fstStore != null) {
      return this.fstStore.getReverseBytesReader();
    } else {
      return bytes.getReverseReader();
    }
  }

  public static abstract class BytesReader extends DataInput {
    public abstract long getPosition();

    public abstract void setPosition(long pos);

    public abstract boolean reversed();
  }
}
