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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.StoredFieldsReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfos;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.StoredFieldVisitor;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.AlreadyClosedException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IndexInput;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.RamUsageEstimator;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

import static org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40.Lucene40StoredFieldsWriter.*;

public final class Lucene40StoredFieldsReader extends StoredFieldsReader implements Cloneable, Closeable {

  private static final long RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(Lucene40StoredFieldsReader.class);

  private final FieldInfos fieldInfos;
  private final IndexInput fieldsStream;
  private final IndexInput indexStream;
  private int numTotalDocs;
  private int size;
  private boolean closed;


  @Override
  public Lucene40StoredFieldsReader clone() {
    ensureOpen();
    return new Lucene40StoredFieldsReader(fieldInfos, numTotalDocs, size, fieldsStream.clone(), indexStream.clone());
  }
  
  private Lucene40StoredFieldsReader(FieldInfos fieldInfos, int numTotalDocs, int size, IndexInput fieldsStream, IndexInput indexStream) {
    this.fieldInfos = fieldInfos;
    this.numTotalDocs = numTotalDocs;
    this.size = size;
    this.fieldsStream = fieldsStream;
    this.indexStream = indexStream;
  }

  public Lucene40StoredFieldsReader(Directory d, SegmentInfo si, FieldInfos fn, IOContext context) throws IOException {
    final String segment = si.name;
    boolean success = false;
    fieldInfos = fn;
    try {
      fieldsStream = d.openInput(IndexFileNames.segmentFileName(segment, "", FIELDS_EXTENSION), context);
      final String indexStreamFN = IndexFileNames.segmentFileName(segment, "", FIELDS_INDEX_EXTENSION);
      indexStream = d.openInput(indexStreamFN, context);
      
      CodecUtil.checkHeader(indexStream, CODEC_NAME_IDX, VERSION_START, VERSION_CURRENT);
      CodecUtil.checkHeader(fieldsStream, CODEC_NAME_DAT, VERSION_START, VERSION_CURRENT);
      assert HEADER_LENGTH_DAT == fieldsStream.getFilePointer();
      assert HEADER_LENGTH_IDX == indexStream.getFilePointer();
      final long indexSize = indexStream.length() - HEADER_LENGTH_IDX;
      this.size = (int) (indexSize >> 3);
      // Verify two sources of "maxDoc" agree:
      if (this.size != si.getDocCount()) {
        throw new CorruptIndexException("doc counts differ for segment " + segment + ": fieldsReader shows " + this.size + " but segmentInfo shows " + si.getDocCount());
      }
      numTotalDocs = (int) (indexSize >> 3);
      success = true;
    } finally {
      // With lock-less commits, it's entirely possible (and
      // fine) to hit a FileNotFound exception above. In
      // this case, we want to explicitly close any subset
      // of things that were opened so that we don't have to
      // wait for a GC to do so.
      if (!success) {
        try {
          close();
        } catch (Throwable t) {} // ensure we throw our original exception
      }
    }
  }

  private void ensureOpen() throws AlreadyClosedException {
    if (closed) {
      throw new AlreadyClosedException("this FieldsReader is closed");
    }
  }

  @Override
  public final void close() throws IOException {
    if (!closed) {
      IOUtils.close(fieldsStream, indexStream);
      closed = true;
    }
  }

  public final int size() {
    return size;
  }

  private void seekIndex(int docID) throws IOException {
    indexStream.seek(HEADER_LENGTH_IDX + docID * 8L);
  }

  @Override
  public final void visitDocument(int n, StoredFieldVisitor visitor) throws IOException {
    seekIndex(n);
    fieldsStream.seek(indexStream.readLong());

    final int numFields = fieldsStream.readVInt();
    for (int fieldIDX = 0; fieldIDX < numFields; fieldIDX++) {
      int fieldNumber = fieldsStream.readVInt();
      FieldInfo fieldInfo = fieldInfos.fieldInfo(fieldNumber);
      
      int bits = fieldsStream.readByte() & 0xFF;
      assert bits <= (FIELD_IS_NUMERIC_MASK | FIELD_IS_BINARY): "bits=" + Integer.toHexString(bits);

      switch(visitor.needsField(fieldInfo)) {
        case YES:
          readField(visitor, fieldInfo, bits);
          break;
        case NO: 
          skipField(bits);
          break;
        case STOP: 
          return;
      }
    }
  }

  private void readField(StoredFieldVisitor visitor, FieldInfo info, int bits) throws IOException {
    final int numeric = bits & FIELD_IS_NUMERIC_MASK;
    if (numeric != 0) {
      switch(numeric) {
        case FIELD_IS_NUMERIC_INT:
          visitor.intField(info, fieldsStream.readInt());
          return;
        case FIELD_IS_NUMERIC_LONG:
          visitor.longField(info, fieldsStream.readLong());
          return;
        case FIELD_IS_NUMERIC_FLOAT:
          visitor.floatField(info, Float.intBitsToFloat(fieldsStream.readInt()));
          return;
        case FIELD_IS_NUMERIC_DOUBLE:
          visitor.doubleField(info, Double.longBitsToDouble(fieldsStream.readLong()));
          return;
        default:
          throw new CorruptIndexException("Invalid numeric type: " + Integer.toHexString(numeric));
      }
    } else { 
      final int length = fieldsStream.readVInt();
      byte bytes[] = new byte[length];
      fieldsStream.readBytes(bytes, 0, length);
      if ((bits & FIELD_IS_BINARY) != 0) {
        visitor.binaryField(info, bytes);
      } else {
        visitor.stringField(info, new String(bytes, 0, bytes.length, StandardCharsets.UTF_8));
      }
    }
  }
  
  private void skipField(int bits) throws IOException {
    final int numeric = bits & FIELD_IS_NUMERIC_MASK;
    if (numeric != 0) {
      switch(numeric) {
        case FIELD_IS_NUMERIC_INT:
        case FIELD_IS_NUMERIC_FLOAT:
          fieldsStream.readInt();
          return;
        case FIELD_IS_NUMERIC_LONG:
        case FIELD_IS_NUMERIC_DOUBLE:
          fieldsStream.readLong();
          return;
        default: 
          throw new CorruptIndexException("Invalid numeric type: " + Integer.toHexString(numeric));
      }
    } else {
      final int length = fieldsStream.readVInt();
      fieldsStream.seek(fieldsStream.getFilePointer() + length);
    }
  }


  public final IndexInput rawDocs(int[] lengths, int startDocID, int numDocs) throws IOException {
    seekIndex(startDocID);
    long startOffset = indexStream.readLong();
    long lastOffset = startOffset;
    int count = 0;
    while (count < numDocs) {
      final long offset;
      final int docID = startDocID + count + 1;
      assert docID <= numTotalDocs;
      if (docID < numTotalDocs) 
        offset = indexStream.readLong();
      else
        offset = fieldsStream.length();
      lengths[count++] = (int) (offset-lastOffset);
      lastOffset = offset;
    }

    fieldsStream.seek(startOffset);

    return fieldsStream;
  }

  @Override
  public long ramBytesUsed() {
    return RAM_BYTES_USED;
  }

  @Override
  public void checkIntegrity() throws IOException {}
}
