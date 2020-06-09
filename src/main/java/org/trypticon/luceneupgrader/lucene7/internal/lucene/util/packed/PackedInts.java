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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed;


import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LegacyNumericDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Accountable;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.LongsRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.RamUsageEstimator;

public class PackedInts {

  public static final float FASTEST = 7f;

  public static final float FAST = 0.5f;

  public static final float DEFAULT = 0.25f;

  public static final float COMPACT = 0f;

  public static final int DEFAULT_BUFFER_SIZE = 1024; // 1K

  public final static String CODEC_NAME = "PackedInts";
  public static final int VERSION_MONOTONIC_WITHOUT_ZIGZAG = 2;
  public final static int VERSION_START = VERSION_MONOTONIC_WITHOUT_ZIGZAG;
  public final static int VERSION_CURRENT = VERSION_MONOTONIC_WITHOUT_ZIGZAG;

  public static void checkVersion(int version) {
    if (version < VERSION_START) {
      throw new IllegalArgumentException("Version is too old, should be at least " + VERSION_START + " (got " + version + ")");
    } else if (version > VERSION_CURRENT) {
      throw new IllegalArgumentException("Version is too new, should be at most " + VERSION_CURRENT + " (got " + version + ")");
    }
  }

  public enum Format {
    PACKED(0) {

      @Override
      public long byteCount(int packedIntsVersion, int valueCount, int bitsPerValue) {
        return (long) Math.ceil((double) valueCount * bitsPerValue / 8);      
      }

    },

    PACKED_SINGLE_BLOCK(1) {

      @Override
      public int longCount(int packedIntsVersion, int valueCount, int bitsPerValue) {
        final int valuesPerBlock = 64 / bitsPerValue;
        return (int) Math.ceil((double) valueCount / valuesPerBlock);
      }

      @Override
      public boolean isSupported(int bitsPerValue) {
        return Packed64SingleBlock.isSupported(bitsPerValue);
      }

      @Override
      public float overheadPerValue(int bitsPerValue) {
        assert isSupported(bitsPerValue);
        final int valuesPerBlock = 64 / bitsPerValue;
        final int overhead = 64 % bitsPerValue;
        return (float) overhead / valuesPerBlock;
      }

    };

    public static Format byId(int id) {
      for (Format format : Format.values()) {
        if (format.getId() == id) {
          return format;
        }
      }
      throw new IllegalArgumentException("Unknown format id: " + id);
    }

    private Format(int id) {
      this.id = id;
    }

    public int id;

    public int getId() {
      return id;
    }

    public long byteCount(int packedIntsVersion, int valueCount, int bitsPerValue) {
      assert bitsPerValue >= 0 && bitsPerValue <= 64 : bitsPerValue;
      // assume long-aligned
      return 8L * longCount(packedIntsVersion, valueCount, bitsPerValue);
    }

    public int longCount(int packedIntsVersion, int valueCount, int bitsPerValue) {
      assert bitsPerValue >= 0 && bitsPerValue <= 64 : bitsPerValue;
      final long byteCount = byteCount(packedIntsVersion, valueCount, bitsPerValue);
      assert byteCount < 8L * Integer.MAX_VALUE;
      if ((byteCount % 8) == 0) {
        return (int) (byteCount / 8);
      } else {
        return (int) (byteCount / 8 + 1);
      }
    }

    public boolean isSupported(int bitsPerValue) {
      return bitsPerValue >= 1 && bitsPerValue <= 64;
    }

    public float overheadPerValue(int bitsPerValue) {
      assert isSupported(bitsPerValue);
      return 0f;
    }

    public final float overheadRatio(int bitsPerValue) {
      assert isSupported(bitsPerValue);
      return overheadPerValue(bitsPerValue) / bitsPerValue;
    }
  }

  public static class FormatAndBits {
    public final Format format;
    public final int bitsPerValue;
    public FormatAndBits(Format format, int bitsPerValue) {
      this.format = format;
      this.bitsPerValue = bitsPerValue;
    }

    @Override
    public String toString() {
      return "FormatAndBits(format=" + format + " bitsPerValue=" + bitsPerValue + ")";
    }
  }

