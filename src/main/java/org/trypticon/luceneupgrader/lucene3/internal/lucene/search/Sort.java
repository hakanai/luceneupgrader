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

import java.io.Serializable;
import java.util.Arrays;


public class Sort
implements Serializable {

  public static final Sort RELEVANCE = new Sort();

  public static final Sort INDEXORDER = new Sort(SortField.FIELD_DOC);

  // internal representation of the sort criteria
  SortField[] fields;

  public Sort() {
    this(SortField.FIELD_SCORE);
  }

  public Sort(SortField field) {
    setSort(field);
  }

  public Sort(SortField... fields) {
    setSort(fields);
  }

  public void setSort(SortField field) {
    this.fields = new SortField[] { field };
  }

  public void setSort(SortField... fields) {
    this.fields = fields;
  }
  
  public SortField[] getSort() {
    return fields;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();

    for (int i = 0; i < fields.length; i++) {
      buffer.append(fields[i].toString());
      if ((i+1) < fields.length)
        buffer.append(',');
    }

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Sort)) return false;
    final Sort other = (Sort)o;
    return Arrays.equals(this.fields, other.fields);
  }

  @Override
  public int hashCode() {
    return 0x45aaf665 + Arrays.hashCode(fields);
  }
}
