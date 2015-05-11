package org.apache.lucene.document;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.index.FieldInfo.IndexOptions;

import java.io.Serializable;

/**
 * Synonymous with {@code Field}.
 *
 * <p><bold>WARNING</bold>: This interface may change within minor versions, despite Lucene's backward compatibility requirements.
 * This means new methods may be added from version to version.  This change only affects the Fieldable API; other backwards
 * compatibility promises remain intact. For example, Lucene can still
 * read and write indices created within the same major version.
 * </p>
 *
 **/
public interface Fieldable extends Serializable {

  /** Returns the name of the field as an interned string.
   * For example "date", "title", "body", ...
   */
  String name();

  /** The value of the field as a String, or null.
   * <p>
   * For indexing, if isStored()==true, the stringValue() will be used as the stored field value
   * unless isBinary()==true, in which case getBinaryValue() will be used.
   *
   * If isIndexed()==true and isTokenized()==false, this String value will be indexed as a single token.
   * If isIndexed()==true and isTokenized()==true, then tokenStreamValue() will be used to generate indexed tokens if not null,
   * else readerValue() will be used to generate indexed tokens if not null, else stringValue() will be used to generate tokens.
   */
  String stringValue();

  /** True if the value of the field is to be stored in the index for return
    with search hits. */
  boolean  isStored();

  /** True if the value of the field is to be indexed, so that it may be
    searched on. */
  boolean  isIndexed();

  /** True if the value of the field should be tokenized as text prior to
    indexing.  Un-tokenized fields are indexed as a single word and may not be
    Reader-valued. */
  boolean  isTokenized();

  /** True if the term or terms used to index this field are stored as a term
   *  vector, available from {@code org.apache.lucene.index.IndexReader#getTermFreqVector(int,String)}.
   *  These methods do not provide access to the original content of the field,
   *  only to terms used to index it. If the original content must be
   *  preserved, use the <code>stored</code> attribute instead.
   *
   *
   */
  boolean isTermVectorStored();

  /**
   * True if terms are stored as term vector together with their offsets 
   * (start and end positon in source text).
   */
  boolean isStoreOffsetWithTermVector();

  /**
   * True if terms are stored as term vector together with their token positions.
   */
  boolean isStorePositionWithTermVector();

  /** True if the value of the field is stored as binary */
  boolean  isBinary();

  /** Expert:
   *
   * If set, omit normalization factors associated with this indexed field.
   * This effectively disables indexing boosts and length normalization for this field.
   */
  void setOmitNorms(boolean omitNorms);

  /**
   * Returns offset into byte[] segment that is used as value, if Field is not binary
   * returned value is undefined
   * @return index of the first character in byte[] segment that represents this Field value
   */
  int getBinaryOffset();
  
  /**
   * Returns length of byte[] segment that is used as value, if Field is not binary
   * returned value is undefined
   * @return length of byte[] segment that represents this Field value
   */
  int getBinaryLength();

  /**
   * Return the raw byte[] for the binary field.  Note that
   * you must also call {@code #getBinaryLength} and {@code
   * #getBinaryOffset} to know which range of bytes in this
   * returned array belong to the field.
   * @return reference to the Field value as byte[].
   */
  byte[] getBinaryValue();

  /**
   * Return the raw byte[] for the binary field.  Note that
   * you must also call {@code #getBinaryLength} and {@code
   * #getBinaryOffset} to know which range of bytes in this
   * returned array belong to the field.<p>
   * About reuse: if you pass in the result byte[] and it is
   * used, likely the underlying implementation will hold
   * onto this byte[] and return it in future calls to
   * {@code #getBinaryValue()}.
   * So if you subsequently re-use the same byte[] elsewhere
   * it will alter this Fieldable's value.
   * @param result  User defined buffer that will be used if
   *  possible.  If this is null or not large enough, a new
   *  buffer is allocated
   * @return reference to the Field value as byte[].
   */
  byte[] getBinaryValue(byte[] result);
  
  /** */
  IndexOptions getIndexOptions();
  
  /** Expert:
  *
  * If set, omit term freq, and optionally positions and payloads from
  * postings for this field.
  *
  * <p><b>NOTE</b>: While this option reduces storage space
  * required in the index, it also means any query
  * requiring positional information, such as {@code
  * PhraseQuery} or {@code SpanQuery} subclasses will
  * silently fail to find results.
  */
  void setIndexOptions(IndexOptions indexOptions);
}
