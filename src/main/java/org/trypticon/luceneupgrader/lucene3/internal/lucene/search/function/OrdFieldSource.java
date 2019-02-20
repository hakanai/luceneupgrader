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

public class OrdFieldSource extends ValueSource {
  protected String field;


  public OrdFieldSource(String field) {
    this.field = field;
  }

  @Override
  public String description() {
    return "ord(" + field + ')';
  }

  @Override
  public DocValues getValues(IndexReader reader) throws IOException {
    final int[] arr = FieldCache.DEFAULT.getStringIndex(reader, field).order;
    return new DocValues() {
      @Override
      public float floatVal(int doc) {
        return arr[doc];
      }
      @Override
      public String strVal(int doc) {
        // the string value of the ordinal, not the string itself
        return Integer.toString(arr[doc]);
      }
      @Override
      public String toString(int doc) {
        return description() + '=' + intVal(doc);
      }
      @Override
      Object getInnerArray() {
        return arr;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null) return false;
    if (o.getClass() != OrdFieldSource.class) return false;
    OrdFieldSource other = (OrdFieldSource)o;
    return this.field.equals(other.field);
  }

  private static final int hcode = OrdFieldSource.class.hashCode();
  
  @Override
  public int hashCode() {
    return hcode + field.hashCode();
  }
}
