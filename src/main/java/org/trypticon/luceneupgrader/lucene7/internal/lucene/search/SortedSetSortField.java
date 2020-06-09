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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedSetDocValues;

public class SortedSetSortField extends SortField {
  
  private final SortedSetSelector.Type selector;
  
  public SortedSetSortField(String field, boolean reverse) {
    this(field, reverse, SortedSetSelector.Type.MIN);
  }

  public SortedSetSortField(String field, boolean reverse, SortedSetSelector.Type selector) {
    super(field, SortField.Type.CUSTOM, reverse);
    if (selector == null) {
      throw new NullPointerException();
    }
    this.selector = selector;
  }
  
  public SortedSetSelector.Type getSelector() {
    return selector;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + selector.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    SortedSetSortField other = (SortedSetSortField) obj;
    if (selector != other.selector) return false;
    return true;
  }
  
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<sortedset" + ": \"").append(getField()).append("\">");
    if (getReverse()) buffer.append('!');
    if (missingValue != null) {
      buffer.append(" missingValue=");
      buffer.append(missingValue);
    }
    buffer.append(" selector=");
    buffer.append(selector);

    return buffer.toString();
  }

  @Override
  public void setMissingValue(Object missingValue) {
    if (missingValue != STRING_FIRST && missingValue != STRING_LAST) {
      throw new IllegalArgumentException("For SORTED_SET type, missing value must be either STRING_FIRST or STRING_LAST");
    }
    this.missingValue = missingValue;
  }
  
  @Override
  public FieldComparator<?> getComparator(int numHits, int sortPos) {
    return new FieldComparator.TermOrdValComparator(numHits, getField(), missingValue == STRING_LAST) {
      @Override
      protected SortedDocValues getSortedDocValues(LeafReaderContext context, String field) throws IOException {
        return SortedSetSelector.wrap(DocValues.getSortedSet(context.reader(), field), selector);
      }
    };
  }
}
