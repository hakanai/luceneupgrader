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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.MergedIterator;

public final class MultiFields extends Fields {
  private final Fields[] subs;
  private final ReaderSlice[] subSlices;
  private final Map<String,Terms> terms = new ConcurrentHashMap<>();

  public static Fields getFields(IndexReader reader) throws IOException {
    final List<LeafReaderContext> leaves = reader.leaves();
    switch (leaves.size()) {
      case 1:
        // already an atomic reader / reader with one leave
        return new LeafReaderFields(leaves.get(0).reader());
      default:
        final List<Fields> fields = new ArrayList<>(leaves.size());
        final List<ReaderSlice> slices = new ArrayList<>(leaves.size());
        for (final LeafReaderContext ctx : leaves) {
          final LeafReader r = ctx.reader();
          final Fields f = new LeafReaderFields(r);
          fields.add(f);
          slices.add(new ReaderSlice(ctx.docBase, r.maxDoc(), fields.size()-1));
        }
        if (fields.size() == 1) {
          return fields.get(0);
        } else {
          return new MultiFields(fields.toArray(Fields.EMPTY_ARRAY),
                                         slices.toArray(ReaderSlice.EMPTY_ARRAY));
        }
    }
  }

  public static Bits getLiveDocs(IndexReader reader) {
    if (reader.hasDeletions()) {
      final List<LeafReaderContext> leaves = reader.leaves();
      final int size = leaves.size();
      assert size > 0 : "A reader with deletions must have at least one leave";
      if (size == 1) {
        return leaves.get(0).reader().getLiveDocs();
      }
      final Bits[] liveDocs = new Bits[size];
      final int[] starts = new int[size + 1];
      for (int i = 0; i < size; i++) {
        // record all liveDocs, even if they are null
        final LeafReaderContext ctx = leaves.get(i);
        liveDocs[i] = ctx.reader().getLiveDocs();
        starts[i] = ctx.docBase;
      }
      starts[size] = reader.maxDoc();
      return new MultiBits(liveDocs, starts, true);
    } else {
      return null;
    }
  }

  public static Terms getTerms(IndexReader r, String field) throws IOException {
    final List<LeafReaderContext> leaves = r.leaves();
    if (leaves.size() == 1) {
      return leaves.get(0).reader().terms(field);
    }

    final List<Terms> termsPerLeaf = new ArrayList<>(leaves.size());
    final List<ReaderSlice> slicePerLeaf = new ArrayList<>(leaves.size());

    for (int leafIdx = 0; leafIdx < leaves.size(); leafIdx++) {
      LeafReaderContext ctx = leaves.get(leafIdx);
      Terms subTerms = ctx.reader().terms(field);
      if (subTerms != null) {
        termsPerLeaf.add(subTerms);
        slicePerLeaf.add(new ReaderSlice(ctx.docBase, r.maxDoc(), leafIdx - 1));
      }
    }

    if (termsPerLeaf.size() == 0) {
      return null;
    } else {
      return new MultiTerms(termsPerLeaf.toArray(Terms.EMPTY_ARRAY),
          slicePerLeaf.toArray(ReaderSlice.EMPTY_ARRAY));
    }
  }
  
  public static PostingsEnum getTermDocsEnum(IndexReader r, String field, BytesRef term) throws IOException {
    return getTermDocsEnum(r, field, term, PostingsEnum.FREQS);
  }
  
