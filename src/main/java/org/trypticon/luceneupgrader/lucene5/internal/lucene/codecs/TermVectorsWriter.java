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
import java.util.Iterator;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.PostingsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Terms;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermsEnum;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.DataInput;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.BytesRefBuilder;

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


  public abstract void finish(FieldInfos fis, int numDocs) throws IOException;


  // TODO: we should probably nuke this and make a more efficient 4.x format
  // PreFlex-RW could then be slow and buffer (it's only used in tests...)
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
    int numReaders = mergeState.maxDocs.length;
    for (int i = 0; i < numReaders; i++) {
      int maxDoc = mergeState.maxDocs[i];
      Bits liveDocs = mergeState.liveDocs[i];
      TermVectorsReader termVectorsReader = mergeState.termVectorsReaders[i];
      if (termVectorsReader != null) {
        termVectorsReader.checkIntegrity();
      }

      for (int docID=0;docID<maxDoc;docID++) {
        if (liveDocs != null && !liveDocs.get(docID)) {
          // skip deleted docs
          continue;
        }
        // NOTE: it's very important to first assign to vectors then pass it to
        // termVectorsWriter.addAllDocVectors; see LUCENE-1282
        Fields vectors;
        if (termVectorsReader == null) {
          vectors = null;
        } else {
          vectors = termVectorsReader.get(docID);
        }
        addAllDocVectors(vectors, mergeState);
        docCount++;
      }
    }
    finish(mergeState.mergeFieldInfos, docCount);
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
    PostingsEnum docsAndPositionsEnum = null;
    
    int fieldCount = 0;
    for(String fieldName : vectors) {
      fieldCount++;
      final FieldInfo fieldInfo = mergeState.mergeFieldInfos.fieldInfo(fieldName);

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
        termsEnum = terms.iterator();
        while(termsEnum.next() != null) {
          numTerms++;
        }
      }
      
      startField(fieldInfo, numTerms, hasPositions, hasOffsets, hasPayloads);
      termsEnum = terms.iterator();

      int termCount = 0;
      while(termsEnum.next() != null) {
        termCount++;

        final int freq = (int) termsEnum.totalTermFreq();
        
        startTerm(termsEnum.term(), freq);

        if (hasPositions || hasOffsets) {
          docsAndPositionsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS | PostingsEnum.PAYLOADS);
          assert docsAndPositionsEnum != null;
          
          final int docID = docsAndPositionsEnum.nextDoc();
          assert docID != DocIdSetIterator.NO_MORE_DOCS;
          assert docsAndPositionsEnum.freq() == freq;

          for(int posUpto=0; posUpto<freq; posUpto++) {
            final int pos = docsAndPositionsEnum.nextPosition();
            final int startOffset = docsAndPositionsEnum.startOffset();
            final int endOffset = docsAndPositionsEnum.endOffset();
            
            final BytesRef payload = docsAndPositionsEnum.getPayload();

            assert !hasPositions || pos >= 0 ;
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

  @Override
  public abstract void close() throws IOException;
}
