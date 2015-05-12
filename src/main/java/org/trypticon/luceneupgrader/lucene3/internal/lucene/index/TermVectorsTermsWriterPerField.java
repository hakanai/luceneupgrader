package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.RamUsageEstimator;

final class TermVectorsTermsWriterPerField extends TermsHashConsumerPerField {

  final TermVectorsTermsWriterPerThread perThread;
  final TermsHashPerField termsHashPerField;
  final FieldInfo fieldInfo;

  int maxNumPostings;

  public TermVectorsTermsWriterPerField(TermsHashPerField termsHashPerField, TermVectorsTermsWriterPerThread perThread, FieldInfo fieldInfo) {
    this.termsHashPerField = termsHashPerField;
    this.perThread = perThread;
    this.fieldInfo = fieldInfo;
  }

  @Override
  int getStreamCount() {
    return 2;
  }

  void shrinkHash() {
    termsHashPerField.shrinkHash();
    maxNumPostings = 0;
  }
  

  @Override
  ParallelPostingsArray createPostingsArray(int size) {
    return new TermVectorsPostingsArray(size);
  }

  static final class TermVectorsPostingsArray extends ParallelPostingsArray {
    public TermVectorsPostingsArray(int size) {
      super(size);
      freqs = new int[size];
    }

    int[] freqs;                                       // How many times this term occurred in the current doc

    @Override
    int bytesPerPosting() {
      return super.bytesPerPosting() + 3 * RamUsageEstimator.NUM_BYTES_INT;
    }
  }
}
