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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;


public final class FieldInfo {
  public final String name;
  public final int number;

  public boolean isIndexed;

  // True if any document indexed term vectors
  public boolean storeTermVector;

  public boolean omitNorms; // omit norms associated with indexed fields  
  public IndexOptions indexOptions;
  
  public boolean storePayloads; // whether this field stores payloads together with term positions

  public static enum IndexOptions {
    DOCS_ONLY,
    DOCS_AND_FREQS,
    DOCS_AND_FREQS_AND_POSITIONS
  };

  FieldInfo(String na, boolean tk, int nu, boolean storeTermVector, 
            boolean omitNorms, boolean storePayloads, IndexOptions indexOptions) {
    name = na;
    isIndexed = tk;
    number = nu;
    if (isIndexed) {
      this.storeTermVector = storeTermVector;
      this.storePayloads = storePayloads;
      this.omitNorms = omitNorms;
      this.indexOptions = indexOptions;
    } else { // for non-indexed fields, leave defaults
      this.storeTermVector = false;
      this.storePayloads = false;
      this.omitNorms = true;
      this.indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    }
    assert indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS || !storePayloads;
  }

  @Override
  public Object clone() {
    return new FieldInfo(name, isIndexed, number, storeTermVector,
                         omitNorms, storePayloads, indexOptions);
  }

  void update(boolean isIndexed, boolean storeTermVector,
              boolean omitNorms, boolean storePayloads, IndexOptions indexOptions) {

    if (this.isIndexed != isIndexed) {
      this.isIndexed = true;                      // once indexed, always index
    }
    if (isIndexed) { // if updated field data is not for indexing, leave the updates out
      if (this.storeTermVector != storeTermVector) {
        this.storeTermVector = true;                // once vector, always vector
      }
      if (this.storePayloads != storePayloads) {
        this.storePayloads = true;
      }
      if (this.omitNorms != omitNorms) {
        this.omitNorms = false;                // once norms are stored, always store
      }
      if (this.indexOptions != indexOptions) {
        // downgrade
        this.indexOptions = this.indexOptions.compareTo(indexOptions) < 0 ? this.indexOptions : indexOptions;
        this.storePayloads = false;
      }
    }
    assert this.indexOptions == IndexOptions.DOCS_AND_FREQS_AND_POSITIONS || !this.storePayloads;
  }
}