  public static FormatAndBits fastestFormatAndBits(int valueCount, int bitsPerValue, float acceptableOverheadRatio) {
    if (valueCount == -1) {
      valueCount = Integer.MAX_VALUE;
    }

    acceptableOverheadRatio = Math.max(COMPACT, acceptableOverheadRatio);
    acceptableOverheadRatio = Math.min(FASTEST, acceptableOverheadRatio);
    float acceptableOverheadPerValue = acceptableOverheadRatio * bitsPerValue; // in bits

    int maxBitsPerValue = bitsPerValue + (int) acceptableOverheadPerValue;

    int actualBitsPerValue = -1;
    Format format = Format.PACKED;

    if (bitsPerValue <= 8 && maxBitsPerValue >= 8) {
      actualBitsPerValue = 8;
    } else if (bitsPerValue <= 16 && maxBitsPerValue >= 16) {
      actualBitsPerValue = 16;
    } else if (bitsPerValue <= 32 && maxBitsPerValue >= 32) {
      actualBitsPerValue = 32;
    } else if (bitsPerValue <= 64 && maxBitsPerValue >= 64) {
      actualBitsPerValue = 64;
    } else if (valueCount <= Packed8ThreeBlocks.MAX_SIZE && bitsPerValue <= 24 && maxBitsPerValue >= 24) {
      actualBitsPerValue = 24;
    } else if (valueCount <= Packed16ThreeBlocks.MAX_SIZE && bitsPerValue <= 48 && maxBitsPerValue >= 48) {
      actualBitsPerValue = 48;
    } else {
      for (int bpv = bitsPerValue; bpv <= maxBitsPerValue; ++bpv) {
        if (Format.PACKED_SINGLE_BLOCK.isSupported(bpv)) {
          float overhead = Format.PACKED_SINGLE_BLOCK.overheadPerValue(bpv);
          float acceptableOverhead = acceptableOverheadPerValue + bitsPerValue - bpv;
          if (overhead <= acceptableOverhead) {
            actualBitsPerValue = bpv;
            format = Format.PACKED_SINGLE_BLOCK;
            break;
          }
        }
      }
      if (actualBitsPerValue < 0) {
        actualBitsPerValue = bitsPerValue;
      }
    }

    return new FormatAndBits(format, actualBitsPerValue);
  }

  public static interface Decoder {

    int longBlockCount();

    int longValueCount();

    int byteBlockCount();

    int byteValueCount();

    void decode(long[] blocks, int blocksOffset, long[] values, int valuesOffset, int iterations);

    void decode(byte[] blocks, int blocksOffset, long[] values, int valuesOffset, int iterations);

    void decode(long[] blocks, int blocksOffset, int[] values, int valuesOffset, int iterations);

    void decode(byte[] blocks, int blocksOffset, int[] values, int valuesOffset, int iterations);

  }

  public static interface Encoder {

    int longBlockCount();

    int longValueCount();

    int byteBlockCount();

    int byteValueCount();

    void encode(long[] values, int valuesOffset, long[] blocks, int blocksOffset, int iterations);

    void encode(long[] values, int valuesOffset, byte[] blocks, int blocksOffset, int iterations);

    void encode(int[] values, int valuesOffset, long[] blocks, int blocksOffset, int iterations);

    void encode(int[] values, int valuesOffset, byte[] blocks, int blocksOffset, int iterations);

  }

  public static abstract class Reader extends LegacyNumericDocValues implements Accountable {

    public int get(int index, long[] arr, int off, int len) {
      assert len > 0 : "len must be > 0 (got " + len + ")";
      assert index >= 0 && index < size();
      assert off + len <= arr.length;

      final int gets = Math.min(size() - index, len);
      for (int i = index, o = off, end = index + gets; i < end; ++i, ++o) {
        arr[o] = get(i);
      }
      return gets;
    }

    public abstract int size();
  }

  public static interface ReaderIterator {
    long next() throws IOException;
    LongsRef next(int count) throws IOException;
    int getBitsPerValue();
    int size();
    int ord();
  }

  static abstract class ReaderIteratorImpl implements ReaderIterator {

    protected final DataInput in;
    protected final int bitsPerValue;
    protected final int valueCount;

    protected ReaderIteratorImpl(int valueCount, int bitsPerValue, DataInput in) {
      this.in = in;
      this.bitsPerValue = bitsPerValue;
      this.valueCount = valueCount;
    }

