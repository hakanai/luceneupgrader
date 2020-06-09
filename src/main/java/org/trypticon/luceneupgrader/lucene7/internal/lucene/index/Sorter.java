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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.FieldComparator;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Sort;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortedNumericSelector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortedNumericSortField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortedSetSelector;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.SortedSetSortField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.TimSorter;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed.PackedInts;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.packed.PackedLongValues;

import static org.trypticon.luceneupgrader.lucene7.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

final class Sorter {
  final Sort sort;
  
  Sorter(Sort sort) {
    if (sort.needsScores()) {
      throw new IllegalArgumentException("Cannot sort an index with a Sort that refers to the relevance score");
    }
    this.sort = sort;
  }

  static abstract class DocMap {

    abstract int oldToNew(int docID);

    abstract int newToOld(int docID);

    abstract int size();
  }

  static boolean isConsistent(DocMap docMap) {
    final int maxDoc = docMap.size();
    for (int i = 0; i < maxDoc; ++i) {
      final int newID = docMap.oldToNew(i);
      final int oldID = docMap.newToOld(newID);
      assert newID >= 0 && newID < maxDoc : "doc IDs must be in [0-" + maxDoc + "[, got " + newID;
      assert i == oldID : "mapping is inconsistent: " + i + " --oldToNew--> " + newID + " --newToOld--> " + oldID;
      if (i != oldID || newID < 0 || newID >= maxDoc) {
        return false;
      }
    }
    return true;
  }

  static abstract class DocComparator {

    public abstract int compare(int docID1, int docID2);
  }

  private static final class DocValueSorter extends TimSorter {
    
    private final int[] docs;
    private final Sorter.DocComparator comparator;
    private final int[] tmp;
    
    DocValueSorter(int[] docs, Sorter.DocComparator comparator) {
      super(docs.length / 64);
      this.docs = docs;
      this.comparator = comparator;
      tmp = new int[docs.length / 64];
    }
    
    @Override
    protected int compare(int i, int j) {
      return comparator.compare(docs[i], docs[j]);
    }
    
    @Override
    protected void swap(int i, int j) {
      int tmpDoc = docs[i];
      docs[i] = docs[j];
      docs[j] = tmpDoc;
    }

    @Override
    protected void copy(int src, int dest) {
      docs[dest] = docs[src];
    }

    @Override
    protected void save(int i, int len) {
      System.arraycopy(docs, i, tmp, 0, len);
    }

    @Override
    protected void restore(int i, int j) {
      docs[j] = tmp[i];
    }

    @Override
    protected int compareSaved(int i, int j) {
      return comparator.compare(tmp[i], docs[j]);
    }
  }

  private static Sorter.DocMap sort(final int maxDoc, DocComparator comparator) {
    // check if the index is sorted
    boolean sorted = true;
    for (int i = 1; i < maxDoc; ++i) {
      if (comparator.compare(i-1, i) > 0) {
        sorted = false;
        break;
      }
    }
    if (sorted) {
      return null;
    }

    // sort doc IDs
    final int[] docs = new int[maxDoc];
    for (int i = 0; i < maxDoc; i++) {
      docs[i] = i;
    }
    
    DocValueSorter sorter = new DocValueSorter(docs, comparator);
    // It can be common to sort a reader, add docs, sort it again, ... and in
    // that case timSort can save a lot of time
    sorter.sort(0, docs.length); // docs is now the newToOld mapping

    // The reason why we use MonotonicAppendingLongBuffer here is that it
    // wastes very little memory if the index is in random order but can save
    // a lot of memory if the index is already "almost" sorted
    final PackedLongValues.Builder newToOldBuilder = PackedLongValues.monotonicBuilder(PackedInts.COMPACT);
    for (int i = 0; i < maxDoc; ++i) {
      newToOldBuilder.add(docs[i]);
    }
    final PackedLongValues newToOld = newToOldBuilder.build();

    // invert the docs mapping:
    for (int i = 0; i < maxDoc; ++i) {
      docs[(int) newToOld.get(i)] = i;
    } // docs is now the oldToNew mapping

    final PackedLongValues.Builder oldToNewBuilder = PackedLongValues.monotonicBuilder(PackedInts.COMPACT);
    for (int i = 0; i < maxDoc; ++i) {
      oldToNewBuilder.add(docs[i]);
    }
    final PackedLongValues oldToNew = oldToNewBuilder.build();
    
    return new Sorter.DocMap() {

      @Override
      public int oldToNew(int docID) {
        return (int) oldToNew.get(docID);
      }

      @Override
      public int newToOld(int docID) {
        return (int) newToOld.get(docID);
      }

      @Override
      public int size() {
        return maxDoc;
      }
    };
  }

  static SortField.Type getSortFieldType(SortField sortField) {
    if (sortField instanceof SortedSetSortField) {
      return SortField.Type.STRING;
    } else if (sortField instanceof SortedNumericSortField) {
      return ((SortedNumericSortField) sortField).getNumericType();
    } else {
      return sortField.getType();
    }
  }

  static NumericDocValues getOrWrapNumeric(LeafReader reader, SortField sortField) throws IOException {
    if (sortField instanceof SortedNumericSortField) {
      SortedNumericSortField sf = (SortedNumericSortField) sortField;
      return SortedNumericSelector.wrap(DocValues.getSortedNumeric(reader, sf.getField()), sf.getSelector(), sf.getNumericType());
    } else {
      return DocValues.getNumeric(reader, sortField.getField());
    }
  }

