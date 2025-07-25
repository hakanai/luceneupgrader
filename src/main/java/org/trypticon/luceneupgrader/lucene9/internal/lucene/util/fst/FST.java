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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst;

import static org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst.FST.Arc.BitTable;
import static org.trypticon.luceneupgrader.lucene9.internal.lucene.util.fst.FSTCompiler.getOnHeapReaderWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.ByteBuffersDataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.InputStreamDataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.OutputStreamDataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Constants;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;

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

/**
 * Represents an finite state machine (FST), using a compact byte[] format.
 *
 * <p>The format is similar to what's used by Morfologik
 * (https://github.com/morfologik/morfologik-stemming).
 *
 * <p>See the {@link org.apache.lucene.util.fst package documentation} for some simple examples.
 *
 * @lucene.experimental
 */
public final class FST<T> implements Accountable {

  final FSTMetadata<T> metadata;

  /** Specifies allowed range of each int input label for this FST. */
  public enum INPUT_TYPE {
    BYTE1,
    BYTE2,
    BYTE4
  }

  private static final long BASE_RAM_BYTES_USED =
      RamUsageEstimator.shallowSizeOfInstance(FST.class);

  static final int BIT_FINAL_ARC = 1 << 0;
  static final int BIT_LAST_ARC = 1 << 1;
  static final int BIT_TARGET_NEXT = 1 << 2;

  // TODO: we can free up a bit if we can nuke this:
  static final int BIT_STOP_NODE = 1 << 3;

  /** This flag is set if the arc has an output. */
  public static final int BIT_ARC_HAS_OUTPUT = 1 << 4;

  static final int BIT_ARC_HAS_FINAL_OUTPUT = 1 << 5;

  /**
   * Value of the arc flags to declare a node with fixed length (sparse) arcs designed for binary
   * search.
   */
  // We use this as a marker because this one flag is illegal by itself.
  public static final byte ARCS_FOR_BINARY_SEARCH = BIT_ARC_HAS_FINAL_OUTPUT;

  /**
   * Value of the arc flags to declare a node with fixed length dense arcs and bit table designed
   * for direct addressing.
   */
  static final byte ARCS_FOR_DIRECT_ADDRESSING = 1 << 6;

  /**
   * Value of the arc flags to declare a node with continuous arcs designed for pos the arc directly
   * with labelToPos - firstLabel. like {@link #ARCS_FOR_BINARY_SEARCH} we use flag combinations
   * that will not occur at the same time.
   */
  static final byte ARCS_FOR_CONTINUOUS = ARCS_FOR_DIRECT_ADDRESSING + ARCS_FOR_BINARY_SEARCH;

  // Increment version to change it
  private static final String FILE_FORMAT_NAME = "FST";

  /** First supported version, this is the version that was used when releasing Lucene 7.0. */
  public static final int VERSION_START = 6;

  // Version 7 introduced direct addressing for arcs, but it's not recorded here because it doesn't
  // need version checks on the read side, it uses new flag values on arcs instead.

  private static final int VERSION_LITTLE_ENDIAN = 8;

  /** Version that started storing continuous arcs. */
  public static final int VERSION_CONTINUOUS_ARCS = 9;

  /** Current version. */
  public static final int VERSION_CURRENT = VERSION_CONTINUOUS_ARCS;

  /** Version that was used when releasing Lucene 9.0. */
  public static final int VERSION_90 = VERSION_LITTLE_ENDIAN;

  // Never serialized; just used to represent the virtual
  // final node w/ no arcs:
  static final long FINAL_END_NODE = -1;

  // Never serialized; just used to represent the virtual
  // non-final node w/ no arcs:
  static final long NON_FINAL_END_NODE = 0;

  /** If arc has this label then that arc is final/accepted */
  public static final int END_LABEL = -1;

  /** The reader of the FST, used to read bytes from the underlying FST storage */
  private final FSTReader fstReader;

  public final Outputs<T> outputs;

  /** Represents a single arc. */
  public static final class Arc<T> {

    // *** Arc fields.

    private int label;

    private T output;

    private long target;

    private byte flags;

    private T nextFinalOutput;

