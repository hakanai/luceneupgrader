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

import java.io.IOException;
import java.util.Arrays;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.RamUsageEstimator;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.StringHelper;

/**
 * An FST {@link Outputs} implementation where each output is a sequence of bytes.
 *
 * @lucene.experimental
 */
public final class ByteSequenceOutputs extends Outputs<BytesRef> {

  private static final BytesRef NO_OUTPUT = new BytesRef();
  private static final ByteSequenceOutputs singleton = new ByteSequenceOutputs();

  private ByteSequenceOutputs() {}

  public static ByteSequenceOutputs getSingleton() {
    return singleton;
  }

  @Override
  public BytesRef common(BytesRef output1, BytesRef output2) {
    assert output1 != null;
    assert output2 != null;

    int mismatchPos =
        Arrays.mismatch(
            output1.bytes,
            output1.offset,
            output1.offset + output1.length,
            output2.bytes,
            output2.offset,
            output2.offset + output2.length);

    if (mismatchPos == 0) {
      // no common prefix
      return NO_OUTPUT;
    } else if (mismatchPos == -1) {
      // exactly equals
      return output1;
    } else if (mismatchPos == output1.length) {
      // output1 is a prefix of output2
      return output1;
    } else if (mismatchPos == output2.length) {
      // output2 is a prefix of output1
      return output2;
    } else {
      return new BytesRef(output1.bytes, output1.offset, mismatchPos);
    }
  }

  @Override
  public BytesRef subtract(BytesRef output, BytesRef inc) {
    assert output != null;
    assert inc != null;
    if (inc == NO_OUTPUT) {
      // no prefix removed
      return output;
    } else {
      assert StringHelper.startsWith(output, inc);
      if (inc.length == output.length) {
        // entire output removed
        return NO_OUTPUT;
      } else {
        assert inc.length < output.length
            : "inc.length=" + inc.length + " vs output.length=" + output.length;
        assert inc.length > 0;
        return new BytesRef(output.bytes, output.offset + inc.length, output.length - inc.length);
      }
    }
  }

  @Override
  public BytesRef add(BytesRef prefix, BytesRef output) {
    assert prefix != null;
    assert output != null;
    if (prefix == NO_OUTPUT) {
      return output;
    } else if (output == NO_OUTPUT) {
      return prefix;
    } else {
      assert prefix.length > 0;
      assert output.length > 0;
      BytesRef result = new BytesRef(prefix.length + output.length);
      System.arraycopy(prefix.bytes, prefix.offset, result.bytes, 0, prefix.length);
      System.arraycopy(output.bytes, output.offset, result.bytes, prefix.length, output.length);
      result.length = prefix.length + output.length;
      return result;
    }
  }

  @Override
  public void write(BytesRef prefix, DataOutput out) throws IOException {
    assert prefix != null;
    out.writeVInt(prefix.length);
    out.writeBytes(prefix.bytes, prefix.offset, prefix.length);
  }

  @Override
  public BytesRef read(DataInput in) throws IOException {
    final int len = in.readVInt();
    if (len == 0) {
      return NO_OUTPUT;
    } else {
      final BytesRef output = new BytesRef(len);
      in.readBytes(output.bytes, 0, len);
      output.length = len;
      return output;
    }
  }

  @Override
  public void skipOutput(DataInput in) throws IOException {
    final int len = in.readVInt();
    if (len != 0) {
      in.skipBytes(len);
    }
  }

  @Override
  public BytesRef getNoOutput() {
    return NO_OUTPUT;
  }

  @Override
  public String outputToString(BytesRef output) {
    return output.toString();
  }

  private static final long BASE_NUM_BYTES = RamUsageEstimator.shallowSizeOf(NO_OUTPUT);

  @Override
  public long ramBytesUsed(BytesRef output) {
    return BASE_NUM_BYTES + RamUsageEstimator.sizeOf(output.bytes);
  }

  @Override
  public String toString() {
    return "ByteSequenceOutputs";
  }
}