  static SortedDocValues getOrWrapSorted(LeafReader reader, SortField sortField) throws IOException {
    if (sortField instanceof SortedSetSortField) {
      SortedSetSortField sf = (SortedSetSortField) sortField;
      return SortedSetSelector.wrap(DocValues.getSortedSet(reader, sf.getField()), sf.getSelector());
    } else {
      return DocValues.getSorted(reader, sortField.getField());
    }
  }

  static DocComparator getDocComparator(LeafReader reader, SortField sortField) throws IOException {
    return getDocComparator(reader.maxDoc(), sortField,
        () -> getOrWrapSorted(reader, sortField),
        () -> getOrWrapNumeric(reader, sortField));
  }

  interface NumericDocValuesSupplier {
    NumericDocValues get() throws IOException;
  }

  interface SortedDocValuesSupplier {
    SortedDocValues get() throws IOException;
  }

  static DocComparator getDocComparator(int maxDoc,
                                        SortField sortField,
                                        SortedDocValuesSupplier sortedProvider,
                                        NumericDocValuesSupplier numericProvider) throws IOException {

    final int reverseMul = sortField.getReverse() ? -1 : 1;
    final SortField.Type sortType = getSortFieldType(sortField);

    switch(sortType) {

      case STRING:
      {
        final SortedDocValues sorted = sortedProvider.get();
        final int missingOrd;
        if (sortField.getMissingValue() == SortField.STRING_LAST) {
          missingOrd = Integer.MAX_VALUE;
        } else {
          missingOrd = Integer.MIN_VALUE;
        }

        final int[] ords = new int[maxDoc];
        Arrays.fill(ords, missingOrd);
        int docID;
        while ((docID = sorted.nextDoc()) != NO_MORE_DOCS) {
          ords[docID] = sorted.ordValue();
        }

        return new DocComparator() {
          @Override
          public int compare(int docID1, int docID2) {
            return reverseMul * Integer.compare(ords[docID1], ords[docID2]);
          }
        };
      }

      case LONG:
      {
        final NumericDocValues dvs = numericProvider.get();
        long[] values = new long[maxDoc];
        if (sortField.getMissingValue() != null) {
          Arrays.fill(values, (Long) sortField.getMissingValue());
        }
        while (true) {
          int docID = dvs.nextDoc();
          if (docID == NO_MORE_DOCS) {
            break;
          }
          values[docID] = dvs.longValue();
        }

        return new DocComparator() {
          @Override
          public int compare(int docID1, int docID2) {
            return reverseMul * Long.compare(values[docID1], values[docID2]);
          }
        };
      }

      case INT:
      {
        final NumericDocValues dvs = numericProvider.get();
        int[] values = new int[maxDoc];
        if (sortField.getMissingValue() != null) {
          Arrays.fill(values, (Integer) sortField.getMissingValue());
        }

        while (true) {
          int docID = dvs.nextDoc();
          if (docID == NO_MORE_DOCS) {
            break;
          }
          values[docID] = (int) dvs.longValue();
        }

        return new DocComparator() {
          @Override
          public int compare(int docID1, int docID2) {
            return reverseMul * Integer.compare(values[docID1], values[docID2]);
          }
        };
      }

      case DOUBLE:
      {
        final NumericDocValues dvs = numericProvider.get();
        double[] values = new double[maxDoc];
        if (sortField.getMissingValue() != null) {
          Arrays.fill(values, (Double) sortField.getMissingValue());
        }
        while (true) {
          int docID = dvs.nextDoc();
          if (docID == NO_MORE_DOCS) {
            break;
          }
          values[docID] = Double.longBitsToDouble(dvs.longValue());
        }

        return new DocComparator() {
          @Override
          public int compare(int docID1, int docID2) {
            return reverseMul * Double.compare(values[docID1], values[docID2]);
          }
        };
      }

      case FLOAT:
      {
        final NumericDocValues dvs = numericProvider.get();
        float[] values = new float[maxDoc];
        if (sortField.getMissingValue() != null) {
          Arrays.fill(values, (Float) sortField.getMissingValue());
        }
        while (true) {
          int docID = dvs.nextDoc();
          if (docID == NO_MORE_DOCS) {
            break;
          }
          values[docID] = Float.intBitsToFloat((int) dvs.longValue());
        }

        return new DocComparator() {
          @Override
          public int compare(int docID1, int docID2) {
            return reverseMul * Float.compare(values[docID1], values[docID2]);
          }
        };
      }

      default:
        throw new IllegalArgumentException("unhandled SortField.getType()=" + sortField.getType());
    }
  }


  DocMap sort(LeafReader reader) throws IOException {
    SortField fields[] = sort.getSort();
    final DocComparator comparators[] = new DocComparator[fields.length];

    for (int i = 0; i < fields.length; i++) {
      comparators[i] = getDocComparator(reader, fields[i]);
    }
    return sort(reader.maxDoc(), comparators);
  }


  DocMap sort(int maxDoc, DocComparator[] comparators) throws IOException {
    final DocComparator comparator = new DocComparator() {
      @Override
      public int compare(int docID1, int docID2) {
        for (int i = 0; i < comparators.length; i++) {
          int comp = comparators[i].compare(docID1, docID2);
          if (comp != 0) {
            return comp;
          }
        }
        return Integer.compare(docID1, docID2); // docid order tiebreak
      }
    };

    return sort(maxDoc, comparator);
  }

  public String getID() {
    return sort.toString();
  }

  @Override
  public String toString() {
    return getID();
  }

  static final Scorer FAKESCORER = new Scorer(null) {

    float score;
    int doc = -1;

    @Override
    public int docID() {
      return doc;
    }

    public DocIdSetIterator iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public float score() throws IOException {
      return score;
    }
  };
  
}
