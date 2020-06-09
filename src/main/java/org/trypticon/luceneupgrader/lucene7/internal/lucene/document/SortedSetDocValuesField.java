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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.SortedSetDocValues;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;


public class SortedSetDocValuesField extends Field {

  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDocValuesType(DocValuesType.SORTED_SET);
    TYPE.freeze();
  }

  public SortedSetDocValuesField(String name, BytesRef bytes) {
    super(name, TYPE);
    fieldsData = bytes;
  }

  public static Query newSlowRangeQuery(String field,
      BytesRef lowerValue, BytesRef upperValue,
      boolean lowerInclusive, boolean upperInclusive) {
    return new SortedSetDocValuesRangeQuery(field, lowerValue, upperValue, lowerInclusive, upperInclusive) {
      @Override
      SortedSetDocValues getValues(LeafReader reader, String field) throws IOException {
        return DocValues.getSortedSet(reader, field);
      }
    };
  }

  public static Query newSlowExactQuery(String field, BytesRef value) {
    return newSlowRangeQuery(field, value, value, true, true);
  }
}
