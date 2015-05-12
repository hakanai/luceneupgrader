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

import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.util.RamUsageEstimator;

// TODO: break into separate freq and prox writers as
// codecs; make separate container (tii/tis/skip/*) that can
// be configured as any number of files 1..N
final class FreqProxTermsWriterPerField extends TermsHashConsumerPerField implements Comparable<FreqProxTermsWriterPerField> {

  final FreqProxTermsWriterPerThread perThread;
  final TermsHashPerField termsHashPerField;
  final FieldInfo fieldInfo;
  final DocumentsWriter.DocState docState;
  final FieldInvertState fieldState;
  IndexOptions indexOptions;

  public FreqProxTermsWriterPerField(TermsHashPerField termsHashPerField, FreqProxTermsWriterPerThread perThread, FieldInfo fieldInfo) {
    this.termsHashPerField = termsHashPerField;
    this.perThread = perThread;
    this.fieldInfo = fieldInfo;
    docState = termsHashPerField.docState;
    fieldState = termsHashPerField.fieldState;
    indexOptions = fieldInfo.indexOptions;
  }

  @Override
  int getStreamCount() {
    if (indexOptions != IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
      return 1;
    else
      return 2;
  }

  @Override
  void finish() {}

  boolean hasPayloads;

  public int compareTo(FreqProxTermsWriterPerField other) {
    return fieldInfo.name.compareTo(other.fieldInfo.name);
  }

  void reset() {
    // Record, up front, whether our in-RAM format will be
    // with or without term freqs:
    indexOptions = fieldInfo.indexOptions;
  }

  @Override
  void start(Fieldable f) {
  }

  @Override
  ParallelPostingsArray createPostingsArray(int size) {
    return new FreqProxPostingsArray(size);
  }

  static final class FreqProxPostingsArray extends ParallelPostingsArray {
    public FreqProxPostingsArray(int size) {
      super(size);
      docFreqs = new int[size];
      lastDocIDs = new int[size];
      lastDocCodes = new int[size];
      lastPositions = new int[size];
    }

    int docFreqs[];                                    // # times this term occurs in the current doc
    int lastDocIDs[];                                  // Last docID where this term occurred
    int lastDocCodes[];                                // Code for prior doc
    int lastPositions[];                               // Last position where this term occurred

    @Override
    void copyTo(ParallelPostingsArray toArray, int numToCopy) {
      assert toArray instanceof FreqProxPostingsArray;
      FreqProxPostingsArray to = (FreqProxPostingsArray) toArray;

      super.copyTo(toArray, numToCopy);

      System.arraycopy(docFreqs, 0, to.docFreqs, 0, numToCopy);
      System.arraycopy(lastDocIDs, 0, to.lastDocIDs, 0, numToCopy);
      System.arraycopy(lastDocCodes, 0, to.lastDocCodes, 0, numToCopy);
      System.arraycopy(lastPositions, 0, to.lastPositions, 0, numToCopy);
    }

    @Override
    int bytesPerPosting() {
      return ParallelPostingsArray.BYTES_PER_POSTING + 4 * RamUsageEstimator.NUM_BYTES_INT;
    }
  }

}

