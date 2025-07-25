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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.perfield;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.Terms;

/** Utility class creating a new {@link MergeState} to be restricted to a set of fields. */
final class PerFieldMergeState {

  /**
   * Create a new MergeState from the given {@link MergeState} instance with restricted fields.
   *
   * @param fields The fields to keep in the new instance.
   * @return The new MergeState with restricted fields
   */
  static MergeState restrictFields(MergeState in, Collection<String> fields) {
    var fieldInfos = new FieldInfos[in.fieldInfos.length];
    for (int i = 0; i < in.fieldInfos.length; i++) {
      fieldInfos[i] = new FilterFieldInfos(in.fieldInfos[i], fields);
    }
    var fieldsProducers = new FieldsProducer[in.fieldsProducers.length];
    for (int i = 0; i < in.fieldsProducers.length; i++) {
      fieldsProducers[i] =
          in.fieldsProducers[i] == null
              ? null
              : new FilterFieldsProducer(in.fieldsProducers[i], fields);
    }
    var mergeFieldInfos = new FilterFieldInfos(in.mergeFieldInfos, fields);
    return new MergeState(
        in.docMaps,
        in.segmentInfo,
        mergeFieldInfos,
        in.storedFieldsReaders,
        in.termVectorsReaders,
        in.normsProducers,
        in.docValuesProducers,
        fieldInfos,
        in.liveDocs,
        fieldsProducers,
        in.pointsReaders,
        in.knnVectorsReaders,
        in.maxDocs,
        in.infoStream,
        in.intraMergeTaskExecutor,
        in.needsIndexSort);
  }

  private static class FilterFieldInfos extends FieldInfos {
    private final Set<String> filteredNames;
    private final List<FieldInfo> filtered;

    // Copy of the private fields from FieldInfos
    // Renamed so as to be less confusing about which fields we're referring to
    private final boolean filteredHasVectors;
    private final boolean filteredHasPostings;
    private final boolean filteredHasProx;
    private final boolean filteredHasPayloads;
    private final boolean filteredHasOffsets;
    private final boolean filteredHasFreq;
    private final boolean filteredHasNorms;
    private final boolean filteredHasDocValues;
    private final boolean filteredHasPointValues;

    FilterFieldInfos(FieldInfos src, Collection<String> filterFields) {
      // Copy all the input FieldInfo objects since the field numbering must be kept consistent
      super(toArray(src));

      boolean hasVectors = false;
      boolean hasPostings = false;
      boolean hasProx = false;
      boolean hasPayloads = false;
      boolean hasOffsets = false;
      boolean hasFreq = false;
      boolean hasNorms = false;
      boolean hasDocValues = false;
      boolean hasPointValues = false;

      this.filteredNames = new HashSet<>(filterFields);
      this.filtered = new ArrayList<>(filterFields.size());
      for (FieldInfo fi : src) {
        if (this.filteredNames.contains(fi.name)) {
          this.filtered.add(fi);
          hasVectors |= fi.hasVectors();
          hasPostings |= fi.getIndexOptions() != IndexOptions.NONE;
          hasProx |= fi.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
          hasFreq |= fi.getIndexOptions() != IndexOptions.DOCS;
          hasOffsets |=
              fi.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                  >= 0;
          hasNorms |= fi.hasNorms();
          hasDocValues |= fi.getDocValuesType() != DocValuesType.NONE;
          hasPayloads |= fi.hasPayloads();
          hasPointValues |= (fi.getPointDimensionCount() != 0);
        }
      }

      this.filteredHasVectors = hasVectors;
      this.filteredHasPostings = hasPostings;
      this.filteredHasProx = hasProx;
      this.filteredHasPayloads = hasPayloads;
      this.filteredHasOffsets = hasOffsets;
      this.filteredHasFreq = hasFreq;
      this.filteredHasNorms = hasNorms;
      this.filteredHasDocValues = hasDocValues;
      this.filteredHasPointValues = hasPointValues;
    }

    private static FieldInfo[] toArray(FieldInfos src) {
      FieldInfo[] res = new FieldInfo[src.size()];
      int i = 0;
      for (FieldInfo fi : src) {
        res[i++] = fi;
      }
      return res;
    }

    @Override
    public Iterator<FieldInfo> iterator() {
      return filtered.iterator();
    }

    @Override
    public boolean hasFreq() {
      return filteredHasFreq;
    }

    @Override
    public boolean hasPostings() {
      return filteredHasPostings;
    }

    @Override
    public boolean hasProx() {
      return filteredHasProx;
    }

    @Override
    public boolean hasPayloads() {
      return filteredHasPayloads;
    }

    @Override
    public boolean hasOffsets() {
      return filteredHasOffsets;
    }

    @Override
    public boolean hasVectors() {
      return filteredHasVectors;
    }

    @Override
    public boolean hasNorms() {
      return filteredHasNorms;
    }

    @Override
    public boolean hasDocValues() {
      return filteredHasDocValues;
    }

    @Override
    public boolean hasPointValues() {
      return filteredHasPointValues;
    }

    @Override
    public int size() {
      return filtered.size();
    }

    @Override
    public FieldInfo fieldInfo(String fieldName) {
      if (!filteredNames.contains(fieldName)) {
        // Throw IAE to be consistent with fieldInfo(int) which throws it as well on invalid numbers
        throw new IllegalArgumentException(
            "The field named '"
                + fieldName
                + "' is not accessible in the current "
                + "merge context, available ones are: "
                + filteredNames);
      }
      return super.fieldInfo(fieldName);
    }

    @Override
    public FieldInfo fieldInfo(int fieldNumber) {
      FieldInfo res = super.fieldInfo(fieldNumber);
      if (!filteredNames.contains(res.name)) {
        throw new IllegalArgumentException(
            "The field named '"
                + res.name
                + "' numbered '"
                + fieldNumber
                + "' is not "
                + "accessible in the current merge context, available ones are: "
                + filteredNames);
      }
      return res;
    }
  }

  private static class FilterFieldsProducer extends FieldsProducer {
    private final FieldsProducer in;
    private final List<String> filtered;

    FilterFieldsProducer(FieldsProducer in, Collection<String> filterFields) {
      this.in = in;
      this.filtered = new ArrayList<>(filterFields);
    }

    @Override
    public Iterator<String> iterator() {
      return filtered.iterator();
    }

    @Override
    public Terms terms(String field) throws IOException {
      if (!filtered.contains(field)) {
        throw new IllegalArgumentException(
            "The field named '"
                + field
                + "' is not accessible in the current "
                + "merge context, available ones are: "
                + filtered);
      }
      return in.terms(field);
    }

    @Override
    public int size() {
      return filtered.size();
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    @Override
    public void checkIntegrity() throws IOException {
      in.checkIntegrity();
    }
  }
}
