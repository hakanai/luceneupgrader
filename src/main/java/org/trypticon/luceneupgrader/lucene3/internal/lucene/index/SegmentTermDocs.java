package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

/**
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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.FieldInfo.IndexOptions;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.BitVector;

import java.io.IOException;

class SegmentTermDocs implements TermDocs {
  protected SegmentReader parent;
  protected IndexInput freqStream;
  protected int count;
  protected int df;
  protected BitVector deletedDocs;
  int doc = 0;
  int freq;

  private long freqBasePointer;

  protected boolean currentFieldStoresPayloads;
  protected IndexOptions indexOptions;
  
  protected SegmentTermDocs(SegmentReader parent, boolean raw) {
    this.parent = parent;
    this.freqStream = (IndexInput) parent.core.freqStream.clone();
    if (!raw) {
      synchronized (parent) {
        this.deletedDocs = parent.deletedDocs;
      }
    } else {
      this.deletedDocs = null;
    }
  }

  protected SegmentTermDocs(SegmentReader parent) {
    this(parent, false);
  }

  public void seek(Term term) throws IOException {
    TermInfo ti = parent.core.getTermsReader().get(term);
    seek(ti, term);
  }

  public void seek(TermEnum termEnum) throws IOException {
    TermInfo ti;
    Term term;
    
    // use comparison of fieldinfos to verify that termEnum belongs to the same segment as this SegmentTermDocs
    if (termEnum instanceof SegmentTermEnum && ((SegmentTermEnum) termEnum).fieldInfos == parent.core.fieldInfos) {        // optimized case
      SegmentTermEnum segmentTermEnum = ((SegmentTermEnum) termEnum);
      term = segmentTermEnum.term();
      ti = segmentTermEnum.termInfo();
    } else  {                                         // punt case
      term = termEnum.term();
      ti = parent.core.getTermsReader().get(term);
    }
    
    seek(ti, term);
  }

  void seek(TermInfo ti, Term term) throws IOException {
    count = 0;
    FieldInfo fi = parent.core.fieldInfos.fieldInfo(term.field);
    indexOptions = (fi != null) ? fi.indexOptions : IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
    //noinspection SimplifiableConditionalExpression
    currentFieldStoresPayloads = (fi != null) ? fi.storePayloads : false;
    if (ti == null) {
      df = 0;
    } else {
      df = ti.docFreq;
      doc = 0;
      freqBasePointer = ti.freqPointer;
      freqStream.seek(freqBasePointer);
    }
  }

  public void close() throws IOException {
    freqStream.close();
  }

  public final int doc() { return doc; }
  public final int freq() { return freq; }

  protected void skippingDoc() {
  }

  public boolean next() throws IOException {
    while (true) {
      if (count == df)
        return false;
      final int docCode = freqStream.readVInt();
      
      if (indexOptions == IndexOptions.DOCS_ONLY) {
        doc += docCode;
        freq = 1;
      } else {
        doc += docCode >>> 1;       // shift off low bit
        if ((docCode & 1) != 0)       // if low bit is set
          freq = 1;         // freq is one
        else
          freq = freqStream.readVInt();     // else read freq
      }
      
      count++;

      if (deletedDocs == null || !deletedDocs.get(doc))
        break;
      skippingDoc();
    }
    return true;
  }
}
