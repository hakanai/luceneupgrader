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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.NumericTokenStream; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.FieldCache; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeFilter; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeQuery; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;

public final class FloatField extends Field {
  

  public static final FieldType TYPE_NOT_STORED = new FieldType();
  static {
    TYPE_NOT_STORED.setIndexed(true);
    TYPE_NOT_STORED.setTokenized(true);
    TYPE_NOT_STORED.setOmitNorms(true);
    TYPE_NOT_STORED.setIndexOptions(IndexOptions.DOCS_ONLY);
    TYPE_NOT_STORED.setNumericType(FieldType.NumericType.FLOAT);
    TYPE_NOT_STORED.setNumericPrecisionStep(NumericUtils.PRECISION_STEP_DEFAULT_32);
    TYPE_NOT_STORED.freeze();
  }


  public static final FieldType TYPE_STORED = new FieldType();
  static {
    TYPE_STORED.setIndexed(true);
    TYPE_STORED.setTokenized(true);
    TYPE_STORED.setOmitNorms(true);
    TYPE_STORED.setIndexOptions(IndexOptions.DOCS_ONLY);
    TYPE_STORED.setNumericType(FieldType.NumericType.FLOAT);
    TYPE_STORED.setNumericPrecisionStep(NumericUtils.PRECISION_STEP_DEFAULT_32);
    TYPE_STORED.setStored(true);
    TYPE_STORED.freeze();
  }


  public FloatField(String name, float value, Store stored) {
    super(name, stored == Store.YES ? TYPE_STORED : TYPE_NOT_STORED);
    fieldsData = Float.valueOf(value);
  }
  

  public FloatField(String name, float value, FieldType type) {
    super(name, type);
    if (type.numericType() != FieldType.NumericType.FLOAT) {
      throw new IllegalArgumentException("type.numericType() must be FLOAT but got " + type.numericType());
    }
    fieldsData = Float.valueOf(value);
  }
}
