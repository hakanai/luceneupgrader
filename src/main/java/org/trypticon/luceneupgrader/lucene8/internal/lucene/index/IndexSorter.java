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

package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.FieldComparator;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.SortField;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.LongValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.NumericUtils;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.packed.PackedInts;

import static org.trypticon.luceneupgrader.lucene8.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public interface IndexSorter {

  interface ComparableProvider {
    long getAsComparableLong(int docID) throws IOException;
  }

  interface DocComparator {
    int compare(int docID1, int docID2);
  }

  ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException;

  DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException;

  String getProviderName();

  interface NumericDocValuesProvider {
    NumericDocValues get(LeafReader reader) throws IOException;
  }

  interface SortedDocValuesProvider {
    SortedDocValues get(LeafReader reader) throws IOException;
  }

  final class IntSorter implements IndexSorter {

    private final Integer missingValue;
    private final int reverseMul;
    private final NumericDocValuesProvider valuesProvider;
    private final String providerName;

    public IntSorter(String providerName, Integer missingValue, boolean reverse, NumericDocValuesProvider valuesProvider) {
      this.missingValue = missingValue;
      this.reverseMul = reverse ? -1 : 1;
      this.valuesProvider = valuesProvider;
      this.providerName = providerName;
    }

    @Override
    public ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException {
      ComparableProvider[] providers = new ComparableProvider[readers.size()];
      final long missingValue;
      if (this.missingValue != null) {
        missingValue = this.missingValue;
      } else {
        missingValue = 0L;
      }

      for(int readerIndex=0;readerIndex<readers.size();readerIndex++) {
        final NumericDocValues values = valuesProvider.get(readers.get(readerIndex));

        providers[readerIndex] = docID -> {
          if (values.advanceExact(docID)) {
            return values.longValue();
          } else {
            return missingValue;
          }
        };
      }
      return providers;
    }

    @Override
    public DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException {
      final NumericDocValues dvs = valuesProvider.get(reader);
      int[] values = new int[maxDoc];
      if (this.missingValue != null) {
        Arrays.fill(values, this.missingValue);
      }
      while (true) {
        int docID = dvs.nextDoc();
        if (docID == NO_MORE_DOCS) {
          break;
        }
        values[docID] = (int) dvs.longValue();
      }

      return (docID1, docID2) -> reverseMul * Integer.compare(values[docID1], values[docID2]);
    }

    @Override
    public String getProviderName() {
      return providerName;
    }
  }

  final class LongSorter implements IndexSorter {

    private final String providerName;
    private final Long missingValue;
    private final int reverseMul;
    private final NumericDocValuesProvider valuesProvider;

    public LongSorter(String providerName, Long missingValue, boolean reverse, NumericDocValuesProvider valuesProvider) {
      this.providerName = providerName;
      this.missingValue = missingValue;
      this.reverseMul = reverse ? -1 : 1;
      this.valuesProvider = valuesProvider;
    }

    @Override
    public ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException {
      ComparableProvider[] providers = new ComparableProvider[readers.size()];
      final long missingValue;
      if (this.missingValue != null) {
        missingValue = this.missingValue;
      } else {
        missingValue = 0L;
      }

      for(int readerIndex=0;readerIndex<readers.size();readerIndex++) {
        final NumericDocValues values = valuesProvider.get(readers.get(readerIndex));

        providers[readerIndex] = docID -> {
          if (values.advanceExact(docID)) {
            return values.longValue();
          } else {
            return missingValue;
          }
        };
      }
      return providers;
    }

    @Override
    public DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException {
      final NumericDocValues dvs = valuesProvider.get(reader);
      long[] values = new long[maxDoc];
      if (this.missingValue != null) {
        Arrays.fill(values, this.missingValue);
      }
      while (true) {
        int docID = dvs.nextDoc();
        if (docID == NO_MORE_DOCS) {
          break;
        }
        values[docID] = dvs.longValue();
      }

      return (docID1, docID2) -> reverseMul * Long.compare(values[docID1], values[docID2]);
    }

    @Override
    public String getProviderName() {
      return providerName;
    }
  }

  final class FloatSorter implements IndexSorter {

    private final String providerName;
    private final Float missingValue;
    private final int reverseMul;
    private final NumericDocValuesProvider valuesProvider;

    public FloatSorter(String providerName, Float missingValue, boolean reverse, NumericDocValuesProvider valuesProvider) {
      this.providerName = providerName;
      this.missingValue = missingValue;
      this.reverseMul = reverse ? -1 : 1;
      this.valuesProvider = valuesProvider;
    }

    @Override
    public ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException {
      ComparableProvider[] providers = new ComparableProvider[readers.size()];
      final float missingValue;
      if (this.missingValue != null) {
        missingValue = this.missingValue;
      } else {
        missingValue = 0.0f;
      }

      for(int readerIndex=0;readerIndex<readers.size();readerIndex++) {
        final NumericDocValues values = valuesProvider.get(readers.get(readerIndex));

        providers[readerIndex] = docID -> {
          float value = missingValue;
          if (values.advanceExact(docID)) {
            value = Float.intBitsToFloat((int) values.longValue());
          }
          return NumericUtils.floatToSortableInt(value);
        };
      }
      return providers;
    }

    @Override
    public DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException {
      final NumericDocValues dvs = valuesProvider.get(reader);
      float[] values = new float[maxDoc];
      if (this.missingValue != null) {
        Arrays.fill(values, this.missingValue);
      }
      while (true) {
        int docID = dvs.nextDoc();
        if (docID == NO_MORE_DOCS) {
          break;
        }
        values[docID] = Float.intBitsToFloat((int) dvs.longValue());
      }

      return (docID1, docID2) -> reverseMul * Float.compare(values[docID1], values[docID2]);
    }

    @Override
    public String getProviderName() {
      return providerName;
    }
  }

  final class DoubleSorter implements IndexSorter {

    private final String providerName;
    private final Double missingValue;
    private final int reverseMul;
    private final NumericDocValuesProvider valuesProvider;

    public DoubleSorter(String providerName, Double missingValue, boolean reverse, NumericDocValuesProvider valuesProvider) {
      this.providerName = providerName;
      this.missingValue = missingValue;
      this.reverseMul = reverse ? -1 : 1;
      this.valuesProvider = valuesProvider;
    }

    @Override
    public ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException {
      ComparableProvider[] providers = new ComparableProvider[readers.size()];
      final double missingValue;
      if (this.missingValue != null) {
        missingValue = this.missingValue;
      } else {
        missingValue = 0.0f;
      }

      for(int readerIndex=0;readerIndex<readers.size();readerIndex++) {
        final NumericDocValues values = valuesProvider.get(readers.get(readerIndex));

        providers[readerIndex] = docID -> {
          double value = missingValue;
          if (values.advanceExact(docID)) {
            value = Double.longBitsToDouble(values.longValue());
          }
          return NumericUtils.doubleToSortableLong(value);
        };
      }
      return providers;
    }

    @Override
    public DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException {
      final NumericDocValues dvs = valuesProvider.get(reader);
      double[] values = new double[maxDoc];
      if (missingValue != null) {
        Arrays.fill(values, missingValue);
      }
      while (true) {
        int docID = dvs.nextDoc();
        if (docID == NO_MORE_DOCS) {
          break;
        }
        values[docID] = Double.longBitsToDouble(dvs.longValue());
      }

      return (docID1, docID2) -> reverseMul * Double.compare(values[docID1], values[docID2]);
    }

    @Override
    public String getProviderName() {
      return providerName;
    }
  }

  final class StringSorter implements IndexSorter {

    private final String providerName;
    private final Object missingValue;
    private final int reverseMul;
    private final SortedDocValuesProvider valuesProvider;

    public StringSorter(String providerName, Object missingValue, boolean reverse, SortedDocValuesProvider valuesProvider) {
      this.providerName = providerName;
      this.missingValue = missingValue;
      this.reverseMul = reverse ? -1 : 1;
      this.valuesProvider = valuesProvider;
    }

    @Override
    public ComparableProvider[] getComparableProviders(List<? extends LeafReader> readers) throws IOException {
      final ComparableProvider[] providers = new ComparableProvider[readers.size()];
      final SortedDocValues[] values = new SortedDocValues[readers.size()];
      for(int i=0;i<readers.size();i++) {
        final SortedDocValues sorted = valuesProvider.get(readers.get(i));
        values[i] = sorted;
      }
      OrdinalMap ordinalMap = OrdinalMap.build(null, values, PackedInts.DEFAULT);
      final int missingOrd;
      if (missingValue == SortField.STRING_LAST) {
        missingOrd = Integer.MAX_VALUE;
      } else {
        missingOrd = Integer.MIN_VALUE;
      }

      for(int readerIndex=0;readerIndex<readers.size();readerIndex++) {
        final SortedDocValues readerValues = values[readerIndex];
        final LongValues globalOrds = ordinalMap.getGlobalOrds(readerIndex);
        providers[readerIndex] = docID -> {
          if (readerValues.advanceExact(docID)) {
            // translate segment's ord to global ord space:
            return globalOrds.get(readerValues.ordValue());
          } else {
            return missingOrd;
          }
        };
      }
      return providers;
    }

    @Override
    public DocComparator getDocComparator(LeafReader reader, int maxDoc) throws IOException {
      final SortedDocValues sorted = valuesProvider.get(reader);
      final int missingOrd;
      if (missingValue == SortField.STRING_LAST) {
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

      return (docID1, docID2) -> reverseMul * Integer.compare(ords[docID1], ords[docID2]);
    }

    @Override
    public String getProviderName() {
      return providerName;
    }
  }

}
