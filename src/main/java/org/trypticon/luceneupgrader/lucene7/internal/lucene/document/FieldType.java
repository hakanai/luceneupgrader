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


import java.util.HashMap;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.analysis.Analyzer; // javadocs
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.DocValuesType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexOptions;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexableFieldType;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.PointValues;

public class FieldType implements IndexableFieldType  {

  private boolean stored;
  private boolean tokenized = true;
  private boolean storeTermVectors;
  private boolean storeTermVectorOffsets;
  private boolean storeTermVectorPositions;
  private boolean storeTermVectorPayloads;
  private boolean omitNorms;
  private IndexOptions indexOptions = IndexOptions.NONE;
  private boolean frozen;
  private DocValuesType docValuesType = DocValuesType.NONE;
  private int dataDimensionCount;
  private int indexDimensionCount;
  private int dimensionNumBytes;
  private Map<String, String> attributes;

  public FieldType(IndexableFieldType ref) {
    this.stored = ref.stored();
    this.tokenized = ref.tokenized();
    this.storeTermVectors = ref.storeTermVectors();
    this.storeTermVectorOffsets = ref.storeTermVectorOffsets();
    this.storeTermVectorPositions = ref.storeTermVectorPositions();
    this.storeTermVectorPayloads = ref.storeTermVectorPayloads();
    this.omitNorms = ref.omitNorms();
    this.indexOptions = ref.indexOptions();
    this.docValuesType = ref.docValuesType();
    this.dataDimensionCount = ref.pointDataDimensionCount();
    this.indexDimensionCount = ref.pointIndexDimensionCount();
    this.dimensionNumBytes = ref.pointNumBytes();
    if (ref.getAttributes() != null) {
      this.attributes = new HashMap<>(ref.getAttributes());
    }
    // Do not copy frozen!
  }
  
  public FieldType() {
  }

  protected void checkIfFrozen() {
    if (frozen) {
      throw new IllegalStateException("this FieldType is already frozen and cannot be changed");
    }
  }

  public void freeze() {
    this.frozen = true;
  }
  
  @Override
  public boolean stored() {
    return this.stored;
  }
  
  public void setStored(boolean value) {
    checkIfFrozen();
    this.stored = value;
  }

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
    if (value == null) {
      throw new NullPointerException("IndexOptions must not be null");
    }
    this.indexOptions = value;
  }

  public void setDimensions(int dimensionCount, int dimensionNumBytes) {
    this.setDimensions(dimensionCount, dimensionCount, dimensionNumBytes);
  }

  public void setDimensions(int dataDimensionCount, int indexDimensionCount, int dimensionNumBytes) {
    if (dataDimensionCount < 0) {
      throw new IllegalArgumentException("dataDimensionCount must be >= 0; got " + dataDimensionCount);
    }
    if (dataDimensionCount > PointValues.MAX_DIMENSIONS) {
      throw new IllegalArgumentException("dataDimensionCount must be <= " + PointValues.MAX_DIMENSIONS + "; got " + dataDimensionCount);
    }
    if (indexDimensionCount < 0) {
      throw new IllegalArgumentException("indexDimensionCount must be >= 0; got " + indexDimensionCount);
    }
    if (indexDimensionCount > dataDimensionCount) {
      throw new IllegalArgumentException("indexDimensionCount must be <= dataDimensionCount: " + dataDimensionCount + "; got " + indexDimensionCount);
    }
    if (dimensionNumBytes < 0) {
      throw new IllegalArgumentException("dimensionNumBytes must be >= 0; got " + dimensionNumBytes);
    }
    if (dimensionNumBytes > PointValues.MAX_NUM_BYTES) {
      throw new IllegalArgumentException("dimensionNumBytes must be <= " + PointValues.MAX_NUM_BYTES + "; got " + dimensionNumBytes);
    }
    if (dataDimensionCount == 0) {
      if (indexDimensionCount != 0) {
        throw new IllegalArgumentException("when dataDimensionCount is 0, indexDimensionCount must be 0; got " + indexDimensionCount);
      }
      if (dimensionNumBytes != 0) {
        throw new IllegalArgumentException("when dataDimensionCount is 0, dimensionNumBytes must be 0; got " + dimensionNumBytes);
      }
    } else if (indexDimensionCount == 0) {
      throw new IllegalArgumentException("when dataDimensionCount is > 0, indexDimensionCount must be > 0; got " + indexDimensionCount);
    } else if (dimensionNumBytes == 0) {
      if (dataDimensionCount != 0) {
        throw new IllegalArgumentException("when dimensionNumBytes is 0, dataDimensionCount must be 0; got " + dataDimensionCount);
      }
    }

    this.dataDimensionCount = dataDimensionCount;
    this.indexDimensionCount = indexDimensionCount;
    this.dimensionNumBytes = dimensionNumBytes;
  }

  @Override
  public int pointDataDimensionCount() {
    return dataDimensionCount;
  }

  @Override
  public int pointIndexDimensionCount() {
    return indexDimensionCount;
  }

  @Override
  public int pointNumBytes() {
    return dimensionNumBytes;
  }

  public String putAttribute(String key, String value) {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    return attributes.put(key, value);
  }

  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    if (stored()) {
      result.append("stored");
    }
    if (indexOptions != IndexOptions.NONE) {
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
    }
    if (dataDimensionCount != 0) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append("pointDataDimensionCount=");
      result.append(dataDimensionCount);
      result.append(",pointIndexDimensionCount=");
      result.append(indexDimensionCount);
      result.append(",pointNumBytes=");
      result.append(dimensionNumBytes);
    }
    if (docValuesType != DocValuesType.NONE) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append("docValuesType=");
      result.append(docValuesType);
    }
    
    return result.toString();
  }
  
  @Override
  public DocValuesType docValuesType() {
    return docValuesType;
  }

  public void setDocValuesType(DocValuesType type) {
    checkIfFrozen();
    if (type == null) {
      throw new NullPointerException("DocValuesType must not be null");
    }
    docValuesType = type;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + dataDimensionCount;
    result = prime * result + indexDimensionCount;
    result = prime * result + dimensionNumBytes;
    result = prime * result + ((docValuesType == null) ? 0 : docValuesType.hashCode());
    result = prime * result + indexOptions.hashCode();
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
    if (dataDimensionCount != other.dataDimensionCount) return false;
    if (indexDimensionCount != other.indexDimensionCount) return false;
    if (dimensionNumBytes != other.dimensionNumBytes) return false;
    if (docValuesType != other.docValuesType) return false;
    if (indexOptions != other.indexOptions) return false;
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
