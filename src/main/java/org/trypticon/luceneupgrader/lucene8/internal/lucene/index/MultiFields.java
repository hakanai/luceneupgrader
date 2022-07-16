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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.MergedIterator;

public final class MultiFields extends Fields {
  private final Fields[] subs;
  private final ReaderSlice[] subSlices;
  private final Map<String,Terms> terms = new ConcurrentHashMap<>();

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

}

