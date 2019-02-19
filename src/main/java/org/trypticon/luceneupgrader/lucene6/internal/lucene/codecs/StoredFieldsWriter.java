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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis.Analyzer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis.TokenStream;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.document.StoredField;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.DocIDMerger;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexableField;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexableFieldType;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;

import static org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public abstract class StoredFieldsWriter implements Closeable {
  
  protected StoredFieldsWriter() {
  }


  public abstract void startDocument() throws IOException;

  public void finishDocument() throws IOException {}

  public abstract void writeField(FieldInfo info, IndexableField field) throws IOException;
  

  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;

  private static class StoredFieldsMergeSub extends DocIDMerger.Sub {
    private final StoredFieldsReader reader;
    private final int maxDoc;
    private final MergeVisitor visitor;
    int docID = -1;

    public StoredFieldsMergeSub(MergeVisitor visitor, MergeState.DocMap docMap, StoredFieldsReader reader, int maxDoc) {
      super(docMap);
      this.maxDoc = maxDoc;
      this.reader = reader;
      this.visitor = visitor;
    }

    @Override
    public int nextDoc() {
      docID++;
      if (docID == maxDoc) {
        return NO_MORE_DOCS;
      } else {
        return docID;
      }
    }
  }
  

  public int merge(MergeState mergeState) throws IOException {
    List<StoredFieldsMergeSub> subs = new ArrayList<>();
    for(int i=0;i<mergeState.storedFieldsReaders.length;i++) {
      StoredFieldsReader storedFieldsReader = mergeState.storedFieldsReaders[i];
      storedFieldsReader.checkIntegrity();
      subs.add(new StoredFieldsMergeSub(new MergeVisitor(mergeState, i), mergeState.docMaps[i], storedFieldsReader, mergeState.maxDocs[i]));
    }

    final DocIDMerger<StoredFieldsMergeSub> docIDMerger = DocIDMerger.of(subs, mergeState.needsIndexSort);

    int docCount = 0;
    while (true) {
      StoredFieldsMergeSub sub = docIDMerger.next();
      if (sub == null) {
        break;
      }
      assert sub.mappedDocID == docCount;
      startDocument();
      sub.reader.visitDocument(sub.docID, sub.visitor);
      finishDocument();
      docCount++;
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
