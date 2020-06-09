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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.util.bkd;


import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.LongBitSet;

public abstract class PointReader implements Closeable {

  public abstract boolean next() throws IOException;

  public abstract byte[] packedValue();

  public abstract long ord();

  public abstract int docID();

  public void markOrds(long count, LongBitSet ordBitSet) throws IOException {
    for(int i=0;i<count;i++) {
      boolean result = next();
      if (result == false) {
        throw new IllegalStateException("did not see enough points from reader=" + this);
      }
      assert ordBitSet.get(ord()) == false: "ord=" + ord() + " was seen twice from " + this;
      ordBitSet.set(ord());
    }
  }

  public long split(long count, LongBitSet rightTree, PointWriter left, PointWriter right, boolean doClearBits) throws IOException {

    // Partition this source according to how the splitDim split the values:
    long rightCount = 0;
    for (long i=0;i<count;i++) {
      boolean result = next();
      assert result;
      byte[] packedValue = packedValue();
      long ord = ord();
      int docID = docID();
      if (rightTree.get(ord)) {
        right.append(packedValue, ord, docID);
        rightCount++;
        if (doClearBits) {
          rightTree.clear(ord);
        }
      } else {
        left.append(packedValue, ord, docID);
      }
    }

    return rightCount;
  }
}