    @Override
    public long next() throws IOException {
      LongsRef nextValues = next(1);
      assert nextValues.length > 0;
      final long result = nextValues.longs[nextValues.offset];
      ++nextValues.offset;
      --nextValues.length;
      return result;
    }

    @Override
    public int getBitsPerValue() {
      return bitsPerValue;
    }

    @Override
    public int size() {
      return valueCount;
    }
  }

  public static abstract class Mutable extends Reader {

    public abstract int getBitsPerValue();

    public abstract void set(int index, long value);

    public int set(int index, long[] arr, int off, int len) {
      assert len > 0 : "len must be > 0 (got " + len + ")";
      assert index >= 0 && index < size();
      len = Math.min(len, size() - index);
      assert off + len <= arr.length;

      for (int i = index, o = off, end = index + len; i < end; ++i, ++o) {
        set(i, arr[o]);
      }
      return len;
    }

    public void fill(int fromIndex, int toIndex, long val) {
      assert val <= maxValue(getBitsPerValue());
      assert fromIndex <= toIndex;
      for (int i = fromIndex; i < toIndex; ++i) {
        set(i, val);
      }
    }

    public void clear() {
      fill(0, size(), 0);
    }

    public void save(DataOutput out) throws IOException {
      Writer writer = getWriterNoHeader(out, getFormat(), size(), getBitsPerValue(), DEFAULT_BUFFER_SIZE);
      writer.writeHeader();
      for (int i = 0; i < size(); ++i) {
        writer.add(get(i));
      }
      writer.finish();
    }

    Format getFormat() {
      return Format.PACKED;
    }

  }

  static abstract class ReaderImpl extends Reader {
    protected final int valueCount;

    protected ReaderImpl(int valueCount) {
      this.valueCount = valueCount;
    }

    @Override
    public abstract long get(int index);

    @Override
    public final int size() {
      return valueCount;
    }
  }

  static abstract class MutableImpl extends Mutable {

    protected final int valueCount;
    protected final int bitsPerValue;

    protected MutableImpl(int valueCount, int bitsPerValue) {
      this.valueCount = valueCount;
      assert bitsPerValue > 0 && bitsPerValue <= 64 : "bitsPerValue=" + bitsPerValue;
      this.bitsPerValue = bitsPerValue;
    }

    @Override
    public final int getBitsPerValue() {
      return bitsPerValue;
    }

    @Override
    public final int size() {
      return valueCount;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(valueCount=" + valueCount + ",bitsPerValue=" + bitsPerValue + ")";
    }
  }

  public static final class NullReader extends Reader {

    private final int valueCount;

    public NullReader(int valueCount) {
      this.valueCount = valueCount;
    }

    @Override
    public long get(int index) {
      return 0;
    }

    @Override
    public int get(int index, long[] arr, int off, int len) {
      assert len > 0 : "len must be > 0 (got " + len + ")";
      assert index >= 0 && index < valueCount;
      len = Math.min(len, valueCount - index);
      Arrays.fill(arr, off, off + len, 0);
      return len;
    }

    @Override
    public int size() {
      return valueCount;
    }

    @Override
    public long ramBytesUsed() {
      return RamUsageEstimator.alignObjectSize(RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + Integer.BYTES);
    }
  }

  public static abstract class Writer {
    protected final DataOutput out;
    protected final int valueCount;
    protected final int bitsPerValue;

    protected Writer(DataOutput out, int valueCount, int bitsPerValue) {
      assert bitsPerValue <= 64;
      assert valueCount >= 0 || valueCount == -1;
      this.out = out;
      this.valueCount = valueCount;
      this.bitsPerValue = bitsPerValue;
    }

    void writeHeader() throws IOException {
      assert valueCount != -1;
      CodecUtil.writeHeader(out, CODEC_NAME, VERSION_CURRENT);
      out.writeVInt(bitsPerValue);
      out.writeVInt(valueCount);
      out.writeVInt(getFormat().getId());
    }

    protected abstract PackedInts.Format getFormat();

    public abstract void add(long v) throws IOException;

    public final int bitsPerValue() {
      return bitsPerValue;
    }

    public abstract void finish() throws IOException;

    public abstract int ord();
  }

  public static Decoder getDecoder(Format format, int version, int bitsPerValue) {
    checkVersion(version);
    return BulkOperation.of(format, bitsPerValue);
  }

