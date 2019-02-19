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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.util.fst;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.BytesRef;

public final class BytesRefFSTEnum<T> extends FSTEnum<T> {
  private final BytesRef current = new BytesRef(10);
  private final InputOutput<T> result = new InputOutput<T>();
  private BytesRef target;

  public static class InputOutput<T> {
    public BytesRef input;
    public T output;
  }


  public BytesRefFSTEnum(FST<T> fst) {
    super(fst);
    result.input = current;
    current.offset = 1;
  }

  public InputOutput<T> current() {
    return result;
  }

  public InputOutput<T> next() throws IOException {
    //System.out.println("  enum.next");
    doNext();
    return setResult();
  }

  public InputOutput<T> seekCeil(BytesRef target) throws IOException {
    this.target = target;
    targetLength = target.length;
    super.doSeekCeil();
    return setResult();
  }

  public InputOutput<T> seekFloor(BytesRef target) throws IOException {
    this.target = target;
    targetLength = target.length;
    super.doSeekFloor();
    return setResult();
  }


  public InputOutput<T> seekExact(BytesRef target) throws IOException {
    this.target = target;
    targetLength = target.length;
    if (super.doSeekExact()) {
      assert upto == 1+target.length;
      return setResult();
    } else {
      return null;
    }
  }

  @Override
  protected int getTargetLabel() {
    if (upto-1 == target.length) {
      return FST.END_LABEL;
    } else {
      return target.bytes[target.offset + upto - 1] & 0xFF;
    }
  }

  @Override
  protected int getCurrentLabel() {
    // current.offset fixed at 1
    return current.bytes[upto] & 0xFF;
  }

  @Override
  protected void setCurrentLabel(int label) {
    current.bytes[upto] = (byte) label;
  }

  @Override
  protected void grow() {
    current.bytes = ArrayUtil.grow(current.bytes, upto+1);
  }

  private InputOutput<T> setResult() {
    if (upto == 0) {
      return null;
    } else {
      current.length = upto-1;
      result.output = output[upto];
      return result;
    }
  }
}
