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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.util.HashMap;
import java.util.Map;



public final class FieldInfo {
  public final String name;
  public final int number;

  private boolean indexed;
  private DocValuesType docValueType;

  // True if any document indexed term vectors
  private boolean storeTermVector;

  private DocValuesType normType;
  private boolean omitNorms; // omit norms associated with indexed fields  
  private IndexOptions indexOptions;
  private boolean storePayloads; // whether this field stores payloads together with term positions

  private Map<String,String> attributes;

  private long dvGen;
  
  public static enum IndexOptions {
    // NOTE: order is important here; FieldInfo uses this
    // order to merge two conflicting IndexOptions (always
    // "downgrades" by picking the lowest).
    // TODO: maybe rename to just DOCS?
    DOCS_ONLY,
    DOCS_AND_FREQS,
    DOCS_AND_FREQS_AND_POSITIONS,
    DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS,
  }
  
  public static enum DocValuesType {
    NUMERIC,
    BINARY,
    SORTED,
    SORTED_NUMERIC,
    SORTED_SET
  }

  public FieldInfo(String name, boolean indexed, int number, boolean storeTermVector, boolean omitNorms,
      boolean storePayloads, IndexOptions indexOptions, DocValuesType docValues, DocValuesType normsType, 
      long dvGen, Map<String,String> attributes) {
    this.name = name;
    this.indexed = indexed;
    this.number = number;
    this.docValueType = docValues;
    if (indexed) {
      this.storeTermVector = storeTermVector;
      this.storePayloads = storePayloads;
      this.omitNorms = omitNorms;
      this.indexOptions = indexOptions;
      this.normType = !omitNorms ? normsType : null;
    } else { // for non-indexed fields, leave defaults
      this.storeTermVector = false;
      this.storePayloads = false;
      this.omitNorms = false;
      this.indexOptions = null;
      this.normType = null;
    }
    this.dvGen = dvGen;
    this.attributes = attributes;
    assert checkConsistency();
  }

  private boolean checkConsistency() {
    if (!indexed) {
      assert !storeTermVector;
      assert !storePayloads;
      assert !omitNorms;
      assert normType == null;
      assert indexOptions == null;
    } else {
      assert indexOptions != null;
      if (omitNorms) {
        assert normType == null;
      }
      // Cannot store payloads unless positions are indexed:
      assert indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0 || !this.storePayloads;
    }
    
    if (dvGen != -1) {
      assert docValueType != null;
    }

    return true;
  }

  void update(IndexableFieldType ft) {
    update(ft.indexed(), false, ft.omitNorms(), false, ft.indexOptions());
  }

  // should only be called by FieldInfos#addOrUpdate
  void update(boolean indexed, boolean storeTermVector, boolean omitNorms, boolean storePayloads, IndexOptions indexOptions) {
    //System.out.println("FI.update field=" + name + " indexed=" + indexed + " omitNorms=" + omitNorms + " this.omitNorms=" + this.omitNorms);
    this.indexed |= indexed;  // once indexed, always indexed
    if (indexed) { // if updated field data is not for indexing, leave the updates out
      this.storeTermVector |= storeTermVector;                // once vector, always vector
      this.storePayloads |= storePayloads;
      if (this.omitNorms != omitNorms) {
        this.omitNorms = true;                // if one require omitNorms at least once, it remains off for life
        this.normType = null;
      }
      if (this.indexOptions != indexOptions) {
        if (this.indexOptions == null) {
          this.indexOptions = indexOptions;
        } else {
          // downgrade
          this.indexOptions = this.indexOptions.compareTo(indexOptions) < 0 ? this.indexOptions : indexOptions;
        }
        if (this.indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0) {
          // cannot store payloads if we don't store positions:
          this.storePayloads = false;
        }
      }
    }
    assert checkConsistency();
  }

  void setDocValuesType(DocValuesType type) {
    if (docValueType != null && type != null && docValueType != type) {
      throw new IllegalArgumentException("cannot change DocValues type from " + docValueType + " to " + type + " for field \"" + name + "\"");
    }
    docValueType = type;
    assert checkConsistency();
  }
  
  public IndexOptions getIndexOptions() {
    return indexOptions;
  }
  
  public boolean hasDocValues() {
    return docValueType != null;
  }

  public DocValuesType getDocValuesType() {
    return docValueType;
  }
  
  void setDocValuesGen(long dvGen) {
    this.dvGen = dvGen;
    assert checkConsistency();
  }
  
  public long getDocValuesGen() {
    return dvGen;
  }
  
  public DocValuesType getNormType() {
    return normType;
  }

  void setStoreTermVectors() {
    storeTermVector = true;
    assert checkConsistency();
  }
  
  void setStorePayloads() {
    if (indexed && indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
      storePayloads = true;
    }
    assert checkConsistency();
  }

  void setNormValueType(DocValuesType type) {
    if (normType != null && normType != type) {
      throw new IllegalArgumentException("cannot change Norm type from " + normType + " to " + type + " for field \"" + name + "\"");
    }
    normType = type;
    assert checkConsistency();
  }
  
  public boolean omitsNorms() {
    return omitNorms;
  }
  
  public boolean hasNorms() {
    return normType != null;
  }
  
  public boolean isIndexed() {
    return indexed;
  }
  
  public boolean hasPayloads() {
    return storePayloads;
  }
  
  public boolean hasVectors() {
    return storeTermVector;
  }
  
  public String getAttribute(String key) {
    if (attributes == null) {
      return null;
    } else {
      return attributes.get(key);
    }
  }
  
  public String putAttribute(String key, String value) {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    return attributes.put(key, value);
  }
  
  public Map<String,String> attributes() {
    return attributes;
  }
}
