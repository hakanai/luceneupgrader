package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexableField;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;

public abstract class StoredFieldsWriter implements Closeable {
  
  protected StoredFieldsWriter() {
  }


  public abstract void startDocument() throws IOException;

  public void finishDocument() throws IOException {}

  public abstract void writeField(FieldInfo info, IndexableField field) throws IOException;

  public abstract void abort();
  

  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;
  

  public int merge(MergeState mergeState) throws IOException {
    int docCount = 0;
    for (AtomicReader reader : mergeState.readers) {
      final int maxDoc = reader.maxDoc();
      final Bits liveDocs = reader.getLiveDocs();
      for (int i = 0; i < maxDoc; i++) {
        if (liveDocs != null && !liveDocs.get(i)) {
          // skip deleted docs
          continue;
        }
        // TODO: this could be more efficient using
        // FieldVisitor instead of loading/writing entire
        // doc; ie we just have to renumber the field number
        // on the fly?
        // NOTE: it's very important to first assign to doc then pass it to
        // fieldsWriter.addDocument; see LUCENE-1282
        Document doc = reader.document(i);
        addDocument(doc, mergeState.fieldInfos);
        docCount++;
        mergeState.checkAbort.work(300);
      }
    }
    finish(mergeState.fieldInfos, docCount);
    return docCount;
  }
  
  protected final void addDocument(Iterable<? extends IndexableField> doc, FieldInfos fieldInfos) throws IOException {
    startDocument();
    for (IndexableField field : doc) {
      if (field.fieldType().stored()) {
        writeField(fieldInfos.fieldInfo(field.name()), field);
      }
    }

    finishDocument();
  }

  @Override
  public abstract void close() throws IOException;
}
