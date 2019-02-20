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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.PriorityQueue;
import java.io.IOException;
import java.text.Collator;
import java.util.Locale;

class FieldDocSortedHitQueue extends PriorityQueue<FieldDoc> {

  volatile SortField[] fields = null;

  // used in the case where the fields are sorted by locale
  // based strings
  volatile Collator[] collators = null;

  volatile FieldComparator<?>[] comparators = null;


  FieldDocSortedHitQueue (int size) {
    initialize (size);
  }


  @SuppressWarnings({"unchecked","rawtypes"})
  void setFields (SortField[] fields) throws IOException {
    this.fields = fields;
    this.collators = hasCollators (fields);
    comparators = new FieldComparator[fields.length];
    for(int fieldIDX=0;fieldIDX<fields.length;fieldIDX++) {
      comparators[fieldIDX] = fields[fieldIDX].getComparator(1, fieldIDX);
    }
  }


  SortField[] getFields() {
    return fields;
  }



  private Collator[] hasCollators (final SortField[] fields) {
    if (fields == null) return null;
    Collator[] ret = new Collator[fields.length];
    for (int i=0; i<fields.length; ++i) {
      Locale locale = fields[i].getLocale();
      if (locale != null)
        ret[i] = Collator.getInstance (locale);
    }
    return ret;
  }


  @Override
  @SuppressWarnings({"unchecked","rawtypes"})
  protected final boolean lessThan(final FieldDoc docA, final FieldDoc docB) {
    final int n = fields.length;
    int c = 0;
    for (int i=0; i<n && c==0; ++i) {
      final int type = fields[i].getType();
      if (type == SortField.STRING) {
        final String s1 = (String) docA.fields[i];
        final String s2 = (String) docB.fields[i];
        // null values need to be sorted first, because of how FieldCache.getStringIndex()
        // works - in that routine, any documents without a value in the given field are
        // put first.  If both are null, the next SortField is used
        if (s1 == null) {
          c = (s2 == null) ? 0 : -1;
        } else if (s2 == null) {
          c = 1;
        } else if (fields[i].getLocale() == null) {
          c = s1.compareTo(s2);
        } else {
          c = collators[i].compare(s1, s2);
        }
      } else {
        final FieldComparator<Object> comp = (FieldComparator<Object>) comparators[i];
        c = comp.compareValues(docA.fields[i], docB.fields[i]);
      }
      // reverse sort
      if (fields[i].getReverse()) {
        c = -c;
      }
    }

    // avoid random sort order that could lead to duplicates (bug #31241):
    if (c == 0)
      return docA.doc > docB.doc;

    return c > 0;
  }
}