  public static Encoder getEncoder(Format format, int version, int bitsPerValue) {
    checkVersion(version);
    return BulkOperation.of(format, bitsPerValue);
  }

  public static Reader getReaderNoHeader(DataInput in, Format format, int version,
      int valueCount, int bitsPerValue) throws IOException {
    checkVersion(version);
    switch (format) {
      case PACKED_SINGLE_BLOCK:
        return Packed64SingleBlock.create(in, valueCount, bitsPerValue);
      case PACKED:
        switch (bitsPerValue) {
          case 8:
            return new Direct8(version, in, valueCount);
          case 16:
            return new Direct16(version, in, valueCount);
          case 32:
            return new Direct32(version, in, valueCount);
          case 64:
            return new Direct64(version, in, valueCount);
          case 24:
            if (valueCount <= Packed8ThreeBlocks.MAX_SIZE) {
              return new Packed8ThreeBlocks(version, in, valueCount);
            }
            break;
          case 48:
            if (valueCount <= Packed16ThreeBlocks.MAX_SIZE) {
              return new Packed16ThreeBlocks(version, in, valueCount);
            }
            break;
        }
        return new Packed64(version, in, valueCount, bitsPerValue);
      default:
        throw new AssertionError("Unknown Writer format: " + format);
    }
  }

  public static Reader getReader(DataInput in) throws IOException {
    final int version = CodecUtil.checkHeader(in, CODEC_NAME, VERSION_START, VERSION_CURRENT);
    final int bitsPerValue = in.readVInt();
    assert bitsPerValue > 0 && bitsPerValue <= 64: "bitsPerValue=" + bitsPerValue;
    final int valueCount = in.readVInt();
    final Format format = Format.byId(in.readVInt());

    return getReaderNoHeader(in, format, version, valueCount, bitsPerValue);
  }

  public static ReaderIterator getReaderIteratorNoHeader(DataInput in, Format format, int version,
      int valueCount, int bitsPerValue, int mem) {
    checkVersion(version);
    return new PackedReaderIterator(format, version, valueCount, bitsPerValue, in, mem);
  }

  public static ReaderIterator getReaderIterator(DataInput in, int mem) throws IOException {
    final int version = CodecUtil.checkHeader(in, CODEC_NAME, VERSION_START, VERSION_CURRENT);
    final int bitsPerValue = in.readVInt();
    assert bitsPerValue > 0 && bitsPerValue <= 64: "bitsPerValue=" + bitsPerValue;
    final int valueCount = in.readVInt();
    final Format format = Format.byId(in.readVInt());
    return getReaderIteratorNoHeader(in, format, version, valueCount, bitsPerValue, mem);
  }

  public static Reader getDirectReaderNoHeader(final IndexInput in, Format format,
      int version, int valueCount, int bitsPerValue) {
    checkVersion(version);
    switch (format) {
      case PACKED:
        return new DirectPackedReader(bitsPerValue, valueCount, in);
      case PACKED_SINGLE_BLOCK:
        return new DirectPacked64SingleBlockReader(bitsPerValue, valueCount, in);
      default:
        throw new AssertionError("Unknwown format: " + format);
    }
  }

  public static Reader getDirectReader(IndexInput in) throws IOException {
    final int version = CodecUtil.checkHeader(in, CODEC_NAME, VERSION_START, VERSION_CURRENT);
    final int bitsPerValue = in.readVInt();
    assert bitsPerValue > 0 && bitsPerValue <= 64: "bitsPerValue=" + bitsPerValue;
    final int valueCount = in.readVInt();
    final Format format = Format.byId(in.readVInt());
    return getDirectReaderNoHeader(in, format, version, valueCount, bitsPerValue);
  }
  
  public static Mutable getMutable(int valueCount,
      int bitsPerValue, float acceptableOverheadRatio) {
    final FormatAndBits formatAndBits = fastestFormatAndBits(valueCount, bitsPerValue, acceptableOverheadRatio);
    return getMutable(valueCount, formatAndBits.bitsPerValue, formatAndBits.format);
  }

