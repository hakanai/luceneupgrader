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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.util.fst;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.store.DataOutput;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;

public abstract class Outputs<T> {

  // TODO: maybe change this API to allow for re-use of the
  // output instances -- this is an insane amount of garbage
  // (new object per byte/char/int) if eg used during
  // analysis

  public abstract T common(T output1, T output2);

  public abstract T subtract(T output, T inc);

  public abstract T add(T prefix, T output);

  public abstract void write(T output, DataOutput out) throws IOException;


  public void writeFinalOutput(T output, DataOutput out) throws IOException {
    write(output, out);
  }

  public abstract T read(DataInput in) throws IOException;

  public void skipOutput(DataInput in) throws IOException {
    read(in);
  }


  public T readFinalOutput(DataInput in) throws IOException {
    return read(in);
  }
  

  public void skipFinalOutput(DataInput in) throws IOException {
    skipOutput(in);
  }


  public abstract T getNoOutput();

  public abstract String outputToString(T output);

  // TODO: maybe make valid(T output) public...?  for asserts

  public T merge(T first, T second) {
    throw new UnsupportedOperationException();
  }

  public abstract long ramBytesUsed(T output);
}
