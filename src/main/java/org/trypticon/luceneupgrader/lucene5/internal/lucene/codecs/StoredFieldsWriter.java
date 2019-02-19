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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.document.StoredField;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexableField;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexableFieldType;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;

public abstract class StoredFieldsWriter implements Closeable {
  
  protected StoredFieldsWriter() {
  }


  public abstract void startDocument() throws IOException;

  public void finishDocument() throws IOException {}

  public abstract void writeField(FieldInfo info, IndexableField field) throws IOException;
  

  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;
  

  public int merge(MergeState mergeState) throws IOException {
    int docCount = 0;
    for (int i=0;i<mergeState.storedFieldsReaders.length;i++) {
      StoredFieldsReader storedFieldsReader = mergeState.storedFieldsReaders[i];
      storedFieldsReader.checkIntegrity();
      MergeVisitor visitor = new MergeVisitor(mergeState, i);
      int maxDoc = mergeState.maxDocs[i];
      Bits liveDocs = mergeState.liveDocs[i];
      for (int docID=0;docID<maxDoc;docID++) {
        if (liveDocs != null && !liveDocs.get(docID)) {
          // skip deleted docs
          continue;
        }
        startDocument();
        storedFieldsReader.visitDocument(docID, visitor);
        finishDocument();
        docCount++;
      }
    }
    finish(mergeState.mergeFieldInfos, docCount);
    return docCount;
  }
  

  protected class MergeVisitor extends StoredFieldVisitor implements IndexableField {
    BytesRef binaryValue;
    String stringValue;
    Number numericValue;
    FieldInfo currentField;
    FieldInfos remapper;
    
    public MergeVisitor(MergeState mergeState, int readerIndex) {
      // if field numbers are aligned, we can save hash lookups
      // on every field access. Otherwise, we need to lookup
      // fieldname each time, and remap to a new number.
      for (FieldInfo fi : mergeState.fieldInfos[readerIndex]) {
        FieldInfo other = mergeState.mergeFieldInfos.fieldInfo(fi.number);
        if (other == null || !other.name.equals(fi.name)) {
          remapper = mergeState.mergeFieldInfos;
          break;
        }
      }
    }
    
    @Override
    public void binaryField(FieldInfo fieldInfo, byte[] value) throws IOException {
      reset(fieldInfo);
      // TODO: can we avoid new BR here?
      binaryValue = new BytesRef(value);
      write();
    }

    @Override
    public void stringField(FieldInfo fieldInfo, byte[] value) throws IOException {
      reset(fieldInfo);
      // TODO: can we avoid new String here?
      stringValue = new String(value, StandardCharsets.UTF_8);
      write();
    }

    @Override
    public void intField(FieldInfo fieldInfo, int value) throws IOException {
      reset(fieldInfo);
      numericValue = value;
      write();
    }

    @Override
    public void longField(FieldInfo fieldInfo, long value) throws IOException {
      reset(fieldInfo);
      numericValue = value;
      write();
    }

    @Override
    public void floatField(FieldInfo fieldInfo, float value) throws IOException {
      reset(fieldInfo);
      numericValue = value;
      write();
    }

    @Override
    public void doubleField(FieldInfo fieldInfo, double value) throws IOException {
      reset(fieldInfo);
      numericValue = value;
      write();
    }

    @Override
    public Status needsField(FieldInfo fieldInfo) throws IOException {
      return Status.YES;
    }

    @Override
    public String name() {
      return currentField.name;
    }

    @Override
    public IndexableFieldType fieldType() {
      return StoredField.TYPE;
    }

    @Override
    public BytesRef binaryValue() {
      return binaryValue;
    }

    @Override
    public String stringValue() {
      return stringValue;
    }

    @Override
    public Number numericValue() {
      return numericValue;
    }

    @Override
    public Reader readerValue() {
      return null;
    }
    
    @Override
    public float boost() {
      return 1F;
    }

    @Override
    public TokenStream tokenStream(Analyzer analyzer, TokenStream reuse) {
      return null;
    }

    void reset(FieldInfo field) {
      if (remapper != null) {
        // field numbers are not aligned, we need to remap to the new field number
        currentField = remapper.fieldInfo(field.name);
      } else {
        currentField = field;
      }
      binaryValue = null;
      stringValue = null;
      numericValue = null;
    }
    
    void write() throws IOException {
      writeField(currentField, this);
    }
  }

  @Override
  public abstract void close() throws IOException;
}
