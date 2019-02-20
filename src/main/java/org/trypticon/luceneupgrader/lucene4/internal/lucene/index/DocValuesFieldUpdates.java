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

abstract class DocValuesFieldUpdates {
  
  protected static final int PAGE_SIZE = 1024;

  static abstract class Iterator {
    
    abstract int nextDoc();
    
    abstract int doc();
    
    abstract Object value();
    
    abstract void reset();
    
  }

  static class Container {
  
    final Map<String,NumericDocValuesFieldUpdates> numericDVUpdates = new HashMap<>();
    final Map<String,BinaryDocValuesFieldUpdates> binaryDVUpdates = new HashMap<>();
    
    boolean any() {
      for (NumericDocValuesFieldUpdates updates : numericDVUpdates.values()) {
        if (updates.any()) {
          return true;
        }
      }
      for (BinaryDocValuesFieldUpdates updates : binaryDVUpdates.values()) {
        if (updates.any()) {
          return true;
        }
      }
      return false;
    }
    
    int size() {
      return numericDVUpdates.size() + binaryDVUpdates.size();
    }
    
    long ramBytesPerDoc() {
      long ramBytesPerDoc = 0;
      for (NumericDocValuesFieldUpdates updates : numericDVUpdates.values()) {
        ramBytesPerDoc += updates.ramBytesPerDoc();
      }
      for (BinaryDocValuesFieldUpdates updates : binaryDVUpdates.values()) {
        ramBytesPerDoc += updates.ramBytesPerDoc();
      }
      return ramBytesPerDoc;
    }
    
    DocValuesFieldUpdates getUpdates(String field, FieldInfo.DocValuesType type) {
      switch (type) {
        case NUMERIC:
          return numericDVUpdates.get(field);
        case BINARY:
          return binaryDVUpdates.get(field);
        default:
          throw new IllegalArgumentException("unsupported type: " + type);
      }
    }
    
    DocValuesFieldUpdates newUpdates(String field, FieldInfo.DocValuesType type, int maxDoc) {
      switch (type) {
        case NUMERIC:
          assert numericDVUpdates.get(field) == null;
          NumericDocValuesFieldUpdates numericUpdates = new NumericDocValuesFieldUpdates(field, maxDoc);
          numericDVUpdates.put(field, numericUpdates);
          return numericUpdates;
        case BINARY:
          assert binaryDVUpdates.get(field) == null;
          BinaryDocValuesFieldUpdates binaryUpdates = new BinaryDocValuesFieldUpdates(field, maxDoc);
          binaryDVUpdates.put(field, binaryUpdates);
          return binaryUpdates;
        default:
          throw new IllegalArgumentException("unsupported type: " + type);
      }
    }
    
    @Override
    public String toString() {
      return "numericDVUpdates=" + numericDVUpdates + " binaryDVUpdates=" + binaryDVUpdates;
    }
  }
  
  final String field;
  final FieldInfo.DocValuesType type;
  
  protected DocValuesFieldUpdates(String field, FieldInfo.DocValuesType type) {
    this.field = field;
    this.type = type;
  }
  
  protected static int estimateCapacity(int size) {
    return (int) Math.ceil((double) size / PAGE_SIZE) * PAGE_SIZE;
  }
  
  public abstract void add(int doc, Object value);
  
  public abstract Iterator iterator();
  
  public abstract void merge(DocValuesFieldUpdates other);

  public abstract boolean any();
  
  public abstract long ramBytesPerDoc();

}
