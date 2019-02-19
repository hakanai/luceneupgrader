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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.fst;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ArrayUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IntsRef;

import java.io.IOException;

public final class IntsRefFSTEnum<T> extends FSTEnum<T> {
  private final IntsRef current = new IntsRef(10);
  private final InputOutput<T> result = new InputOutput<>();
  private IntsRef target;

  public static class InputOutput<T> {
    public IntsRef input;
    public T output;
  }


  public IntsRefFSTEnum(FST<T> fst) {
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

  public InputOutput<T> seekCeil(IntsRef target) throws IOException {
    this.target = target;
    targetLength = target.length;
    super.doSeekCeil();
    return setResult();
  }

  public InputOutput<T> seekFloor(IntsRef target) throws IOException {
    this.target = target;
    targetLength = target.length;
    super.doSeekFloor();
    return setResult();
  }


  public InputOutput<T> seekExact(IntsRef target) throws IOException {
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
      return target.ints[target.offset + upto - 1];
    }
  }

  @Override
  protected int getCurrentLabel() {
    // current.offset fixed at 1
    return current.ints[upto];
  }

  @Override
  protected void setCurrentLabel(int label) {
    current.ints[upto] = label;
  }

  @Override
  protected void grow() {
    current.ints = ArrayUtil.grow(current.ints, upto+1);
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
