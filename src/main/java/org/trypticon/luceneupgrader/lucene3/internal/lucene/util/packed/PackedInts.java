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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util.packed;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CodecUtil;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Constants;

import java.io.IOException;

public class PackedInts {

  private final static String CODEC_NAME = "PackedInts";
  private final static int VERSION_START = 0;
  private final static int VERSION_CURRENT = VERSION_START;

  public static interface Reader {
    long get(int index);

    int getBitsPerValue();

    int size();

    Object getArray();

    boolean hasArray();
  }
  
  public static interface Mutable extends Reader {
    void set(int index, long value);

    void clear();
  }

  public static abstract class ReaderImpl implements Reader {
    protected final int bitsPerValue;
    protected final int valueCount;

    protected ReaderImpl(int valueCount, int bitsPerValue) {
      this.bitsPerValue = bitsPerValue;
      assert bitsPerValue > 0 && bitsPerValue <= 64 : "bitsPerValue=" + bitsPerValue;
      this.valueCount = valueCount;
    }

    public int getBitsPerValue() {
      return bitsPerValue;
    }
    
    public int size() {
      return valueCount;
    }

    public long getMaxValue() { // Convenience method
      return maxValue(bitsPerValue);
    }

    public Object getArray() {
      return null;
    }

    public boolean hasArray() {
      return false;
    }
  }


  public static abstract class Writer {
    protected final DataOutput out;
    protected final int bitsPerValue;
    protected final int valueCount;

    protected Writer(DataOutput out, int valueCount, int bitsPerValue)
      throws IOException {
      assert bitsPerValue <= 64;

      this.out = out;
      this.valueCount = valueCount;
      this.bitsPerValue = bitsPerValue;
      CodecUtil.writeHeader(out, CODEC_NAME, VERSION_CURRENT);
      out.writeVInt(bitsPerValue);
      out.writeVInt(valueCount);
    }

    public abstract void add(long v) throws IOException;
    public abstract void finish() throws IOException;
  }

  public static Reader getReader(DataInput in) throws IOException {
    CodecUtil.checkHeader(in, CODEC_NAME, VERSION_START, VERSION_START);
    final int bitsPerValue = in.readVInt();
    assert bitsPerValue > 0 && bitsPerValue <= 64: "bitsPerValue=" + bitsPerValue;
    final int valueCount = in.readVInt();

    switch (bitsPerValue) {
    case 8:
      return new Direct8(in, valueCount);
    case 16:
      return new Direct16(in, valueCount);
    case 32:
      return new Direct32(in, valueCount);
    case 64:
      return new Direct64(in, valueCount);
    default:
      if (Constants.JRE_IS_64BIT || bitsPerValue >= 32) {
        return new Packed64(in, valueCount, bitsPerValue);
      } else {
        return new Packed32(in, valueCount, bitsPerValue);
      }
    }
  }

  public static Mutable getMutable(
         int valueCount, int bitsPerValue) {
    switch (bitsPerValue) {
    case 8:
      return new Direct8(valueCount);
    case 16:
      return new Direct16(valueCount);
    case 32:
      return new Direct32(valueCount);
    case 64:
      return new Direct64(valueCount);
    default:
      if (Constants.JRE_IS_64BIT || bitsPerValue >= 32) {
        return new Packed64(valueCount, bitsPerValue);
      } else {
        return new Packed32(valueCount, bitsPerValue);
      }
    }
  }

  public static Writer getWriter(DataOutput out, int valueCount, int bitsPerValue)
    throws IOException {
    return new PackedWriter(out, valueCount, bitsPerValue);
  }


  public static int bitsRequired(long maxValue) {
    // Very high long values does not translate well to double, so we do an
    // explicit check for the edge cases
    if (maxValue > 0x3FFFFFFFFFFFFFFFL) {
      return 63;
    } if (maxValue > 0x1FFFFFFFFFFFFFFFL) {
      return 62;
    }
    return Math.max(1, (int) Math.ceil(Math.log(1+maxValue)/Math.log(2.0)));
  }

  public static long maxValue(int bitsPerValue) {
    return bitsPerValue == 64 ? Long.MAX_VALUE : ~(~0L << bitsPerValue);
  }

  public static int getNextFixedSize(int bitsPerValue) {
    if (bitsPerValue <= 8) {
      return 8;
    } else if (bitsPerValue <= 16) {
      return 16;
    } else if (bitsPerValue <= 32) {
      return 32;
    } else {
      return 64;
    }
  }

  public static int getRoundedFixedSize(int bitsPerValue) {
    if (bitsPerValue > 58 || (bitsPerValue < 32 && bitsPerValue > 29)) { // 10% space-waste is ok
      return getNextFixedSize(bitsPerValue);
    } else {
      return bitsPerValue;
    }
  }
}
