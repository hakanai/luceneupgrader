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
package org.trypticon.luceneupgrader.lucene8.internal.lucene.document;

import java.io.IOException;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.DoubleValues;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.DoubleValuesSource;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene8.internal.lucene.util.BytesRef;

class FeatureDoubleValuesSource extends DoubleValuesSource {
  
  private final BytesRef featureName;
  private final String field;

  public FeatureDoubleValuesSource(String field, String featureName) {
    this.field = Objects.requireNonNull(field);
    this.featureName = new BytesRef(Objects.requireNonNull(featureName));
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    return true;
  }

  @Override
  public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
    Terms terms = ctx.reader().terms(field);
    if (terms == null) {
      return DoubleValues.EMPTY;
    } else {
      TermsEnum termsEnum = terms.iterator();
      if (termsEnum.seekExact(featureName) == false) {
        return DoubleValues.EMPTY;
      } else {
        PostingsEnum currentReaderPostingsValues = termsEnum.postings(null, PostingsEnum.FREQS);
        return new FeatureDoubleValues(currentReaderPostingsValues);
      }
    }
  }

  @Override
  public boolean needsScores() {
    return false;
  }

  @Override
  public DoubleValuesSource rewrite(IndexSearcher reader) throws IOException {
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, featureName);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    FeatureDoubleValuesSource other = (FeatureDoubleValuesSource) obj;
    return Objects.equals(field, other.field) &&
        Objects.equals(featureName, other.featureName);
  }

  @Override
  public String toString() {
    return "FeatureDoubleValuesSource("+field+", "+featureName.utf8ToString()+")";
  }
  
  static class FeatureDoubleValues extends DoubleValues {
    
    private final PostingsEnum currentReaderPostingsValues;

    public FeatureDoubleValues(PostingsEnum currentReaderPostingsValues) throws IOException {
      this.currentReaderPostingsValues = currentReaderPostingsValues;
    }

    @Override
    public double doubleValue() throws IOException {
      return FeatureField.decodeFeatureValue(currentReaderPostingsValues.freq());
    }

    @Override
    public boolean advanceExact(int doc) throws IOException {
      if (doc >= currentReaderPostingsValues.docID()
          && (currentReaderPostingsValues.docID() == doc || currentReaderPostingsValues.advance(doc) == doc)) {
        return true;
      } else {
        return false;
      }
    }
    
  }

}
