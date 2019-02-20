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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.document;

import java.util.*;             // for javadoc
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.ScoreDoc; // for javadoc
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Searcher;  // for javadoc
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;  // for javadoc

public final class Document implements java.io.Serializable {
  List<Fieldable> fields = new ArrayList<Fieldable>();
  private float boost = 1.0f;

  public Document() {}



  public void setBoost(float boost) {
    this.boost = boost;
  }


  public float getBoost() {
    return boost;
  }

  public final void add(Fieldable field) {
    fields.add(field);
  }
  
  public final void removeField(String name) {
    Iterator<Fieldable> it = fields.iterator();
    while (it.hasNext()) {
      Fieldable field = it.next();
      if (field.name().equals(name)) {
        it.remove();
        return;
      }
    }
  }
  
  public final void removeFields(String name) {
    Iterator<Fieldable> it = fields.iterator();
    while (it.hasNext()) {
      Fieldable field = it.next();
      if (field.name().equals(name)) {
        it.remove();
      }
    }
  }


  @Deprecated
  public final Field getField(String name) {
    return (Field) getFieldable(name);
  }



 public Fieldable getFieldable(String name) {
   for (Fieldable field : fields) {
     if (field.name().equals(name))
       return field;
   }
   return null;
 }


  public final String get(String name) {
   for (Fieldable field : fields) {
      if (field.name().equals(name) && (!field.isBinary()))
        return field.stringValue();
    }
    return null;
  }


  public final List<Fieldable> getFields() {
    return fields;
  }

  private final static Field[] NO_FIELDS = new Field[0];
  
   @Deprecated
   public final Field[] getFields(String name) {
     List<Field> result = new ArrayList<Field>();
     for (Fieldable field : fields) {
       if (field.name().equals(name)) {
         result.add((Field) field);
       }
     }

     if (result.size() == 0)
       return NO_FIELDS;

     return result.toArray(new Field[result.size()]);
   }


   private final static Fieldable[] NO_FIELDABLES = new Fieldable[0];

   public Fieldable[] getFieldables(String name) {
     List<Fieldable> result = new ArrayList<Fieldable>();
     for (Fieldable field : fields) {
       if (field.name().equals(name)) {
         result.add(field);
       }
     }

     if (result.size() == 0)
       return NO_FIELDABLES;

     return result.toArray(new Fieldable[result.size()]);
   }


   private final static String[] NO_STRINGS = new String[0];

  public final String[] getValues(String name) {
    List<String> result = new ArrayList<String>();
    for (Fieldable field : fields) {
      if (field.name().equals(name) && (!field.isBinary()))
        result.add(field.stringValue());
    }
    
    if (result.size() == 0)
      return NO_STRINGS;
    
    return result.toArray(new String[result.size()]);
  }

  private final static byte[][] NO_BYTES = new byte[0][];

  public final byte[][] getBinaryValues(String name) {
    List<byte[]> result = new ArrayList<byte[]>();
    for (Fieldable field : fields) {
      if (field.name().equals(name) && (field.isBinary()))
        result.add(field.getBinaryValue());
    }
  
    if (result.size() == 0)
      return NO_BYTES;
  
    return result.toArray(new byte[result.size()][]);
  }
  
  public final byte[] getBinaryValue(String name) {
    for (Fieldable field : fields) {
      if (field.name().equals(name) && (field.isBinary()))
        return field.getBinaryValue();
    }
    return null;
  }
  
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
