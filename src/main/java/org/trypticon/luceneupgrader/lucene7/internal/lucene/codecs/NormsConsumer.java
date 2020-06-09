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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocIDMerger;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState;

public abstract class NormsConsumer implements Closeable {
  
  protected NormsConsumer() {}
  
  public abstract void addNormsField(FieldInfo field, NormsProducer normsProducer) throws IOException;

  public void merge(MergeState mergeState) throws IOException {
    for(NormsProducer normsProducer : mergeState.normsProducers) {
      if (normsProducer != null) {
        normsProducer.checkIntegrity();
      }
    }
    for (FieldInfo mergeFieldInfo : mergeState.mergeFieldInfos) {
      if (mergeFieldInfo.hasNorms()) {
        mergeNormsField(mergeFieldInfo, mergeState);
      }
    }
  }
  
  private static class NumericDocValuesSub extends DocIDMerger.Sub {

    private final NumericDocValues values;
    
    public NumericDocValuesSub(MergeState.DocMap docMap, NumericDocValues values) {
      super(docMap);
      this.values = values;
      assert values.docID() == -1;
    }

    @Override
    public int nextDoc() throws IOException {
      return values.nextDoc();
    }
  }

  public void mergeNormsField(final FieldInfo mergeFieldInfo, final MergeState mergeState) throws IOException {

    // TODO: try to share code with default merge of DVConsumer by passing MatchAllBits ?
    addNormsField(mergeFieldInfo,
                  new NormsProducer() {
                    @Override
                    public NumericDocValues getNorms(FieldInfo fieldInfo) throws IOException {
                      if (fieldInfo != mergeFieldInfo) {
                        throw new IllegalArgumentException("wrong fieldInfo");
                      }

                        List<NumericDocValuesSub> subs = new ArrayList<>();
                        assert mergeState.docMaps.length == mergeState.docValuesProducers.length;
                        for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                          NumericDocValues norms = null;
                          NormsProducer normsProducer = mergeState.normsProducers[i];
                          if (normsProducer != null) {
                            FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
                            if (readerFieldInfo != null && readerFieldInfo.hasNorms()) {
                              norms = normsProducer.getNorms(readerFieldInfo);
                            }
                          }

                          if (norms != null) {
                            subs.add(new NumericDocValuesSub(mergeState.docMaps[i], norms));
                          }
                        }

                        final DocIDMerger<NumericDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);

                        return new NumericDocValues() {
                          private int docID = -1;
                          private NumericDocValuesSub current;

                          @Override
                          public int docID() {
                            return docID;
                          }

                          @Override
                          public int nextDoc() throws IOException {
                            current = docIDMerger.next();
                            if (current == null) {
                              docID = NO_MORE_DOCS;
                            } else {
                              docID = current.mappedDocID;
                            }
                            return docID;
                          }

                          @Override
                          public int advance(int target) throws IOException {
                            throw new UnsupportedOperationException();
                          }

                          @Override
                          public boolean advanceExact(int target) throws IOException {
                            throw new UnsupportedOperationException();
                          }

                          @Override
                          public long cost() {
                            return 0;
                          }

                          @Override
                          public long longValue() throws IOException {
                            return current.values.longValue();
                          }
                        };
                    }
                    
                    @Override
                    public void checkIntegrity() {
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public long ramBytesUsed() {
                      return 0;
                    }
                  });
  }
}
