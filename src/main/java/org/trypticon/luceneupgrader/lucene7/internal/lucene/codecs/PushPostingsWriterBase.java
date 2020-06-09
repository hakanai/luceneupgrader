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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.codecs;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.FixedBitSet;

// TODO: find a better name; this defines the API that the
// terms dict impls use to talk to a postings impl.
// TermsDict + PostingsReader/WriterBase == PostingsConsumer/Producer
public abstract class PushPostingsWriterBase extends PostingsWriterBase {

  // Reused in writeTerm
  private PostingsEnum postingsEnum;
  private int enumFlags;

  protected FieldInfo fieldInfo;

  protected IndexOptions indexOptions;

  protected boolean writeFreqs;

  protected boolean writePositions;

  protected boolean writePayloads;

  protected boolean writeOffsets;

  protected PushPostingsWriterBase() {
  }

  public abstract BlockTermState newTermState() throws IOException;

  public abstract void startTerm() throws IOException;

  public abstract void finishTerm(BlockTermState state) throws IOException;

  @Override
  public int setField(FieldInfo fieldInfo) {
    this.fieldInfo = fieldInfo;
    indexOptions = fieldInfo.getIndexOptions();

    writeFreqs = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
    writePositions = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
    writeOffsets = indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;        
    writePayloads = fieldInfo.hasPayloads();

    if (writeFreqs == false) {
      enumFlags = 0;
    } else if (writePositions == false) {
      enumFlags = PostingsEnum.FREQS;
    } else if (writeOffsets == false) {
      if (writePayloads) {
        enumFlags = PostingsEnum.PAYLOADS;
      } else {
        enumFlags = PostingsEnum.POSITIONS;
      }
    } else {
      if (writePayloads) {
        enumFlags = PostingsEnum.PAYLOADS | PostingsEnum.OFFSETS;
      } else {
        enumFlags = PostingsEnum.OFFSETS;
      }
    }

    return 0;
  }

  @Override
  public final BlockTermState writeTerm(BytesRef term, TermsEnum termsEnum, FixedBitSet docsSeen) throws IOException {
    startTerm();
    postingsEnum = termsEnum.postings(postingsEnum, enumFlags);
    assert postingsEnum != null;

    int docFreq = 0;
    long totalTermFreq = 0;
    while (true) {
      int docID = postingsEnum.nextDoc();
      if (docID == PostingsEnum.NO_MORE_DOCS) {
        break;
      }
      docFreq++;
      docsSeen.set(docID);
      int freq;
      if (writeFreqs) {
        freq = postingsEnum.freq();
        totalTermFreq += freq;
      } else {
        freq = -1;
      }
      startDoc(docID, freq);

      if (writePositions) {
        for(int i=0;i<freq;i++) {
          int pos = postingsEnum.nextPosition();
          BytesRef payload = writePayloads ? postingsEnum.getPayload() : null;
          int startOffset;
          int endOffset;
          if (writeOffsets) {
            startOffset = postingsEnum.startOffset();
            endOffset = postingsEnum.endOffset();
          } else {
            startOffset = -1;
            endOffset = -1;
          }
          addPosition(pos, payload, startOffset, endOffset);
        }
      }

      finishDoc();
    }

    if (docFreq == 0) {
      return null;
    } else {
      BlockTermState state = newTermState();
      state.docFreq = docFreq;
      state.totalTermFreq = writeFreqs ? totalTermFreq : -1;
      finishTerm(state);
      return state;
    }
  }

  public abstract void startDoc(int docID, int freq) throws IOException;

  public abstract void addPosition(int position, BytesRef payload, int startOffset, int endOffset) throws IOException;

  public abstract void finishDoc() throws IOException;
}
