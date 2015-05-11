package org.apache.lucene.document;

/**
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

import java.util.ArrayList;
import java.util.List;

/** Documents are the unit of indexing and search.
 *
 * A Document is a set of fields.  Each field has a name and a textual value.
 * A field may be {@code Fieldable#isStored() stored} with the document, in which
 * case it is returned with search hits on the document.  Thus each document
 * should typically contain one or more stored fields which uniquely identify
 * it.
 *
 * <p>Note that fields which are <i>not</i> {@code Fieldable#isStored() stored} are
 * <i>not</i> available in documents retrieved from the index, e.g. with {@code
 * ScoreDoc#doc}, {@code Searcher#doc(int)} or {@code
 * IndexReader#document(int)}.
 */

public final class Document implements java.io.Serializable {
  List<Fieldable> fields = new ArrayList<Fieldable>();

  /** Constructs a new document with no fields. */
  public Document() {}


  /**
   * <p>Adds a field to a document.  Several fields may be added with
   * the same name.  In this case, if the fields are indexed, their text is
   * treated as though appended for the purposes of search.</p>
   * <p> Note that add like the removeField(s) methods only makes sense 
   * prior to adding a document to an index. These methods cannot
   * be used to change the content of an existing index! In order to achieve this,
   * a document has to be deleted from an index and a new changed version of that
   * document has to be added.</p>
   */
  public final void add(Fieldable field) {
    fields.add(field);
  }


  /** Returns a List of all the fields in a document.
   * <p>Note that fields which are <i>not</i> {@code Fieldable#isStored() stored} are
   * <i>not</i> available in documents retrieved from the
   * index, e.g. {@code Searcher#doc(int)} or {@code
   * IndexReader#document(int)}.
   */
  public final List<Fieldable> getFields() {
    return fields;
  }


    /** Prints the fields of a document for human consumption. */
  @Override
  public final String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Document<");
    for (int i = 0; i < fields.size(); i++) {
      Fieldable field = fields.get(i);
      buffer.append(field.toString());
      if (i != fields.size()-1)
        buffer.append(" ");
    }
    buffer.append(">");
    return buffer.toString();
  }
}
