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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.index;


import java.util.Map;
import java.util.Objects;


public final class FieldInfo {
  public final String name;
  public final int number;

  private DocValuesType docValuesType = DocValuesType.NONE;

  // True if any document indexed term vectors
  private boolean storeTermVector;

  private boolean omitNorms; // omit norms associated with indexed fields  

  private IndexOptions indexOptions = IndexOptions.NONE;
  private boolean storePayloads; // whether this field stores payloads together with term positions

  private final Map<String,String> attributes;

  private long dvGen;

  private int pointDataDimensionCount;
  private int pointIndexDimensionCount;
  private int pointNumBytes;

  // whether this field is used as the soft-deletes field
  private final boolean softDeletesField;

  public FieldInfo(String name, int number, boolean storeTermVector, boolean omitNorms, boolean storePayloads,
                   IndexOptions indexOptions, DocValuesType docValues, long dvGen, Map<String,String> attributes,
                   int pointDataDimensionCount, int pointIndexDimensionCount, int pointNumBytes, boolean softDeletesField) {
    this.name = Objects.requireNonNull(name);
    this.number = number;
    this.docValuesType = Objects.requireNonNull(docValues, "DocValuesType must not be null (field: \"" + name + "\")");
    this.indexOptions = Objects.requireNonNull(indexOptions, "IndexOptions must not be null (field: \"" + name + "\")");
    if (indexOptions != IndexOptions.NONE) {
      this.storeTermVector = storeTermVector;
      this.storePayloads = storePayloads;
      this.omitNorms = omitNorms;
    } else { // for non-indexed fields, leave defaults
      this.storeTermVector = false;
      this.storePayloads = false;
      this.omitNorms = false;
    }
    this.dvGen = dvGen;
    this.attributes = Objects.requireNonNull(attributes);
    this.pointDataDimensionCount = pointDataDimensionCount;
    this.pointIndexDimensionCount = pointIndexDimensionCount;
    this.pointNumBytes = pointNumBytes;
    this.softDeletesField = softDeletesField;
    assert checkConsistency();
  }

  public boolean checkConsistency() {
    if (indexOptions != IndexOptions.NONE) {
      // Cannot store payloads unless positions are indexed:
      if (indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0 && storePayloads) {
        throw new IllegalStateException("indexed field '" + name + "' cannot have payloads without positions");
      }
    } else {
      if (storeTermVector) {
        throw new IllegalStateException("non-indexed field '" + name + "' cannot store term vectors");
      }
      if (storePayloads) {
        throw new IllegalStateException("non-indexed field '" + name + "' cannot store payloads");
      }
      if (omitNorms) {
        throw new IllegalStateException("non-indexed field '" + name + "' cannot omit norms");
      }
    }

    if (pointDataDimensionCount < 0) {
      throw new IllegalStateException("pointDataDimensionCount must be >= 0; got " + pointDataDimensionCount);
    }

    if (pointIndexDimensionCount < 0) {
      throw new IllegalStateException("pointIndexDimensionCount must be >= 0; got " + pointIndexDimensionCount);
    }

    if (pointNumBytes < 0) {
      throw new IllegalStateException("pointNumBytes must be >= 0; got " + pointNumBytes);
    }

    if (pointDataDimensionCount != 0 && pointNumBytes == 0) {
      throw new IllegalStateException("pointNumBytes must be > 0 when pointDataDimensionCount=" + pointDataDimensionCount);
    }

    if (pointIndexDimensionCount != 0 && pointDataDimensionCount == 0) {
      throw new IllegalStateException("pointIndexDimensionCount must be 0 when pointDataDimensionCount=0");
    }

    if (pointNumBytes != 0 && pointDataDimensionCount == 0) {
      throw new IllegalStateException("pointDataDimensionCount must be > 0 when pointNumBytes=" + pointNumBytes);
    }
    
    if (dvGen != -1 && docValuesType == DocValuesType.NONE) {
      throw new IllegalStateException("field '" + name + "' cannot have a docvalues update generation without having docvalues");
    }

    return true;
  }