    private long nextArc;

    private byte nodeFlags;

    // *** Fields for arcs belonging to a node with fixed length arcs.
    // So only valid when bytesPerArc != 0.
    // nodeFlags == ARCS_FOR_BINARY_SEARCH || nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.

    private int bytesPerArc;

    private long posArcsStart;

    private int arcIdx;

    private int numArcs;

    // *** Fields for a direct addressing node. nodeFlags == ARCS_FOR_DIRECT_ADDRESSING.

    /**
     * Start position in the {@link FST.BytesReader} of the presence bits for a direct addressing
     * node, aka the bit-table
     */
    private long bitTableStart;

    /** First label of a direct addressing node. */
    private int firstLabel;

    /**
     * Index of the current label of a direct addressing node. While {@link #arcIdx} is the current
     * index in the label range, {@link #presenceIndex} is its corresponding index in the list of
     * actually present labels. It is equal to the number of bits set before the bit at {@link
     * #arcIdx} in the bit-table. This field is a cache to avoid to count bits set repeatedly when
     * iterating the next arcs.
     */
    private int presenceIndex;

    /** Returns this */
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
      // We could avoid copying them if bytesPerArc() == 0 (this was the case with previous code,
      // and the current code
      // still supports that), but it may actually help external uses of FST to have consistent arc
      // state, and debugging
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
        b.append(" arcArray(idx=")
            .append(arcIdx())
            .append(" of ")
            .append(numArcs())
            .append(")")
            .append("(")
            .append(
                nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING
                    ? "da"
                    : nodeFlags() == ARCS_FOR_CONTINUOUS ? "cs" : "bs")
            .append(")");
      }
      return b.toString();
    }

    public int label() {
      return label;
    }

    public T output() {
      return output;
    }

    /** Ord/address to target node. */
    public long target() {
      return target;
    }

    public byte flags() {
      return flags;
    }

    public T nextFinalOutput() {
      return nextFinalOutput;
    }

    /**
     * Address (into the byte[]) of the next arc - only for list of variable length arc. Or
     * ord/address to the next node if label == {@link #END_LABEL}.
     */
    long nextArc() {
      return nextArc;
    }

    /** Where we are in the array; only valid if bytesPerArc != 0. */
    public int arcIdx() {
      return arcIdx;
    }

    /**
     * Node header flags. Only meaningful to check if the value is either {@link
     * #ARCS_FOR_BINARY_SEARCH} or {@link #ARCS_FOR_DIRECT_ADDRESSING} or {@link
     * #ARCS_FOR_CONTINUOUS} (other value when bytesPerArc == 0).
     */
    public byte nodeFlags() {
      return nodeFlags;
    }

    /** Where the first arc in the array starts; only valid if bytesPerArc != 0 */
    public long posArcsStart() {
      return posArcsStart;
    }

    /**
     * Non-zero if this arc is part of a node with fixed length arcs, which means all arcs for the
     * node are encoded with a fixed number of bytes so that we binary search or direct address. We
     * do when there are enough arcs leaving one node. It wastes some bytes but gives faster
     * lookups.
     */
    public int bytesPerArc() {
      return bytesPerArc;
    }

    /**
     * How many arcs; only valid if bytesPerArc != 0 (fixed length arcs). For a node designed for
     * binary search this is the array size. For a node designed for direct addressing, this is the
     * label range.
     */
    public int numArcs() {
      return numArcs;
    }

    /**
     * First label of a direct addressing node. Only valid if nodeFlags == {@link
     * #ARCS_FOR_DIRECT_ADDRESSING} or {@link #ARCS_FOR_CONTINUOUS}.
     */
    int firstLabel() {
      return firstLabel;
    }

    /**
     * Helper methods to read the bit-table of a direct addressing node. Only valid for {@link Arc}
     * with {@link Arc#nodeFlags()} == {@code ARCS_FOR_DIRECT_ADDRESSING}.
     */
    static class BitTable {

      /** See {@link BitTableUtil#isBitSet(int, FST.BytesReader)}. */
      static boolean isBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.isBitSet(bitIndex, in);
      }

      /**
       * See {@link BitTableUtil#countBits(int, FST.BytesReader)}. The count of bit set is the
       * number of arcs of a direct addressing node.
       */
      static int countBits(Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.countBits(getNumPresenceBytes(arc.numArcs()), in);
      }

      /** See {@link BitTableUtil#countBitsUpTo(int, FST.BytesReader)}. */
      static int countBitsUpTo(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.countBitsUpTo(bitIndex, in);
      }

      /** See {@link BitTableUtil#nextBitSet(int, int, FST.BytesReader)}. */
      static int nextBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.nextBitSet(bitIndex, getNumPresenceBytes(arc.numArcs()), in);
      }

      /** See {@link BitTableUtil#previousBitSet(int, FST.BytesReader)}. */
      static int previousBitSet(int bitIndex, Arc<?> arc, FST.BytesReader in) throws IOException {
        assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
        in.setPosition(arc.bitTableStart);
        return BitTableUtil.previousBitSet(bitIndex, in);
      }

      /** Asserts the bit-table of the provided {@link Arc} is valid. */
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

  private static final int DEFAULT_MAX_BLOCK_BITS = Constants.JRE_IS_64BIT ? 30 : 28;

  /**
   * Load a previously saved FST with a DataInput for metdata using an {@link OnHeapFSTStore} with
   * maxBlockBits set to {@link #DEFAULT_MAX_BLOCK_BITS}
   */
  public FST(FSTMetadata<T> metadata, DataInput in) throws IOException {
    this(metadata, new OnHeapFSTStore(DEFAULT_MAX_BLOCK_BITS, in, metadata.numBytes));
  }

  /** Create the FST with a metadata object and a FSTReader. */
  FST(FSTMetadata<T> metadata, FSTReader fstReader) {
    assert fstReader != null;
    this.metadata = Objects.requireNonNull(metadata, "FSTMetadata cannot be null");
    this.outputs = metadata.outputs;
    this.fstReader = fstReader;
  }

  /**
   * Create a FST from a {@link FSTReader}. Return null if the metadata is null.
   *
   * @param fstMetadata the metadata
   * @param fstReader the FSTReader
   * @return the FST
   */
  public static <T> FST<T> fromFSTReader(FSTMetadata<T> fstMetadata, FSTReader fstReader) {
    // FSTMetadata could be null if there is no node accepted by the FST
    if (fstMetadata == null) {
      return null;
    }
    return new FST<>(fstMetadata, Objects.requireNonNull(fstReader, "FSTReader cannot be null"));
  }

  /**
   * Read the FST metadata from DataInput
   *
   * @param metaIn the DataInput of the metadata
   * @param outputs the FST outputs
   * @return the FST metadata
   * @param <T> the output type
   * @throws IOException if exception occurred during parsing
   */
  public static <T> FSTMetadata<T> readMetadata(DataInput metaIn, Outputs<T> outputs)
      throws IOException {
    // NOTE: only reads formats VERSION_START up to VERSION_CURRENT; we don't have
    // back-compat promise for FSTs (they are experimental), but we are sometimes able to offer it
    int version = CodecUtil.checkHeader(metaIn, FILE_FORMAT_NAME, VERSION_START, VERSION_CURRENT);
    T emptyOutput;
    if (metaIn.readByte() == 1) {
      // accepts empty string
      // 1 KB blocks:
      ReadWriteDataOutput emptyBytes = (ReadWriteDataOutput) getOnHeapReaderWriter(10);
      int numBytes = metaIn.readVInt();
      emptyBytes.copyBytes(metaIn, numBytes);

      emptyBytes.freeze();

      // De-serialize empty-string output:
      BytesReader reader = emptyBytes.getReverseBytesReader();
      // NoOutputs uses 0 bytes when writing its output,
      // so we have to check here else BytesStore gets
      // angry:
      if (numBytes > 0) {
        reader.setPosition(numBytes - 1);
      }
      emptyOutput = outputs.readFinalOutput(reader);
    } else {
      emptyOutput = null;
    }
    INPUT_TYPE inputType;
    final byte t = metaIn.readByte();
    switch (t) {
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
        throw new CorruptIndexException("invalid input type " + t, metaIn);
    }
    long startNode = metaIn.readVLong();
    long numBytes = metaIn.readVLong();
    return new FSTMetadata<>(inputType, outputs, emptyOutput, startNode, version, numBytes);
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED + fstReader.ramBytesUsed();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(input=" + metadata.inputType + ",output=" + outputs;
  }

  public long numBytes() {
    return metadata.numBytes;
  }

  public T getEmptyOutput() {
    return metadata.emptyOutput;
  }

  public FSTMetadata<T> getMetadata() {
    return metadata;
  }

  /**
   * Save the FST to DataOutput.
   *
   * @param metaOut the DataOutput to write the metadata to
   * @param out the DataOutput to write the FST bytes to
   */
  public void save(DataOutput metaOut, DataOutput out) throws IOException {
    metadata.save(metaOut);
    fstReader.writeTo(out);
  }

  /** Writes an automaton to a file. */
  public void save(final Path path) throws IOException {
    try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(path))) {
      DataOutput out = new OutputStreamDataOutput(os);
      save(out, out);
    }
  }

  /** Reads an automaton from a file. */
  public static <T> FST<T> read(Path path, Outputs<T> outputs) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      DataInput in = new InputStreamDataInput(new BufferedInputStream(is));
      return new FST<>(readMetadata(in, outputs), in);
    }
  }

  /** Reads one BYTE1/2/4 label from the provided {@link DataInput}. */
  public int readLabel(DataInput in) throws IOException {
    final int v;
    if (metadata.inputType == INPUT_TYPE.BYTE1) {
      // Unsigned byte:
      v = in.readByte() & 0xFF;
    } else if (metadata.inputType == INPUT_TYPE.BYTE2) {
      // Unsigned short:
      if (metadata.version < VERSION_LITTLE_ENDIAN) {
        v = Short.reverseBytes(in.readShort()) & 0xFFFF;
      } else {
        v = in.readShort() & 0xFFFF;
      }
    } else {
      v = in.readVInt();
    }
    return v;
  }

  /** returns true if the node at this address has any outgoing arcs */
  public static <T> boolean targetHasArcs(Arc<T> arc) {
    return arc.target() > 0;
  }

  /**
   * Gets the number of bytes required to flag the presence of each arc in the given label range,
   * one bit per arc.
   */
  static int getNumPresenceBytes(int labelRange) {
    assert labelRange >= 0;
    return (labelRange + 7) >> 3;
  }

  /**
   * Reads the presence bits of a direct-addressing node. Actually we don't read them here, we just
   * keep the pointer to the bit-table start and we skip them.
   */
  private void readPresenceBytes(Arc<T> arc, BytesReader in) throws IOException {
    assert arc.bytesPerArc() > 0;
    assert arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING;
    arc.bitTableStart = in.getPosition();
    in.skipBytes(getNumPresenceBytes(arc.numArcs()));
  }

  /** Fills virtual 'start' arc, ie, an empty incoming arc to the FST's start node */
  public Arc<T> getFirstArc(Arc<T> arc) {
    T NO_OUTPUT = outputs.getNoOutput();

    if (metadata.emptyOutput != null) {
      arc.flags = BIT_FINAL_ARC | BIT_LAST_ARC;
      arc.nextFinalOutput = metadata.emptyOutput;
      if (metadata.emptyOutput != NO_OUTPUT) {
        arc.flags = (byte) (arc.flags() | BIT_ARC_HAS_FINAL_OUTPUT);
      }
    } else {
      arc.flags = BIT_LAST_ARC;
      arc.nextFinalOutput = NO_OUTPUT;
    }
    arc.output = NO_OUTPUT;

    // If there are no nodes, ie, the FST only accepts the
    // empty string, then startNode is 0
    arc.target = metadata.startNode;
    return arc;
  }

  /**
   * Follows the <code>follow</code> arc and reads the last arc of its target; this changes the
   * provided <code>arc</code> (2nd arg) in-place and returns it.
   *
   * @return Returns the second argument (<code>arc</code>).
   */
  Arc<T> readLastTargetArc(Arc<T> follow, Arc<T> arc, BytesReader in) throws IOException {
    // System.out.println("readLast");
    if (!targetHasArcs(follow)) {
      // System.out.println("  end node");
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
      if (flags == ARCS_FOR_BINARY_SEARCH
          || flags == ARCS_FOR_DIRECT_ADDRESSING
          || flags == ARCS_FOR_CONTINUOUS) {
        // Special arc which is actually a node header for fixed length arcs.
        // Jump straight to end to find the last arc.
        arc.numArcs = in.readVInt();
        arc.bytesPerArc = in.readVInt();
        // System.out.println("  array numArcs=" + arc.numArcs + " bpa=" + arc.bytesPerArc);
        if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
          readPresenceBytes(arc, in);
          arc.firstLabel = readLabel(in);
          arc.posArcsStart = in.getPosition();
          readLastArcByDirectAddressing(arc, in);
        } else if (flags == ARCS_FOR_BINARY_SEARCH) {
          arc.arcIdx = arc.numArcs() - 2;
          arc.posArcsStart = in.getPosition();
          readNextRealArc(arc, in);
        } else {
          arc.firstLabel = readLabel(in);
          arc.posArcsStart = in.getPosition();
          readLastArcByContinuous(arc, in);
        }
      } else {
        arc.flags = flags;
        // non-array: linear scan
        arc.bytesPerArc = 0;
        // System.out.println("  scan");
        while (!arc.isLast()) {
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

  /**
   * Follow the <code>follow</code> arc and read the first arc of its target; this changes the
   * provided <code>arc</code> (2nd arg) in-place and returns it.
   *
   * @return Returns the second argument (<code>arc</code>).
   */
  public Arc<T> readFirstTargetArc(Arc<T> follow, Arc<T> arc, BytesReader in) throws IOException {
    // int pos = address;
    // System.out.println("    readFirstTarget follow.target=" + follow.target + " isFinal=" +
    // follow.isFinal());
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
      // System.out.println("    insert isFinal; nextArc=" + follow.target + " isLast=" +
      // arc.isLast() + " output=" + outputs.outputToString(arc.output));
      return arc;
    } else {
      return readFirstRealTargetArc(follow.target(), arc, in);
    }
  }

  private void readFirstArcInfo(long nodeAddress, Arc<T> arc, final BytesReader in)
      throws IOException {
    in.setPosition(nodeAddress);

    byte flags = arc.nodeFlags = in.readByte();
    if (flags == ARCS_FOR_BINARY_SEARCH
        || flags == ARCS_FOR_DIRECT_ADDRESSING
        || flags == ARCS_FOR_CONTINUOUS) {
      // Special arc which is actually a node header for fixed length arcs.
      arc.numArcs = in.readVInt();
      arc.bytesPerArc = in.readVInt();
      arc.arcIdx = -1;
      if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
        readPresenceBytes(arc, in);
        arc.firstLabel = readLabel(in);
        arc.presenceIndex = -1;
      } else if (flags == ARCS_FOR_CONTINUOUS) {
        arc.firstLabel = readLabel(in);
      }
      arc.posArcsStart = in.getPosition();
    } else {
      arc.nextArc = nodeAddress;
      arc.bytesPerArc = 0;
    }
  }

  public Arc<T> readFirstRealTargetArc(long nodeAddress, Arc<T> arc, final BytesReader in)
      throws IOException {
    readFirstArcInfo(nodeAddress, arc, in);
    return readNextRealArc(arc, in);
  }

  /**
   * Returns whether <code>arc</code>'s target points to a node in expanded format (fixed length
   * arcs).
   */
  boolean isExpandedTarget(Arc<T> follow, BytesReader in) throws IOException {
    if (!targetHasArcs(follow)) {
      return false;
    } else {
      in.setPosition(follow.target());
      byte flags = in.readByte();
      return flags == ARCS_FOR_BINARY_SEARCH
          || flags == ARCS_FOR_DIRECT_ADDRESSING
          || flags == ARCS_FOR_CONTINUOUS;
    }
  }

  /** In-place read; returns the arc. */
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

  /** Peeks at next arc's label; does not alter arc. Do not call this if arc.isLast()! */
  int readNextArcLabel(Arc<T> arc, BytesReader in) throws IOException {
    assert !arc.isLast();

    if (arc.label() == END_LABEL) {
      // System.out.println("    nextArc fake " + arc.nextArc);
      // Next arc is the first arc of a node.
      // Position to read the first arc label.

      in.setPosition(arc.nextArc());
      byte flags = in.readByte();
      if (flags == ARCS_FOR_BINARY_SEARCH
          || flags == ARCS_FOR_DIRECT_ADDRESSING
          || flags == ARCS_FOR_CONTINUOUS) {
        // System.out.println("    nextArc fixed length arc");
        // Special arc which is actually a node header for fixed length arcs.
        int numArcs = in.readVInt();
        in.readVInt(); // Skip bytesPerArc.
        if (flags == ARCS_FOR_BINARY_SEARCH) {
          in.readByte(); // Skip arc flags.
        } else if (flags == ARCS_FOR_DIRECT_ADDRESSING) {
          in.skipBytes(getNumPresenceBytes(numArcs));
        } // Nothing to do for ARCS_FOR_CONTINUOUS
      }
    } else {
      switch (arc.nodeFlags()) {
        case ARCS_FOR_BINARY_SEARCH:
          // Point to next arc, -1 to skip arc flags.
          in.setPosition(arc.posArcsStart() - (1 + arc.arcIdx()) * (long) arc.bytesPerArc() - 1);
          break;
        case ARCS_FOR_DIRECT_ADDRESSING:
          // Direct addressing node. The label is not stored but rather inferred
          // based on first label and arc index in the range.
          assert BitTable.assertIsValid(arc, in);
          assert BitTable.isBitSet(arc.arcIdx(), arc, in);
          int nextIndex = BitTable.nextBitSet(arc.arcIdx(), arc, in);
          assert nextIndex != -1;
          return arc.firstLabel() + nextIndex;
        case ARCS_FOR_CONTINUOUS:
          return arc.firstLabel() + arc.arcIdx() + 1;
        default:
          // Variable length arcs - linear search.
          assert arc.bytesPerArc() == 0;
          // Arcs have variable length.
          // System.out.println("    nextArc real list");
          // Position to next arc, -1 to skip flags.
          in.setPosition(arc.nextArc() - 1);
          break;
      }
    }
    return readLabel(in);
  }

  public Arc<T> readArcByIndex(Arc<T> arc, final BytesReader in, int idx) throws IOException {
    assert arc.bytesPerArc() > 0;
    assert arc.nodeFlags() == ARCS_FOR_BINARY_SEARCH;
    assert idx >= 0 && idx < arc.numArcs();
    in.setPosition(arc.posArcsStart() - idx * (long) arc.bytesPerArc());
    arc.arcIdx = idx;
    arc.flags = in.readByte();
    return readArc(arc, in);
  }

  /**
   * Reads a Continuous node arc, with the provided index in the label range.
   *
   * @param rangeIndex The index of the arc in the label range. It must be within the label range.
   */
  public Arc<T> readArcByContinuous(Arc<T> arc, final BytesReader in, int rangeIndex)
      throws IOException {
    assert rangeIndex >= 0 && rangeIndex < arc.numArcs();
    in.setPosition(arc.posArcsStart() - rangeIndex * (long) arc.bytesPerArc());
    arc.arcIdx = rangeIndex;
    arc.flags = in.readByte();
    return readArc(arc, in);
  }

  /**
   * Reads a present direct addressing node arc, with the provided index in the label range.
   *
   * @param rangeIndex The index of the arc in the label range. It must be present. The real arc
   *     offset is computed based on the presence bits of the direct addressing node.
   */
  public Arc<T> readArcByDirectAddressing(Arc<T> arc, final BytesReader in, int rangeIndex)
      throws IOException {
    assert BitTable.assertIsValid(arc, in);
    assert rangeIndex >= 0 && rangeIndex < arc.numArcs();
    assert BitTable.isBitSet(rangeIndex, arc, in);
    int presenceIndex = BitTable.countBitsUpTo(rangeIndex, arc, in);
    return readArcByDirectAddressing(arc, in, rangeIndex, presenceIndex);
  }

  /**
   * Reads a present direct addressing node arc, with the provided index in the label range and its
   * corresponding presence index (which is the count of presence bits before it).
   */
  private Arc<T> readArcByDirectAddressing(
      Arc<T> arc, final BytesReader in, int rangeIndex, int presenceIndex) throws IOException {
    in.setPosition(arc.posArcsStart() - presenceIndex * (long) arc.bytesPerArc());
    arc.arcIdx = rangeIndex;
    arc.presenceIndex = presenceIndex;
    arc.flags = in.readByte();
    return readArc(arc, in);
  }

  /**
   * Reads the last arc of a direct addressing node. This method is equivalent to call {@link
   * #readArcByDirectAddressing(Arc, BytesReader, int)} with {@code rangeIndex} equal to {@code
   * arc.numArcs() - 1}, but it is faster.
   */
  public Arc<T> readLastArcByDirectAddressing(Arc<T> arc, final BytesReader in) throws IOException {
    assert BitTable.assertIsValid(arc, in);
    int presenceIndex = BitTable.countBits(arc, in) - 1;
    return readArcByDirectAddressing(arc, in, arc.numArcs() - 1, presenceIndex);
  }

  /** Reads the last arc of a continuous node. */
  public Arc<T> readLastArcByContinuous(Arc<T> arc, final BytesReader in) throws IOException {
    return readArcByContinuous(arc, in, arc.numArcs() - 1);
  }

  /** Never returns null, but you should never call this if arc.isLast() is true. */
  public Arc<T> readNextRealArc(Arc<T> arc, final BytesReader in) throws IOException {

    // TODO: can't assert this because we call from readFirstArc
    // assert !flag(arc.flags, BIT_LAST_ARC);

    switch (arc.nodeFlags()) {
      case ARCS_FOR_BINARY_SEARCH:
      case ARCS_FOR_CONTINUOUS:
        assert arc.bytesPerArc() > 0;
        arc.arcIdx++;
        assert arc.arcIdx() >= 0 && arc.arcIdx() < arc.numArcs();
        in.setPosition(arc.posArcsStart() - arc.arcIdx() * (long) arc.bytesPerArc());
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

  /**
   * Reads an arc. <br>
   * Precondition: The arc flags byte has already been read and set; the given BytesReader is
   * positioned just after the arc flags byte.
   */
  private Arc<T> readArc(Arc<T> arc, BytesReader in) throws IOException {
    if (arc.nodeFlags() == ARCS_FOR_DIRECT_ADDRESSING || arc.nodeFlags() == ARCS_FOR_CONTINUOUS) {
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
          int numArcs =
              arc.nodeFlags == ARCS_FOR_DIRECT_ADDRESSING
                  ? BitTable.countBits(arc, in)
                  : arc.numArcs();
          in.setPosition(arc.posArcsStart() - arc.bytesPerArc() * (long) numArcs);
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

  /**
   * Finds an arc leaving the incoming arc, replacing the arc in place. This returns null if the arc
   * was not found, else the incoming arc.
   */
  public Arc<T> findTargetArc(int labelToMatch, Arc<T> follow, Arc<T> arc, BytesReader in)
      throws IOException {

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
        // System.out.println("    cycle");
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
          // System.out.println("    found!");
          return readNextRealArc(arc, in);
        }
      }
      return null;
    } else if (flags == ARCS_FOR_CONTINUOUS) {
      arc.numArcs = in.readVInt();
      arc.bytesPerArc = in.readVInt();
      arc.firstLabel = readLabel(in);
      arc.posArcsStart = in.getPosition();
      int arcIndex = labelToMatch - arc.firstLabel();
      if (arcIndex < 0 || arcIndex >= arc.numArcs()) {
        return null; // Before or after label range.
      }
      arc.arcIdx = arcIndex - 1;
      return readNextRealArc(arc, in);
    }

    // Linear scan
    readFirstArcInfo(follow.target(), arc, in);
    in.setPosition(arc.nextArc());
    while (true) {
      assert arc.bytesPerArc() == 0;
      flags = arc.flags = in.readByte();
      long pos = in.getPosition();
      int label = readLabel(in);
      if (label == labelToMatch) {
        in.setPosition(pos);
        return readArc(arc, in);
      } else if (label > labelToMatch) {
        return null;
      } else if (arc.isLast()) {
        return null;
      } else {
        if (flag(flags, BIT_ARC_HAS_OUTPUT)) {
          outputs.skipOutput(in);
        }
        if (flag(flags, BIT_ARC_HAS_FINAL_OUTPUT)) {
          outputs.skipFinalOutput(in);
        }
        if (flag(flags, BIT_STOP_NODE) == false && flag(flags, BIT_TARGET_NEXT) == false) {
          readUnpackedNodeTarget(in);
        }
      }
    }
  }

  private void seekToNextNode(BytesReader in) throws IOException {

    while (true) {

      final int flags = in.readByte();
      readLabel(in);

      if (flag(flags, BIT_ARC_HAS_OUTPUT)) {
        outputs.skipOutput(in);
      }

      if (flag(flags, BIT_ARC_HAS_FINAL_OUTPUT)) {
        outputs.skipFinalOutput(in);
      }

      if (flag(flags, BIT_STOP_NODE) == false && flag(flags, BIT_TARGET_NEXT) == false) {
        readUnpackedNodeTarget(in);
      }

      if (flag(flags, BIT_LAST_ARC)) {
        return;
      }
    }
  }

  /** Returns a {@link BytesReader} for this FST, positioned at position 0. */
  public BytesReader getBytesReader() {
    return fstReader.getReverseBytesReader();
  }

  /** Reads bytes stored in an FST. */
  public abstract static class BytesReader extends DataInput {
    /** Get current read position. */
    public abstract long getPosition();

    /** Set current read position. */
    public abstract void setPosition(long pos);
  }

  /**
   * Represents the FST metadata.
   *
   * @param <T> the FST output type
   */
  public static final class FSTMetadata<T> {
    final INPUT_TYPE inputType;
    final Outputs<T> outputs;
    final int version;
    // if non-null, this FST accepts the empty string and
    // produces this output
    T emptyOutput;
    long startNode;
    long numBytes;

    public FSTMetadata(
        INPUT_TYPE inputType,
        Outputs<T> outputs,
        T emptyOutput,
        long startNode,
        int version,
        long numBytes) {
      this.inputType = inputType;
      this.outputs = outputs;
      this.emptyOutput = emptyOutput;
      this.startNode = startNode;
      this.version = version;
      this.numBytes = numBytes;
    }

    /**
     * Returns the version constant of the binary format this FST was written in. See the {@code
     * static final int VERSION} constants in FST's javadoc, e.g. {@link
     * FST#VERSION_CONTINUOUS_ARCS}.
     */
    public int getVersion() {
      return version;
    }

    public T getEmptyOutput() {
      return emptyOutput;
    }

    public long getNumBytes() {
      return numBytes;
    }

    /**
     * Save the metadata to a DataOutput
     *
     * @param metaOut the DataOutput to write the metadata to
     */
    public void save(DataOutput metaOut) throws IOException {
      CodecUtil.writeHeader(metaOut, FILE_FORMAT_NAME, VERSION_CURRENT);
      // TODO: really we should encode this as an arc, arriving
      // to the root node, instead of special casing here:
      if (emptyOutput != null) {
        // Accepts empty string
        metaOut.writeByte((byte) 1);

        // Serialize empty-string output:
        ByteBuffersDataOutput ros = new ByteBuffersDataOutput();
        outputs.writeFinalOutput(emptyOutput, ros);
        byte[] emptyOutputBytes = ros.toArrayCopy();
        int emptyLen = emptyOutputBytes.length;

        // reverse
        final int stopAt = emptyLen / 2;
        int upto = 0;
        while (upto < stopAt) {
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
      metaOut.writeVLong(numBytes);
    }
  }
}
