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
package org.trypticon.luceneupgrader.lucene9.internal.lucene.document;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene9.internal.lucene.index.StoredFields;

/**
 * A {@link StoredFieldVisitor} that creates a {@link Document} from stored fields.
 *
 * <p>This visitor supports loading all stored fields, or only specific requested fields provided
 * from a {@link Set}.
 *
 * <p>This is used by {@link StoredFields#document(int)} to load a document.
 *
 * @lucene.experimental
 */
public class DocumentStoredFieldVisitor extends StoredFieldVisitor {
  private final Document doc = new Document();
  private final Set<String> fieldsToAdd;

  /**
   * Load only fields named in the provided <code>Set&lt;String&gt;</code>.
   *
   * @param fieldsToAdd Set of fields to load, or <code>null</code> (all fields).
   */
  public DocumentStoredFieldVisitor(Set<String> fieldsToAdd) {
    this.fieldsToAdd = fieldsToAdd;
  }

  /** Load only fields named in the provided fields. */
  public DocumentStoredFieldVisitor(String... fields) {
    fieldsToAdd = new HashSet<>(Arrays.asList(fields));
  }

  /** Load all stored fields. */
  public DocumentStoredFieldVisitor() {
    this.fieldsToAdd = null;
  }

  @Override
  public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
    doc.add(new StoredField(fieldInfo.name, value));
  }

  @Override
  public void stringField(FieldInfo fieldInfo, String value) throws IOException {
    final FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(fieldInfo.hasVectors());
    ft.setOmitNorms(fieldInfo.omitsNorms());
    ft.setIndexOptions(fieldInfo.getIndexOptions());
    doc.add(
        new StoredField(
            fieldInfo.name, Objects.requireNonNull(value, "String value should not be null"), ft));
  }

  @Override
  public void intField(FieldInfo fieldInfo, int value) {
    doc.add(new StoredField(fieldInfo.name, value));
  }

  @Override
  public void longField(FieldInfo fieldInfo, long value) {
    doc.add(new StoredField(fieldInfo.name, value));
  }

  @Override
  public void floatField(FieldInfo fieldInfo, float value) {
    doc.add(new StoredField(fieldInfo.name, value));
  }

  @Override
  public void doubleField(FieldInfo fieldInfo, double value) {
    doc.add(new StoredField(fieldInfo.name, value));
  }

  @Override
  public Status needsField(FieldInfo fieldInfo) throws IOException {
    return fieldsToAdd == null || fieldsToAdd.contains(fieldInfo.name) ? Status.YES : Status.NO;
  }

  /**
   * Retrieve the visited document.
   *
   * @return {@link Document} populated with stored fields. Note that only the stored information in
   *     the field instances is valid, data such as indexing options, term vector options, etc is
   *     not set.
   */
  public Document getDocument() {
    return doc;
  }
}
