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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.analysis.Analyzer; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexableFieldType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.NumericRangeQuery; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.NumericUtils;

public class FieldType implements IndexableFieldType {


  public static enum NumericType {
    INT,
    LONG,
    FLOAT,
    DOUBLE
  }

  private boolean indexed;
  private boolean stored;
  private boolean tokenized = true;
  private boolean storeTermVectors;
  private boolean storeTermVectorOffsets;
  private boolean storeTermVectorPositions;
  private boolean storeTermVectorPayloads;
  private boolean omitNorms;
  private IndexOptions indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
  private NumericType numericType;
  private boolean frozen;
  private int numericPrecisionStep = NumericUtils.PRECISION_STEP_DEFAULT;
  private DocValuesType docValueType;

  public FieldType(FieldType ref) {
    this.indexed = ref.indexed();
    this.stored = ref.stored();
    this.tokenized = ref.tokenized();
    this.storeTermVectors = ref.storeTermVectors();
    this.storeTermVectorOffsets = ref.storeTermVectorOffsets();
    this.storeTermVectorPositions = ref.storeTermVectorPositions();
    this.storeTermVectorPayloads = ref.storeTermVectorPayloads();
    this.omitNorms = ref.omitNorms();
    this.indexOptions = ref.indexOptions();
    this.docValueType = ref.docValueType();
    this.numericType = ref.numericType();
    // Do not copy frozen!
  }
  
  public FieldType() {
  }

  private void checkIfFrozen() {
    if (frozen) {
      throw new IllegalStateException("this FieldType is already frozen and cannot be changed");
    }
  }

  public void freeze() {
    this.frozen = true;
  }
  
  @Override
  public boolean indexed() {
    return this.indexed;
  }
  
  public void setIndexed(boolean value) {
    checkIfFrozen();
    this.indexed = value;
  }

  @Override
  public boolean stored() {
    return this.stored;
  }
  
  public void setStored(boolean value) {
    checkIfFrozen();
    this.stored = value;
  }

  @Override
  public boolean tokenized() {
    return this.tokenized;
  }
  
  public void setTokenized(boolean value) {
    checkIfFrozen();
    this.tokenized = value;
  }

  @Override
  public boolean storeTermVectors() {
    return this.storeTermVectors;
  }
  
  public void setStoreTermVectors(boolean value) {
    checkIfFrozen();
    this.storeTermVectors = value;
  }

  @Override
  public boolean storeTermVectorOffsets() {
    return this.storeTermVectorOffsets;
  }
  
  public void setStoreTermVectorOffsets(boolean value) {
    checkIfFrozen();
    this.storeTermVectorOffsets = value;
  }

  @Override
  public boolean storeTermVectorPositions() {
    return this.storeTermVectorPositions;
  }
  
  public void setStoreTermVectorPositions(boolean value) {
    checkIfFrozen();
    this.storeTermVectorPositions = value;
  }
  
  @Override
  public boolean storeTermVectorPayloads() {
    return this.storeTermVectorPayloads;
  }
  
  public void setStoreTermVectorPayloads(boolean value) {
    checkIfFrozen();
    this.storeTermVectorPayloads = value;
  }
  
  @Override
  public boolean omitNorms() {
    return this.omitNorms;
  }
  
  public void setOmitNorms(boolean value) {
    checkIfFrozen();
    this.omitNorms = value;
  }

  @Override
  public IndexOptions indexOptions() {
    return this.indexOptions;
  }
  
  public void setIndexOptions(IndexOptions value) {
    checkIfFrozen();
    this.indexOptions = value;
  }

  public void setNumericType(NumericType type) {
    checkIfFrozen();
    numericType = type;
  }


  public NumericType numericType() {
    return numericType;
  }

  public void setNumericPrecisionStep(int precisionStep) {
    checkIfFrozen();
    if (precisionStep < 1) {
      throw new IllegalArgumentException("precisionStep must be >= 1 (got " + precisionStep + ")");
    }
    this.numericPrecisionStep = precisionStep;
  }


  public int numericPrecisionStep() {
    return numericPrecisionStep;
  }

  @Override
  public final String toString() {
    StringBuilder result = new StringBuilder();
    if (stored()) {
      result.append("stored");
    }
    if (indexed()) {
      if (result.length() > 0)
        result.append(",");
      result.append("indexed");
      if (tokenized()) {
        result.append(",tokenized");
      }
      if (storeTermVectors()) {
        result.append(",termVector");
      }
      if (storeTermVectorOffsets()) {
        result.append(",termVectorOffsets");
      }
      if (storeTermVectorPositions()) {
        result.append(",termVectorPosition");
      }
      if (storeTermVectorPayloads()) {
        result.append(",termVectorPayloads");
      }
      if (omitNorms()) {
        result.append(",omitNorms");
      }
      if (indexOptions != IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
        result.append(",indexOptions=");
        result.append(indexOptions);
      }
      if (numericType != null) {
        result.append(",numericType=");
        result.append(numericType);
        result.append(",numericPrecisionStep=");
        result.append(numericPrecisionStep);
      }
    }
    if (docValueType != null) {
      if (result.length() > 0)
        result.append(",");
      result.append("docValueType=");
      result.append(docValueType);
    }
    
    return result.toString();
  }
  
  @Override
  public DocValuesType docValueType() {
    return docValueType;
  }

  public void setDocValueType(DocValuesType type) {
    checkIfFrozen();
    docValueType = type;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((docValueType == null) ? 0 : docValueType.hashCode());
    result = prime * result + ((indexOptions == null) ? 0 : indexOptions.hashCode());
    result = prime * result + (indexed ? 1231 : 1237);
    result = prime * result + numericPrecisionStep;
    result = prime * result + ((numericType == null) ? 0 : numericType.hashCode());
    result = prime * result + (omitNorms ? 1231 : 1237);
    result = prime * result + (storeTermVectorOffsets ? 1231 : 1237);
    result = prime * result + (storeTermVectorPayloads ? 1231 : 1237);
    result = prime * result + (storeTermVectorPositions ? 1231 : 1237);
    result = prime * result + (storeTermVectors ? 1231 : 1237);
    result = prime * result + (stored ? 1231 : 1237);
    result = prime * result + (tokenized ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    FieldType other = (FieldType) obj;
    if (docValueType != other.docValueType) return false;
    if (indexOptions != other.indexOptions) return false;
    if (indexed != other.indexed) return false;
    if (numericPrecisionStep != other.numericPrecisionStep) return false;
    if (numericType != other.numericType) return false;
    if (omitNorms != other.omitNorms) return false;
    if (storeTermVectorOffsets != other.storeTermVectorOffsets) return false;
    if (storeTermVectorPayloads != other.storeTermVectorPayloads) return false;
    if (storeTermVectorPositions != other.storeTermVectorPositions) return false;
    if (storeTermVectors != other.storeTermVectors) return false;
    if (stored != other.stored) return false;
    if (tokenized != other.tokenized) return false;
    return true;
  }
}
