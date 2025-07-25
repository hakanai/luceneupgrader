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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.index;

import java.io.Reader;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.InvertableType;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.document.StoredValue;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.util.BytesRef;

// TODO: how to handle versioning here...?

/**
 * Represents a single field for indexing. IndexWriter consumes Iterable&lt;IndexableField&gt; as a
 * document.
 *
 * @lucene.experimental
 */
public interface IndexableField {

  /** Field name */
  String name();

  /** {@link IndexableFieldType} describing the properties of this field. */
  IndexableFieldType fieldType();

  /**
   * Creates the TokenStream used for indexing this field. If appropriate, implementations should
   * use the given Analyzer to create the TokenStreams.
   *
   * @param analyzer Analyzer that should be used to create the TokenStreams from
   * @param reuse TokenStream for a previous instance of this field <b>name</b>. This allows custom
   *     field types (like StringField and NumericField) that do not use the analyzer to still have
   *     good performance. Note: the passed-in type may be inappropriate, for example if you mix up
   *     different types of Fields for the same field name. So it's the responsibility of the
   *     implementation to check.
   * @return TokenStream value for indexing the document. Should always return a non-null value if
   *     the field is to be indexed
   */
  TokenStream tokenStream(Analyzer analyzer, TokenStream reuse);

  /** Non-null if this field has a binary value */
  BytesRef binaryValue();

  /** Non-null if this field has a string value */
  String stringValue();

  /** Non-null if this field has a string value */
  default CharSequence getCharSequenceValue() {
    return stringValue();
  }

  /** Non-null if this field has a Reader value */
  Reader readerValue();

  /** Non-null if this field has a numeric value */
  Number numericValue();

  /**
   * Stored value. This method is called to populate stored fields and must return a non-null value
   * if the field stored.
   */
  StoredValue storedValue();

  /**
   * Describes how this field should be inverted. This must return a non-null value if the field
   * indexes terms and postings.
   */
  InvertableType invertableType();
}