  // should only be called by FieldInfos#addOrUpdate
  void update(boolean storeTermVector, boolean omitNorms, boolean storePayloads, IndexOptions indexOptions,
              Map<String, String> attributes, int dataDimensionCount, int indexDimensionCount, int dimensionNumBytes) {
    if (indexOptions == null) {
      throw new NullPointerException("IndexOptions must not be null (field: \"" + name + "\")");
    }
    //System.out.println("FI.update field=" + name + " indexed=" + indexed + " omitNorms=" + omitNorms + " this.omitNorms=" + this.omitNorms);
    if (this.indexOptions != indexOptions) {
      if (this.indexOptions == IndexOptions.NONE) {
        this.indexOptions = indexOptions;
      } else if (indexOptions != IndexOptions.NONE) {
        // downgrade
        this.indexOptions = this.indexOptions.compareTo(indexOptions) < 0 ? this.indexOptions : indexOptions;
      }
    }

    if (this.pointDataDimensionCount == 0 && dataDimensionCount != 0) {
      this.pointDataDimensionCount = dataDimensionCount;
      this.pointIndexDimensionCount = indexDimensionCount;
      this.pointNumBytes = dimensionNumBytes;
    } else if (dataDimensionCount != 0 && (this.pointDataDimensionCount != dataDimensionCount || this.pointIndexDimensionCount != indexDimensionCount || this.pointNumBytes != dimensionNumBytes)) {
      throw new IllegalArgumentException("cannot change field \"" + name + "\" from points dataDimensionCount=" + this.pointDataDimensionCount + ", indexDimensionCount=" + this.pointIndexDimensionCount + ", numBytes=" + this.pointNumBytes + " to inconsistent dataDimensionCount=" + dataDimensionCount +", indexDimensionCount=" + indexDimensionCount + ", numBytes=" + dimensionNumBytes);
    }

    if (this.indexOptions != IndexOptions.NONE) { // if updated field data is not for indexing, leave the updates out
      this.storeTermVector |= storeTermVector;                // once vector, always vector
      this.storePayloads |= storePayloads;

      // Awkward: only drop norms if incoming update is indexed:
      if (indexOptions != IndexOptions.NONE && this.omitNorms != omitNorms) {
        this.omitNorms = true;                // if one require omitNorms at least once, it remains off for life
      }
    }
    if (this.indexOptions == IndexOptions.NONE || this.indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
      // cannot store payloads if we don't store positions:
      this.storePayloads = false;
    }
    if (attributes != null) {
      this.attributes.putAll(attributes);
    }
    assert checkConsistency();
  }

  public void setPointDimensions(int dataDimensionCount, int indexDimensionCount, int numBytes) {
    if (dataDimensionCount <= 0) {
      throw new IllegalArgumentException("point data dimension count must be >= 0; got " + dataDimensionCount + " for field=\"" + name + "\"");
    }
    if (dataDimensionCount > PointValues.MAX_DIMENSIONS) {
      throw new IllegalArgumentException("point data dimension count must be < PointValues.MAX_DIMENSIONS (= " + PointValues.MAX_DIMENSIONS + "); got " + dataDimensionCount + " for field=\"" + name + "\"");
    }
    if (indexDimensionCount > dataDimensionCount) {
      throw new IllegalArgumentException("point index dimension count must be <= point data dimension count (= " + dataDimensionCount + "); got " + indexDimensionCount + " for field=\"" + name + "\"");
    }
    if (numBytes <= 0) {
      throw new IllegalArgumentException("point numBytes must be >= 0; got " + numBytes + " for field=\"" + name + "\"");
    }
    if (numBytes > PointValues.MAX_NUM_BYTES) {
      throw new IllegalArgumentException("point numBytes must be <= PointValues.MAX_NUM_BYTES (= " + PointValues.MAX_NUM_BYTES + "); got " + numBytes + " for field=\"" + name + "\"");
    }
    if (pointDataDimensionCount != 0 && pointDataDimensionCount != dataDimensionCount) {
      throw new IllegalArgumentException("cannot change point data dimension count from " + pointDataDimensionCount + " to " + dataDimensionCount + " for field=\"" + name + "\"");
    }
    if (pointIndexDimensionCount != 0 && pointIndexDimensionCount != indexDimensionCount) {
      throw new IllegalArgumentException("cannot change point index dimension count from " + pointIndexDimensionCount + " to " + indexDimensionCount + " for field=\"" + name + "\"");
    }
    if (pointNumBytes != 0 && pointNumBytes != numBytes) {
      throw new IllegalArgumentException("cannot change point numBytes from " + pointNumBytes + " to " + numBytes + " for field=\"" + name + "\"");
    }

    pointDataDimensionCount = dataDimensionCount;
    pointIndexDimensionCount = indexDimensionCount;
    pointNumBytes = numBytes;

    assert checkConsistency();
  }

