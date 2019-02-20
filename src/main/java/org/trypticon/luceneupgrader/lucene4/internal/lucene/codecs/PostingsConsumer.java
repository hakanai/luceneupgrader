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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet;

public abstract class PostingsConsumer {

  protected PostingsConsumer() {
  }


  public abstract void startDoc(int docID, int freq) throws IOException;


  public abstract void addPosition(int position, BytesRef payload, int startOffset, int endOffset) throws IOException;

  public abstract void finishDoc() throws IOException;

  public TermStats merge(final MergeState mergeState, IndexOptions indexOptions, final DocsEnum postings, final FixedBitSet visitedDocs) throws IOException {

    int df = 0;
    long totTF = 0;

    if (indexOptions == IndexOptions.DOCS_ONLY) {
      while(true) {
        final int doc = postings.nextDoc();
        if (doc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        }
        visitedDocs.set(doc);
        this.startDoc(doc, -1);
        this.finishDoc();
        df++;
      }
      totTF = -1;
    } else if (indexOptions == IndexOptions.DOCS_AND_FREQS) {
      while(true) {
        final int doc = postings.nextDoc();
        if (doc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        }
        visitedDocs.set(doc);
        final int freq = postings.freq();
        this.startDoc(doc, freq);
        this.finishDoc();
        df++;
        totTF += freq;
      }
    } else if (indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
      final DocsAndPositionsEnum postingsEnum = (DocsAndPositionsEnum) postings;
      while(true) {
        final int doc = postingsEnum.nextDoc();
        if (doc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        }
        visitedDocs.set(doc);
        final int freq = postingsEnum.freq();
        this.startDoc(doc, freq);
        totTF += freq;
        for(int i=0;i<freq;i++) {
          final int position = postingsEnum.nextPosition();
          final BytesRef payload = postingsEnum.getPayload();
          this.addPosition(position, payload, -1, -1);
        }
        this.finishDoc();
        df++;
      }
    } else {
      assert indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
      final DocsAndPositionsEnum postingsEnum = (DocsAndPositionsEnum) postings;
      while(true) {
        final int doc = postingsEnum.nextDoc();
        if (doc == DocIdSetIterator.NO_MORE_DOCS) {
          break;
        }
        visitedDocs.set(doc);
        final int freq = postingsEnum.freq();
        this.startDoc(doc, freq);
        totTF += freq;
        for(int i=0;i<freq;i++) {
          final int position = postingsEnum.nextPosition();
          final BytesRef payload = postingsEnum.getPayload();
          this.addPosition(position, payload, postingsEnum.startOffset(), postingsEnum.endOffset());
        }
        this.finishDoc();
        df++;
      }
    }
    return new TermStats(df, indexOptions == IndexOptions.DOCS_ONLY ? -1 : totTF);
  }
}
