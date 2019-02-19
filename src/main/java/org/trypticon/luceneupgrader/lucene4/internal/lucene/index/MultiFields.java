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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.MergedIterator;

public final class MultiFields extends Fields {
  private final Fields[] subs;
  private final ReaderSlice[] subSlices;
  private final Map<String,Terms> terms = new ConcurrentHashMap<>();


  public static Fields getFields(IndexReader reader) throws IOException {
    final List<AtomicReaderContext> leaves = reader.leaves();
    switch (leaves.size()) {
      case 0:
        // no fields
        return null;
      case 1:
        // already an atomic reader / reader with one leave
        return leaves.get(0).reader().fields();
      default:
        final List<Fields> fields = new ArrayList<>();
        final List<ReaderSlice> slices = new ArrayList<>();
        for (final AtomicReaderContext ctx : leaves) {
          final AtomicReader r = ctx.reader();
          final Fields f = r.fields();
          if (f != null) {
            fields.add(f);
            slices.add(new ReaderSlice(ctx.docBase, r.maxDoc(), fields.size()-1));
          }
        }
        if (fields.isEmpty()) {
          return null;
        } else if (fields.size() == 1) {
          return fields.get(0);
        } else {
          return new MultiFields(fields.toArray(Fields.EMPTY_ARRAY),
                                         slices.toArray(ReaderSlice.EMPTY_ARRAY));
        }
    }
  }


  public static Bits getLiveDocs(IndexReader reader) {
    if (reader.hasDeletions()) {
      final List<AtomicReaderContext> leaves = reader.leaves();
      final int size = leaves.size();
      assert size > 0 : "A reader with deletions must have at least one leave";
      if (size == 1) {
        return leaves.get(0).reader().getLiveDocs();
      }
      final Bits[] liveDocs = new Bits[size];
      final int[] starts = new int[size + 1];
      for (int i = 0; i < size; i++) {
        // record all liveDocs, even if they are null
        final AtomicReaderContext ctx = leaves.get(i);
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
    final Fields fields = getFields(r);
    if (fields == null) {
      return null;
    } else {
      return fields.terms(field);
    }
  }
  

  public static DocsEnum getTermDocsEnum(IndexReader r, Bits liveDocs, String field, BytesRef term) throws IOException {
    return getTermDocsEnum(r, liveDocs, field, term, DocsEnum.FLAG_FREQS);
  }
  

  public static DocsEnum getTermDocsEnum(IndexReader r, Bits liveDocs, String field, BytesRef term, int flags) throws IOException {
    assert field != null;
    assert term != null;
    final Terms terms = getTerms(r, field);
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator(null);
      if (termsEnum.seekExact(term)) {
        return termsEnum.docs(liveDocs, null, flags);
      }
    }
    return null;
  }


  public static DocsAndPositionsEnum getTermPositionsEnum(IndexReader r, Bits liveDocs, String field, BytesRef term) throws IOException {
    return getTermPositionsEnum(r, liveDocs, field, term, DocsAndPositionsEnum.FLAG_OFFSETS | DocsAndPositionsEnum.FLAG_PAYLOADS);
  }


  public static DocsAndPositionsEnum getTermPositionsEnum(IndexReader r, Bits liveDocs, String field, BytesRef term, int flags) throws IOException {
    assert field != null;
    assert term != null;
    final Terms terms = getTerms(r, field);
    if (terms != null) {
      final TermsEnum termsEnum = terms.iterator(null);
      if (termsEnum.seekExact(term)) {
        return termsEnum.docsAndPositions(liveDocs, null, flags);
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
    final FieldInfos.Builder builder = new FieldInfos.Builder();
    for(final AtomicReaderContext ctx : reader.leaves()) {
      builder.add(ctx.reader().getFieldInfos());
    }
    return builder.finish();
  }


  public static Collection<String> getIndexedFields(IndexReader reader) {
    final Collection<String> fields = new HashSet<>();
    for(final FieldInfo fieldInfo : getMergedFieldInfos(reader)) {
      if (fieldInfo.isIndexed()) {
        fields.add(fieldInfo.name);
      }
    }
    return fields;
  }
}

