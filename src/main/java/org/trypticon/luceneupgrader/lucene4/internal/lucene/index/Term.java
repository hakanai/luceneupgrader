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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

public final class Term implements Comparable<Term> {
  String field;
  BytesRef bytes;


  public Term(String fld, BytesRef bytes) {
    field = fld;
    this.bytes = bytes;
  }


  public Term(String fld, String text) {
    this(fld, new BytesRef(text));
  }


  public Term(String fld) {
    this(fld, new BytesRef());
  }

  public final String field() { return field; }

  public final String text() {
    return toString(bytes);
  }
  
  public static final String toString(BytesRef termText) {
    // the term might not be text, but usually is. so we make a best effort
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(termText.bytes, termText.offset, termText.length)).toString();
    } catch (CharacterCodingException e) {
      return termText.toString();
    }
  }

  public final BytesRef bytes() { return bytes; }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Term other = (Term) obj;
    if (field == null) {
      if (other.field != null)
        return false;
    } else if (!field.equals(other.field))
      return false;
    if (bytes == null) {
      if (other.bytes != null)
        return false;
    } else if (!bytes.equals(other.bytes))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((bytes == null) ? 0 : bytes.hashCode());
    return result;
  }

  @Override
  public final int compareTo(Term other) {
    if (field.equals(other.field)) {
      return bytes.compareTo(other.bytes);
    } else {
      return field.compareTo(other.field);
    }
  }


  final void set(String fld, BytesRef bytes) {
    field = fld;
    this.bytes = bytes;
  }

  @Override
  public final String toString() { return field + ":" + text(); }
}
