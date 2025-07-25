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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BitUtil;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.UnicodeUtil;

/** A {@link DataOutput} storing data in a list of {@link ByteBuffer}s. */
public final class ByteBuffersDataOutput extends DataOutput implements Accountable {
  private static final ByteBuffer EMPTY = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);

  private static final byte[] EMPTY_BYTE_ARRAY = {};

  public static final IntFunction<ByteBuffer> ALLOCATE_BB_ON_HEAP = ByteBuffer::allocate;

  /** A singleton instance of "no-reuse" buffer strategy. */
  public static final Consumer<ByteBuffer> NO_REUSE =
      (bb) -> {
        throw new RuntimeException("reset() is not allowed on this buffer.");
      };

  /**
   * An implementation of a {@link ByteBuffer} allocation and recycling policy. The blocks are
   * recycled if exactly the same size is requested, otherwise they're released to be GCed.
   */
  public static final class ByteBufferRecycler {
    private final ArrayDeque<ByteBuffer> reuse = new ArrayDeque<>();
    private final IntFunction<ByteBuffer> delegate;

    public ByteBufferRecycler(IntFunction<ByteBuffer> delegate) {
      this.delegate = Objects.requireNonNull(delegate);
    }

    public ByteBuffer allocate(int size) {
      while (!reuse.isEmpty()) {
        ByteBuffer bb = reuse.removeFirst();
        // If we don't have a buffer of exactly the requested size, discard it.
        if (bb.remaining() == size) {
          return bb;
        }
      }

      return delegate.apply(size);
    }

    public void reuse(ByteBuffer buffer) {
      buffer.rewind();
      reuse.addLast(buffer);
    }
  }

  /** Default {@code minBitsPerBlock} */
  public static final int DEFAULT_MIN_BITS_PER_BLOCK = 10; // 1024 B

  /** Default {@code maxBitsPerBlock} */
  public static final int DEFAULT_MAX_BITS_PER_BLOCK = 26; //   64 MB

  /** Smallest {@code minBitsPerBlock} allowed */
  public static final int LIMIT_MIN_BITS_PER_BLOCK = 1;

  /** Largest {@code maxBitsPerBlock} allowed */
  public static final int LIMIT_MAX_BITS_PER_BLOCK = 31;

  /**
   * Maximum number of blocks at the current {@link #blockBits} block size before we increase the
   * block size (and thus decrease the number of blocks).
   */
  static final int MAX_BLOCKS_BEFORE_BLOCK_EXPANSION = 100;

  /** Maximum block size: {@code 2^bits}. */
  private final int maxBitsPerBlock;

  /** {@link ByteBuffer} supplier. */
  private final IntFunction<ByteBuffer> blockAllocate;

  /** {@link ByteBuffer} recycler on {@link #reset}. */
  private final Consumer<ByteBuffer> blockReuse;

  /** Current block size: {@code 2^bits}. */
  private int blockBits;

  /** Blocks storing data. */
  private final ArrayDeque<ByteBuffer> blocks = new ArrayDeque<>();

  /** Cumulative RAM usage across all blocks. */
  private long ramBytesUsed;

  /** The current-or-next write block. */
  private ByteBuffer currentBlock = EMPTY;

  /**
   * Create a new output, suitable for writing a file of around {@code expectedSize} bytes.
   *
   * <p>Memory allocation will be optimized based on the {@code expectedSize} hint, so that there is
   * less overhead for larger files.
   *
   * @param expectedSize estimated size of the output file
   */
  public ByteBuffersDataOutput(long expectedSize) {
    this(
        computeBlockSizeBitsFor(expectedSize),
        DEFAULT_MAX_BITS_PER_BLOCK,
        ALLOCATE_BB_ON_HEAP,
        NO_REUSE);
  }

  /** Creates a new output with all defaults. */
  public ByteBuffersDataOutput() {
    this(DEFAULT_MIN_BITS_PER_BLOCK, DEFAULT_MAX_BITS_PER_BLOCK, ALLOCATE_BB_ON_HEAP, NO_REUSE);
  }

  /**
   * Expert: Creates a new output with custom parameters.
   *
   * @param minBitsPerBlock minimum bits per block
   * @param maxBitsPerBlock maximum bits per block
   * @param blockAllocate block allocator
   * @param blockReuse block recycler
   */
  public ByteBuffersDataOutput(
      int minBitsPerBlock,
      int maxBitsPerBlock,
      IntFunction<ByteBuffer> blockAllocate,
      Consumer<ByteBuffer> blockReuse) {
    if (minBitsPerBlock < LIMIT_MIN_BITS_PER_BLOCK) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT,
              "minBitsPerBlock (%s) too small, must be at least %s",
              minBitsPerBlock,
              LIMIT_MIN_BITS_PER_BLOCK));
    }
    if (maxBitsPerBlock > LIMIT_MAX_BITS_PER_BLOCK) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT,
              "maxBitsPerBlock (%s) too large, must not exceed %s",
              maxBitsPerBlock,
              LIMIT_MAX_BITS_PER_BLOCK));
    }
    if (minBitsPerBlock > maxBitsPerBlock) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT,
              "minBitsPerBlock (%s) cannot exceed maxBitsPerBlock (%s)",
              minBitsPerBlock,
              maxBitsPerBlock));
    }
    this.maxBitsPerBlock = maxBitsPerBlock;
    this.blockBits = minBitsPerBlock;
    this.blockAllocate = Objects.requireNonNull(blockAllocate, "Block allocator must not be null.");
    this.blockReuse = Objects.requireNonNull(blockReuse, "Block reuse must not be null.");
  }

  @Override
  public void writeByte(byte b) {
    if (!currentBlock.hasRemaining()) {
      appendBlock();
    }
    currentBlock.put(b);
  }

  @Override
  public void writeBytes(byte[] src, int offset, int length) {
    assert length >= 0;
    while (length > 0) {
      if (!currentBlock.hasRemaining()) {
        appendBlock();
      }

      int chunk = Math.min(currentBlock.remaining(), length);
      currentBlock.put(src, offset, chunk);
      length -= chunk;
      offset += chunk;
    }
  }

  @Override
  public void writeBytes(byte[] b, int length) {
    writeBytes(b, 0, length);
  }

  public void writeBytes(byte[] b) {
    writeBytes(b, 0, b.length);
  }

  public void writeBytes(ByteBuffer buffer) {
    buffer = buffer.duplicate();
    int length = buffer.remaining();
    while (length > 0) {
      if (!currentBlock.hasRemaining()) {
        appendBlock();
      }

      int chunk = Math.min(currentBlock.remaining(), length);
      buffer.limit(buffer.position() + chunk);
      currentBlock.put(buffer);

      length -= chunk;
    }
  }

  /**
   * Return a list of read-only view of {@link ByteBuffer} blocks over the current content written
   * to the output.
   */
  public ArrayList<ByteBuffer> toBufferList() {
    ArrayList<ByteBuffer> result = new ArrayList<>(Math.max(blocks.size(), 1));
    if (blocks.isEmpty()) {
      result.add(EMPTY);
    } else {
      for (ByteBuffer bb : blocks) {
        bb = bb.asReadOnlyBuffer().flip().order(ByteOrder.LITTLE_ENDIAN);
        result.add(bb);
      }
    }
    return result;
  }

  /**
   * Returns a list of writeable blocks over the (source) content buffers.
   *
   * <p>This method returns the raw content of source buffers that may change over the lifetime of
   * this object (blocks can be recycled or discarded, for example). Most applications should favor
   * calling {@link #toBufferList()} which returns a read-only <i>view</i> over the content of the
   * source buffers.
   *
   * <p>The difference between {@link #toBufferList()} and {@link #toWriteableBufferList()} is that
   * read-only view of source buffers will always return {@code false} from {@link
   * ByteBuffer#hasArray()} (which sometimes may be required to avoid double copying).
   */
  public ArrayList<ByteBuffer> toWriteableBufferList() {
    ArrayList<ByteBuffer> result = new ArrayList<>(Math.max(blocks.size(), 1));
    if (blocks.isEmpty()) {
      result.add(EMPTY);
    } else {
      for (ByteBuffer bb : blocks) {
        bb = bb.duplicate().flip();
        result.add(bb);
      }
    }
    return result;
  }

  /**
   * Return a {@link ByteBuffersDataInput} for the set of current buffers ({@link #toBufferList()}).
   */
  public ByteBuffersDataInput toDataInput() {
    return new ByteBuffersDataInput(toBufferList());
  }

  /**
   * Return a contiguous array with the current content written to the output. The returned array is
   * always a copy (can be mutated).
   *
   * <p>If the {@link #size()} of the underlying buffers exceeds maximum size of Java array, an
   * {@link RuntimeException} will be thrown.
   */
  public byte[] toArrayCopy() {
    if (blocks.size() == 0) {
      return EMPTY_BYTE_ARRAY;
    }

    // We could try to detect single-block, array-based ByteBuffer here
    // and use Arrays.copyOfRange, but I don't think it's worth the extra
    // instance checks.
    long size = size();
    if (size > Integer.MAX_VALUE) {
      throw new RuntimeException("Data exceeds maximum size of a single byte array: " + size);
    }

    byte[] arr = new byte[Math.toIntExact(size())];
    int offset = 0;
    for (ByteBuffer bb : toBufferList()) {
      int len = bb.remaining();
      bb.get(arr, offset, len);
      offset += len;
    }
    return arr;
  }

  @Override
  public void copyBytes(DataInput input, long numBytes) throws IOException {
    assert numBytes >= 0 : "numBytes=" + numBytes;
    int length = (int) numBytes;
    while (length > 0) {
      if (!currentBlock.hasRemaining()) {
        appendBlock();
      }
      if (!currentBlock.hasArray()) {
        break;
      }
      int chunk = Math.min(currentBlock.remaining(), length);
      final int pos = currentBlock.position();
      input.readBytes(currentBlock.array(), Math.addExact(currentBlock.arrayOffset(), pos), chunk);
      length -= chunk;
      currentBlock.position(pos + chunk);
    }
    // if current block is Direct, we fall back to super.copyBytes for remaining bytes
    if (length > 0) {
      super.copyBytes(input, length);
    }
  }

  /** Copy the current content of this object into another {@link DataOutput}. */
  public void copyTo(DataOutput output) throws IOException {
    for (ByteBuffer bb : blocks) {
      if (bb.hasArray()) {
        output.writeBytes(bb.array(), bb.arrayOffset(), bb.position());
      } else {
        bb = bb.asReadOnlyBuffer().flip();
        output.copyBytes(new ByteBuffersDataInput(Collections.singletonList(bb)), bb.remaining());
      }
    }
  }

  /**
   * @return The number of bytes written to this output so far.
   */
  public long size() {
    long size = 0;
    int blockCount = blocks.size();
    if (blockCount >= 1) {
      long fullBlockSize = (blockCount - 1L) * blockSize();
      long lastBlockSize = blocks.getLast().position();
      size = fullBlockSize + lastBlockSize;
    }
    return size;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.ROOT, "%,d bytes, block size: %,d, blocks: %,d", size(), blockSize(), blocks.size());
  }

  // Specialized versions of writeXXX methods that break execution into
  // fast/ slow path if the result would fall on the current block's
  // boundary.
  //
  // We also remove the IOException from methods because it (theoretically)
  // cannot be thrown from byte buffers.

  @Override
  public void writeShort(short v) {
    try {
      if (currentBlock.remaining() >= Short.BYTES) {
        currentBlock.putShort(v);
      } else {
        super.writeShort(v);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void writeInt(int v) {
    try {
      if (currentBlock.remaining() >= Integer.BYTES) {
        currentBlock.putInt(v);
      } else {
        super.writeInt(v);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void writeLong(long v) {
    try {
      if (currentBlock.remaining() >= Long.BYTES) {
        currentBlock.putLong(v);
      } else {
        super.writeLong(v);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final int MAX_CHARS_PER_WINDOW = 1024;

  @Override
  public void writeString(String v) {
    try {
      if (v.length() <= MAX_CHARS_PER_WINDOW) {
        final BytesRef utf8 = new BytesRef(v);
        writeVInt(utf8.length);
        writeBytes(utf8.bytes, utf8.offset, utf8.length);
      } else {
        writeLongString(v);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void writeMapOfStrings(Map<String, String> map) {
    try {
      super.writeMapOfStrings(map);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void writeSetOfStrings(Set<String> set) {
    try {
      super.writeSetOfStrings(set);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public long ramBytesUsed() {
    // Return a rough estimation for allocated blocks. Note that we do not make
    // any special distinction for direct memory buffers.
    assert ramBytesUsed
        == blocks.stream().mapToLong(ByteBuffer::capacity).sum()
            + (long) blocks.size() * RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    return ramBytesUsed;
  }

  /**
   * This method resets this object to a clean (zero-size) state and publishes any currently
   * allocated buffers for reuse to the reuse strategy provided in the constructor.
   *
   * <p>Sharing byte buffers for reads and writes is dangerous and will very likely lead to
   * hard-to-debug issues, use with great care.
   */
  public void reset() {
    if (blockReuse != NO_REUSE) {
      blocks.forEach(blockReuse);
    }
    blocks.clear();
    ramBytesUsed = 0;
    currentBlock = EMPTY;
  }

  /**
   * @return Returns a new {@link ByteBuffersDataOutput} with the {@link #reset()} capability.
   */
  // TODO: perhaps we can move it out to an utility class (as a supplier of preconfigured
  // instances?)
  public static ByteBuffersDataOutput newResettableInstance() {
    ByteBuffersDataOutput.ByteBufferRecycler reuser =
        new ByteBuffersDataOutput.ByteBufferRecycler(ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP);
    return new ByteBuffersDataOutput(
        ByteBuffersDataOutput.DEFAULT_MIN_BITS_PER_BLOCK,
        ByteBuffersDataOutput.DEFAULT_MAX_BITS_PER_BLOCK,
        reuser::allocate,
        reuser::reuse);
  }

  private int blockSize() {
    return 1 << blockBits;
  }

  private void appendBlock() {
    if (blocks.size() >= MAX_BLOCKS_BEFORE_BLOCK_EXPANSION && blockBits < maxBitsPerBlock) {
      rewriteToBlockSize(blockBits + 1);
      if (blocks.getLast().hasRemaining()) {
        return;
      }
    }

    final int requiredBlockSize = 1 << blockBits;
    currentBlock = blockAllocate.apply(requiredBlockSize).order(ByteOrder.LITTLE_ENDIAN);
    assert currentBlock.capacity() == requiredBlockSize;
    blocks.add(currentBlock);
    ramBytesUsed += RamUsageEstimator.NUM_BYTES_OBJECT_REF + currentBlock.capacity();
  }

  private void rewriteToBlockSize(int targetBlockBits) {
    assert targetBlockBits <= maxBitsPerBlock;

    // We copy over data blocks to an output with one-larger block bit size.
    // We also discard references to blocks as we're copying to allow GC to
    // clean up partial results in case of memory pressure.
    ByteBuffersDataOutput cloned =
        new ByteBuffersDataOutput(targetBlockBits, targetBlockBits, blockAllocate, NO_REUSE);
    ByteBuffer block;
    while ((block = blocks.pollFirst()) != null) {
      block.flip();
      cloned.writeBytes(block);
      if (blockReuse != NO_REUSE) {
        blockReuse.accept(block);
      }
    }

    assert blocks.isEmpty();
    this.blockBits = targetBlockBits;
    blocks.addAll(cloned.blocks);
    ramBytesUsed = cloned.ramBytesUsed;
  }

  private static int computeBlockSizeBitsFor(long bytes) {
    long powerOfTwo = BitUtil.nextHighestPowerOfTwo(bytes / MAX_BLOCKS_BEFORE_BLOCK_EXPANSION);
    if (powerOfTwo == 0) {
      return DEFAULT_MIN_BITS_PER_BLOCK;
    }

    int blockBits = Long.numberOfTrailingZeros(powerOfTwo);
    blockBits = Math.min(blockBits, DEFAULT_MAX_BITS_PER_BLOCK);
    blockBits = Math.max(blockBits, DEFAULT_MIN_BITS_PER_BLOCK);
    return blockBits;
  }

  /** Writes a long string in chunks */
  private void writeLongString(final String s) throws IOException {
    final int byteLen = UnicodeUtil.calcUTF16toUTF8Length(s, 0, s.length());
    writeVInt(byteLen);
    final byte[] buf =
        new byte[Math.min(byteLen, UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR * MAX_CHARS_PER_WINDOW)];
    for (int i = 0, end = s.length(); i < end; ) {
      // do one fewer chars than MAX_CHARS_PER_WINDOW in case we run into an unpaired surrogate
      // below and need to increase the step to cover the lower surrogate as well
      int step = Math.min(end - i, MAX_CHARS_PER_WINDOW - 1);
      if (i + step < end && Character.isHighSurrogate(s.charAt(i + step - 1))) {
        step++;
      }
      int upTo = UnicodeUtil.UTF16toUTF8(s, i, step, buf);
      writeBytes(buf, 0, upTo);
      i += step;
    }
  }
}