  public static Mutable getMutable(int valueCount,
      int bitsPerValue, PackedInts.Format format) {
    assert valueCount >= 0;
    switch (format) {
      case PACKED_SINGLE_BLOCK:
        return Packed64SingleBlock.create(valueCount, bitsPerValue);
      case PACKED:
        switch (bitsPerValue) {
          case 8:
            return new Direct8(valueCount);
          case 16:
            return new Direct16(valueCount);
          case 32:
            return new Direct32(valueCount);
          case 64:
            return new Direct64(valueCount);
          case 24:
            if (valueCount <= Packed8ThreeBlocks.MAX_SIZE) {
              return new Packed8ThreeBlocks(valueCount);
            }
            break;
          case 48:
            if (valueCount <= Packed16ThreeBlocks.MAX_SIZE) {
              return new Packed16ThreeBlocks(valueCount);
            }
            break;
        }
        return new Packed64(valueCount, bitsPerValue);
      default:
        throw new AssertionError();
    }
  }

  public static Writer getWriterNoHeader(
      DataOutput out, Format format, int valueCount, int bitsPerValue, int mem) {
    return new PackedWriter(format, out, valueCount, bitsPerValue, mem);
  }

  public static Writer getWriter(DataOutput out,
      int valueCount, int bitsPerValue, float acceptableOverheadRatio)
    throws IOException {
    assert valueCount >= 0;

    final FormatAndBits formatAndBits = fastestFormatAndBits(valueCount, bitsPerValue, acceptableOverheadRatio);
    final Writer writer = getWriterNoHeader(out, formatAndBits.format, valueCount, formatAndBits.bitsPerValue, DEFAULT_BUFFER_SIZE);
    writer.writeHeader();
    return writer;
  }

  public static int bitsRequired(long maxValue) {
    if (maxValue < 0) {
      throw new IllegalArgumentException("maxValue must be non-negative (got: " + maxValue + ")");
    }
    return unsignedBitsRequired(maxValue);
  }

  public static int unsignedBitsRequired(long bits) {
    return Math.max(1, 64 - Long.numberOfLeadingZeros(bits));
  }

  public static long maxValue(int bitsPerValue) {
    return bitsPerValue == 64 ? Long.MAX_VALUE : ~(~0L << bitsPerValue);
  }

  public static void copy(Reader src, int srcPos, Mutable dest, int destPos, int len, int mem) {
    assert srcPos + len <= src.size();
    assert destPos + len <= dest.size();
    final int capacity = mem >>> 3;
    if (capacity == 0) {
      for (int i = 0; i < len; ++i) {
        dest.set(destPos++, src.get(srcPos++));
      }
    } else if (len > 0) {
      // use bulk operations
      final long[] buf = new long[Math.min(capacity, len)];
      copy(src, srcPos, dest, destPos, len, buf);
    }
  }

  static void copy(Reader src, int srcPos, Mutable dest, int destPos, int len, long[] buf) {
    assert buf.length > 0;
    int remaining = 0;
    while (len > 0) {
      final int read = src.get(srcPos, buf, remaining, Math.min(len, buf.length - remaining));
      assert read > 0;
      srcPos += read;
      len -= read;
      remaining += read;
      final int written = dest.set(destPos, buf, 0, remaining);
      assert written > 0;
      destPos += written;
      if (written < remaining) {
        System.arraycopy(buf, written, buf, 0, remaining - written);
      }
      remaining -= written;
    }
    while (remaining > 0) {
      final int written = dest.set(destPos, buf, 0, remaining);
      destPos += written;
      remaining -= written;
      System.arraycopy(buf, written, buf, 0, remaining);
    }
  }

  static int checkBlockSize(int blockSize, int minBlockSize, int maxBlockSize) {
    if (blockSize < minBlockSize || blockSize > maxBlockSize) {
      throw new IllegalArgumentException("blockSize must be >= " + minBlockSize + " and <= " + maxBlockSize + ", got " + blockSize);
    }
    if ((blockSize & (blockSize - 1)) != 0) {
      throw new IllegalArgumentException("blockSize must be a power of two, got " + blockSize);
    }
    return Integer.numberOfTrailingZeros(blockSize);
  }

  static int numBlocks(long size, int blockSize) {
    final int numBlocks = (int) (size / blockSize) + (size % blockSize == 0 ? 0 : 1);
    if ((long) numBlocks * blockSize < size) {
      throw new IllegalArgumentException("size is too large for this block size");
    }
    return numBlocks;
  }

}
