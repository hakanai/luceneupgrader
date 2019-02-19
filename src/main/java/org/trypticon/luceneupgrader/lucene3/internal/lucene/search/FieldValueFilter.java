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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Bits.MatchAllBits;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.Bits.MatchNoBits;

import java.io.IOException;

public class FieldValueFilter extends Filter {
  private final String field;
  private final boolean negate;

  public FieldValueFilter(String field) {
    this(field, false);
  }

  public FieldValueFilter(String field, boolean negate) {
    this.field = field;
    this.negate = negate;
  }
  
  public String field() {
    return field;
  }
  
  public boolean negate() {
    return negate; 
  }

  @Override
  public DocIdSet getDocIdSet(final IndexReader reader) throws IOException {
    final Bits docsWithField = FieldCache.DEFAULT.getDocsWithField(
        reader, field);
    if (negate) {
      if (docsWithField instanceof MatchAllBits) {
        return null;
      }
      return new FieldCacheDocIdSet(reader) {
        @Override
        protected final boolean matchDoc(int doc) {
          return !docsWithField.get(doc);
        }
      };
    } else {
      if (docsWithField instanceof MatchNoBits) {
        return null;
      }
      if (docsWithField instanceof DocIdSet) {
        // UweSays: this is always the case for our current impl - but who knows
        // :-)
        final DocIdSet dis = (DocIdSet) docsWithField;
        return (reader.hasDeletions()) ?
          new FilteredDocIdSet(dis) {
            @Override
            protected final boolean match(int doc) {
              return !reader.isDeleted(doc);
            }
          } : dis;
      }
      return new FieldCacheDocIdSet(reader) {
        @Override
        protected final boolean matchDoc(int doc) {
          return docsWithField.get(doc);
        }
      };
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + (negate ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FieldValueFilter other = (FieldValueFilter) obj;
    if (field == null) {
      if (other.field != null)
        return false;
    } else if (!field.equals(other.field))
      return false;
    if (negate != other.negate)
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "FieldValueFilter [field=" + field + ", negate=" + negate + "]";
  }

}
