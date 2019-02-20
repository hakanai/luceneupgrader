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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocIDMerger;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SegmentWriteState;
import static org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public abstract class NormsConsumer implements Closeable {
  
  protected NormsConsumer() {}
  
  public abstract void addNormsField(FieldInfo field, Iterable<Number> values) throws IOException;


  public void merge(MergeState mergeState) throws IOException {
    for(NormsProducer normsProducer : mergeState.normsProducers) {
      if (normsProducer != null) {
        normsProducer.checkIntegrity();
      }
    }
    for (FieldInfo mergeFieldInfo : mergeState.mergeFieldInfos) {
      if (mergeFieldInfo.hasNorms()) {
        List<NumericDocValues> toMerge = new ArrayList<>();
        for (int i=0;i<mergeState.normsProducers.length;i++) {
          NormsProducer normsProducer = mergeState.normsProducers[i];
          NumericDocValues norms = null;
          if (normsProducer != null) {
            FieldInfo fieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
            if (fieldInfo != null && fieldInfo.hasNorms()) {
              norms = normsProducer.getNorms(fieldInfo);
            }
          }
          if (norms == null) {
            norms = DocValues.emptyNumeric();
          }
          toMerge.add(norms);
        }
        mergeNormsField(mergeFieldInfo, mergeState, toMerge);
      }
    }
  }
  
  private static class NumericDocValuesSub extends DocIDMerger.Sub {

    private final NumericDocValues values;
    private int docID = -1;
    private final int maxDoc;

    public NumericDocValuesSub(MergeState.DocMap docMap, NumericDocValues values, int maxDoc) {
      super(docMap);
      this.values = values;
      this.maxDoc = maxDoc;
    }

    @Override
    public int nextDoc() {
      docID++;
      if (docID == maxDoc) {
        return NO_MORE_DOCS;
      } else {
        return docID;
      }
    }
  }

  public void mergeNormsField(final FieldInfo fieldInfo, final MergeState mergeState, final List<NumericDocValues> toMerge) throws IOException {

    // TODO: try to share code with default merge of DVConsumer by passing MatchAllBits ?
    addNormsField(fieldInfo,
                    new Iterable<Number>() {
                      @Override
                      public Iterator<Number> iterator() {

                        // We must make a new DocIDMerger for each iterator:
                        List<NumericDocValuesSub> subs = new ArrayList<>();
                        assert mergeState.docMaps.length == toMerge.size();
                        for(int i=0;i<toMerge.size();i++) {
                          subs.add(new NumericDocValuesSub(mergeState.docMaps[i], toMerge.get(i), mergeState.maxDocs[i]));
                        }

                        final DocIDMerger<NumericDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);

                        return new Iterator<Number>() {
                          long nextValue;
                          boolean nextIsSet;

                          @Override
                          public boolean hasNext() {
                            return nextIsSet || setNext();
                          }

                          @Override
                          public void remove() {
                            throw new UnsupportedOperationException();
                          }

                          @Override
                          public Number next() {
                            if (!hasNext()) {
                              throw new NoSuchElementException();
                            }
                            assert nextIsSet;
                            nextIsSet = false;
                            return nextValue;
                          }

                          private boolean setNext() {
                            NumericDocValuesSub sub = docIDMerger.next();
                            if (sub == null) {
                              return false;
                            }
                            nextIsSet = true;
                            nextValue = sub.values.get(sub.docID);
                            return true;
                          }
                        };
                      }
                    });
  }
}
