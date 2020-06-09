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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.document;


import java.util.*;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexReader;  // for javadoc
import org.trypticon.luceneupgrader.lucene7.internal.lucene.index.IndexableField;
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.IndexSearcher;  // for javadoc
import org.trypticon.luceneupgrader.lucene7.internal.lucene.search.ScoreDoc; // for javadoc
import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;


public final class Document implements Iterable<IndexableField> {

  private final List<IndexableField> fields = new ArrayList<>();

  public Document() {}
  
  @Override
  public Iterator<IndexableField> iterator() {
    return fields.iterator();
  }

  public final void add(IndexableField field) {
    fields.add(field);
  }
  
  public final void removeField(String name) {
    Iterator<IndexableField> it = fields.iterator();
    while (it.hasNext()) {
      IndexableField field = it.next();
      if (field.name().equals(name)) {
        it.remove();
        return;
      }
    }
  }
  
  public final void removeFields(String name) {
    Iterator<IndexableField> it = fields.iterator();
    while (it.hasNext()) {
      IndexableField field = it.next();
      if (field.name().equals(name)) {
        it.remove();
      }
    }
  }


  public final BytesRef[] getBinaryValues(String name) {
    final List<BytesRef> result = new ArrayList<>();
    for (IndexableField field : fields) {
      if (field.name().equals(name)) {
        final BytesRef bytes = field.binaryValue();
        if (bytes != null) {
          result.add(bytes);
        }
      }
    }
  
    return result.toArray(new BytesRef[result.size()]);
  }
  
  public final BytesRef getBinaryValue(String name) {
    for (IndexableField field : fields) {
      if (field.name().equals(name)) {
        final BytesRef bytes = field.binaryValue();
        if (bytes != null) {
          return bytes;
        }
      }
    }
    return null;
  }

  public final IndexableField getField(String name) {
    for (IndexableField field : fields) {
      if (field.name().equals(name)) {
        return field;
      }
    }
    return null;
  }

  public IndexableField[] getFields(String name) {
    List<IndexableField> result = new ArrayList<>();
    for (IndexableField field : fields) {
      if (field.name().equals(name)) {
        result.add(field);
      }
    }

    return result.toArray(new IndexableField[result.size()]);
  }
  
  public final List<IndexableField> getFields() {
    return Collections.unmodifiableList(fields);
  }
  
  private final static String[] NO_STRINGS = new String[0];

  public final String[] getValues(String name) {
    List<String> result = new ArrayList<>();
    for (IndexableField field : fields) {
      if (field.name().equals(name) && field.stringValue() != null) {
        result.add(field.stringValue());
      }
    }
    
    if (result.size() == 0) {
      return NO_STRINGS;
    }
    
    return result.toArray(new String[result.size()]);
  }

  public final String get(String name) {
    for (IndexableField field : fields) {
      if (field.name().equals(name) && field.stringValue() != null) {
        return field.stringValue();
      }
    }
    return null;
  }
  
  @Override
  public final String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Document<");
    for (int i = 0; i < fields.size(); i++) {
      IndexableField field = fields.get(i);
      buffer.append(field.toString());
      if (i != fields.size()-1) {
        buffer.append(" ");
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  public void clear() {
    fields.clear();
  }
}
