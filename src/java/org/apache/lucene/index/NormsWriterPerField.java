package org.apache.lucene.index;

/**
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

import org.apache.lucene.util.ArrayUtil;

/** Taps into DocInverter, as an InvertedDocEndConsumer,
 *  which is called at the end of inverting each field.  We
 *  just look at the length for the field (docState.length)
 *  and record the norm. */

final class NormsWriterPerField extends InvertedDocEndConsumerPerField implements Comparable<NormsWriterPerField> {

  final FieldInfo fieldInfo;
  final DocumentsWriter.DocState docState;

  // Holds all docID/norm pairs we've seen
  int[] docIDs = new int[1];
  byte[] norms = new byte[1];
  int upto;


  public void reset() {
    // Shrink back if we are overallocated now:
    docIDs = ArrayUtil.shrink(docIDs, upto);
    norms = ArrayUtil.shrink(norms, upto);
    upto = 0;
  }

  public NormsWriterPerField(final NormsWriterPerThread perThread, final FieldInfo fieldInfo) {
    this.fieldInfo = fieldInfo;
    docState = perThread.docState;
  }

  @Override
  void abort() {
    upto = 0;
  }

  public int compareTo(NormsWriterPerField other) {
    return fieldInfo.name.compareTo(other.fieldInfo.name);
  }

}
