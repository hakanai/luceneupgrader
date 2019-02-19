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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.document;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.NumericDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.SortedNumericDocValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.IndexOrDocValuesQuery;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Query;



public class NumericDocValuesField extends Field {

  public static final FieldType TYPE = new FieldType();
  static {
    TYPE.setDocValuesType(DocValuesType.NUMERIC);
    TYPE.freeze();
  }


  public NumericDocValuesField(String name, long value) {
    super(name, TYPE);
    fieldsData = Long.valueOf(value);
  }

  public static Query newRangeQuery(String field, long lowerValue, long upperValue) {
    return new SortedNumericDocValuesRangeQuery(field, lowerValue, upperValue) {
      @Override
      SortedNumericDocValues getValues(LeafReader reader, String field) throws IOException {
        NumericDocValues values = reader.getNumericDocValues(field);
        if (values == null) {
          return null;
        }
        return DocValues.singleton(values, reader.getDocsWithField(field));
      }
    };
  }


  public static Query newExactQuery(String field, long value) {
    return newRangeQuery(field, value, value);
  }
}