  public static PostingsEnum getTermDocsEnum(IndexReader r, String field, BytesRef term, int flags) throws IOException {
    assert field != null;
    assert term != null;
    final Terms terms = getTerms(r, field);
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator();
      if (termsEnum.seekExact(term)) {
        return termsEnum.postings(null, flags);
      }
    }
    return null;
  }

  public static PostingsEnum getTermPositionsEnum(IndexReader r, String field, BytesRef term) throws IOException {
    return getTermPositionsEnum(r, field, term, PostingsEnum.ALL);
  }

  public static PostingsEnum getTermPositionsEnum(IndexReader r, String field, BytesRef term, int flags) throws IOException {
    assert field != null;
    assert term != null;
    final Terms terms = getTerms(r, field);
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator();
      if (termsEnum.seekExact(term)) {
        return termsEnum.postings(null, flags);
      }
    }
    return null;
  }

  // TODO: why is this public?
  public MultiFields(Fields[] subs, ReaderSlice[] subSlices) {
    this.subs = subs;
    this.subSlices = subSlices;
  }

  @SuppressWarnings({"unchecked","rawtypes"})
  @Override
  public Iterator<String> iterator() {
    Iterator<String> subIterators[] = new Iterator[subs.length];
    for(int i=0;i<subs.length;i++) {
      subIterators[i] = subs[i].iterator();
    }
    return new MergedIterator<>(subIterators);
  }

  @Override
  public Terms terms(String field) throws IOException {
    Terms result = terms.get(field);
    if (result != null)
      return result;


    // Lazy init: first time this field is requested, we
    // create & add to terms:
    final List<Terms> subs2 = new ArrayList<>();
    final List<ReaderSlice> slices2 = new ArrayList<>();

    // Gather all sub-readers that share this field
    for(int i=0;i<subs.length;i++) {
      final Terms terms = subs[i].terms(field);
      if (terms != null) {
        subs2.add(terms);
        slices2.add(subSlices[i]);
      }
    }
    if (subs2.size() == 0) {
      result = null;
      // don't cache this case with an unbounded cache, since the number of fields that don't exist
      // is unbounded.
    } else {
      result = new MultiTerms(subs2.toArray(Terms.EMPTY_ARRAY),
          slices2.toArray(ReaderSlice.EMPTY_ARRAY));
      terms.put(field, result);
    }

    return result;
  }

  @Override
  public int size() {
    return -1;
  }

  public static FieldInfos getMergedFieldInfos(IndexReader reader) {
    final List<LeafReaderContext> leaves = reader.leaves();
    if (leaves.isEmpty()) {
      return FieldInfos.EMPTY;
    } else if (leaves.size() == 1) {
      return leaves.get(0).reader().getFieldInfos();
    } else {
      final String softDeletesField = leaves.stream()
          .map(l -> l.reader().getFieldInfos().getSoftDeletesField())
          .filter(Objects::nonNull)
          .findAny().orElse(null);
      final FieldInfos.Builder builder = new FieldInfos.Builder(new FieldInfos.FieldNumbers(softDeletesField));
      for (final LeafReaderContext ctx : leaves) {
        builder.add(ctx.reader().getFieldInfos());
      }
      return builder.finish();
    }
  }

  public static Collection<String> getIndexedFields(IndexReader reader) {
    final Collection<String> fields = new HashSet<>();
    for(final FieldInfo fieldInfo : getMergedFieldInfos(reader)) {
      if (fieldInfo.getIndexOptions() != IndexOptions.NONE) {
        fields.add(fieldInfo.name);
      }
    }
    return fields;
  }

  private static class LeafReaderFields extends Fields {

    private final LeafReader leafReader;
    private final List<String> indexedFields;

    LeafReaderFields(LeafReader leafReader) {
      this.leafReader = leafReader;
      this.indexedFields = new ArrayList<>();
      for (FieldInfo fieldInfo : leafReader.getFieldInfos()) {
        if (fieldInfo.getIndexOptions() != IndexOptions.NONE) {
          indexedFields.add(fieldInfo.name);
        }
      }
      Collections.sort(indexedFields);
    }

    @Override
    public Iterator<String> iterator() {
      return Collections.unmodifiableList(indexedFields).iterator();
    }

    @Override
    public int size() {
      return indexedFields.size();
    }

    @Override
    public Terms terms(String field) throws IOException {
      return leafReader.terms(field);
    }
  }
}