  public int getPointDataDimensionCount() {
    return pointDataDimensionCount;
  }

  public int getPointIndexDimensionCount() {
    return pointIndexDimensionCount;
  }

  public int getPointNumBytes() {
    return pointNumBytes;
  }

  public void setDocValuesType(DocValuesType type) {
    if (type == null) {
      throw new NullPointerException("DocValuesType must not be null (field: \"" + name + "\")");
    }
    if (docValuesType != DocValuesType.NONE && type != DocValuesType.NONE && docValuesType != type) {
      throw new IllegalArgumentException("cannot change DocValues type from " + docValuesType + " to " + type + " for field \"" + name + "\"");
    }
    docValuesType = type;
    assert checkConsistency();
  }
  
  public IndexOptions getIndexOptions() {
    return indexOptions;
  }

  public void setIndexOptions(IndexOptions newIndexOptions) {
    if (indexOptions != newIndexOptions) {
      if (indexOptions == IndexOptions.NONE) {
        indexOptions = newIndexOptions;
      } else if (newIndexOptions != IndexOptions.NONE) {
        // downgrade
        indexOptions = indexOptions.compareTo(newIndexOptions) < 0 ? indexOptions : newIndexOptions;
      }
    }

    if (indexOptions == IndexOptions.NONE || indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
      // cannot store payloads if we don't store positions:
      storePayloads = false;
    }
  }
  
  public DocValuesType getDocValuesType() {
    return docValuesType;
  }
  
  void setDocValuesGen(long dvGen) {
    this.dvGen = dvGen;
    assert checkConsistency();
  }
  
  public long getDocValuesGen() {
    return dvGen;
  }
  
  void setStoreTermVectors() {
    storeTermVector = true;
    assert checkConsistency();
  }
  
  void setStorePayloads() {
    if (indexOptions != IndexOptions.NONE && indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
      storePayloads = true;
    }
    assert checkConsistency();
  }

  public boolean omitsNorms() {
    return omitNorms;
  }

  public void setOmitsNorms() {
    if (indexOptions == IndexOptions.NONE) {
      throw new IllegalStateException("cannot omit norms: this field is not indexed");
    }
    omitNorms = true;
  }
  
  public boolean hasNorms() {
    return indexOptions != IndexOptions.NONE && omitNorms == false;
  }
  
  public boolean hasPayloads() {
    return storePayloads;
  }
  
  public boolean hasVectors() {
    return storeTermVector;
  }
  
  public String getAttribute(String key) {
    return attributes.get(key);
  }
  
  public String putAttribute(String key, String value) {
    return attributes.put(key, value);
  }
  
  public Map<String,String> attributes() {
    return attributes;
  }

  public boolean isSoftDeletesField() {
    return softDeletesField;
  }
}
