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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.StringHelper;

public final class Term implements Comparable<Term>, java.io.Serializable {
  String field;
  String text;


  public Term(String fld, String txt) {
    field = StringHelper.intern(fld);
    text = txt;
  }


  public Term(String fld) {
    this(fld, "", true);
  }

  Term(String fld, String txt, boolean intern) {
    field = intern ? StringHelper.intern(fld) : fld;	  // field names are interned
    text = txt;					          // unless already known to be
  }

  public final String field() { return field; }

  public final String text() { return text; }
  
  public Term createTerm(String text)
  {
      return new Term(field,text,false);
  }

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
    } else if (field != other.field)
      return false;
    if (text == null) {
      if (other.text != null)
        return false;
    } else if (!text.equals(other.text))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((text == null) ? 0 : text.hashCode());
    return result;
  }

  public final int compareTo(Term other) {
    if (field == other.field)			  // fields are interned
      return text.compareTo(other.text);
    else
      return field.compareTo(other.field);
  }

  final void set(String fld, String txt) {
    field = fld;
    text = txt;
  }

  @Override
  public final String toString() { return field + ":" + text; }

  private void readObject(java.io.ObjectInputStream in)
    throws java.io.IOException, ClassNotFoundException
  {
      in.defaultReadObject();
      field = StringHelper.intern(field);
  }
}
