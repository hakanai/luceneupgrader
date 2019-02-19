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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.index;


import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.DocumentStoredFieldVisitor;



public abstract class StoredFieldVisitor {

  protected StoredFieldVisitor() {
  }
  

  public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
  }

  public void stringField(FieldInfo fieldInfo, byte[] value) throws IOException {
  }

  public void intField(FieldInfo fieldInfo, int value) throws IOException {
  }

  public void longField(FieldInfo fieldInfo, long value) throws IOException {
  }

  public void floatField(FieldInfo fieldInfo, float value) throws IOException {
  }

  public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
  }
  
  public abstract Status needsField(FieldInfo fieldInfo) throws IOException;
  
  public static enum Status {
    YES,
    NO,
    STOP
  }
}
