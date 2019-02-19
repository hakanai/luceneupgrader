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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache;

import java.io.IOException;

public class ReverseOrdFieldSource extends ValueSource {
  public String field;


  public ReverseOrdFieldSource(String field) {
    this.field = field;
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.ValueSource#description() */
  @Override
  public String description() {
    return "rord("+field+')';
  }

  /*(non-Javadoc) @see org.apache.lucene.search.function.ValueSource#getValues(org.apache.lucene.index.IndexReader) */
  @Override
  public DocValues getValues(IndexReader reader) throws IOException {
    final FieldCache.StringIndex sindex = FieldCache.DEFAULT.getStringIndex(reader, field);

    final int arr[] = sindex.order;
    final int end = sindex.lookup.length;

    return new DocValues() {
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#floatVal(int) */
      @Override
      public float floatVal(int doc) {
        return (end - arr[doc]);
      }
      /* (non-Javadoc) @see org.apache.lucene.search.function.DocValues#intVal(int) */
      @Override
      public int intVal(int doc) {
        return end - arr[doc];
      }
      /* (non-Javadoc) @see org.apache.lucene.search.function.DocValues#strVal(int) */
      @Override
      public String strVal(int doc) {
        // the string value of the ordinal, not the string itself
        return Integer.toString(intVal(doc));
      }
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#toString(int) */
      @Override
      public String toString(int doc) {
        return description() + '=' + strVal(doc);
      }
      /*(non-Javadoc) @see org.apache.lucene.search.function.DocValues#getInnerArray() */
      @Override
      Object getInnerArray() {
        return arr;
      }
    };
  }

  /*(non-Javadoc) @see java.lang.Object#equals(java.lang.Object) */
  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null) return false;
    if (o.getClass() != ReverseOrdFieldSource.class) return false;
    ReverseOrdFieldSource other = (ReverseOrdFieldSource)o;
    return this.field.equals(other.field); 
  }

  private static final int hcode = ReverseOrdFieldSource.class.hashCode();
  
  /*(non-Javadoc) @see java.lang.Object#hashCode() */
  @Override
  public int hashCode() {
    return hcode + field.hashCode();
  }
}
