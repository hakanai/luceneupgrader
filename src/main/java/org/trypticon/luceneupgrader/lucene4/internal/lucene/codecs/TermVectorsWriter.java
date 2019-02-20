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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

import java.io.Closeable;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsAndPositionsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRefBuilder;

public abstract class TermVectorsWriter implements Closeable {
  
  protected TermVectorsWriter() {
  }


  public abstract void startDocument(int numVectorFields) throws IOException;

  public void finishDocument() throws IOException {};

  public abstract void startField(FieldInfo info, int numTerms, boolean positions, boolean offsets, boolean payloads) throws IOException;

  public void finishField() throws IOException {};


  public abstract void startTerm(BytesRef term, int freq) throws IOException;

  public void finishTerm() throws IOException {}

  public abstract void addPosition(int position, int startOffset, int endOffset, BytesRef payload) throws IOException;
  
  public abstract void abort();


  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;


  // TODO: we should probably nuke this and make a more efficient 4.x format
  // PreFlex-RW could then be slow and buffer (its only used in tests...)
  public void addProx(int numProx, DataInput positions, DataInput offsets) throws IOException {
    int position = 0;
    int lastOffset = 0;
    BytesRefBuilder payload = null;

    for (int i = 0; i < numProx; i++) {
      final int startOffset;
      final int endOffset;
      final BytesRef thisPayload;
      
      if (positions == null) {
        position = -1;
        thisPayload = null;
      } else {
        int code = positions.readVInt();
        position += code >>> 1;
        if ((code & 1) != 0) {
          // This position has a payload
          final int payloadLength = positions.readVInt();

          if (payload == null) {
            payload = new BytesRefBuilder();
          }
          payload.grow(payloadLength);

          positions.readBytes(payload.bytes(), 0, payloadLength);
          payload.setLength(payloadLength);
          thisPayload = payload.get();
        } else {
          thisPayload = null;
        }
      }
      
      if (offsets == null) {
        startOffset = endOffset = -1;
      } else {
        startOffset = lastOffset + offsets.readVInt();
        endOffset = startOffset + offsets.readVInt();
        lastOffset = endOffset;
      }
      addPosition(position, startOffset, endOffset, thisPayload);
    }
  }
  

  public int merge(MergeState mergeState) throws IOException {
    int docCount = 0;
    for (int i = 0; i < mergeState.readers.size(); i++) {
      final AtomicReader reader = mergeState.readers.get(i);
      final int maxDoc = reader.maxDoc();
      final Bits liveDocs = reader.getLiveDocs();

      for (int docID = 0; docID < maxDoc; docID++) {
        if (liveDocs != null && !liveDocs.get(docID)) {
          // skip deleted docs
          continue;
        }
        // NOTE: it's very important to first assign to vectors then pass it to
        // termVectorsWriter.addAllDocVectors; see LUCENE-1282
        Fields vectors = reader.getTermVectors(docID);
        addAllDocVectors(vectors, mergeState);
        docCount++;
        mergeState.checkAbort.work(300);
      }
    }
    finish(mergeState.fieldInfos, docCount);
    return docCount;
  }
  
  protected final void addAllDocVectors(Fields vectors, MergeState mergeState) throws IOException {
    if (vectors == null) {
      startDocument(0);
      finishDocument();
      return;
    }

    int numFields = vectors.size();
    if (numFields == -1) {
      // count manually! TODO: Maybe enforce that Fields.size() returns something valid?
      numFields = 0;
      for (final Iterator<String> it = vectors.iterator(); it.hasNext(); ) {
        it.next();
        numFields++;
      }
    }
    startDocument(numFields);
    
    String lastFieldName = null;
    
    TermsEnum termsEnum = null;
    DocsAndPositionsEnum docsAndPositionsEnum = null;
    
    int fieldCount = 0;
    for(String fieldName : vectors) {
      fieldCount++;
      final FieldInfo fieldInfo = mergeState.fieldInfos.fieldInfo(fieldName);

      assert lastFieldName == null || fieldName.compareTo(lastFieldName) > 0: "lastFieldName=" + lastFieldName + " fieldName=" + fieldName;
      lastFieldName = fieldName;

      final Terms terms = vectors.terms(fieldName);
      if (terms == null) {
        // FieldsEnum shouldn't lie...
        continue;
      }
      
      final boolean hasPositions = terms.hasPositions();
      final boolean hasOffsets = terms.hasOffsets();
      final boolean hasPayloads = terms.hasPayloads();
      assert !hasPayloads || hasPositions;
      
      int numTerms = (int) terms.size();
      if (numTerms == -1) {
        // count manually. It is stupid, but needed, as Terms.size() is not a mandatory statistics function
        numTerms = 0;
        termsEnum = terms.iterator(termsEnum);
        while(termsEnum.next() != null) {
          numTerms++;
        }
      }
      
      startField(fieldInfo, numTerms, hasPositions, hasOffsets, hasPayloads);
      termsEnum = terms.iterator(termsEnum);

      int termCount = 0;
      while(termsEnum.next() != null) {
        termCount++;

        final int freq = (int) termsEnum.totalTermFreq();
        
        startTerm(termsEnum.term(), freq);

        if (hasPositions || hasOffsets) {
          docsAndPositionsEnum = termsEnum.docsAndPositions(null, docsAndPositionsEnum);
          assert docsAndPositionsEnum != null;
          
          final int docID = docsAndPositionsEnum.nextDoc();
          assert docID != DocIdSetIterator.NO_MORE_DOCS;
          assert docsAndPositionsEnum.freq() == freq;

          for(int posUpto=0; posUpto<freq; posUpto++) {
            final int pos = docsAndPositionsEnum.nextPosition();
            final int startOffset = docsAndPositionsEnum.startOffset();
            final int endOffset = docsAndPositionsEnum.endOffset();
            
            final BytesRef payload = docsAndPositionsEnum.getPayload();

            assert !hasPositions || pos >= 0;
            addPosition(pos, startOffset, endOffset, payload);
          }
        }
        finishTerm();
      }
      assert termCount == numTerms;
      finishField();
    }
    assert fieldCount == numFields;
    finishDocument();
  }
  
  public abstract Comparator<BytesRef> getComparator() throws IOException;

  @Override
  public abstract void close() throws IOException;
}
