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
import java.util.Comparator;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocsAndPositionsEnum;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.FixedBitSet;

public abstract class TermsConsumer {

  protected TermsConsumer() {
  }


  public abstract PostingsConsumer startTerm(BytesRef text) throws IOException;


  public abstract void finishTerm(BytesRef text, TermStats stats) throws IOException;


  public abstract void finish(long sumTotalTermFreq, long sumDocFreq, int docCount) throws IOException;

  public abstract Comparator<BytesRef> getComparator() throws IOException;

  private MappingMultiDocsEnum docsEnum;
  private MappingMultiDocsEnum docsAndFreqsEnum;
  private MappingMultiDocsAndPositionsEnum postingsEnum;

  public void merge(MergeState mergeState, IndexOptions indexOptions, TermsEnum termsEnum) throws IOException {

    BytesRef term;
    assert termsEnum != null;
    long sumTotalTermFreq = 0;
    long sumDocFreq = 0;
    long sumDFsinceLastAbortCheck = 0;
    FixedBitSet visitedDocs = new FixedBitSet(mergeState.segmentInfo.getDocCount());

    if (indexOptions == IndexOptions.DOCS_ONLY) {
      if (docsEnum == null) {
        docsEnum = new MappingMultiDocsEnum();
      }
      docsEnum.setMergeState(mergeState);

      MultiDocsEnum docsEnumIn = null;

      while((term = termsEnum.next()) != null) {
        // We can pass null for liveDocs, because the
        // mapping enum will skip the non-live docs:
        docsEnumIn = (MultiDocsEnum) termsEnum.docs(null, docsEnumIn, DocsEnum.FLAG_NONE);
        if (docsEnumIn != null) {
          docsEnum.reset(docsEnumIn);
          final PostingsConsumer postingsConsumer = startTerm(term);
          final TermStats stats = postingsConsumer.merge(mergeState, indexOptions, docsEnum, visitedDocs);
          if (stats.docFreq > 0) {
            finishTerm(term, stats);
            sumTotalTermFreq += stats.docFreq;
            sumDFsinceLastAbortCheck += stats.docFreq;
            sumDocFreq += stats.docFreq;
            if (sumDFsinceLastAbortCheck > 60000) {
              mergeState.checkAbort.work(sumDFsinceLastAbortCheck/5.0);
              sumDFsinceLastAbortCheck = 0;
            }
          }
        }
      }
    } else if (indexOptions == IndexOptions.DOCS_AND_FREQS) {
      if (docsAndFreqsEnum == null) {
        docsAndFreqsEnum = new MappingMultiDocsEnum();
      }
      docsAndFreqsEnum.setMergeState(mergeState);

      MultiDocsEnum docsAndFreqsEnumIn = null;

      while((term = termsEnum.next()) != null) {
        // We can pass null for liveDocs, because the
        // mapping enum will skip the non-live docs:
        docsAndFreqsEnumIn = (MultiDocsEnum) termsEnum.docs(null, docsAndFreqsEnumIn);
        assert docsAndFreqsEnumIn != null;
        docsAndFreqsEnum.reset(docsAndFreqsEnumIn);
        final PostingsConsumer postingsConsumer = startTerm(term);
        final TermStats stats = postingsConsumer.merge(mergeState, indexOptions, docsAndFreqsEnum, visitedDocs);
        if (stats.docFreq > 0) {
          finishTerm(term, stats);
          sumTotalTermFreq += stats.totalTermFreq;
          sumDFsinceLastAbortCheck += stats.docFreq;
          sumDocFreq += stats.docFreq;
          if (sumDFsinceLastAbortCheck > 60000) {
            mergeState.checkAbort.work(sumDFsinceLastAbortCheck/5.0);
            sumDFsinceLastAbortCheck = 0;
          }
        }
      }
    } else if (indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
      if (postingsEnum == null) {
        postingsEnum = new MappingMultiDocsAndPositionsEnum();
      }
      postingsEnum.setMergeState(mergeState);
      MultiDocsAndPositionsEnum postingsEnumIn = null;
      while((term = termsEnum.next()) != null) {
        // We can pass null for liveDocs, because the
        // mapping enum will skip the non-live docs:
        postingsEnumIn = (MultiDocsAndPositionsEnum) termsEnum.docsAndPositions(null, postingsEnumIn, DocsAndPositionsEnum.FLAG_PAYLOADS);
        assert postingsEnumIn != null;
        postingsEnum.reset(postingsEnumIn);

        final PostingsConsumer postingsConsumer = startTerm(term);
        final TermStats stats = postingsConsumer.merge(mergeState, indexOptions, postingsEnum, visitedDocs);
        if (stats.docFreq > 0) {
          finishTerm(term, stats);
          sumTotalTermFreq += stats.totalTermFreq;
          sumDFsinceLastAbortCheck += stats.docFreq;
          sumDocFreq += stats.docFreq;
          if (sumDFsinceLastAbortCheck > 60000) {
            mergeState.checkAbort.work(sumDFsinceLastAbortCheck/5.0);
            sumDFsinceLastAbortCheck = 0;
          }
        }
      }
    } else {
      assert indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
      if (postingsEnum == null) {
        postingsEnum = new MappingMultiDocsAndPositionsEnum();
      }
      postingsEnum.setMergeState(mergeState);
      MultiDocsAndPositionsEnum postingsEnumIn = null;
      while((term = termsEnum.next()) != null) {
        // We can pass null for liveDocs, because the
        // mapping enum will skip the non-live docs:
        postingsEnumIn = (MultiDocsAndPositionsEnum) termsEnum.docsAndPositions(null, postingsEnumIn);
        assert postingsEnumIn != null;
        postingsEnum.reset(postingsEnumIn);

        final PostingsConsumer postingsConsumer = startTerm(term);
        final TermStats stats = postingsConsumer.merge(mergeState, indexOptions, postingsEnum, visitedDocs);
        if (stats.docFreq > 0) {
          finishTerm(term, stats);
          sumTotalTermFreq += stats.totalTermFreq;
          sumDFsinceLastAbortCheck += stats.docFreq;
          sumDocFreq += stats.docFreq;
          if (sumDFsinceLastAbortCheck > 60000) {
            mergeState.checkAbort.work(sumDFsinceLastAbortCheck/5.0);
            sumDFsinceLastAbortCheck = 0;
          }
        }
      }
    }
    finish(indexOptions == IndexOptions.DOCS_ONLY ? -1 : sumTotalTermFreq, sumDocFreq, visitedDocs.cardinality());
  }
}
