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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.index;


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

  private int pointDimensionCount;
  private int pointIndexDimensionCount;
  private int pointNumBytes;

  // whether this field is used as the soft-deletes field
  private final boolean softDeletesField;

  public FieldInfo(String name, int number, boolean storeTermVector, boolean omitNorms, boolean storePayloads,
                   IndexOptions indexOptions, DocValuesType docValues, long dvGen, Map<String,String> attributes,
                   int pointDimensionCount, int pointIndexDimensionCount, int pointNumBytes, boolean softDeletesField) {
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
    this.pointDimensionCount = pointDimensionCount;
    this.pointIndexDimensionCount = pointIndexDimensionCount;
    this.pointNumBytes = pointNumBytes;
    this.softDeletesField = softDeletesField;
    this.checkConsistency();
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

    if (pointDimensionCount < 0) {
      throw new IllegalStateException("pointDimensionCount must be >= 0; got " + pointDimensionCount);
    }

    if (pointIndexDimensionCount < 0) {
      throw new IllegalStateException("pointIndexDimensionCount must be >= 0; got " + pointIndexDimensionCount);
    }

    if (pointNumBytes < 0) {
      throw new IllegalStateException("pointNumBytes must be >= 0; got " + pointNumBytes);
    }

    if (pointDimensionCount != 0 && pointNumBytes == 0) {
      throw new IllegalStateException("pointNumBytes must be > 0 when pointDimensionCount=" + pointDimensionCount);
    }

    if (pointIndexDimensionCount != 0 && pointDimensionCount == 0) {
      throw new IllegalStateException("pointIndexDimensionCount must be 0 when pointDimensionCount=0");
    }

    if (pointNumBytes != 0 && pointDimensionCount == 0) {
      throw new IllegalStateException("pointDimensionCount must be > 0 when pointNumBytes=" + pointNumBytes);
    }
    
    if (dvGen != -1 && docValuesType == DocValuesType.NONE) {
      throw new IllegalStateException("field '" + name + "' cannot have a docvalues update generation without having docvalues");
    }

    return true;
  }

  // should only be called by FieldInfos#addOrUpdate
  void update(boolean storeTermVector, boolean omitNorms, boolean storePayloads, IndexOptions indexOptions,
              Map<String, String> attributes, int dimensionCount, int indexDimensionCount, int dimensionNumBytes) {
    if (indexOptions == null) {
      throw new NullPointerException("IndexOptions must not be null (field: \"" + name + "\")");
    }
    //System.out.println("FI.update field=" + name + " indexed=" + indexed + " omitNorms=" + omitNorms + " this.omitNorms=" + this.omitNorms);
    if (this.indexOptions != indexOptions) {
      if (this.indexOptions == IndexOptions.NONE) {
        this.indexOptions = indexOptions;
      } else if (indexOptions != IndexOptions.NONE) {
        throw new IllegalArgumentException("cannot change field \"" + name + "\" from index options=" + this.indexOptions + " to inconsistent index options=" + indexOptions);
      }
    }

    if (this.pointDimensionCount == 0 && dimensionCount != 0) {
      this.pointDimensionCount = dimensionCount;
      this.pointIndexDimensionCount = indexDimensionCount;
      this.pointNumBytes = dimensionNumBytes;
    } else if (dimensionCount != 0 && (this.pointDimensionCount != dimensionCount || this.pointIndexDimensionCount != indexDimensionCount || this.pointNumBytes != dimensionNumBytes)) {
      throw new IllegalArgumentException("cannot change field \"" + name + "\" from points dimensionCount=" + this.pointDimensionCount + ", indexDimensionCount=" + this.pointIndexDimensionCount + ", numBytes=" + this.pointNumBytes + " to inconsistent dimensionCount=" + dimensionCount +", indexDimensionCount=" + indexDimensionCount + ", numBytes=" + dimensionNumBytes);
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
    this.checkConsistency();
  }

  public void setPointDimensions(int dimensionCount, int indexDimensionCount, int numBytes) {
    if (dimensionCount <= 0) {
      throw new IllegalArgumentException("point dimension count must be >= 0; got " + dimensionCount + " for field=\"" + name + "\"");
    }
    if (indexDimensionCount > PointValues.MAX_INDEX_DIMENSIONS) {
      throw new IllegalArgumentException("point index dimension count must be < PointValues.MAX_INDEX_DIMENSIONS (= " + PointValues.MAX_INDEX_DIMENSIONS + "); got " + indexDimensionCount + " for field=\"" + name + "\"");
    }
    if (indexDimensionCount > dimensionCount) {
      throw new IllegalArgumentException("point index dimension count must be <= point dimension count (= " + dimensionCount + "); got " + indexDimensionCount + " for field=\"" + name + "\"");
    }
    if (numBytes <= 0) {
      throw new IllegalArgumentException("point numBytes must be >= 0; got " + numBytes + " for field=\"" + name + "\"");
    }
    if (numBytes > PointValues.MAX_NUM_BYTES) {
      throw new IllegalArgumentException("point numBytes must be <= PointValues.MAX_NUM_BYTES (= " + PointValues.MAX_NUM_BYTES + "); got " + numBytes + " for field=\"" + name + "\"");
    }
    if (pointDimensionCount != 0 && pointDimensionCount != dimensionCount) {
      throw new IllegalArgumentException("cannot change point dimension count from " + pointDimensionCount + " to " + dimensionCount + " for field=\"" + name + "\"");
    }
    if (pointIndexDimensionCount != 0 && pointIndexDimensionCount != indexDimensionCount) {
      throw new IllegalArgumentException("cannot change point index dimension count from " + pointIndexDimensionCount + " to " + indexDimensionCount + " for field=\"" + name + "\"");
    }
    if (pointNumBytes != 0 && pointNumBytes != numBytes) {
      throw new IllegalArgumentException("cannot change point numBytes from " + pointNumBytes + " to " + numBytes + " for field=\"" + name + "\"");
    }

    pointDimensionCount = dimensionCount;
    pointIndexDimensionCount = indexDimensionCount;
    pointNumBytes = numBytes;

    this.checkConsistency();
  }

  public int getPointDimensionCount() {
    return pointDimensionCount;
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
    this.checkConsistency();
  }
  
  public IndexOptions getIndexOptions() {
    return indexOptions;
  }

  public void setIndexOptions(IndexOptions newIndexOptions) {
    if (indexOptions != newIndexOptions) {
      if (indexOptions == IndexOptions.NONE) {
        indexOptions = newIndexOptions;
      } else if (newIndexOptions != IndexOptions.NONE) {
        throw new IllegalArgumentException("cannot change field \"" + name + "\" from index options=" + indexOptions + " to inconsistent index options=" + newIndexOptions);
      }
    }

    if (indexOptions == IndexOptions.NONE || indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
      // cannot store payloads if we don't store positions:
      storePayloads = false;
    }
    this.checkConsistency();
  }
  
  public DocValuesType getDocValuesType() {
    return docValuesType;
  }
  
  void setDocValuesGen(long dvGen) {
    this.dvGen = dvGen;
    this.checkConsistency();
  }
  
  public long getDocValuesGen() {
    return dvGen;
  }
  
  void setStoreTermVectors() {
    storeTermVector = true;
    this.checkConsistency();
  }
  
  void setStorePayloads() {
    if (indexOptions != IndexOptions.NONE && indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
      storePayloads = true;
    }
    this.checkConsistency();
  }

  public boolean omitsNorms() {
    return omitNorms;
  }

  public void setOmitsNorms() {
    if (indexOptions == IndexOptions.NONE) {
      throw new IllegalStateException("cannot omit norms: this field is not indexed");
    }
    omitNorms = true;
    this.checkConsistency();
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
