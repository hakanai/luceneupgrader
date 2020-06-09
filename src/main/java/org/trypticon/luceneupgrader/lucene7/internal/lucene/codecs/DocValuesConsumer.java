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
import java.util.Iterator;
import java.util.List;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.BinaryDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocIDMerger;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.EmptyDocValuesProducer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.FilteredTermsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.OrdinalMap;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SegmentWriteState; // javadocs
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedSetDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.LongBitSet;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.LongValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed.PackedInts;

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public abstract class DocValuesConsumer implements Closeable {
  
  protected DocValuesConsumer() {}

  public abstract void addNumericField(FieldInfo field, DocValuesProducer valuesProducer) throws IOException;    

  public abstract void addBinaryField(FieldInfo field, DocValuesProducer valuesProducer) throws IOException;

  public abstract void addSortedField(FieldInfo field, DocValuesProducer valuesProducer) throws IOException;
  
  public abstract void addSortedNumericField(FieldInfo field, DocValuesProducer valuesProducer) throws IOException;

  public abstract void addSortedSetField(FieldInfo field, DocValuesProducer valuesProducer) throws IOException;
  
  public void merge(MergeState mergeState) throws IOException {
    for(DocValuesProducer docValuesProducer : mergeState.docValuesProducers) {
      if (docValuesProducer != null) {
        docValuesProducer.checkIntegrity();
      }
    }

    for (FieldInfo mergeFieldInfo : mergeState.mergeFieldInfos) {
      DocValuesType type = mergeFieldInfo.getDocValuesType();
      if (type != DocValuesType.NONE) {
        if (type == DocValuesType.NUMERIC) {
          mergeNumericField(mergeFieldInfo, mergeState);
        } else if (type == DocValuesType.BINARY) {
          mergeBinaryField(mergeFieldInfo, mergeState);
        } else if (type == DocValuesType.SORTED) {
          mergeSortedField(mergeFieldInfo, mergeState);
        } else if (type == DocValuesType.SORTED_SET) {
          mergeSortedSetField(mergeFieldInfo, mergeState);
        } else if (type == DocValuesType.SORTED_NUMERIC) {
          mergeSortedNumericField(mergeFieldInfo, mergeState);
        } else {
          throw new AssertionError("type=" + type);
        }
      }
    }
  }

  private static class NumericDocValuesSub extends DocIDMerger.Sub {

    final NumericDocValues values;

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
  
  public void mergeNumericField(final FieldInfo mergeFieldInfo, final MergeState mergeState) throws IOException {
    addNumericField(mergeFieldInfo,
                    new EmptyDocValuesProducer() {
                      @Override
                      public NumericDocValues getNumeric(FieldInfo fieldInfo) throws IOException {
                        if (fieldInfo != mergeFieldInfo) {
                          throw new IllegalArgumentException("wrong fieldInfo");
                        }

                        List<NumericDocValuesSub> subs = new ArrayList<>();
                        assert mergeState.docMaps.length == mergeState.docValuesProducers.length;
                        long cost = 0;
                        for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                          NumericDocValues values = null;
                          DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
                          if (docValuesProducer != null) {
                            FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
                            if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.NUMERIC) {
                              values = docValuesProducer.getNumeric(readerFieldInfo);
                            }
                          }
                          if (values != null) {
                            cost += values.cost();
                            subs.add(new NumericDocValuesSub(mergeState.docMaps[i], values));
                          }
                        }

                        final DocIDMerger<NumericDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);

                        final long finalCost = cost;
                        
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
                            return finalCost;
                          }

                          @Override
                          public long longValue() throws IOException {
                            return current.values.longValue();
                          }
                        };
                      }
                    });
  }
  
  private static class BinaryDocValuesSub extends DocIDMerger.Sub {

    final BinaryDocValues values;

    public BinaryDocValuesSub(MergeState.DocMap docMap, BinaryDocValues values) {
      super(docMap);
      this.values = values;
      assert values.docID() == -1;
    }

    @Override
    public int nextDoc() throws IOException {
      return values.nextDoc();
    }
  }

  public void mergeBinaryField(FieldInfo mergeFieldInfo, final MergeState mergeState) throws IOException {
    addBinaryField(mergeFieldInfo,
                   new EmptyDocValuesProducer() {
                     @Override
                     public BinaryDocValues getBinary(FieldInfo fieldInfo) throws IOException {
                       if (fieldInfo != mergeFieldInfo) {
                         throw new IllegalArgumentException("wrong fieldInfo");
                       }
                   
                       List<BinaryDocValuesSub> subs = new ArrayList<>();

                       long cost = 0;
                       for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                         BinaryDocValues values = null;
                         DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
                         if (docValuesProducer != null) {
                           FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
                           if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.BINARY) {
                             values = docValuesProducer.getBinary(readerFieldInfo);
                           }
                         }
                         if (values != null) {
                           cost += values.cost();
                           subs.add(new BinaryDocValuesSub(mergeState.docMaps[i], values));
                         }
                       }

                       final DocIDMerger<BinaryDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);
                       final long finalCost = cost;
                       
                       return new BinaryDocValues() {
                         private BinaryDocValuesSub current;
                         private int docID = -1;

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
                           return finalCost;
                         }

                         @Override
                         public BytesRef binaryValue() throws IOException {
                           return current.values.binaryValue();
                         }
                       };
                     }
                   });
  }

  private static class SortedNumericDocValuesSub extends DocIDMerger.Sub {

    final SortedNumericDocValues values;

    public SortedNumericDocValuesSub(MergeState.DocMap docMap, SortedNumericDocValues values) {
      super(docMap);
      this.values = values;
      assert values.docID() == -1;
    }

    @Override
    public int nextDoc() throws IOException {
      return values.nextDoc();
    }
  }

  public void mergeSortedNumericField(FieldInfo mergeFieldInfo, final MergeState mergeState) throws IOException {
    
    addSortedNumericField(mergeFieldInfo,
                          new EmptyDocValuesProducer() {
                            @Override
                            public SortedNumericDocValues getSortedNumeric(FieldInfo fieldInfo) throws IOException {
                              if (fieldInfo != mergeFieldInfo) {
                                throw new IllegalArgumentException("wrong FieldInfo");
                              }
                              
                              // We must make new iterators + DocIDMerger for each iterator:
                              List<SortedNumericDocValuesSub> subs = new ArrayList<>();
                              long cost = 0;
                              for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                                DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
                                SortedNumericDocValues values = null;
                                if (docValuesProducer != null) {
                                  FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
                                  if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.SORTED_NUMERIC) {
                                    values = docValuesProducer.getSortedNumeric(readerFieldInfo);
                                  }
                                }
                                if (values == null) {
                                  values = DocValues.emptySortedNumeric(mergeState.maxDocs[i]);
                                }
                                cost += values.cost();
                                subs.add(new SortedNumericDocValuesSub(mergeState.docMaps[i], values));
                              }

                              final long finalCost = cost;

                              final DocIDMerger<SortedNumericDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);

                              return new SortedNumericDocValues() {

                                private int docID = -1;
                                private SortedNumericDocValuesSub currentSub;

                                @Override
                                public int docID() {
                                  return docID;
                                }
                                
                                @Override
                                public int nextDoc() throws IOException {
                                  currentSub = docIDMerger.next();
                                  if (currentSub == null) {
                                    docID = NO_MORE_DOCS;
                                  } else {
                                    docID = currentSub.mappedDocID;
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
                                public int docValueCount() {
                                  return currentSub.values.docValueCount();
                                }

                                @Override
                                public long cost() {
                                  return finalCost;
                                }

                                @Override
                                public long nextValue() throws IOException {
                                  return currentSub.values.nextValue();
                                }
                              };
                            }
                          });
  }

  private static class SortedDocValuesSub extends DocIDMerger.Sub {

    final SortedDocValues values;
    final LongValues map;
    
    public SortedDocValuesSub(MergeState.DocMap docMap, SortedDocValues values, LongValues map) {
      super(docMap);
      this.values = values;
      this.map = map;
      assert values.docID() == -1;
    }

    @Override
    public int nextDoc() throws IOException {
      return values.nextDoc();
    }
  }

  public void mergeSortedField(FieldInfo fieldInfo, final MergeState mergeState) throws IOException {
    List<SortedDocValues> toMerge = new ArrayList<>();
    for (int i=0;i<mergeState.docValuesProducers.length;i++) {
      SortedDocValues values = null;
      DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
      if (docValuesProducer != null) {
        FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(fieldInfo.name);
        if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.SORTED) {
          values = docValuesProducer.getSorted(fieldInfo);
        }
      }
      if (values == null) {
        values = DocValues.emptySorted();
      }
      toMerge.add(values);
    }

    final int numReaders = toMerge.size();
    final SortedDocValues dvs[] = toMerge.toArray(new SortedDocValues[numReaders]);
    
    // step 1: iterate thru each sub and mark terms still in use
    TermsEnum liveTerms[] = new TermsEnum[dvs.length];
    long[] weights = new long[liveTerms.length];
    for (int sub=0;sub<numReaders;sub++) {
      SortedDocValues dv = dvs[sub];
      Bits liveDocs = mergeState.liveDocs[sub];
      if (liveDocs == null) {
        liveTerms[sub] = dv.termsEnum();
        weights[sub] = dv.getValueCount();
      } else {
        LongBitSet bitset = new LongBitSet(dv.getValueCount());
        int docID;
        while ((docID = dv.nextDoc()) != NO_MORE_DOCS) {
          if (liveDocs.get(docID)) {
            int ord = dv.ordValue();
            if (ord >= 0) {
              bitset.set(ord);
            }
          }
        }
        liveTerms[sub] = new BitsFilteredTermsEnum(dv.termsEnum(), bitset);
        weights[sub] = bitset.cardinality();
      }
    }
    
    // step 2: create ordinal map (this conceptually does the "merging")
    final OrdinalMap map = OrdinalMap.build(null, liveTerms, weights, PackedInts.COMPACT);
    
    // step 3: add field
    addSortedField(fieldInfo,
                   new EmptyDocValuesProducer() {
                     @Override
                     public SortedDocValues getSorted(FieldInfo fieldInfoIn) throws IOException {
                       if (fieldInfoIn != fieldInfo) {
                         throw new IllegalArgumentException("wrong FieldInfo");
                       }

                       // We must make new iterators + DocIDMerger for each iterator:

                       List<SortedDocValuesSub> subs = new ArrayList<>();
                       long cost = 0;
                       for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                         SortedDocValues values = null;
                         DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
                         if (docValuesProducer != null) {
                           FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(fieldInfo.name);
                           if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.SORTED) {
                             values = docValuesProducer.getSorted(readerFieldInfo);
                           }
                         }
                         if (values == null) {
                           values = DocValues.emptySorted();
                         }
                         cost += values.cost();
                         
                         subs.add(new SortedDocValuesSub(mergeState.docMaps[i], values, map.getGlobalOrds(i)));
                       }

                       final long finalCost = cost;

                       final DocIDMerger<SortedDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);
                       
                       return new SortedDocValues() {
                         private int docID = -1;
                         private int ord;

                         @Override
                         public int docID() {
                           return docID;
                         }

                         @Override
                         public int nextDoc() throws IOException {
                           SortedDocValuesSub sub = docIDMerger.next();
                           if (sub == null) {
                             return docID = NO_MORE_DOCS;
                           }
                           int subOrd = sub.values.ordValue();
                           assert subOrd != -1;
                           ord = (int) sub.map.get(subOrd);
                           docID = sub.mappedDocID;
                           return docID;
                         }

                         @Override
                         public int ordValue() {
                           return ord;
                         }
                         
                         @Override
                         public int advance(int target) {
                           throw new UnsupportedOperationException();
                         }

                         @Override
                         public boolean advanceExact(int target) throws IOException {
                           throw new UnsupportedOperationException();
                         }

                         @Override
                         public long cost() {
                           return finalCost;
                         }

                         @Override
                         public int getValueCount() {
                           return (int) map.getValueCount();
                         }
                         
                         @Override
                         public BytesRef lookupOrd(int ord) throws IOException {
                           int segmentNumber = map.getFirstSegmentNumber(ord);
                           int segmentOrd = (int) map.getFirstSegmentOrd(ord);
                           return dvs[segmentNumber].lookupOrd(segmentOrd);
                         }
                       };
                     }
                   });
  }
  
  private static class SortedSetDocValuesSub extends DocIDMerger.Sub {

    final SortedSetDocValues values;
    final LongValues map;
    
    public SortedSetDocValuesSub(MergeState.DocMap docMap, SortedSetDocValues values, LongValues map) {
      super(docMap);
      this.values = values;
      this.map = map;
      assert values.docID() == -1;
    }

    @Override
    public int nextDoc() throws IOException {
      return values.nextDoc();
    }

    @Override
    public String toString() {
      return "SortedSetDocValuesSub(mappedDocID=" + mappedDocID + " values=" + values + ")";
    }
  }

  public void mergeSortedSetField(FieldInfo mergeFieldInfo, final MergeState mergeState) throws IOException {

    List<SortedSetDocValues> toMerge = new ArrayList<>();
    for (int i=0;i<mergeState.docValuesProducers.length;i++) {
      SortedSetDocValues values = null;
      DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
      if (docValuesProducer != null) {
        FieldInfo fieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
        if (fieldInfo != null && fieldInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
          values = docValuesProducer.getSortedSet(fieldInfo);
        }
      }
      if (values == null) {
        values = DocValues.emptySortedSet();
      }
      toMerge.add(values);
    }

    // step 1: iterate thru each sub and mark terms still in use
    TermsEnum liveTerms[] = new TermsEnum[toMerge.size()];
    long[] weights = new long[liveTerms.length];
    for (int sub = 0; sub < liveTerms.length; sub++) {
      SortedSetDocValues dv = toMerge.get(sub);
      Bits liveDocs = mergeState.liveDocs[sub];
      if (liveDocs == null) {
        liveTerms[sub] = dv.termsEnum();
        weights[sub] = dv.getValueCount();
      } else {
        LongBitSet bitset = new LongBitSet(dv.getValueCount());
        int docID;
        while ((docID = dv.nextDoc()) != NO_MORE_DOCS) {
          if (liveDocs.get(docID)) {
            long ord;
            while ((ord = dv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
              bitset.set(ord);
            }
          }
        }
        liveTerms[sub] = new BitsFilteredTermsEnum(dv.termsEnum(), bitset);
        weights[sub] = bitset.cardinality();
      }
    }
    
    // step 2: create ordinal map (this conceptually does the "merging")
    final OrdinalMap map = OrdinalMap.build(null, liveTerms, weights, PackedInts.COMPACT);
    
    // step 3: add field
    addSortedSetField(mergeFieldInfo,
                      new EmptyDocValuesProducer() {
                        @Override
                        public SortedSetDocValues getSortedSet(FieldInfo fieldInfo) throws IOException {
                          if (fieldInfo != mergeFieldInfo) {
                            throw new IllegalArgumentException("wrong FieldInfo");
                          }

                          // We must make new iterators + DocIDMerger for each iterator:
                          List<SortedSetDocValuesSub> subs = new ArrayList<>();

                          long cost = 0;
                          
                          for (int i=0;i<mergeState.docValuesProducers.length;i++) {
                            SortedSetDocValues values = null;
                            DocValuesProducer docValuesProducer = mergeState.docValuesProducers[i];
                            if (docValuesProducer != null) {
                              FieldInfo readerFieldInfo = mergeState.fieldInfos[i].fieldInfo(mergeFieldInfo.name);
                              if (readerFieldInfo != null && readerFieldInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
                                values = docValuesProducer.getSortedSet(readerFieldInfo);
                              }
                            }
                            if (values == null) {
                              values = DocValues.emptySortedSet();
                            }
                            cost += values.cost();
                            subs.add(new SortedSetDocValuesSub(mergeState.docMaps[i], values, map.getGlobalOrds(i)));
                          }
            
                          final DocIDMerger<SortedSetDocValuesSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);
                          
                          final long finalCost = cost;

                          return new SortedSetDocValues() {
                            private int docID = -1;
                            private SortedSetDocValuesSub currentSub;

                            @Override
                            public int docID() {
                              return docID;
                            }

                            @Override
                            public int nextDoc() throws IOException {
                              currentSub = docIDMerger.next();
                              if (currentSub == null) {
                                docID = NO_MORE_DOCS;
                              } else {
                                docID = currentSub.mappedDocID;
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
                            public long nextOrd() throws IOException {
                              long subOrd = currentSub.values.nextOrd();
                              if (subOrd == NO_MORE_ORDS) {
                                return NO_MORE_ORDS;
                              }
                              return currentSub.map.get(subOrd);
                            }

                            @Override
                            public long cost() {
                              return finalCost;
                            }

                            @Override
                            public BytesRef lookupOrd(long ord) throws IOException {
                              int segmentNumber = map.getFirstSegmentNumber(ord);
                              long segmentOrd = map.getFirstSegmentOrd(ord);
                              return toMerge.get(segmentNumber).lookupOrd(segmentOrd);
                            }

                            @Override
                            public long getValueCount() {
                              return map.getValueCount();
                            }
                          };
                        }
                      });
  }
  
  // TODO: seek-by-ord to nextSetBit
  static class BitsFilteredTermsEnum extends FilteredTermsEnum {
    final LongBitSet liveTerms;
    
    BitsFilteredTermsEnum(TermsEnum in, LongBitSet liveTerms) {
      super(in, false); // <-- not passing false here wasted about 3 hours of my time!!!!!!!!!!!!!
      assert liveTerms != null;
      this.liveTerms = liveTerms;
    }

    @Override
    protected AcceptStatus accept(BytesRef term) throws IOException {
      if (liveTerms.get(ord())) {
        return AcceptStatus.YES;
      } else {
        return AcceptStatus.NO;
      }
    }
  }
  
  public static boolean isSingleValued(Iterable<Number> docToValueCount) {
    for (Number count : docToValueCount) {
      if (count.longValue() > 1) {
        return false;
      }
    }
    return true;
  }
  
  public static Iterable<Number> singletonView(final Iterable<Number> docToValueCount, final Iterable<Number> values, final Number missingValue) {
    assert isSingleValued(docToValueCount);
    return new Iterable<Number>() {

      @Override
      public Iterator<Number> iterator() {
        final Iterator<Number> countIterator = docToValueCount.iterator();
        final Iterator<Number> valuesIterator = values.iterator();
        return new Iterator<Number>() {

          @Override
          public boolean hasNext() {
            return countIterator.hasNext();
          }

          @Override
          public Number next() {
            int count = countIterator.next().intValue();
            if (count == 0) {
              return missingValue;
            } else {
              return valuesIterator.next();
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }
}
